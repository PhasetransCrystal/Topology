package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.component.MachineWorkView;
import net.ptcrys.topo.api.machine.component.ServiceMatch;
import net.ptcrys.topo.api.machine.data.MachineDataScope;
import net.ptcrys.topo.api.machine.ui.ComponentCollector;
import net.ptcrys.topo.api.machine.ui.MachineUiContribution;
import net.ptcrys.topo.api.machine.ui.PageCollector;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The slim composition root for a machine. It assembles its {@link MachineComponents} from the
 * definition, runs the trait lifecycle, and delegates persistence/sync to its machine data domain.
 */
public class MachineBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LoggerFactory.getLogger(MachineBlockEntity.class);

    private static final MachineComponents.RuntimeAccess COMPONENT_RUNTIME_ACCESS = new ComponentRuntimeAccess();

    private final MachineDefinition definition;
    private final MachineDataScope data;
    private final MachinePerformanceMonitor performance;
    private @Nullable MachineComponents components;
    /** Work views request an aggregate ACTIVE-state refresh only when their running state changes. */
    private boolean activeBlockStateRefreshRequested = true;
    /** Existing third-party work views keep legacy polling unless they explicitly publish edges. */
    private boolean activeBlockStatePollingRequired = true;

    /** Framework-only access token for machine trait lifecycle fanout. */
    public static final class ComponentRuntimeAccess implements MachineComponents.RuntimeAccess {

        private ComponentRuntimeAccess() {}
    }

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.definition = resolveDefinition(state);
        this.data = MachineDataScope.create(this);
        this.performance = new MachinePerformanceMonitor(
                () -> level == null ? Long.MIN_VALUE + 1L : level.getGameTime(),
                () -> requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS),
                () -> requireComponents().tickersForRuntime(COMPONENT_RUNTIME_ACCESS));
    }

    private static MachineDefinition resolveDefinition(BlockState state) {
        if (state.getBlock() instanceof MachineBlock block) {
            return block.definition();
        }
        throw new IllegalStateException(
                "MachineBlockEntity placed on a non-machine block: " + state.getBlock());
    }

    public final MachineDefinition definition() {
        return definition;
    }

    public final MachineComponents machineComponents() {
        return requireComponents();
    }

    public final MachineDataScope data() {
        return data;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        data.lifecycleAttachLevel(level);
        activeBlockStateRefreshRequested = true;
    }

    final void initializeMachineRuntime() {
        if (components != null) {
            throw new IllegalStateException("Machine runtime is already initialized for " + definition.id());
        }

        MachineComponents createdComponents = MachineComponents.createForRuntime(
                COMPONENT_RUNTIME_ACCESS, this, definition.componentMounts());
        createdComponents.resolveAll(COMPONENT_RUNTIME_ACCESS);
        performance.initialize(createdComponents.allForRuntime(COMPONENT_RUNTIME_ACCESS).size());
        this.components = createdComponents;
        this.activeBlockStatePollingRequired = requiresActiveBlockStatePolling(createdComponents);
        activeBlockStateRefreshRequested = true;
    }

    private MachineComponents requireComponents() {
        MachineComponents current = components;
        if (current == null) {
            throw new IllegalStateException("Machine runtime is not initialized for " + definition.id());
        }
        return current;
    }

    @Override
    protected final void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        saveMachineAdditional(output);
        data.lifecycleSavePersisted(output);
    }

    protected void saveMachineAdditional(ValueOutput output) {}

    @Override
    protected final void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        loadMachineAdditional(input);
        // Fixed order: disk scopes first, then optional client baseline packed in update-tag form.
        data.lifecycleLoadPersisted(input);
        data.lifecycleLoadClientBaselineFromUpdateTag(input);
    }

    protected void loadMachineAdditional(ValueInput input) {}

    @Override
    public final CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        byte[] sync = data.lifecycleWriteUpdateTag(registryAccessForUpdateTag(registries));
        if (sync.length > 0) {
            tag.putByteArray(MachineDataScope.UPDATE_TAG_SYNC_KEY, sync);
        }
        return tag;
    }

    @Override
    public final ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, BlockEntity::getUpdateTag);
    }

    private RegistryAccess registryAccessForUpdateTag(HolderLookup.Provider registries) {
        if (level != null) {
            return level.registryAccess();
        }
        if (registries instanceof RegistryAccess registryAccess) {
            return registryAccess;
        }
        return RegistryAccess.EMPTY;
    }

    public final void collectMachineUi(PageCollector pages, ComponentCollector components) {
        // UI collection is a structural declaration phase. LDLib2 builds block UIs on both sides,
        // and the client-side menu can be created before this block entity receives its initial
        // machine-data baseline (phase may still be CLIENT_WAITING).
        //
        // Contract for trait authors:
        // - Do NOT call DataBoolean/DataString#value() / similar requireReadAccess paths here.
        // - Use valueOrElse(default) for any initial chrome that needs a number/string/boolean.
        // - Prefer HOVER_TOOLTIPS / bindings that evaluate later (when the domain is LIVE).
        MachineUiContribution contribution = new MachineUiContribution(pages, components);
        for (MachineComponent trait : requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            trait.collectMachineUi(contribution);
        }
    }

    private void syncActiveBlockStateFromWorkViews(MachineComponents currentComponents) {
        BlockState state = getBlockState();
        if (!state.hasProperty(OrientedActiveMachineBlock.ACTIVE)) {
            return;
        }
        boolean running = false;
        // Cached binding set is immutable after mount. Edge-aware views reach this only on changes;
        // legacy third-party views retain the previous every-tick polling behavior.
        for (ServiceMatch<MachineWorkView> match : currentComponents.servicesCached(MachineWorkView.KEY)) {
            if (match.value().isRunning()) {
                running = true;
                break;
            }
        }
        if (state.getValue(OrientedActiveMachineBlock.ACTIVE) != running) {
            level.setBlockAndUpdate(getBlockPos(), state.setValue(OrientedActiveMachineBlock.ACTIVE, running));
        }
    }

    private static boolean requiresActiveBlockStatePolling(MachineComponents currentComponents) {
        for (ServiceMatch<MachineWorkView> match : currentComponents.servicesCached(MachineWorkView.KEY)) {
            if (!match.value().publishesRunningStateEdges()) {
                return true;
            }
        }
        return false;
    }

    /** Called by work components when their externally visible running state crosses an edge. */
    public final void requestActiveBlockStateRefresh() {
        activeBlockStateRefreshRequested = true;
    }

    public final void afterTickerTick() {
        afterTickerTick(level == null ? Long.MIN_VALUE : level.getGameTime());
    }

    public final void afterTickerTick(long gameTime) {
        if (level == null || level.isClientSide()) {
            return;
        }
        // Gate before any trait/work-view reads; lifecycleServerTick re-checks and runs recompute.
        data.requireBusinessReady("tick machine");
        if (activeBlockStatePollingRequired || activeBlockStateRefreshRequested) {
            activeBlockStateRefreshRequested = false;
            syncActiveBlockStateFromWorkViews(requireComponents());
        }
        data.lifecycleServerTick(gameTime);
    }

    /**
     * Persist marking without vanilla's comparator fan-out: machine blocks expose no analog output
     * signal, so the six-direction {@code updateNeighbourForOutputSignal} scan inside
     * {@code BlockEntity.setChanged()} is dead cost on this per-tick path. {@code Level.setBlock}
     * itself guards comparator updates behind {@code hasAnalogOutputSignal}, so skipping it here is
     * vanilla-consistent.
     */
    public final void markChunkUnsavedForPersist() {
        if (level != null) {
            level.blockEntityChanged(getBlockPos());
        }
    }

    /**
     * Opens a short profiling window for this machine. Jade refreshes this while the player is
     * looking at the block, so machines outside the overlay pay only one volatile-read branch.
     */
    public final void activatePerformanceMonitoring(int ticks) {
        performance.activate(ticks);
    }

    public final boolean isPerformanceMonitoringActive(long gameTime) {
        return performance.isActive(gameTime);
    }

    public final MachinePerformanceSnapshot lastPerformanceSnapshot() {
        return performance.last();
    }

    public final MachinePerformanceSnapshot recentPerformanceSnapshot(int maxAgeTicks) {
        return performance.recent(maxAgeTicks);
    }

    public final int performanceSampleMaxAgeTicks() {
        return performance.sampleMaxAgeTicks();
    }

    /** Server-thread helper for Jade/tests that need the just-recorded current tick now. */
    public final void publishPerformanceSnapshotForCurrentTick() {
        performance.publishRecent(0);
    }

    public final boolean publishRecentPerformanceSnapshot(int maxAgeTicks) {
        return performance.publishRecent(maxAgeTicks);
    }

    public final void recordPerformanceSample(long gameTime, long elapsedNanos, int profileSlot) {
        performance.record(gameTime, elapsedNanos, profileSlot);
    }

    /**
     * Accumulates one monitored ticker sample. {@code frameworkNanos} is the after-tick framework
     * cost (block state sync, computed fields, flush) reported in the snapshot's self bucket.
     */
    public final void recordPerformanceSample(long gameTime, long elapsedNanos, long frameworkNanos, int profileSlot) {
        performance.record(gameTime, elapsedNanos, frameworkNanos, profileSlot);
    }

    @Override
    public final void onLoad() {
        super.onLoad();
        // Single world-entry template: phase → LIVE/WAITING, then trait hooks, then recompute/tickers.
        data.lifecycleEnterWorld();
        onMachineLoaded();
        notifyTraitsLoaded();
        // MIN_VALUE bypasses the per-tick dedupe: load-time recompute must always run even if a
        // ticker already recomputed at this game time before the machine reloaded.
        data.lifecycleRecompute(Long.MIN_VALUE);
        attachTickersToHub();
    }

    protected void onMachineLoaded() {}

    private void attachTickersToHub() {
        for (MachineTicker ticker : requireComponents().tickersForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            ticker.attachToHub();
        }
    }

    private void notifyTraitsLoaded() {
        for (MachineComponent trait : requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            trait.onMachineLoad();
        }
    }

    /**
     * Vanilla destroyed-in-world window: the block is being replaced by a different block (never
     * chunk unload, never same-block state changes, server-side only). Fans the event out to every
     * trait while traits and machine data are still alive and before the block's own drops spawn.
     */
    @Override
    public final void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (MachineComponent trait : requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            trait.onMachineDestroyed(serverLevel, pos, state);
        }
    }

    @Override
    public final void setRemoved() {
        if (isRemoved()) {
            return;
        }
        data.lifecyclePrepareRemoval();
        super.setRemoved();
        try {
            detachTickersFromHub();
            notifyTraitsUnloading();
        } finally {
            // Unload hooks may mutate persisted state. Re-run the O(1) epoch backstop after them;
            // this never scans fields and the chunk-dirty call remains coalesced by the epoch.
            data.lifecyclePrepareRemoval();
            onMachineRemoved();
        }
    }

    protected void onMachineRemoved() {}

    private void detachTickersFromHub() {
        for (MachineTicker ticker : requireComponents().tickersForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            try {
                ticker.detachFromHub();
            } catch (RuntimeException error) {
                LOGGER.error("Failed to detach machine ticker for {} at {}", definition.id(), getBlockPos(), error);
            }
        }
    }

    private void notifyTraitsUnloading() {
        for (MachineComponent trait : requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            trait.onMachineUnload();
        }
    }
}
