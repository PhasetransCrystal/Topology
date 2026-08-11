package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.api.tick.TickHub;
import net.ptcrys.topo.api.api.tick.TickKind;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeDistributionContext;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipeFilterAdapter;
import net.ptcrys.topo.api.pipe.PipeFilterSettings;
import net.ptcrys.topo.api.pipe.PipePortFilter;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.api.pipe.PipeStrategyOffer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

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
    private final Long2ObjectOpenHashMap<PipeNetwork<?>> networkByNode = new Long2ObjectOpenHashMap<>();
    private final ReferenceOpenHashSet<PipeNetwork<?>> networks = new ReferenceOpenHashSet<>();
    private final LongLinkedOpenHashSet rebuildSeeds = new LongLinkedOpenHashSet();
    private final LongArrayList deferredPortRefreshes = new LongArrayList();
    private final DistributionContext context = new DistributionContext();

    private @Nullable TickHandle tickHandle;
    private boolean ticking;
    private PipeNetwork<?>[] tickSnapshot = EMPTY_SNAPSHOT;
    private boolean networksDirty;

    PipeLevelRuntime(ServerLevel level) {
        this.level = level;
        this.data = PipeNetworksSavedData.of(level);
        if (data.nodeCount() > 0) {
            ensureTickHook();
        }
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
        long key = pos.asLong();
        data.addNode(key, definition, PipeSideIntent.packAll(PipeSideIntent.AUTO));
        recomputeRoles(key, true);
        ensureTickHook();
    }

    void onPipeRemoved(BlockPos pos) {
        long key = pos.asLong();
        PipeNodeRecord removed = data.removeNode(key);
        if (removed == null) {
            return;
        }
        retireAt(key);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            if (PipeSideRole.unpack(removed.roleBits(), direction) == PipeSideRole.LINK) {
                long neighbor = cursor.set(key).move(direction).asLong();
                retireAt(neighbor);
                rebuildSeeds.add(neighbor);
            }
        }
    }

    void onNeighborChanged(BlockPos pos) {
        long key = pos.asLong();
        if (data.node(key) != null) {
            recomputeRoles(key, true);
        }
    }

    // --- roles and visuals ---------------------------------------------------------------

    /**
     * Recompute the effective side roles of one node from intent x neighbor reality, write the
     * derived blockstate visuals, and invalidate runtime structures according to what changed.
     * Sides whose neighbor chunk is unloaded keep their previous role so cross-border links
     * survive.
     */
    private void recomputeRoles(long pos, boolean propagate) {
        PipeNodeRecord record = data.node(pos);
        if (record == null) {
            return;
        }
        int oldRoles = record.roleBits();
        int newRoles = computeRoles(pos, record);
        if (oldRoles == newRoles) {
            return;
        }
        data.setRoleBits(record, newRoles);
        writeVisual(pos, record, newRoles);

        boolean linkDelta = false;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            PipeSideRole oldRole = PipeSideRole.unpack(oldRoles, direction);
            PipeSideRole newRole = PipeSideRole.unpack(newRoles, direction);
            if (oldRole == newRole) {
                continue;
            }
            if (oldRole == PipeSideRole.LINK || newRole == PipeSideRole.LINK) {
                linkDelta = true;
                long neighbor = cursor.set(pos).move(direction).asLong();
                retireAt(neighbor);
                rebuildSeeds.add(neighbor);
                if (propagate) {
                    recomputeRoles(neighbor, false);
                }
            }
        }
        if (linkDelta) {
            retireAt(pos);
            rebuildSeeds.add(pos);
        } else {
            refreshPortsAt(pos);
        }
    }

    private int computeRoles(long pos, PipeNodeRecord record) {
        MachineResourceType<?> kind = record.definition().resourceType();
        BlockCapability<? extends ResourceHandler<?>, @Nullable Direction> capability = kind.blockCapability();
        int roles = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : DIRECTIONS) {
            PipeSideIntent intent = record.intent(direction);
            PipeSideRole role = PipeSideRole.NONE;
            if (intent != PipeSideIntent.DISABLED) {
                cursor.set(pos).move(direction);
                if (!level.isLoaded(cursor)) {
                    role = record.role(direction);
                } else {
                    BlockState neighborState = level.getBlockState(cursor);
                    if (neighborState.getBlock() instanceof PipeBlock pipe) {
                        if (pipe.definition().resourceType() == kind && neighborAllowsLink(cursor.asLong(), direction)) {
                            role = PipeSideRole.LINK;
                        }
                    } else if (capability != null && level.getCapability(capability, cursor, direction.getOpposite()) != null) {
                        role = intent == PipeSideIntent.EXTRACT ? PipeSideRole.EXTRACT : PipeSideRole.DESTINATION;
                    }
                }
            }
            roles = PipeSideRole.pack(roles, direction, role);
        }
        return roles;
    }

    private boolean neighborAllowsLink(long neighborPos, Direction directionTowardsNeighbor) {
        PipeNodeRecord neighbor = data.node(neighborPos);
        return neighbor == null || neighbor.intent(directionTowardsNeighbor.getOpposite()) != PipeSideIntent.DISABLED;
    }

    private void writeVisual(long pos, PipeNodeRecord record, int roleBits) {
        BlockPos blockPos = BlockPos.of(pos);
        BlockState current = level.getBlockState(blockPos);
        if (!(current.getBlock() instanceof PipeBlock pipe) || pipe.definition() != record.definition()) {
            return;
        }
        BlockState updated = current;
        for (Direction direction : DIRECTIONS) {
            updated = updated.setValue(
                    PipeBlock.property(direction),
                    PipeSideRole.unpack(roleBits, direction).visual());
        }
        if (updated != current) {
            level.setBlock(blockPos, updated, Block.UPDATE_CLIENTS);
        }
    }

    // --- network component maintenance -----------------------------------------------------

    private void retireAt(long pos) {
        PipeNetwork<?> network = networkByNode.get(pos);
        if (network != null) {
            retire(network);
        }
    }

    private void retire(PipeNetwork<?> network) {
        if (network.retired) {
            return;
        }
        network.retired = true;
        networks.remove(network);
        networksDirty = true;
        for (long pos : network.nodePositions()) {
            PipeNetwork<?> current = networkByNode.get(pos);
            if (current == network) {
                networkByNode.remove(pos);
            }
        }
    }

    private void refreshPortsAt(long pos) {
        if (ticking) {
            deferredPortRefreshes.add(pos);
            return;
        }
        PipeNetwork<?> network = networkByNode.get(pos);
        if (network != null && !network.retired) {
            network.refreshPortsAt(data, pos);
            networksDirty = true; // 抽取口增减改变活跃网络集,下个 tick 重建快照。
        } else {
            rebuildSeeds.add(pos);
        }
    }

    private void resolvePending() {
        while (!deferredPortRefreshes.isEmpty()) {
            long pos = deferredPortRefreshes.removeLong(deferredPortRefreshes.size() - 1);
            PipeNetwork<?> network = networkByNode.get(pos);
            if (network != null && !network.retired) {
                network.refreshPortsAt(data, pos);
                networksDirty = true;
            } else {
                rebuildSeeds.add(pos);
            }
        }
        while (!rebuildSeeds.isEmpty()) {
            long seed = rebuildSeeds.removeFirstLong();
            PipeNetwork<?> existing = networkByNode.get(seed);
            if (existing != null && !existing.retired) {
                continue;
            }
            if (data.node(seed) == null) {
                continue;
            }
            flood(seed);
        }
    }

    private PipeNetwork<?> flood(long seed) {
        PipeNetwork<?> network = PipeNetwork.flood(level, data, seed);
        if (network == null) {
            throw new IllegalStateException("Flooded a pipe network from a missing node at " + BlockPos.of(seed));
        }
        for (long pos : network.nodePositions()) {
            PipeNetwork<?> previous = networkByNode.put(pos, network);
            if (previous != null && previous != network) {
                retire(previous);
                networkByNode.put(pos, network);
            }
        }
        networks.add(network);
        networksDirty = true;
        ensureTickHook();
        return network;
    }

    /** Resolve (building lazily if needed) the network containing the node, for ticks and Jade. */
    public @Nullable PipeNetwork<?> networkAt(BlockPos pos) {
        long key = pos.asLong();
        if (data.node(key) == null) {
            return null;
        }
        resolvePending();
        PipeNetwork<?> network = networkByNode.get(key);
        if (network == null || network.retired) {
            network = flood(key);
        }
        return network;
    }

    // --- chunk reconcile -------------------------------------------------------------------

    void reconcileChunk(long chunkKey) {
        LongArrayList recorded = data.nodesInChunk(chunkKey);
        if (recorded.isEmpty()) {
            return;
        }
        long[] positions = recorded.toLongArray();
        for (long pos : positions) {
            PipeNodeRecord record = data.node(pos);
            if (record == null) {
                continue;
            }
            BlockPos blockPos = BlockPos.of(pos);
            BlockState state = level.getBlockState(blockPos);
            if (!(state.getBlock() instanceof PipeBlock pipe) || pipe.definition() != record.definition()) {
                LOGGER.warn("Pruning stale pipe node at {}: world block is {}", blockPos, state.getBlock());
                data.removeNode(pos);
                retireAt(pos);
                continue;
            }
            writeVisual(pos, record, record.roleBits());
        }
        ensureTickHook();
    }

    // --- server-side configuration API (wrench, port GUI, tests) ------------------------------

    /** Set one side's player intent directly and refresh roles/visuals/topology. */
    public void setSideIntent(BlockPos pos, Direction side, PipeSideIntent intent) {
        long key = pos.asLong();
        PipeNodeRecord record = data.node(key);
        if (record == null) {
            return;
        }
        data.setIntentBits(record, PipeSideIntent.pack(record.intentBits(), side, intent));
        recomputeRoles(key, true);
    }

    /** Switch an extraction port to an offered strategy, resetting to its initial port config. */
    public void setExtractStrategy(BlockPos pos, Direction side, PipeDistributionStrategy strategy) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipeStrategyOffer offer = record.definition().offerFor(strategy);
        if (offer == null) {
            return;
        }
        data.putExtractConfig(record, side,
                offer.strategy().initialPortConfig(record.definition(), offer.aggregation()));
        configGeneration++;
        refreshPortsAt(pos.asLong());
    }

    /** Replace an extraction port's strategy config; fields are re-clamped against the offer. */
    public void setExtractConfig(BlockPos pos, Direction side, PipePortStrategyConfig config) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipeStrategyOffer offer = record.definition().offerFor(config.strategy());
        if (offer == null) {
            return;
        }
        data.putExtractConfig(record, side, clampToOffer(config, record.definition(), offer));
        configGeneration++;
        refreshPortsAt(pos.asLong());
    }

    /**
     * The single clamp point every config write/read funnels through: the interval goes into
     * the offer's {@link AggregationWindow} first, then the per-window amount into
     * {@code [0, maxExtractRate × interval]} (so shrinking the interval truncates an
     * over-budget amount).
     */
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

    // --- port screen plumbing -----------------------------------------------------------------

    /** The port a player just clicked, parked until the menu's server-side createUI consumes it. */
    public record PendingPort(BlockPos pos, Direction side) {}

    private final Map<UUID, PendingPort> pendingPortScreens = new HashMap<>();

    /** Bumped on every port-config write; extractor ports cache their resolved config against it. */
    private long configGeneration;

    /** Open the LDLib2 port screen for an extraction port; false when it cannot open. */
    boolean openPortScreen(BlockPos pos, Direction side, Player player) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null || record.role(side) != PipeSideRole.EXTRACT || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        pendingPortScreens.put(player.getUUID(), new PendingPort(pos.immutable(), side));
        return BlockUIMenuType.openUI(serverPlayer, pos);
    }

    /** Server-side createUI picks up (and clears) the clicked port for this player. */
    public @Nullable PendingPort consumePendingPortScreen(UUID playerId) {
        return pendingPortScreens.remove(playerId);
    }

    /** The port's healed strategy config (resets to the initial offer when missing/invalid). */
    public PipePortStrategyConfig portConfig(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            throw new IllegalStateException("No pipe node at " + pos);
        }
        return healRecordConfig(record, side);
    }

    /**
     * Heal funnel shared by ticking and the port screen: missing configs and configs whose
     * strategy this pipe does not offer reset to the initial offer's {@code initialPortConfig};
     * every survivor is re-clamped against its offer. Healed results persist immediately.
     */
    private PipePortStrategyConfig healRecordConfig(PipeNodeRecord record, Direction side) {
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
            configGeneration++;
        }
        return healed;
    }

    /** Index of the port's strategy within the definition's offer list; -1 when the node is gone. */
    public int portStrategyIndex(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return -1;
        }
        PipeDistributionStrategy strategy = portConfig(pos, side).strategy();
        List<PipeStrategyOffer> offers = record.definition().strategies();
        for (int i = 0; i < offers.size(); i++) {
            if (offers.get(i).strategy() == strategy) {
                return i;
            }
        }
        return -1;
    }

    /** The port's effective per-window amount (one batch), clamped; 0 when the node is gone. */
    public int portAmount(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return 0;
        }
        PipePortStrategyConfig config = portConfig(pos, side);
        int maxAmount = record.definition().maxBatchAmount(config.interval());
        return Math.clamp(config.amountOr(maxAmount), 0, maxAmount);
    }

    /** The port's aggregation interval in ticks; 1 when the node is gone. */
    public int portInterval(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return 1;
        }
        return Math.max(1, portConfig(pos, side).interval());
    }

    /** The window the port's current strategy offer allows; {@code null} when the node is gone. */
    public @Nullable AggregationWindow portWindow(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return null;
        }
        PipeStrategyOffer offer = record.definition().offerFor(portConfig(pos, side).strategy());
        return offer == null ? null : offer.aggregation();
    }

    /** Port screen action: switch to the strategy at the definition's offer-list index. */
    public void uiSelectStrategy(BlockPos pos, Direction side, int index) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        List<PipeStrategyOffer> offers = record.definition().strategies();
        if (index >= 0 && index < offers.size()) {
            setExtractStrategy(pos, side, offers.get(index).strategy());
        }
    }

    /** Port screen action: adjust the per-window amount by {@code delta}; clamp bounds it. */
    public void uiAdjustAmount(BlockPos pos, Direction side, int delta) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = portConfig(pos, side);
        int maxAmount = record.definition().maxBatchAmount(config.interval());
        int newAmount = (int) Math.clamp((long) config.amountOr(maxAmount) + delta, 0, maxAmount);
        setExtractConfig(pos, side, config.withAmount(newAmount));
    }

    /** Port screen action (amount editor popup): set the per-window amount; clamp bounds it. */
    public void uiSetAmount(BlockPos pos, Direction side, int amount) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = portConfig(pos, side);
        int maxAmount = record.definition().maxBatchAmount(config.interval());
        setExtractConfig(pos, side, config.withAmount(Math.clamp(amount, 0, maxAmount)));
    }

    /** Port screen action (amount editor popup): set the interval; the offer clamp bounds it. */
    public void uiSetInterval(BlockPos pos, Direction side, int interval) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = portConfig(pos, side);
        setExtractConfig(pos, side, config.withInterval(Math.max(1, interval)));
    }

    /** Port screen action: adjust the aggregation interval by {@code delta} ticks (offer-clamped). */
    public void uiAdjustInterval(BlockPos pos, Direction side, int delta) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig config = portConfig(pos, side);
        long target = (long) config.interval() + delta;
        setExtractConfig(pos, side, config.withInterval(
                (int) Math.clamp(target, 1, Integer.MAX_VALUE)));
    }

    /**
     * Strategy-agnostic config update channel for strategy-contributed UI widgets (e.g. the
     * by-distance direction toggle): the operator transforms the healed config, the result is
     * re-clamped and persisted. Operators must keep the strategy unchanged.
     */
    public void uiUpdateConfig(BlockPos pos, Direction side, UnaryOperator<PipePortStrategyConfig> update) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortStrategyConfig current = portConfig(pos, side);
        PipePortStrategyConfig updated = update.apply(current);
        if (updated == null || updated.strategy() != current.strategy()) {
            return;
        }
        setExtractConfig(pos, side, updated);
    }

    // --- port filters (white/black lists) ------------------------------------------------------

    /** The port's healed white/black filter; {@code EMPTY} when the node is gone or filterless. */
    public PipePortFilter portFilter(BlockPos pos, Direction side) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return PipePortFilter.EMPTY;
        }
        return healRecordFilter(record, side);
    }

    /**
     * Port screen action: add one normalized entry to the white or black list. Invalid syntax,
     * tag entries on tag-less tiers, duplicates and full lists are no-ops — the screen shows the
     * live count/capacity, so a rejected click is legible without an error channel.
     */
    public void uiAddFilterEntry(BlockPos pos, Direction side, boolean white, String rawEntry) {
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
        // Heal-persist the strategy config first: the saved-data filter nests inside the port
        // entry, which only exists for sides with a persisted config.
        portConfig(pos, side);
        PipePortFilter current = healRecordFilter(record, side);
        if (current.list(white).size() >= settings.entryCapacity()) {
            return;
        }
        PipePortFilter updated = current.withAdded(white, entry);
        if (updated != current) {
            data.putExtractFilter(record, side, updated);
            configGeneration++;
            refreshPortsAt(pos.asLong());
        }
    }

    /** Port screen action: remove one entry (exact stored string) from the white or black list. */
    public void uiRemoveFilterEntry(BlockPos pos, Direction side, boolean white, String entry) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null) {
            return;
        }
        PipePortFilter current = healRecordFilter(record, side);
        PipePortFilter updated = current.withRemoved(white, entry);
        if (updated != current) {
            data.putExtractFilter(record, side, updated);
            configGeneration++;
            refreshPortsAt(pos.asLong());
        }
    }

    /**
     * Filter heal funnel, mirroring {@link #healRecordConfig}: every stored entry re-normalizes
     * against the definition's current {@link PipeFilterSettings} (capacity truncation, tag
     * permission, syntax) so saves from older registrations converge. Healed results persist.
     */
    private PipePortFilter healRecordFilter(PipeNodeRecord record, Direction side) {
        PipePortFilter stored = record.extractFilter(side);
        PipePortFilter healed = clampFilter(stored, record.definition().filterSettings());
        if (!healed.equals(stored)) {
            data.putExtractFilter(record, side, healed);
            configGeneration++;
        }
        return healed;
    }

    /**
     * The single filter clamp point every read/write funnels through. Steady state (every write
     * stores clamped results) passes the allocation-free compliance scan — the port screen's
     * per-tick S2C getters poll this, so the fast path must not allocate.
     */
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

    /** Allocation-free check that a stored list already satisfies the clamp rules. */
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

    /**
     * Compile the port's filter into the engine predicate, cached on the port against the config
     * generation. {@code null} = no filtering (the hot path pays one null check).
     */
    private <R extends Resource> @Nullable Predicate<R> resolvePortFilter(
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
        PipePortFilter filter = healRecordFilter(record, port.side);
        return filter.compile(adapter);
    }

    /**
     * Wrench fallback when no port screen is installed: cycle the extraction port through the
     * strategies its tier unlocks (declaration order), resetting the config to the new strategy's
     * default, and tell the player.
     */
    void cycleExtractStrategy(BlockPos pos, Direction side, Player player) {
        PipeNodeRecord record = data.node(pos.asLong());
        if (record == null || record.role(side) != PipeSideRole.EXTRACT) {
            return;
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
        data.putExtractConfig(record, side, next.strategy().initialPortConfig(definition, next.aggregation()));
        configGeneration++;
        refreshPortsAt(pos.asLong());
        player.sendOverlayMessage(TopoApiLang.PIPE_STRATEGY_SELECTED.getComponent(
                next.strategy().displayName()));
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
        data.setIntentBits(record, PipeSideIntent.pack(record.intentBits(), side, next));
        recomputeRoles(key, true);
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
        resolvePending();
        if (networks.isEmpty()) {
            return;
        }
        if (networksDirty) {
            // 快照只收"有抽取口"的活跃网络:纯密铺(无端口)的网络连每 tick 的跳过判断都不付。
            int active = 0;
            for (PipeNetwork<?> network : networks) {
                if (network.extractorCount() > 0) {
                    active++;
                }
            }
            PipeNetwork<?>[] snapshot = active == 0 ? EMPTY_SNAPSHOT : new PipeNetwork<?>[active];
            int index = 0;
            for (PipeNetwork<?> network : networks) {
                if (network.extractorCount() > 0) {
                    snapshot[index++] = network;
                }
            }
            tickSnapshot = snapshot;
            networksDirty = false;
        }
        ticking = true;
        try {
            for (PipeNetwork<?> network : tickSnapshot) {
                if (!network.retired && network.extractorCount() > 0) {
                    tickNetwork(network, gameTime);
                }
            }
        } finally {
            ticking = false;
        }
        resolvePending();
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
            if (config == null || port.cachedConfigGeneration != configGeneration) {
                config = resolvePortConfig(port, definition);
                port.cachedConfig = config;
                port.cachedFilter = resolvePortFilter(port, definition);
                port.cachedConfigGeneration = configGeneration;
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
     * Resolve the port's strategy config for ticking: shares the heal funnel with the port
     * screen; a vanished record falls back to a transient initial config (never persisted).
     */
    public PipePortStrategyConfig resolvePortConfig(PipeNetwork.ExtractorPort<?> port, PipeDefinition definition) {
        PipeNodeRecord record = data.node(port.pos);
        if (record == null) {
            PipeStrategyOffer initial = definition.initialOffer();
            return initial.strategy().initialPortConfig(definition, initial.aggregation());
        }
        return healRecordConfig(record, port.side);
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
            return java.util.Objects.requireNonNull(definition, "context not bound");
        }

        @Override
        public PipePortStrategyConfig config() {
            return java.util.Objects.requireNonNull(config, "context not bound");
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
            return java.util.Objects.requireNonNull(network, "context not bound")
                    .orderedDestination(java.util.Objects.requireNonNull(port, "context not bound"), index).key;
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
