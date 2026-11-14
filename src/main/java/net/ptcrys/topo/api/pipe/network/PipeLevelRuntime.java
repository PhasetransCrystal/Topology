package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.api.tick.TickHub;
import net.ptcrys.topo.api.api.tick.TickKind;
import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionContext;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipePortSettings;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.api.pipe.PipeStrategyOffer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Per-dimension pipe runtime: owns the lazily flooded network components over the saved-data
 * graph, recomputes side roles and derived visuals on server events, and drives the per-tick
 * extraction pass.
 *
 * <p>
 * Threading: main server thread only. Reentrancy from capability side effects during the
 * transfer pass is handled by deferring structural work — port refreshes and component rebuilds
 * triggered while {@code ticking} queue up and resolve after the pass.
 */
public final class PipeLevelRuntime {

    private static final Logger LOGGER = LoggerFactory.getLogger(PipeLevelRuntime.class);
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final PipeNetwork<?>[] EMPTY_SNAPSHOT = new PipeNetwork<?>[0];

    private final ServerLevel level;
    private final PipeNetworksSavedData data;
    private final PipeTopology topology;
    private final PipePortState portState;
    private final DistributionContext context = new DistributionContext();

    private @Nullable TickHandle tickHandle;

    PipeLevelRuntime(ServerLevel level) {
        this.level = level;
        this.data = PipeNetworksSavedData.of(level);
        this.topology = new PipeTopology(level, data, this::ensureTickHook);
        this.portState = new PipePortState(data);
    }

    public ServerLevel level() {
        return level;
    }

    public PipeNetworksSavedData data() {
        return data;
    }

    void close() {
        TickHandle handle = tickHandle;
        tickHandle = null;
        if (handle != null) {
            handle.unsubscribe();
        }
    }

    private void ensureTickHook() {
        if (tickHandle == null) {
            TickHub hub = TickHub.of(level);
            if (hub != null) {
                tickHandle = hub.register(TickKind.SYNC, 1, (gameTime, handle) -> tick(gameTime));
            }
        }
    }

    // --- block event surface -------------------------------------------------------------

    void onPipePlaced(BlockPos pos, PipeDefinition definition) {
        topology.onPipePlaced(pos, definition);
    }

    void onPipeRemoved(BlockPos pos) {
        topology.onPipeRemoved(pos);
    }

    void onNeighborChanged(BlockPos pos) {
        topology.onNeighborChanged(pos);
    }

    public @Nullable PipeNetwork<?> networkAt(BlockPos pos) {
        return topology.networkAt(pos);
    }

    void reconcileChunk(long chunkKey) {
        topology.reconcileChunk(chunkKey);
    }

    // --- server-side configuration API (wrench, port GUI, tests) ------------------------------

    /** Set one side's player intent directly and refresh roles/visuals/topology. */
    public void setSideIntent(BlockPos pos, Direction side, PipeSideIntent intent) {
        topology.setSideIntent(pos, side, intent);
    }

    /** Switch an extraction port to an offered strategy, resetting to its initial port config. */
    public void setExtractStrategy(BlockPos pos, Direction side, PipeDistributionStrategy strategy) {
        portState.setExtractStrategy(pos, side, strategy);
    }

    /** Replace an extraction port's strategy config; fields are re-clamped against the offer. */
    public void setExtractConfig(BlockPos pos, Direction side, PipePortStrategyConfig config) {
        portState.setExtractConfig(pos, side, config);
    }

    // --- port screen plumbing -----------------------------------------------------------------

    /** The port a player just clicked, parked until the menu's server-side createUI consumes it. */
    public record PendingPort(BlockPos pos, Direction side) {}

    private final Map<UUID, PendingPort> pendingPortScreens = new HashMap<>();

