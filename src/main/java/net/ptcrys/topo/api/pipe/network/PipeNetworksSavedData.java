package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategies;
import net.ptcrys.topo.api.pipe.PipePortFilter;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.Pipes;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Per-dimension authoritative pipe store. Persists every node's definition id, packed intent,
 * packed effective roles and extract-port configs; the runtime graph is derived from these
 * records without touching world blocks, so topology survives across unloaded chunks.
 *
 * <p>
 * Degradation policy: nodes whose definition id no longer resolves are dropped on load with a
 * log line; port configs whose strategy no longer resolves fail their single codec entry and the
 * port falls back to the definition's default strategy on next access.
 */
public final class PipeNetworksSavedData extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeNetworksSavedData.class);

    private record PortEntry(Direction side, PipePortStrategyConfig config, PipePortFilter filter) {

        static final Codec<PortEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Direction.CODEC.fieldOf("side").forGetter(PortEntry::side),
                PipeDistributionStrategies.CONFIG_CODEC.fieldOf("config").forGetter(PortEntry::config),
                PipePortFilter.CODEC.optionalFieldOf("filter", PipePortFilter.EMPTY).forGetter(PortEntry::filter)).apply(instance, PortEntry::new));
    }

    private record NodeEntry(BlockPos pos, Identifier definitionId, int intent, int roles, List<PortEntry> ports) {

        static final Codec<NodeEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(NodeEntry::pos),
                Identifier.CODEC.fieldOf("definition").forGetter(NodeEntry::definitionId),
                Codec.INT.fieldOf("intent").forGetter(NodeEntry::intent),
                Codec.INT.fieldOf("roles").forGetter(NodeEntry::roles),
                PortEntry.CODEC.listOf().optionalFieldOf("ports", List.of()).forGetter(NodeEntry::ports)).apply(instance, NodeEntry::new));
    }

    private static final Codec<List<NodeEntry>> ENTRIES_CODEC = NodeEntry.CODEC.listOf()
            .promotePartial(error -> LOGGER.error("Dropped corrupted pipe node entries: {}", error));

    public static final Codec<PipeNetworksSavedData> CODEC = ENTRIES_CODEC.fieldOf("nodes").codec().xmap(PipeNetworksSavedData::new, PipeNetworksSavedData::toEntries);

    public static final SavedDataType<PipeNetworksSavedData> TYPE = new SavedDataType<>(IdHelper.id("pipe_networks"), PipeNetworksSavedData::new, CODEC);

    private final Long2ObjectOpenHashMap<PipeNodeRecord> nodes = new Long2ObjectOpenHashMap<>();
    /** Runtime index: chunk long -> node positions inside it. Rebuilt on load, maintained on edit. */
    private final Long2ObjectOpenHashMap<LongArrayList> chunkIndex = new Long2ObjectOpenHashMap<>();

    public PipeNetworksSavedData() {}

    private PipeNetworksSavedData(List<NodeEntry> entries) {
        for (NodeEntry entry : entries) {
            PipeDefinition definition = Pipes.byId(entry.definitionId());
            if (definition == null) {
                LOGGER.warn("Dropping pipe node at {}: unknown pipe definition '{}'", entry.pos(), entry.definitionId());
                continue;
            }
            EnumMap<Direction, PipePortStrategyConfig> configs = null;
            EnumMap<Direction, PipePortFilter> filters = null;
            for (PortEntry port : entry.ports()) {
                if (configs == null) {
                    configs = new EnumMap<>(Direction.class);
                }
                configs.put(port.side(), port.config());
                if (!port.filter().isEmpty()) {
                    if (filters == null) {
                        filters = new EnumMap<>(Direction.class);
                    }
                    filters.put(port.side(), port.filter());
                }
            }
            long key = entry.pos().asLong();
            nodes.put(key, new PipeNodeRecord(definition, entry.intent(), entry.roles(), configs, filters));
            chunkIndexAdd(key);
        }
    }

    private List<NodeEntry> toEntries() {
        List<NodeEntry> entries = new ArrayList<>(nodes.size());
        for (var iterator = nodes.long2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
            var entry = iterator.next();
            PipeNodeRecord record = entry.getValue();
            List<PortEntry> ports = List.of();
            Map<Direction, PipePortStrategyConfig> configs = record.extractConfigsView();
            if (!configs.isEmpty()) {
                ports = new ArrayList<>(configs.size());
                for (Map.Entry<Direction, PipePortStrategyConfig> config : configs.entrySet()) {
                    ports.add(new PortEntry(
                            config.getKey(), config.getValue(), record.extractFilter(config.getKey())));
                }
            }
            entries.add(new NodeEntry(
                    BlockPos.of(entry.getLongKey()), record.definition().id(),
                    record.intentBits(), record.roleBits(), ports));
        }
        return entries;
    }

    public static PipeNetworksSavedData of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable PipeNodeRecord node(long pos) {
        return nodes.get(pos);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public PipeNodeRecord addNode(long pos, PipeDefinition definition, int intentBits) {
        PipeNodeRecord record = new PipeNodeRecord(definition, intentBits, 0, null, null);
        PipeNodeRecord previous = nodes.put(pos, record);
        if (previous == null) {
            chunkIndexAdd(pos);
        }
        setDirty();
        return record;
    }

    public @Nullable PipeNodeRecord removeNode(long pos) {
        PipeNodeRecord removed = nodes.remove(pos);
        if (removed != null) {
            chunkIndexRemove(pos);
            setDirty();
        }
        return removed;
    }

    public void setIntentBits(PipeNodeRecord record, int intentBits) {
        if (record.intentBits() != intentBits) {
            record.setIntentBits(intentBits);
            setDirty();
        }
    }

    public void setRoleBits(PipeNodeRecord record, int roleBits) {
        if (record.roleBits() != roleBits) {
            record.setRoleBits(roleBits);
            setDirty();
        }
    }

    public void putExtractConfig(PipeNodeRecord record, Direction side, PipePortStrategyConfig config) {
        record.putExtractConfig(side, Objects.requireNonNull(config, "port config"));
        setDirty();
    }

    public void removeExtractConfig(PipeNodeRecord record, Direction side) {
        if (record.extractConfig(side) != null) {
            record.removeExtractConfig(side);
            setDirty();
        }
    }

    /** Write one extraction side's filter; an empty filter clears the stored entry. */
    public void putExtractFilter(PipeNodeRecord record, Direction side, PipePortFilter filter) {
        record.putExtractFilter(side, Objects.requireNonNull(filter, "port filter"));
        setDirty();
    }

    /** Node positions recorded for the given chunk; empty when none. Read-only borrow. */
    public LongArrayList nodesInChunk(long chunkKey) {
        LongArrayList list = chunkIndex.get(chunkKey);
        return list == null ? EMPTY : list;
    }

    private static final LongArrayList EMPTY = new LongArrayList(0);

    private void chunkIndexAdd(long pos) {
        chunkIndex.computeIfAbsent(chunkKeyOf(pos), unused -> new LongArrayList(4)).add(pos);
    }

    private void chunkIndexRemove(long pos) {
        long chunkKey = chunkKeyOf(pos);
        LongArrayList list = chunkIndex.get(chunkKey);
        if (list != null) {
            list.rem(pos);
            if (list.isEmpty()) {
                chunkIndex.remove(chunkKey);
            }
        }
    }

    private static long chunkKeyOf(long pos) {
        return new ChunkPos(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4).pack();
    }
}
