package net.ptcrys.topo.apiv2.machine;

import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.api.tick.TickHandle;
import net.ptcrys.topo.apiv2.machine.component.MachineComponent;
import net.ptcrys.topo.apiv2.machine.component.MachineComponents;
import net.ptcrys.topo.apiv2.machine.component.MachineWorkView;
import net.ptcrys.topo.apiv2.machine.component.ServiceMatch;
import net.ptcrys.topo.apiv2.machine.data.MachineDataScope;
import net.ptcrys.topo.apiv2.machine.ui.ComponentCollector;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContribution;
import net.ptcrys.topo.apiv2.machine.ui.PageCollector;

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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The slim composition root for a machine. It assembles its {@link MachineComponents} from the
 * definition, runs the trait lifecycle, and delegates persistence/sync to its machine data domain.
 */
public class MachineBlockEntity extends BlockEntity {

    private static final MachineComponents.RuntimeAccess COMPONENT_RUNTIME_ACCESS = new ComponentRuntimeAccess();
    private static final long[] EMPTY_PERFORMANCE_BREAKDOWN = new long[0];

    private final MachineDefinition definition;
    private final MachineDataScope data;
    private @Nullable MachineComponents components;
    /** Work views request an aggregate ACTIVE-state refresh only when their running state changes. */
    private boolean activeBlockStateRefreshRequested = true;
    /** Existing third-party work views keep legacy polling unless they explicitly publish edges. */
    private boolean activeBlockStatePollingRequired = true;
    private long currentPerformanceGameTime = Long.MIN_VALUE;
    private long currentPerformanceTotalNanos;
    private long currentPerformanceSelfNanos;
    private long currentPerformanceComponentsNanos;
    private long[] currentPerformanceTraitNanos = EMPTY_PERFORMANCE_BREAKDOWN;
    // Rolling stats folded once per completed monitored tick: EMA (alpha 1/8) and a slowly
    // decaying peak (1/16 per tick, floored at the latest sample). They smooth the overlay so a
    // single noisy tick cannot masquerade as steady-state cost.
    private long performanceEmaTotalNanos;
    private long performanceEmaSelfNanos;
    private long performanceEmaComponentsNanos;
    private long performancePeakTotalNanos;
    private long performancePeakSelfNanos;
    private long performancePeakComponentsNanos;
    private long[] performanceEmaTraitNanos = EMPTY_PERFORMANCE_BREAKDOWN;
    private long[] performancePeakTraitNanos = EMPTY_PERFORMANCE_BREAKDOWN;
    private boolean performanceSnapshotDirty;
    private volatile long performanceMonitorUntilGameTime = Long.MIN_VALUE;
    private volatile MachinePerformanceSnapshot publishedPerformanceSnapshot = MachinePerformanceSnapshot.EMPTY;

    /** Framework-only access token for machine trait lifecycle fanout. */
    public static final class ComponentRuntimeAccess implements MachineComponents.RuntimeAccess {

        private ComponentRuntimeAccess() {}
    }