    /** Open the LDLib2 port screen for an extraction port; false when it cannot open. */
    boolean openPortScreen(BlockPos pos, Direction side, Player player) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null || record.role(side) != PipeSideRole.EXTRACT || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        pendingPortScreens.put(player.getUUID(), new PendingPort(pos.immutable(), side));
        if (BlockUIMenuType.openUI(serverPlayer, pos)) {
            return true;
        }
        pendingPortScreens.remove(player.getUUID());
        return false;
    }

    /** Server-side createUI picks up (and clears) the clicked port for this player. */
    public @Nullable PendingPort consumePendingPortScreen(UUID playerId) {
        return pendingPortScreens.remove(playerId);
    }

    /** Picks up the pending port only when it belongs to the block whose UI is being created. */
    public @Nullable PendingPort consumePendingPortScreen(UUID playerId, BlockPos expectedPos) {
        PendingPort pending = pendingPortScreens.get(playerId);
        if (pending == null) {
            return null;
        }
        pendingPortScreens.remove(playerId);
        return pending.pos().equals(expectedPos) ? pending : null;
    }

    /** Persisted per-side port settings; topology and tick scheduling remain runtime-owned. */
    public PipePortSettings ports() {
        return portState;
    }

    /**
     * Wrench fallback when no port screen is installed: cycle the extraction port through the
     * strategies its tier unlocks (declaration order), resetting the config to the new strategy's
     * default, and tell the player.
     */
    boolean cycleExtractStrategy(BlockPos pos, Direction side, Player player) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null || record.role(side) != PipeSideRole.EXTRACT) {
            return false;
        }
        PipeDefinition definition = record.definition();
        List<PipeStrategyOffer> offers = definition.strategies();
        PipePortStrategyConfig config = record.extractConfig(side);
        // A port without a persisted config is already implicitly running the initial offer
        // (index 0), so the first click advances off it instead of "initializing" to it.
        int current = 0;
        if (config != null) {
            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).strategy() == config.strategy()) {
                    current = i;
                    break;
                }
            }
        }
        PipeStrategyOffer next = offers.get((current + 1) % offers.size());
        portState.putExtractConfig(pos, record, side, next.strategy().initialPortConfig(definition, next.aggregation()));
        player.sendOverlayMessage(TopoApiLang.PIPE_STRATEGY_SELECTED.getComponent(
                next.strategy().displayName()));
        return true;
    }

    // --- wrench ------------------------------------------------------------------------------

    void cycleSideIntent(BlockPos pos, Direction side, Player player) {
        long key = pos.asLong();
        PipeNodeRecord record = data.node(key);
        if (record == null) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PipeBlock pipe)) {
                return;
            }
            LOGGER.warn("Re-registering unrecorded pipe at {}", pos);
            onPipePlaced(pos, pipe.definition());
            record = data.node(key);
            if (record == null) {
                return;
            }
        }
        boolean towardContainer = false;
        BlockPos neighborPos = pos.relative(side);
        if (level.isLoaded(neighborPos)) {
            BlockState neighborState = level.getBlockState(neighborPos);
            if (!(neighborState.getBlock() instanceof PipeBlock)) {
                BlockCapability<? extends ResourceHandler<?>, @Nullable Direction> capability = record.definition().resourceType().blockCapability();
                towardContainer = capability != null && level.getCapability(capability, neighborPos, side.getOpposite()) != null;
            }
        }
        PipeSideIntent current = record.intent(side);
        PipeSideIntent next = switch (current) {
            case AUTO -> towardContainer ? PipeSideIntent.EXTRACT : PipeSideIntent.DISABLED;
            case EXTRACT -> PipeSideIntent.DISABLED;
            case DISABLED -> PipeSideIntent.AUTO;
        };
        topology.setSideIntent(pos, side, next);
        player.sendOverlayMessage(
                TopoApiLang.PIPE_SIDE_MODE.getComponent(
                        sideLang(side).getComponent(),
                        modeLang(next).getComponent()));
    }

    private static LangKey sideLang(Direction side) {
        return switch (side) {
            case DOWN -> TopoApiLang.PIPE_SIDE_DOWN;
            case UP -> TopoApiLang.PIPE_SIDE_UP;
            case NORTH -> TopoApiLang.PIPE_SIDE_NORTH;
            case SOUTH -> TopoApiLang.PIPE_SIDE_SOUTH;
            case WEST -> TopoApiLang.PIPE_SIDE_WEST;
            case EAST -> TopoApiLang.PIPE_SIDE_EAST;
        };
    }

    private static LangKey modeLang(PipeSideIntent intent) {
        return switch (intent) {
            case AUTO -> TopoApiLang.PIPE_MODE_AUTO;
            case DISABLED -> TopoApiLang.PIPE_MODE_DISABLED;
            case EXTRACT -> TopoApiLang.PIPE_MODE_EXTRACT;
        };
    }

    // --- tick --------------------------------------------------------------------------------

    private void tick(long gameTime) {
        topology.resolvePending();
        PipeNetwork<?>[] snapshot = topology.activeNetworks();
        topology.beginTick();
        try {
            for (PipeNetwork<?> network : snapshot) {
                if (!network.retired && network.extractorCount() > 0) {
                    tickNetwork(network, gameTime);
                }
            }
        } finally {
            topology.endTick();
        }
        topology.resolvePending();
    }

    private <R extends Resource> void tickNetwork(PipeNetwork<R> network, long gameTime) {
        long started = System.nanoTime();
        network.beginTick(gameTime);
        try {
            tickExtractors(network, gameTime);
        } finally {
            network.addTickNanos(System.nanoTime() - started);
        }
    }

    private <R extends Resource> void tickExtractors(PipeNetwork<R> network, long gameTime) {
        List<PipeNetwork.ExtractorPort<R>> ports = network.extractors();
        boolean stats = PipeEngineStats.enabled;
        for (int i = 0; i < ports.size(); i++) {
            if (network.retired) {
                return;
            }
            PipeNetwork.ExtractorPort<R> port = ports.get(i);
            PipeDefinition definition = port.definition;
            PipePortStrategyConfig config = port.cachedConfig;
            long configRevision = portState.revision(port.pos);
            if (config == null || port.cachedConfigRevision != configRevision) {
                config = portState.resolvePortConfig(port, definition);
                port.cachedConfig = config;
                port.cachedFilter = portState.resolvePortFilter(port, definition);
                port.cachedConfigRevision = configRevision;
            }
            // Aggregation due-gate first: a port only wakes on its own phase-spread batch tick,
            // so a 40t port costs one modulo on the other 39 ticks.
            int interval = Math.max(1, config.interval());
            if (interval > 1 && (gameTime + executionPhase(port.pos, port.side.ordinal(), interval)) % interval != 0) {
                continue;
            }
            long mark = stats ? System.nanoTime() : 0;
            ResourceHandler<R> source = port.cache.getCapability();
            if (stats) {
                long now = System.nanoTime();
                PipeEngineStats.capabilityNanos += now - mark;
                PipeEngineStats.portTicks++;
                mark = now;
            }
            if (source == null) {
                continue;
            }
            // Idle gate: pure reads that stop at the first non-empty index; an empty source
            // skips strategy, paths and context entirely — the dominant steady state costs no
            // transactions at all.
            boolean any = hasAnyAmount(source);
            if (stats) {
                long now = System.nanoTime();
                PipeEngineStats.gateNanos += now - mark;
                mark = now;
            }
            if (!any) {
                continue;
            }
            PipeDistributionStrategy strategy = config.strategy();
            // The strategy budget is the per-window batch amount; the future ledger still caps
            // every node at its per-tick throughput along the route, so the physical limit is
            // enforced independently of whatever amount the config asks for.
            int budget = Math.clamp(
                    strategy.budget(definition, config), 0, definition.maxBatchAmount(interval));
            if (budget <= 0) {
                continue;
            }
            network.ensurePaths(port);
            if (port.destOrder.length == 0) {
                continue;
            }
            network.beginPortBatch();
            context.bind(network, port, source, definition, config, budget, interval);
            if (stats) {
                PipeEngineStats.prepareNanos += System.nanoTime() - mark;
            }
            try {
                strategy.distribute(context);
            } finally {
                context.finishPortTick();
                context.clear();
            }
        }
    }

    /** Read-only gate: true as soon as any source index reports a positive amount. */
    private static boolean hasAnyAmount(ResourceHandler<?> source) {
        int size = source.size();
        for (int index = 0; index < size; index++) {
            if (source.getAmountAsLong(index) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Read-only total of every source index; saturates to int. Never allocates. */
    private static int readTotalAmount(ResourceHandler<?> source) {
        long total = 0;
        int size = source.size();
        for (int index = 0; index < size; index++) {
            total += source.getAmountAsLong(index);
            if (total >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) Math.max(0, total);
    }

    /**
     * Stable per-(pos, side) phase inside an aggregation interval, spreading port executions
     * across ticks so same-interval ports do not all batch on the same server tick.
     */
    public static int executionPhase(long pos, int sideOrdinal, int interval) {
        if (interval <= 1) {
            return 0;
        }
        long mixed = pos * 0x9E3779B97F4A7C15L + sideOrdinal * 0x632BE59BD9B4E019L;
        mixed ^= mixed >>> 32;
        return (int) Math.floorMod(mixed, interval);
    }

    /**
     * Engine-side {@link PipeDistributionContext}: one reusable instance, no per-tick allocation.
     * Also the port tick's {@link PipeTxSession}: at most one transaction per port per tick,
     * opened lazily on the first real move and committed after the strategy returns.
     */
    private final class DistributionContext implements PipeDistributionContext, PipeTxSession {

        private @Nullable PipeNetwork<Resource> network;
        private PipeNetwork.@Nullable ExtractorPort<Resource> port;
        private @Nullable ResourceHandler<Resource> source;
        private @Nullable PipeDefinition definition;
        private @Nullable PipePortStrategyConfig config;
        private int budgetRemaining;
        private int batchInterval = 1;
        private int sourceAvailable;
        private @Nullable Resource probeResource;
        private @Nullable Transaction tickTransaction;
        private boolean txAborted;
        private int movedTotal;

        @SuppressWarnings("unchecked")
        <R extends Resource> void bind(
                                       PipeNetwork<R> network, PipeNetwork.ExtractorPort<R> port, ResourceHandler<R> source,
                                       PipeDefinition definition, PipePortStrategyConfig config, int budget, int interval) {
            this.network = (PipeNetwork<Resource>) network;
            this.port = (PipeNetwork.ExtractorPort<Resource>) port;
            this.source = (ResourceHandler<Resource>) source;
            this.definition = definition;
            this.config = config;
            this.budgetRemaining = budget;
            this.batchInterval = interval;
            this.sourceAvailable = -1;
            this.probeResource = null;
            this.txAborted = false;
            this.movedTotal = 0;
        }

        void clear() {
            network = null;
            port = null;
            source = null;
            definition = null;
            config = null;
            budgetRemaining = 0;
            batchInterval = 1;
            sourceAvailable = -1;
            probeResource = null;
            txAborted = false;
            movedTotal = 0;
        }

        @Override
        public @Nullable Transaction transactionOrOpen() {
            if (txAborted) {
                return null;
            }
            Transaction transaction = tickTransaction;
            if (transaction == null) {
                transaction = PipeTransactions.openRoot();
                tickTransaction = transaction;
                if (PipeEngineStats.enabled) {
                    PipeEngineStats.transactions++;
                }
            }
            return transaction;
        }

        @Override
        public void abort() {
            Transaction transaction = tickTransaction;
            tickTransaction = null;
            txAborted = true;
            if (transaction != null) {
                transaction.close();
            }
        }

        /** Commit (when anything moved) and close this port tick's transaction. */
        void finishPortTick() {
            Transaction transaction = tickTransaction;
            tickTransaction = null;
            if (transaction != null) {
                if (!txAborted && movedTotal > 0) {
                    transaction.commit();
                }
                transaction.close();
            }
        }

        @Override
        public PipeDefinition definition() {
            return Objects.requireNonNull(definition, "context not bound");
        }

        @Override
        public PipePortStrategyConfig config() {
            return Objects.requireNonNull(config, "context not bound");
        }

        @Override
        public int budgetRemaining() {
            return budgetRemaining;
        }

        @Override
        public int sourceAvailable() {
            // Lazy pure-read total: only share-based strategies pay for it; greedy ones never ask.
            if (sourceAvailable < 0) {
                ResourceHandler<Resource> boundSource = source;
                sourceAvailable = boundSource == null ? 0 : readTotalAmount(boundSource);
            }
            return sourceAvailable;
        }

        @Override
        public int destinationCount() {
            return port == null ? 0 : port.destOrder.length;
        }

        @Override
        public String destinationKey(int index) {
            return Objects.requireNonNull(network, "context not bound")
                    .orderedDestination(Objects.requireNonNull(port, "context not bound"), index).key;
        }

        @Override
        public int destinationAcceptance(int index) {
            PipeNetwork<Resource> boundNetwork = network;
            PipeNetwork.ExtractorPort<Resource> boundPort = port;
            ResourceHandler<Resource> boundSource = source;
            if (boundNetwork == null || boundPort == null || boundSource == null || index < 0 || index >= boundPort.destOrder.length || budgetRemaining <= 0) {
                return 0;
            }
            Resource probe = probeResource;
            if (probe == null) {
                probe = firstAvailableResource(boundSource, boundPort.cachedFilter);
                if (probe == null) {
                    return 0;
                }
                probeResource = probe;
            }
            return boundNetwork.destinationAcceptance(boundPort, index, probe, budgetRemaining);
        }

        /** First movable resource: non-empty, positive amount, and passing the port's filter. */
        private static @Nullable Resource firstAvailableResource(
                                                                 ResourceHandler<Resource> source, @Nullable Predicate<Resource> filter) {
            int size = source.size();
            for (int index = 0; index < size; index++) {
                Resource resource = source.getResource(index);
                if (!resource.isEmpty() && source.getAmountAsLong(index) > 0 && (filter == null || filter.test(resource))) {
                    return resource;
                }
            }
            return null;
        }

        @Override
        public int transfer(int index, int maxAmount) {
            PipeNetwork<Resource> boundNetwork = network;
            PipeNetwork.ExtractorPort<Resource> boundPort = port;
            ResourceHandler<Resource> boundSource = source;
            if (boundNetwork == null || boundPort == null || boundSource == null || boundNetwork.retired || txAborted || budgetRemaining <= 0 || index < 0 || index >= boundPort.destOrder.length) {
                return 0;
            }
            int moved = boundNetwork.execute(
                    boundPort, boundSource, index, Math.min(maxAmount, budgetRemaining), batchInterval, this);
            budgetRemaining -= moved;
            movedTotal += moved;
            return moved;
        }

        @Override
        public int cursor() {
            return port == null ? 0 : port.cursor;
        }

        @Override
        public void setCursor(int cursor) {
            if (port != null) {
                port.cursor = cursor;
            }
        }
    }
}
