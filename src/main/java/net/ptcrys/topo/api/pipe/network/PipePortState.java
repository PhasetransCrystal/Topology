package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipeFilterAdapter;
import net.ptcrys.topo.api.pipe.PipeFilterSettings;
import net.ptcrys.topo.api.pipe.PipePortFilter;
import net.ptcrys.topo.api.pipe.PipePortSettings;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeStrategyOffer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.resource.Resource;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Persisted port configuration domain: strategy selection, amount/interval windows, filters, and
 * their UI mutation surface. This class never owns topology or tick scheduling; every successful
 * config write advances a per-node revision that the tick loop uses to invalidate its local cache.
 */
final class PipePortState implements PipePortSettings {

    private final PipeNetworksSavedData data;
    private final Long2LongOpenHashMap revisions = new Long2LongOpenHashMap();

    PipePortState(PipeNetworksSavedData data) {
        this.data = Objects.requireNonNull(data, "pipe saved data");
    }

    long revision(long pos) {
        return revisions.get(pos);
    }

    void setExtractStrategy(BlockPos pos, Direction side, PipeDistributionStrategy strategy) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipeStrategyOffer offer = record.definition().offerFor(strategy);
        if (offer == null) {
            return;
        }
        putExtractConfig(pos, record, side, offer.strategy().initialPortConfig(record.definition(), offer.aggregation()));
    }

    void setExtractConfig(BlockPos pos, Direction side, PipePortStrategyConfig config) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipeStrategyOffer offer = record.definition().offerFor(config.strategy());
        if (offer != null) {
            putExtractConfig(pos, record, side, clampToOffer(config, record.definition(), offer));
        }
    }

    void putExtractConfig(BlockPos pos, PipeNodeRecord record, Direction side, PipePortStrategyConfig config) {
        data.putExtractConfig(record, side, config);
        markChanged(pos.asLong());
    }

    private void markChanged(long pos) {
        revisions.addTo(pos, 1L);
    }

    private PipePortStrategyConfig clampToOffer(
                                                PipePortStrategyConfig config, PipeDefinition definition, PipeStrategyOffer offer) {
        AggregationWindow window = offer.aggregation();
        PipePortStrategyConfig result = config;
        int interval = config.interval();
        if (window.clamp(interval) != interval) {
            result = result.withInterval(window.clamp(interval));
        }
        int maxAmount = definition.maxBatchAmount(result.interval());
        int amount = result.amountOr(maxAmount);
        if (Math.clamp(amount, 0, maxAmount) != amount) {
            result = result.withAmount(Math.clamp(amount, 0, maxAmount));
        }
        return result;
    }

    @Override
    public PipePortStrategyConfig config(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            throw new IllegalStateException("No pipe node at " + pos);
        }
        return healRecordConfig(pos.asLong(), record, side);
    }

    private PipePortStrategyConfig healRecordConfig(long pos, PipeNodeRecord record, Direction side) {
        PipeDefinition definition = record.definition();
        PipePortStrategyConfig stored = record.extractConfig(side);
        PipeStrategyOffer offer = stored == null ? null : definition.offerFor(stored.strategy());
        PipePortStrategyConfig healed;
        if (stored == null || offer == null) {
            PipeStrategyOffer initial = definition.initialOffer();
            healed = initial.strategy().initialPortConfig(definition, initial.aggregation());
        } else {
            healed = clampToOffer(stored, definition, offer);
        }
        if (healed != stored) {
            data.putExtractConfig(record, side, healed);
            markChanged(pos);
        }
        return healed;
    }

    @Override
    public int strategyIndex(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return -1;
        }
        PipeDistributionStrategy strategy = config(pos, side).strategy();
        List<PipeStrategyOffer> offers = record.definition().strategies();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).strategy() == strategy) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int amount(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return 0;
        }
        PipePortStrategyConfig config = config(pos, side);
        int maxAmount = record.definition().maxBatchAmount(config.interval());
        return Math.clamp(config.amountOr(maxAmount), 0, maxAmount);
    }

    @Override
    public int interval(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return 1;
        }
        return Math.max(1, config(pos, side).interval());
    }

    @Override
    public @Nullable AggregationWindow window(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return null;
        }
        PipeStrategyOffer offer = record.definition().offerFor(config(pos, side).strategy());
        return offer == null ? null : offer.aggregation();
    }

    @Override
    public void selectStrategy(BlockPos pos, Direction side, int index) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        List<PipeStrategyOffer> offers = record.definition().strategies();
        if (index >= 0 && index < offers.size()) {
            setExtractStrategy(pos, side, offers.get(index).strategy());
        }
    }

    @Override
    public void adjustAmount(BlockPos pos, Direction side, int delta) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = config(pos, side);
        int maxAmount = record.definition().maxBatchAmount(config.interval());
        int newAmount = (int) Math.clamp((long) config.amountOr(maxAmount) + delta, 0, maxAmount);
        setExtractConfig(pos, side, config.withAmount(newAmount));
    }

    @Override
    public void setAmount(BlockPos pos, Direction side, int amount) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = config(pos, side);
        int maxAmount = record.definition().maxBatchAmount(config.interval());
        setExtractConfig(pos, side, config.withAmount(Math.clamp(amount, 0, maxAmount)));
    }

    @Override
    public void setInterval(BlockPos pos, Direction side, int interval) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = config(pos, side);
        setExtractConfig(pos, side, config.withInterval(Math.max(1, interval)));
    }

    @Override
    public void adjustInterval(BlockPos pos, Direction side, int delta) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = config(pos, side);
        long target = (long) config.interval() + delta;
        setExtractConfig(pos, side, config.withInterval(
                (int) Math.clamp(target, 1, Integer.MAX_VALUE)));
    }

    @Override
    public void updateConfig(BlockPos pos, Direction side, UnaryOperator<PipePortStrategyConfig> update) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig current = config(pos, side);
        PipePortStrategyConfig updated = update.apply(current);
        if (updated != null && updated.strategy() == current.strategy()) {
            setExtractConfig(pos, side, updated);
        }
    }

    @Override
    public PipePortFilter filter(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        return record == null ? PipePortFilter.EMPTY : healRecordFilter(pos.asLong(), record, side);
    }

    @Override
    public void addFilterEntry(BlockPos pos, Direction side, boolean white, String rawEntry) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipeFilterSettings settings = record.definition().filterSettings();
        if (!settings.enabled()) {
            return;
        }
        String entry = PipePortFilter.normalizeEntry(rawEntry);
        if (entry == null || (PipePortFilter.isTagEntry(entry) && !settings.allowTags())) {
            return;
        }
        config(pos, side);
        PipePortFilter current = healRecordFilter(pos.asLong(), record, side);
        if (current.list(white).size() >= settings.entryCapacity()) {
            return;
        }
        PipePortFilter updated = current.withAdded(white, entry);
        if (updated != current) {
            data.putExtractFilter(record, side, updated);
            markChanged(pos.asLong());
        }
    }

    @Override
    public void removeFilterEntry(BlockPos pos, Direction side, boolean white, String entry) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortFilter current = healRecordFilter(pos.asLong(), record, side);
        PipePortFilter updated = current.withRemoved(white, entry);
        if (updated != current) {
            data.putExtractFilter(record, side, updated);
            markChanged(pos.asLong());
        }
    }

    private PipePortFilter healRecordFilter(long pos, PipeNodeRecord record, Direction side) {
        PipePortFilter stored = record.extractFilter(side);
        PipePortFilter healed = clampFilter(stored, record.definition().filterSettings());
        if (!healed.equals(stored)) {
            data.putExtractFilter(record, side, healed);
            markChanged(pos);
        }
        return healed;
    }

    private static PipePortFilter clampFilter(PipePortFilter filter, PipeFilterSettings settings) {
        if (filter.isEmpty()) {
            return filter;
        }
        if (!settings.enabled()) {
            return PipePortFilter.EMPTY;
        }
        if (compliant(filter.whitelist(), settings) && compliant(filter.blacklist(), settings)) {
            return filter;
        }
        return new PipePortFilter(
                clampEntries(filter.whitelist(), settings),
                clampEntries(filter.blacklist(), settings));
    }

    private static boolean compliant(List<String> entries, PipeFilterSettings settings) {
        if (entries.size() > settings.entryCapacity()) {
            return false;
        }
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            if (!entry.equals(PipePortFilter.normalizeEntry(entry)) || (PipePortFilter.isTagEntry(entry) && !settings.allowTags()) || entries.indexOf(entry) != i) {
                return false;
            }
        }
        return true;
    }

    private static List<String> clampEntries(List<String> entries, PipeFilterSettings settings) {
        List<String> result = new ArrayList<>(Math.min(entries.size(), settings.entryCapacity()));
        for (String raw : entries) {
            if (result.size() >= settings.entryCapacity()) {
                break;
            }
            String normalized = PipePortFilter.normalizeEntry(raw);
            if (normalized == null || (PipePortFilter.isTagEntry(normalized) && !settings.allowTags()) || result.contains(normalized)) {
                continue;
            }
            result.add(normalized);
        }
        return result;
    }

    <R extends Resource> @Nullable Predicate<R> resolvePortFilter(
                                                                  PipeNetwork.ExtractorPort<R> port, PipeDefinition definition) {
        if (!definition.filterSettings().enabled()) {
            return null;
        }
        PipeFilterAdapter<R> adapter = definition.profile().typedFilterAdapter();
        if (adapter == null) {
            return null;
        }
        PipeNodeRecord record = data.node(port.pos);
        if (record == null) {
            return null;
        }
        return healRecordFilter(port.pos, record, port.side).compile(adapter);
    }

    PipePortStrategyConfig resolvePortConfig(PipeNetwork.ExtractorPort<?> port, PipeDefinition definition) {
        PipeNodeRecord record = data.node(port.pos);
        if (record == null) {
            PipeStrategyOffer initial = definition.initialOffer();
            return initial.strategy().initialPortConfig(definition, initial.aggregation());
        }
        return healRecordConfig(port.pos, record, port.side);
    }
}