    public MachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.definition = resolveDefinition(state);
        this.data = MachineDataScope.create(this);
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
        initializePerformanceStorage(createdComponents);
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
        if (ticks <= 0) {
            return;
        }
        long until = currentPerformanceGameTime() + ticks;
        if (until > performanceMonitorUntilGameTime) {
            performanceMonitorUntilGameTime = until;
        }
    }

    public final boolean isPerformanceMonitoringActive(long gameTime) {
        return gameTime <= performanceMonitorUntilGameTime;
    }

    public final MachinePerformanceSnapshot lastPerformanceSnapshot() {
        publishPerformanceSnapshotIfDirty();
        return publishedPerformanceSnapshot;
    }

    public final MachinePerformanceSnapshot recentPerformanceSnapshot(int maxAgeTicks) {
        publishPerformanceSnapshotIfDirty();
        MachinePerformanceSnapshot snapshot = publishedPerformanceSnapshot;
        return isRecentPerformanceSample(snapshot.gameTime(), currentPerformanceGameTime(), maxAgeTicks) ? snapshot : MachinePerformanceSnapshot.EMPTY;
    }

    /**
     * Freshness window (in ticks) for externally displayed performance samples. A sample counts as
     * current while it is no older than the machine's slowest attached ticker interval plus one
     * tick of cross-tick slack: interval machines (ME hatches tick every 20-40 ticks) keep a
     * stable reading between runs, while every-tick machines still expire stale samples after the
     * same 2-tick window as before.
     */
    public final int performanceSampleMaxAgeTicks() {
        int slowestInterval = 1;
        for (MachineTicker ticker : requireComponents().tickersForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            TickHandle handle = ticker.handle();
            int interval = handle == null ? ticker.tickInterval() : handle.interval();
            slowestInterval = Math.max(slowestInterval, interval);
        }
        return slowestInterval + 1;
    }

    /** Server-thread helper for Jade/tests that need the just-recorded current tick now. */
    public final void publishPerformanceSnapshotForCurrentTick() {
        publishRecentPerformanceSnapshot(0);
    }

    public final boolean publishRecentPerformanceSnapshot(int maxAgeTicks) {
        if (currentPerformanceGameTime == Long.MIN_VALUE || currentPerformanceTotalNanos <= 0L) {
            return false;
        }
        if (!isRecentPerformanceSample(currentPerformanceGameTime, currentPerformanceGameTime(), maxAgeTicks)) {
            return false;
        }
        publishPerformanceSnapshotIfDirty();
        return true;
    }

    private long currentPerformanceGameTime() {
        return level == null ? Long.MIN_VALUE + 1L : level.getGameTime();
    }

    private static boolean isRecentPerformanceSample(long sampleGameTime, long currentGameTime, int maxAgeTicks) {
        if (sampleGameTime == Long.MIN_VALUE || currentGameTime == Long.MIN_VALUE) {
            return false;
        }
        long age = currentGameTime - sampleGameTime;
        return age >= 0L && age <= Math.max(0, maxAgeTicks);
    }

    private void initializePerformanceStorage(MachineComponents createdComponents) {
        int traitCount = createdComponents.allForRuntime(COMPONENT_RUNTIME_ACCESS).size();
        currentPerformanceTraitNanos = traitCount == 0 ? EMPTY_PERFORMANCE_BREAKDOWN : new long[traitCount];
        performanceEmaTraitNanos = traitCount == 0 ? EMPTY_PERFORMANCE_BREAKDOWN : new long[traitCount];
        performancePeakTraitNanos = traitCount == 0 ? EMPTY_PERFORMANCE_BREAKDOWN : new long[traitCount];
    }

    public final void recordPerformanceSample(long gameTime, long elapsedNanos, int profileSlot) {
        recordPerformanceSample(gameTime, elapsedNanos, 0L, profileSlot);
    }

    /**
     * Accumulates one monitored ticker sample. {@code frameworkNanos} is the after-tick framework
     * cost (block state sync, computed fields, flush) reported in the snapshot's self bucket. The
     * published snapshot object is rebuilt lazily on read, never per sample — building it allocates
     * and must not triple the monitored tick cost.
     */
    public final void recordPerformanceSample(long gameTime, long elapsedNanos, long frameworkNanos, int profileSlot) {
        beginPerformanceTick(gameTime);
        long elapsed = normalizeElapsedNanos(elapsedNanos);
        long framework = Math.max(0L, frameworkNanos);
        currentPerformanceTotalNanos = saturatingAdd(currentPerformanceTotalNanos, saturatingAdd(elapsed, framework));
        currentPerformanceComponentsNanos = saturatingAdd(currentPerformanceComponentsNanos, elapsed);
        currentPerformanceSelfNanos = saturatingAdd(currentPerformanceSelfNanos, framework);
        if (profileSlot >= 0 && profileSlot < currentPerformanceTraitNanos.length) {
            currentPerformanceTraitNanos[profileSlot] = saturatingAdd(currentPerformanceTraitNanos[profileSlot], elapsed);
        }
        performanceSnapshotDirty = true;
    }

    private void beginPerformanceTick(long gameTime) {
        if (currentPerformanceGameTime == gameTime) {
            return;
        }
        if (currentPerformanceGameTime != Long.MIN_VALUE && currentPerformanceTotalNanos > 0L) {
            foldPerformanceRollingStats();
        }
        currentPerformanceGameTime = gameTime;
        currentPerformanceTotalNanos = 0L;
        currentPerformanceSelfNanos = 0L;
        currentPerformanceComponentsNanos = 0L;
        Arrays.fill(currentPerformanceTraitNanos, 0L);
    }

    /** Folds the just-completed tick's accumulators into the rolling EMA/peak stats. */
    private void foldPerformanceRollingStats() {
        performanceEmaTotalNanos = emaFold(performanceEmaTotalNanos, currentPerformanceTotalNanos);
        performanceEmaSelfNanos = emaFold(performanceEmaSelfNanos, currentPerformanceSelfNanos);
        performanceEmaComponentsNanos = emaFold(performanceEmaComponentsNanos, currentPerformanceComponentsNanos);
        performancePeakTotalNanos = peakFold(performancePeakTotalNanos, currentPerformanceTotalNanos);
        performancePeakSelfNanos = peakFold(performancePeakSelfNanos, currentPerformanceSelfNanos);
        performancePeakComponentsNanos = peakFold(performancePeakComponentsNanos, currentPerformanceComponentsNanos);
        int len = Math.min(currentPerformanceTraitNanos.length, performanceEmaTraitNanos.length);
        for (int i = 0; i < len; i++) {
            performanceEmaTraitNanos[i] = emaFold(performanceEmaTraitNanos[i], currentPerformanceTraitNanos[i]);
            performancePeakTraitNanos[i] = peakFold(performancePeakTraitNanos[i], currentPerformanceTraitNanos[i]);
        }
    }

    private static long emaFold(long ema, long sample) {
        return ema == 0L ? sample : ema + (sample - ema) / 8L;
    }

    private static long peakFold(long peak, long sample) {
        return Math.max(sample, peak - (peak >>> 4));
    }

    private void publishPerformanceSnapshotIfDirty() {
        if (!performanceSnapshotDirty) {
            return;
        }
        performanceSnapshotDirty = false;
        List<MachineComponent> allTraits = requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS);
        int len = Math.min(allTraits.size(), currentPerformanceTraitNanos.length);
        List<MachinePerformanceSnapshot.ComponentSample> samples = new ArrayList<>(len);
        for (int i = 0; i < len; i++) {
            MachineComponent trait = allTraits.get(i);
            long nanos = currentPerformanceTraitNanos[i];
            long avgNanos = performanceEmaTraitNanos.length > i ? performanceEmaTraitNanos[i] : 0L;
            long peakNanos = performancePeakTraitNanos.length > i ? performancePeakTraitNanos[i] : 0L;
            List<MachinePerformanceSnapshot.TimingSample> children = List.of();
            if (trait instanceof MachineTicker ticker) {
                // Live handle interval, not the static registration value: parked tickers decay
                // their interval at runtime and the panel should show the cadence actually paid.
                var tickerHandle = ticker.handle();
                int liveInterval = tickerHandle == null || tickerHandle.isCancelled() ? ticker.tickInterval() : tickerHandle.interval();
                children = List.of(new MachinePerformanceSnapshot.TimingSample(
                        trait.id() + ".tick",
                        "tick",
                        ticker.kind() + " interval=" + liveInterval,
                        nanos,
                        avgNanos,
                        peakNanos));
            }
            samples.add(new MachinePerformanceSnapshot.ComponentSample(
                    trait.id().toString(),
                    trait.getClass().getSimpleName(),
                    nanos,
                    avgNanos,
                    peakNanos,
                    children));
        }
        publishedPerformanceSnapshot = new MachinePerformanceSnapshot(
                currentPerformanceGameTime,
                currentPerformanceTotalNanos,
                currentPerformanceSelfNanos,
                currentPerformanceComponentsNanos,
                performanceEmaTotalNanos,
                performancePeakTotalNanos,
                performanceEmaSelfNanos,
                performancePeakSelfNanos,
                performanceEmaComponentsNanos,
                performancePeakComponentsNanos,
                samples);
    }

    private static long normalizeElapsedNanos(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return 0L;
        }
        return elapsedNanos == 0L ? 1L : elapsedNanos;
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return r < 0L ? Long.MAX_VALUE : r;
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
            ticker.detachFromHub();
        }
    }

    private void notifyTraitsUnloading() {
        for (MachineComponent trait : requireComponents().allForRuntime(COMPONENT_RUNTIME_ACCESS)) {
            trait.onMachineUnload();
        }
    }
}
