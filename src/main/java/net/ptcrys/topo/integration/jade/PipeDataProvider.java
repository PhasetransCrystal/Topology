package net.ptcrys.topo.integration.jade;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.network.PipeNodeRecord;
import net.ptcrys.topo.data.pipe.BuiltinOIPipeDistributionStrategies;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;

import org.jspecify.annotations.NonNull;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Server-side Jade payload for pipe nodes: last-tick node budget usage, per-side roles and flows,
 * extraction port strategy details and the network summary. All values come from the runtime
 * ledger's completed-tick snapshot — a pure cold-path read.
 */
public final class PipeDataProvider implements IServerDataProvider<BlockAccessor> {

    public static final PipeDataProvider INSTANCE = new PipeDataProvider();

    static final String ROLES_KEY = "roles";
    static final String THROUGHPUT_KEY = "throughput";
    static final String USED_KEY = "used";
    static final String FLOWS_KEY = "flows";
    static final String WINDOW_TICKS_KEY = "windowTicks";
    static final String EXTRACT_PREFIX = "extract_";
    static final String EXTRACT_STRATEGY_KEY = "strategy";
    static final String EXTRACT_RATE_KEY = "rate";
    static final String EXTRACT_CAP_KEY = "cap";
    static final String EXTRACT_INTERVAL_KEY = "interval";
    static final String EXTRACT_DETAIL_KEY = "detail";
    static final String NET_NODES_KEY = "netNodes";
    static final String NET_EXTRACTORS_KEY = "netExtractors";
    static final String NET_DESTINATIONS_KEY = "netDestinations";
    static final String NET_MOVED_KEY = "netMoved";
    static final String NET_PEAK_KEY = "netPeak";
    static final String NET_TICK_NANOS_KEY = "netTickNanos";

    private static final Identifier UID = IdHelper.oi("pipe");

    private PipeDataProvider() {}

    @Override
    public @NonNull Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(@NonNull CompoundTag data, @NonNull BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockPos pos = accessor.getPosition();
        PipeLevelRuntime runtime = PipeNetworkEngine.runtime(serverLevel);
        PipeNodeRecord record = runtime.data().node(pos.asLong());
        if (record == null) {
            return;
        }
        PipeNetwork<?> network = runtime.networkAt(pos);
        if (network == null) {
            return;
        }
        int nodeIndex = network.nodeIndexOf(pos.asLong());
        if (nodeIndex < 0) {
            return;
        }
        data.putInt(ROLES_KEY, record.roleBits());
        data.putInt(THROUGHPUT_KEY, network.throughputOf(nodeIndex));
        data.putLong(USED_KEY, network.windowNodeUsedLast(nodeIndex));
        data.putInt(WINDOW_TICKS_KEY, network.displayWindowTicks());
        long[] flows = new long[6];
        for (Direction direction : Direction.values()) {
            flows[direction.ordinal()] = network.windowFlowLast(nodeIndex, direction);
        }
        data.putLongArray(FLOWS_KEY, flows);

        PipeDefinition definition = record.definition();
        for (Direction direction : Direction.values()) {
            if (record.role(direction) != PipeSideRole.EXTRACT) {
                continue;
            }
            PipePortStrategyConfig config = runtime.portConfig(pos, direction);
            PipeDistributionStrategy strategy = config.strategy();
            int interval = Math.max(1, config.interval());
            int maxAmount = definition.maxBatchAmount(interval);
            CompoundTag extract = new CompoundTag();
            extract.putString(EXTRACT_STRATEGY_KEY, strategy.translationKey());
            // Per-window batch amounts: the panel renders them against the interval directly.
            extract.putInt(EXTRACT_RATE_KEY, Math.clamp(strategy.budget(definition, config), 0, maxAmount));
            extract.putInt(EXTRACT_CAP_KEY, maxAmount);
            extract.putInt(EXTRACT_INTERVAL_KEY, interval);
            if (config instanceof BuiltinOIPipeDistributionStrategies.ByDistanceConfig byDistance) {
                extract.putString(EXTRACT_DETAIL_KEY, byDistance.order().nameLang().key());
            }
            data.put(EXTRACT_PREFIX + direction.getSerializedName(), extract);
        }

        data.putInt(NET_NODES_KEY, network.nodeCount());
        data.putInt(NET_EXTRACTORS_KEY, network.extractorCount());
        data.putInt(NET_DESTINATIONS_KEY, network.destinationCount());
        data.putLong(NET_MOVED_KEY, network.windowMovedLast());
        data.putInt(NET_PEAK_KEY, network.bottleneckPercentLastWindow());
        data.putLong(NET_TICK_NANOS_KEY, network.lastBatchNanos());
    }
}
