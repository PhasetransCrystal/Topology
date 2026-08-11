package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.api.lang.OIApiLang;
import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.api.tick.TickHandle;
import net.ptcrys.topo.apiv2.machine.data.DataEnum;
import net.ptcrys.topo.apiv2.machine.data.DataInt;
import net.ptcrys.topo.apiv2.machine.data.DataLong;
import net.ptcrys.topo.apiv2.machine.data.DataResourceKey;
import net.ptcrys.topo.apiv2.machine.data.DataString;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolRouter;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Minimal server-side state machine for one or more OI recipe types. */
public class RecipeLogic extends MachineTicker
                         implements MachineWorkView, MachineWorkControl, MachineResourceWake {

    public static final ComponentKey<RecipeLogic> RECIPE_LOGIC_1 = ComponentKey.oi("recipe_logic_1", RecipeLogic.class)
            .service(MachineWorkView.KEY, (trait, unused) -> trait)
            .service(MachineWorkControl.KEY, (trait, unused) -> trait)
            .service(MachineResourceWake.KEY, (trait, unused) -> trait);

    /**
     * 停泊退避上限(tick):被证明"无事件不可能有进展"的停泊 tick 把自身 interval 逐步翻倍到此
     * 封顶,与 ME 舱室 20-40t 节奏同档。资源内容变化/启停切换走事件唤醒(interval 立即回 1),
     * 该上限只封顶无唤醒契约的来源(配方重载、RecipeCondition 翻转)的响应延迟。
     */
    public static final int PARKED_BACKOFF_MAX_INTERVAL = 20;

    private final List<OIRecipeType<?>> recipeTypes;
    // Persist only: LDLib2 menu bindings sync these UI values while a screen is open, so recipe
    // runtime state deliberately stays out of machine-data TO_CLIENT snapshots and deltas.
    private final DataEnum<State> state = data().enumField("state", State.class, State.IDLE)
            .persisted()
            .syncNone()
            .done();
    private final DataInt progress = data().intField("progress", 0)
            .persisted()
            .syncNone()
            .done();
    private final DataInt maxProgress = data().intField("maxProgress", 0)
            .persisted()
            .syncNone()
            .done();
    private final DataEnum<State> missingRecipeResumeState = data().enumField("missingRecipeResumeState", State.class, State.WORKING)
            .persisted()
            .syncNone()
            .done();
    // Orthogonal to the state machine: HALTED freezes the tick entry whole (progress, per-tick
    // input draw, recipe starts) and the seven states stay untouched — no PAUSED product states.
    private final DataEnum<WorkMode> workMode = data().enumField("workMode", WorkMode.class, WorkMode.RUNNING)
            .persisted()
            .syncNone()
            .done();
    private final DataResourceKey<Recipe<?>> activeRecipeId = data().resourceKey("activeRecipeId", Registries.RECIPE)
            .persisted()
            .syncNone()
            .done();
    /**
     * Search pool bound for the active run (start consume + tick IO inputs). Always a concrete id;
     * {@link RecipeSearchPoolId#DEFAULT} when idle.
     */
    private final DataString activeRecipePoolId = data().stringField(
            "activeRecipePoolId", RecipeSearchPoolId.DEFAULT.value())
            .persisted()
            .syncNone()
            .done();
    /**
     * Parallel factor locked for the active run ({@code 0} = none / idle / legacy fixed-parallel
     * save). New runs probe inventory; active legacy saves migrate 0 to the declared cap because
     * their start inputs have already been consumed.
     */
    private final DataLong activeParallelFactor = data().longField("activeParallelFactor", 0L)
            .persisted()
            .syncNone()
            .done();
    private @Nullable RecipeHolder<? extends OIRecipe> activeRecipe;
    // Transient active view: recipe after RecipeModifier fold (incl. inventory-limited parallel).
    // Set on successful start, re-derived on reload using locked parallel + pure modifiers, cleared
    // when the run ends. All run-phase I/O uses this view; persistence keeps original recipe id +
    // activeParallelFactor.
    private @Nullable OIRecipe activeRecipeView;
    /** Machine-bound direct tick kernel; rebuilt only when the recipe routing table changes. */
    private OIRecipe.@Nullable BoundTickIoPlan activeTickIoPlan;
    /** Routing revision for the latest bind attempt, including a cached unsupported/fallback result. */
    private long activeTickIoPlanRevision = Long.MIN_VALUE;
    /** Cached strict parse of {@link #activeRecipePoolId}; custom lines must not allocate per tick. */
    private boolean activeRecipePoolCacheInitialized;
    private @Nullable String cachedActiveRecipePoolRaw;
    private @Nullable RecipeSearchPoolId cachedActiveRecipePool;
    /** Last parallel chosen by {@link #applyRecipeModifiers}; committed on successful start. */
    private long pendingParallelApplied;
    private long successfulStartCount;
    private long rememberedStartCount;
    private long lastFailedSearchResourceVersion = Long.MIN_VALUE;
    private long lastFailedSearchRevision = Long.MIN_VALUE;
    private long lastBlockedStartResourceVersion = Long.MIN_VALUE;
    private long lastBlockedStartRevision = Long.MIN_VALUE;
    private long lastTickIoBlockedResourceVersion = Long.MIN_VALUE;
    private long lastOutputBlockedResourceVersion = Long.MIN_VALUE;

    protected RecipeLogic(
                          ComponentContext<? extends RecipeLogic> context,
                          List<OIRecipeType<?>> recipeTypes) {
        super(context);
        this.recipeTypes = List.copyOf(recipeTypes);
    }

    public static ComponentMount<RecipeLogic> mount(
                                                    ComponentKey<RecipeLogic> key,
                                                    OIRecipeType<?>... recipeTypes) {
        List<OIRecipeType<?>> types = List.of(recipeTypes);
        return mount(key, types);
    }

    public static ComponentMount<RecipeLogic> mount(
                                                    ComponentKey<RecipeLogic> key,
                                                    List<OIRecipeType<?>> recipeTypes) {
        Objects.requireNonNull(key, "recipe logic trait key");
        Objects.requireNonNull(recipeTypes, "recipe types");
        if (recipeTypes.isEmpty()) {
            throw new IllegalArgumentException("Recipe logic trait requires at least one recipe type");
        }
        for (OIRecipeType<?> type : recipeTypes) {
            if (type.isVanillaFacing()) {
                throw new IllegalArgumentException(
                        "Cannot mount vanilla-facing recipe type on a machine: " + type.id());
            }
        }
        List<OIRecipeType<?>> types = List.copyOf(recipeTypes);
        return key.mount(context -> new RecipeLogic(context, types), new RecipeLogicMetadata(types));
    }

    public List<OIRecipeType<?>> recipeTypes() {
        return recipeTypes;
    }

    public State state() {
        return state.value();
    }

    @Override
    public boolean isRunning() {
        // HALTED reads as not running so every work-view consumer (active blockstate, UI, Jade)
        // shows the halt immediately; the held state itself stays untouched for the resume.
        return workMode.value() == WorkMode.RUNNING && state().isRunning();
    }

    @Override
    public boolean publishesRunningStateEdges() {
        return true;
    }

    @Override
    public boolean isBusy() {
        return state().isBusy();
    }

    @Override
    public int progress() {
        return progress.value();
    }

    @Override
    public int maxProgress() {
        return maxProgress.value();
    }

    @Override
    public float progressPercent() {
        int max = maxProgress.value();
        return max <= 0 ? 0.0f : Math.clamp(progress.value() / (float) max, 0.0f, 1.0f);
    }

    @Override
    public @Nullable ResourceKey<Recipe<?>> activeRecipeId() {
        return activeRecipeId.value();
    }

    /** Transient monotonic counters used by opt-in performance probes. */
    public long successfulStartCount() {
        return successfulStartCount;
    }

    public long rememberedStartCount() {
        return rememberedStartCount;
    }

    /** GameTest-only progress hook; real machines must advance through {@link MachineTicker}. */
    public void setProgressForGameTest(int progress) {
        if (state() != State.WORKING || activeRecipe == null || maxProgress.value() <= 0) {
            throw new IllegalStateException("Cannot set recipe progress while state=" + state + ", activeRecipe=" + activeRecipeId.value() + ", maxProgress=" + maxProgress.value());
        }
        this.progress.set(Math.clamp(progress, 0, maxProgress.value()));
        // GameTest pokes machine state without a storage commit; make sure a parked ticker still
        // observes the new progress promptly.
        leaveBackoff();
    }

    @Override
    public WorkMode workMode() {
        return workMode.value();
    }

    @Override
    public WorkMode toggleWorkMode() {
        boolean wasRunning = isRunning();
        WorkMode next = workMode.value() == WorkMode.RUNNING ? WorkMode.HALTED : WorkMode.RUNNING;
        workMode.set(next);
        requestActiveRefreshIfChanged(wasRunning);
        // Both directions change what the next tick should do; a parked ticker must re-evaluate
        // now instead of waiting out its backed-off interval.
        leaveBackoff();
        return next;
    }

    /** {@link MachineResourceWake}: any committed storage change can unblock a parked state. */
    @Override
    public void onResourceContentChanged() {
        leaveBackoff();
    }

    /**
     * Parked no-op ticks decay the hub interval (1→2→4→8→16→cap). The decay is driven by executed
     * ticks, so a machine reaches the cap after ~30 quiet ticks; profiler/gametest direct calls
     * pass {@code NoopTickHandle} and are unaffected.
     */
    private void backOffParkedTick(TickHandle handle) {
        int interval = handle.interval();
        if (interval < PARKED_BACKOFF_MAX_INTERVAL) {
            handle.setInterval(Math.min(PARKED_BACKOFF_MAX_INTERVAL, interval * 2));
        }
    }

    /**
     * Event wake: snap back to the every-tick cadence and queue one immediate run on the next
     * heartbeat. No-op while already running every tick, so working machines pay nothing when
     * their own recipe I/O commits fan back into this hook. An explicitly suspended handle is
     * never woken — suspension (gametest manual control, ConditionalTicker) outranks events, and
     * the hub alert queue would otherwise run a suspended hook once.
     */
    private void leaveBackoff() {
        TickHandle attached = handle();
        if (attached == null || attached.isCancelled() || attached.isSuspended() || attached.interval() == 1) {
            return;
        }
        attached.setInterval(1);
        attached.alert();
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        OIRecipe workingRecipe = activeRecipeView;
        if (workingRecipe != null && cachedActiveRecipePool != null && workMode.value() == WorkMode.RUNNING && state.value() == State.WORKING) {
            processWorkingTick(workingRecipe);
            if (handle.interval() != 1) {
                handle.setInterval(1);
            }
            return;
        }
        if (parkedThisTick()) {
            backOffParkedTick(handle);
        } else if (handle.interval() != 1) {
            // Any productive tick (state transition, fresh search, recipe work) snaps back to the
            // every-tick cadence; only proven parked no-ops are allowed to decay the interval.
            handle.setInterval(1);
        }
    }

    /**
     * Runs one state-machine step and reports whether it was a parked no-op: a tick whose early
     * return is proven to repeat until an external event (resource version bump, recipe-set
     * revision change, work-mode toggle) or a backoff-capped poll. Parked ticks decay the hub
     * interval up to {@link #PARKED_BACKOFF_MAX_INTERVAL}; {@link #leaveBackoff()} is the event
     * side that snaps it back.
     */
    private boolean parkedThisTick() {
        if (workMode.value() == WorkMode.HALTED) {
            // The held state cannot progress until the next toggle; toggleWorkMode wakes us.
            return true;
        }
        if (!resolveActiveRecipeIfNeeded()) {
            // MISSING_ACTIVE_RECIPE: only a recipe reload can resolve this; poll at the cap.
            return true;
        }
        return switch (state()) {
            case IDLE, WAITING_TICK_INPUT_TO_START, WAITING_TICK_OUTPUT_TO_START -> tryStartRecipe();
            case WORKING -> {
                processWorkingTick(Objects.requireNonNull(activeRecipeView));
                yield false;
            }
            case WAITING_TICK_INPUT_TO_PROCESS -> retryTickIo();
            case WAITING_OUTPUT -> retryOutputs();
            // Defensive arm: resolveActiveRecipeIfNeeded always leaves MISSING on success.
            case MISSING_ACTIVE_RECIPE -> true;
        };
    }

    private boolean tryStartRecipe() {
        if (!recipeConditionsAllowSearch()) {
            clearIdleSearchCaches();
            // RecipeCondition has no wake contract; a gate flip is only observable by polling, so
            // condition-gated machines keep the every-tick cadence instead of parking.
            return false;
        }
        boolean waitingToStart = state().isWaitingToStart();
        long resourceVersion = machine().machineComponents().resourceContentVersion();
        boolean searchCacheable = mountedSearchesResourceVersionCacheable();
        long searchRevision = recipeSearchRevisionFingerprint();
        if (searchCacheable && ((lastFailedSearchResourceVersion == resourceVersion && lastFailedSearchRevision == searchRevision) || (lastBlockedStartResourceVersion == resourceVersion && lastBlockedStartRevision == searchRevision))) {
            // Optimization: reuse an IDLE "no start-input recipe" result while neither machine
            // resources nor mounted recipe indexes changed; the same guard also skips a repeated
            // start-gate block for a resource-stable first candidate. Principle: empty/invalid or
            // output-blocked single-line machines should not redo full search/transaction work
            // every tick, but only capability-declared resource-stable recipe types may participate.
            return true;
        }

        RecipeSearchPoolRouter.SearchHit hit = searchRecipe();
        if (hit == null) {
            if (waitingToStart) {
                resetToIdle();
                return false;
            }
            if (searchCacheable) {
                lastFailedSearchResourceVersion = resourceVersion;
                lastFailedSearchRevision = recipeSearchRevisionFingerprint();
                // Global idle cache still applies when every pool skipped or missed; per-pool
                // backoff lives on RecipeSearchPool (max 5s, wake on content change).
                return true;
            }
            clearIdleSearchCaches();
            return false;
        }
        clearIdleSearchCaches();
        MachineComponents components = machine().machineComponents();
        RecipeSearchPoolRouter router = components.recipeSearchPoolRouter();
        RecipeSearchPoolRouter.SearchHit candidate = hit;
        while (true) {
            OIRecipe recipe;
            OIRecipe.TickIoResult startTickIo;
            // A historical holder is only a pool-local search hint. Re-run every start gate before
            // learning or committing it; if a later gate rejects it, the one traditional retry
            // below restores the original type/holder selection semantics.
            try (MachineComponents.RecipePoolScope poolScope = components.openRecipePool(candidate.poolId())) {
                recipe = applyRecipeModifiers(candidate.holder().value());
                startTickIo = recipe.checkTickIo(machine());
                if (startTickIo == OIRecipe.TickIoResult.SUCCESS && !recipe.canEmitOutputs(machine())) {
                    startTickIo = OIRecipe.TickIoResult.OUTPUT_BLOCKED;
                }
                candidate.requireCurrentRevision();
                if (startTickIo == OIRecipe.TickIoResult.SUCCESS && recipe.consumeInputs(machine())) {
                    clearRuntimeRetryCaches();
                    activeRecipe = candidate.holder();
                    activeRecipeView = recipe;
                    clearActiveTickIoRuntime();
                    activeRecipeId.set(candidate.holder().id());
                    activeRecipePoolId.set(candidate.poolId().value());
                    cacheActiveRecipePool(candidate.poolId());
                    // Lock inventory-probed parallel for this run (0 when no ParallelModifier).
                    activeParallelFactor.set(Math.max(0L, pendingParallelApplied));
                    progress.set(0);
                    maxProgress.set(recipe.duration());
                    setState(State.WORKING);
                    router.rememberSuccessfulStart(candidate);
                    successfulStartCount++;
                    if (candidate.source() == RecipeSearchPoolRouter.SearchSource.HISTORY) {
                        rememberedStartCount++;
                    }
                    return false;
                }
            }

            if (candidate.source() == RecipeSearchPoolRouter.SearchSource.HISTORY) {
                router.noteRememberedStartFailure(candidate);
                RecipeSearchPoolRouter.SearchHit traditional = router.researchPoolWithoutHistory(
                        machine(), recipeTypes, candidate.poolId(), components::openRecipePool);
                if (traditional == null) {
                    throw new IllegalStateException("Remembered recipe matched inputs but traditional search " + "found no candidate in pool " + candidate.poolId() + " for " + candidate.recipeType().id() + ": " + candidate.holder().id());
                }
                if (!sameSearchCandidate(candidate, traditional)) {
                    candidate = traditional;
                    continue;
                }
            }
            if (startTickIo != OIRecipe.TickIoResult.SUCCESS) {
                return waitToStart(recipe, startTickIo, resourceVersion, searchRevision, searchCacheable);
            }
            clearBlockedStartCache();
            if (waitingToStart) {
                resetToIdle();
            }
            return false;
        }
    }

    private static boolean sameSearchCandidate(
                                               RecipeSearchPoolRouter.SearchHit left, RecipeSearchPoolRouter.SearchHit right) {
        return left.recipeType() == right.recipeType() && left.searchRevision() == right.searchRevision() && left.holder().id().equals(right.holder().id());
    }

    private void processWorkingTick(OIRecipe view) {
        OIRecipe.TickIoResult tickIo = handleActiveTickIo(view);
        if (tickIo != OIRecipe.TickIoResult.SUCCESS) {
            waitDuringProcessing(view, tickIo);
            return;
        }
        clearTickIoBlockedCache();
        advanceProgress(view);
    }

    private boolean retryTickIo() {
        OIRecipe view = activeRecipeView;
        if (view == null) {
            resetToIdle();
            return false;
        }
        return retryProcessTickIo(view);
    }

    private boolean retryProcessTickIo(OIRecipe view) {
        if (view.tickIoResourceVersionStable() && lastTickIoBlockedResourceVersion == machine().machineComponents().resourceContentVersion()) {
            // Optimization: repeated tick-I/O waiting ticks skip transactional retry until an
            // input/output resource actually changes. Principle: if the relevant capabilities are
            // resource-version-stable, the same blocked result is guaranteed while the version is
            // unchanged.
            return true;
        }
        OIRecipe.TickIoResult tickIo = handleActiveTickIo(view);
        if (tickIo != OIRecipe.TickIoResult.SUCCESS) {
            waitDuringProcessing(view, tickIo);
            return false;
        }
        clearTickIoBlockedCache();
        setState(State.WORKING);
        advanceProgress(view);
        return false;
    }

    /**
     * Working-tick I/O with a compiled scalar fast path and a full transactional fallback. The
     * resource batch preserves every field-level dirty callback while collapsing version/wake
     * fan-out and excluding this already-running logic from waking itself.
     */
    private OIRecipe.TickIoResult handleActiveTickIo(OIRecipe recipe) {
        if (!recipe.hasTickIo()) {
            return OIRecipe.TickIoResult.SUCCESS;
        }
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            throw new IllegalStateException("Recipe tick IO must run outside a transaction for " + machine().definition().id() + " at " + machine().getBlockPos() + ", activeRecipe=" + activeRecipeId.value());
        }
        MachineComponents components = machine().machineComponents();
        components.beginResourceMutationBatch(this);
        try {
            long routingRevision = components.recipeRoutingRevision();
            if (activeTickIoPlanRevision != routingRevision) {
                RecipeSearchPoolId poolId = Objects.requireNonNull(
                        resolvedActiveRecipePool(),
                        "active recipe pool must be validated before binding recipe IO");
                activeTickIoPlan = recipe.bindDirectTickIo(machine(), poolId, routingRevision);
                activeTickIoPlanRevision = routingRevision;
            }
            OIRecipe.BoundTickIoPlan plan = activeTickIoPlan;
            if (plan != null && plan.routingRevision() == routingRevision) {
                OIRecipe.TickIoResult directResult = plan.tryHandleDirect();
                if (directResult != null) {
                    return directResult;
                }
            }
            try (MachineComponents.RecipePoolScope poolScope = openActiveRecipePool()) {
                return recipe.handleTickIoTransactional(machine());
            }
        } finally {
            components.endResourceMutationBatch();
        }
    }

    private void advanceProgress(OIRecipe recipe) {
        progress.inc();
        if (progress.value() >= maxProgress.value()) {
            // Emit into the same pool the recipe was started on (activeRecipePoolId).
            try (MachineComponents.RecipePoolScope poolScope = openActiveRecipePool()) {
                if (recipe.emitOutputs(machine())) {
                    resetToIdle();
                } else {
                    setState(State.WAITING_OUTPUT);
                    cacheOutputBlockedIfStable(recipe);
                }
            }
            return;
        }
    }

    private boolean retryOutputs() {
        OIRecipe view = activeRecipeView;
        if (view == null) {
            resetToIdle();
            return false;
        }
        if (progress.value() < maxProgress.value()) {
            return retryProcessTickIo(view);
        }
        if (view.outputsResourceVersionStable() && lastOutputBlockedResourceVersion == machine().machineComponents().resourceContentVersion()) {
            // Optimization: repeated WAITING_OUTPUT ticks skip output insertion simulation until
            // output resources change. Principle: output feasibility for resource-stable
            // capabilities cannot improve without a version bump from extraction or storage edits.
            return true;
        }
        try (MachineComponents.RecipePoolScope poolScope = openActiveRecipePool()) {
            if (view.emitOutputs(machine())) {
                clearOutputBlockedCache();
                resetToIdle();
            } else {
                cacheOutputBlockedIfStable(view);
            }
        }
        return false;
    }

    /** @return whether the blocked start was cached, i.e. following ticks are proven no-ops. */
    private boolean waitToStart(
                                OIRecipe recipe,
                                OIRecipe.TickIoResult reason,
                                long resourceVersion,
                                long searchRevision,
                                boolean searchCacheable) {
        State nextState = reason == OIRecipe.TickIoResult.INPUT_BLOCKED ? State.WAITING_TICK_INPUT_TO_START : State.WAITING_TICK_OUTPUT_TO_START;
        progress.set(0);
        maxProgress.set(0);
        clearActiveRecipe();
        setState(nextState);
        int poolCount = machine().machineComponents().recipeSearchPools().size();
        cacheBlockedStartIfStable(
                recipe,
                resourceVersion,
                searchRevision,
                canCacheBlockedStart(searchCacheable, poolCount));
        return lastBlockedStartResourceVersion != Long.MIN_VALUE;
    }

    static boolean canCacheBlockedStart(boolean searchCacheable, int concretePoolCount) {
        // A machine-wide blocked result is sound only for one concrete line. With multiple lines,
        // the round-robin router must be allowed to try the next line on the following tick.
        return searchCacheable && concretePoolCount == 1;
    }

    private void waitDuringProcessing(OIRecipe recipe, OIRecipe.TickIoResult reason) {
        State nextState = reason == OIRecipe.TickIoResult.INPUT_BLOCKED ? State.WAITING_TICK_INPUT_TO_PROCESS : State.WAITING_OUTPUT;
        setState(nextState);
        cacheTickIoBlockedIfStable(recipe);
    }

    /**
     * Delegates to {@link RecipeSearchPoolRouter#search}: per-pool miss backoff (max 5s), with
     * content-change wake interrupting backoff. Resource-empty pools still get one attempt for
     * recipes without one-time inputs.
     */
    private RecipeSearchPoolRouter.@Nullable SearchHit searchRecipe() {
        Level level = machine().getLevel();
        long gameTime = level == null ? 0L : level.getGameTime();
        MachineComponents components = machine().machineComponents();
        return components.recipeSearchPoolRouter().search(
                machine(),
                recipeTypes,
                gameTime,
                components::openRecipePool);
    }

    private MachineComponents.RecipePoolScope openActiveRecipePool() {
        RecipeSearchPoolId poolId = Objects.requireNonNull(
                resolvedActiveRecipePool(),
                "active recipe pool must be validated before recipe IO");
        return machine().machineComponents().openRecipePool(poolId);
    }

    private @Nullable RecipeSearchPoolId resolvedActiveRecipePool() {
        String raw = activeRecipePoolId.value();
        if (!activeRecipePoolCacheInitialized || !Objects.equals(raw, cachedActiveRecipePoolRaw)) {
            cachedActiveRecipePoolRaw = raw;
            cachedActiveRecipePool = resolvePersistedActivePoolId(raw);
            activeRecipePoolCacheInitialized = true;
        }
        return cachedActiveRecipePool;
    }

    private void cacheActiveRecipePool(RecipeSearchPoolId poolId) {
        cachedActiveRecipePoolRaw = poolId.value();
        cachedActiveRecipePool = poolId;
        activeRecipePoolCacheInitialized = true;
    }

    /**
     * Keeps legacy UNIVERSAL as an explicit compatibility key; the router exposes a non-pollable
     * public/global-only view for it. Concrete ids are never remapped, so a missing dedicated line
     * fails closed instead of silently consuming from or emitting into another line.
     */
    static @Nullable RecipeSearchPoolId resolvePersistedActivePoolId(String raw) {
        return RecipeSearchPoolId.parseStrict(raw);
    }

    private long recipeSearchRevisionFingerprint() {
        long hash = 1125899906842597L;
        for (OIRecipeType<?> type : recipeTypes) {
            hash = 31L * hash + type.searchRevision();
        }
        // Pool membership (router identity) changes must invalidate idle "no recipe" caches.
        for (RecipeSearchPoolId poolId : machine().machineComponents().recipeSearchPools()) {
            hash = 31L * hash + poolId.hashCode();
        }
        return hash;
    }

    private boolean mountedSearchesResourceVersionCacheable() {
        for (OIRecipeType<?> type : recipeTypes) {
            if (!type.resourceVersionSearchCacheable(machine())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extension happens through the capability seam, not subclass overrides: mount a trait that
     * provides {@link RecipeCondition} (or {@link RecipeModifier}) to influence this logic. Uses the
     * non-allocating visitor because this gate runs on every idle search tick.
     */
    private boolean recipeConditionsAllowSearch() {
        return !machine().machineComponents().anyServiceMatches(
                RecipeCondition.KEY, this, condition -> !condition.allowsRecipeSearch());
    }

    private OIRecipe applyRecipeModifiers(OIRecipe recipe) {
        List<ServiceMatch<RecipeModifier>> modifiers = machine().machineComponents().services(RecipeModifier.KEY, this);
        OIRecipe modified = recipe;
        long parallelCap = 1L;
        for (ServiceMatch<RecipeModifier> match : modifiers) {
            RecipeModifier mod = match.value();
            modified = mod.modify(modified, machine());
            parallelCap = Math.max(parallelCap, mod.parallelCap());
        }

        boolean activeRun = state().requiresActiveRecipe();
        long persistedFactor = activeParallelFactor.value();
        OIRecipe folded = modified;
        long resolvedCap = parallelCap;
        long actual = resolveParallelFactor(
                resolvedCap,
                persistedFactor,
                activeRun,
                () -> folded.maxParallelByInputs(machine(), resolvedCap));
        if (actual == 0) {
            pendingParallelApplied = 0L;
            return modified;
        }

        if (activeRun && persistedFactor == 0) {
            // One-way migration from fixed-parallel saves: never inspect already-consumed inputs.
            activeParallelFactor.set(actual);
        }
        pendingParallelApplied = actual;
        return modified.withParallel(actual);
    }

    /**
     * Resolves one parallel factor without allowing an active run to inspect mutable inventory.
     * A positive run lock wins even if the current provider cap changed. A missing lock on an
     * active run is the pre-dynamic-save format, whose only valid factor was its fixed cap.
     */
    static long resolveParallelFactor(
                                      long parallelCap,
                                      long persistedFactor,
                                      boolean activeRun,
                                      LongSupplier newRunInputProbe) {
        Objects.requireNonNull(newRunInputProbe, "new-run input probe");
        if (activeRun && persistedFactor > 0) {
            return persistedFactor;
        }
        if (parallelCap <= 1) {
            return 0;
        }
        if (activeRun) {
            return parallelCap;
        }
        return Math.max(1L, Math.min(newRunInputProbe.getAsLong(), parallelCap));
    }

    @SuppressWarnings("unchecked")
    private boolean resolveActiveRecipeIfNeeded() {
        State currentState = state();
        if (!currentState.requiresActiveRecipe()) {
            return true;
        }
        if (resolvedActiveRecipePool() == null) {
            // A corrupt/foreign line id must halt the active run. Falling back to DEFAULT here
            // would consume from and emit into a different production line.
            enterMissingActiveRecipe(currentState);
            return false;
        }
        RecipeHolder<? extends OIRecipe> current = activeRecipe;
        if (current != null) {
            if (activeRecipeView == null) {
                activeRecipeView = applyRecipeModifiers(current.value());
                clearActiveTickIoRuntime();
            }
            return true;
        }
        ResourceKey<Recipe<?>> recipeId = activeRecipeId.value();
        Level level = machine().getLevel();
        if (recipeId == null || level == null || level.isClientSide() || level.getServer() == null) {
            enterMissingActiveRecipe(currentState);
            return false;
        }
        RecipeHolder<? extends OIRecipe> resolved = resolveSupportedRecipe(level.getServer(), recipeId);
        if (resolved == null) {
            enterMissingActiveRecipe(currentState);
            return false;
        }
        OIRecipe recipe = resolved.value();
        if (!supportsRecipeType(recipe.recipeType())) {
            enterMissingActiveRecipe(currentState);
            return false;
        }
        activeRecipe = resolved;
        // Reload re-derivation: persistence stores only the original recipe id, so rebuild the
        // active view by re-applying every RecipeModifier in the same deterministic mount order as
        // the original start. Pure modifiers make this fold reproduce the running view exactly.
        OIRecipe view = applyRecipeModifiers(recipe);
        activeRecipeView = view;
        clearActiveTickIoRuntime();
        maxProgress.set(Math.max(maxProgress.value(), view.duration()));
        progress.set(Math.clamp(progress.value(), 0, maxProgress.value()));
        if (currentState == State.MISSING_ACTIVE_RECIPE) {
            State resume = missingRecipeResumeState.value();
            setState(resume.requiresActiveRecipe() && resume != State.MISSING_ACTIVE_RECIPE ? resume : State.WORKING);
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private @Nullable RecipeHolder<? extends OIRecipe> resolveSupportedRecipe(
                                                                              MinecraftServer server,
                                                                              ResourceKey<Recipe<?>> recipeId) {
        for (OIRecipeType<?> recipeType : recipeTypes) {
            RecipeHolder<?> resolved = recipeType.resolveRecipe(server, recipeId);
            if (resolved != null && resolved.value() instanceof OIRecipe recipe && supportsRecipeType(recipe.recipeType())) {
                return (RecipeHolder<? extends OIRecipe>) resolved;
            }
        }
        return null;
    }

    private boolean supportsRecipeType(OIRecipeType<?> recipeType) {
        for (OIRecipeType<?> supported : recipeTypes) {
            if (supported == recipeType) {
                return true;
            }
        }
        return false;
    }

    private void resetToIdle() {
        setState(State.IDLE);
        progress.set(0);
        maxProgress.set(0);
        missingRecipeResumeState.set(State.WORKING);
        clearActiveRecipe();
        clearAllSearchAndRetryCaches();
    }

    private void enterMissingActiveRecipe(State previousState) {
        // Safety contract: once start inputs have been committed, losing the recipe definition is a
        // paused/error state, not an excuse to reset to IDLE and make resources disappear.
        if (previousState != State.MISSING_ACTIVE_RECIPE && previousState.requiresActiveRecipe()) {
            missingRecipeResumeState.set(previousState);
        }
        activeRecipe = null;
        activeRecipeView = null;
        clearActiveTickIoRuntime();
        setState(State.MISSING_ACTIVE_RECIPE);
        clearAllSearchAndRetryCaches();
    }

    private void clearActiveRecipe() {
        activeRecipeId.set(null);
        activeRecipePoolId.set(RecipeSearchPoolId.DEFAULT.value());
        cacheActiveRecipePool(RecipeSearchPoolId.DEFAULT);
        activeParallelFactor.set(0L);
        pendingParallelApplied = 0L;
        activeRecipe = null;
        activeRecipeView = null;
        clearActiveTickIoRuntime();
    }

    private void clearActiveTickIoRuntime() {
        activeTickIoPlan = null;
        activeTickIoPlanRevision = Long.MIN_VALUE;
    }

    private void setState(State next) {
        State previous = state.value();
        if (previous == next) {
            return;
        }
        boolean wasRunning = isRunning();
        state.set(next);
        requestActiveRefreshIfChanged(wasRunning);
    }

    private void requestActiveRefreshIfChanged(boolean wasRunning) {
        if (wasRunning != isRunning()) {
            machine().requestActiveBlockStateRefresh();
        }
    }

    private void cacheBlockedStartIfStable(
                                           OIRecipe recipe,
                                           long resourceVersion,
                                           long searchRevision,
                                           boolean searchCacheable) {
        if (!searchCacheable || !recipe.startRetryResourceVersionStable()) {
            clearBlockedStartCache();
            return;
        }
        // Optimization: remember a resource-stable first candidate whose start gates failed.
        // Principle: OI semantics already stop at the first input-matching recipe; if its tick
        // precheck or output precheck failed and no relevant resource changed, the same candidate
        // would fail again on the next IDLE tick.
        lastBlockedStartResourceVersion = resourceVersion;
        lastBlockedStartRevision = searchRevision;
    }

    private void cacheTickIoBlockedIfStable(OIRecipe recipe) {
        if (recipe.tickIoResourceVersionStable()) {
            lastTickIoBlockedResourceVersion = machine().machineComponents().resourceContentVersion();
        } else {
            clearTickIoBlockedCache();
        }
    }

    private void cacheOutputBlockedIfStable(OIRecipe recipe) {
        if (recipe.outputsResourceVersionStable()) {
            lastOutputBlockedResourceVersion = machine().machineComponents().resourceContentVersion();
        } else {
            clearOutputBlockedCache();
        }
    }

    private void clearIdleSearchCaches() {
        lastFailedSearchResourceVersion = Long.MIN_VALUE;
        lastFailedSearchRevision = Long.MIN_VALUE;
        clearBlockedStartCache();
    }

    private void clearBlockedStartCache() {
        lastBlockedStartResourceVersion = Long.MIN_VALUE;
        lastBlockedStartRevision = Long.MIN_VALUE;
    }

    private void clearRuntimeRetryCaches() {
        clearTickIoBlockedCache();
        clearOutputBlockedCache();
    }

    private void clearTickIoBlockedCache() {
        lastTickIoBlockedResourceVersion = Long.MIN_VALUE;
    }

    private void clearOutputBlockedCache() {
        lastOutputBlockedResourceVersion = Long.MIN_VALUE;
    }

    private void clearAllSearchAndRetryCaches() {
        clearIdleSearchCaches();
        clearRuntimeRetryCaches();
    }

    public enum State {

        IDLE(false, false),
        WORKING(true, true),
        WAITING_OUTPUT(true, true),
        WAITING_TICK_INPUT_TO_PROCESS(true, true),
        WAITING_TICK_INPUT_TO_START(true, false),
        WAITING_TICK_OUTPUT_TO_START(true, false),
        MISSING_ACTIVE_RECIPE(true, false);

        private final boolean busy;
        private final boolean running;

        State(boolean busy, boolean running) {
            this.busy = busy;
            this.running = running;
        }

        public boolean isBusy() {
            return busy;
        }

        public boolean isRunning() {
            return running;
        }

        /** UI display of this state; values live in {@code BuiltinOIMachineUiLang}. */
        public net.minecraft.network.chat.Component displayName() {
            return switch (this) {
                case IDLE -> OIApiLang.UI_RECIPE_STATE_IDLE.getComponent();
                case WORKING -> OIApiLang.UI_RECIPE_STATE_WORKING.getComponent();
                case WAITING_OUTPUT -> OIApiLang.UI_RECIPE_STATE_WAITING_OUTPUT.getComponent();
                case WAITING_TICK_INPUT_TO_PROCESS -> OIApiLang.UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_PROCESS.getComponent();
                case WAITING_TICK_INPUT_TO_START -> OIApiLang.UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_START.getComponent();
                case WAITING_TICK_OUTPUT_TO_START -> OIApiLang.UI_RECIPE_STATE_WAITING_TICK_OUTPUT_TO_START.getComponent();
                case MISSING_ACTIVE_RECIPE -> OIApiLang.UI_RECIPE_STATE_MISSING_ACTIVE_RECIPE.getComponent();
            };
        }

        private boolean isWaitingToStart() {
            return this == WAITING_TICK_INPUT_TO_START || this == WAITING_TICK_OUTPUT_TO_START;
        }

        private boolean requiresActiveRecipe() {
            return switch (this) {
                case WORKING, WAITING_OUTPUT, WAITING_TICK_INPUT_TO_PROCESS, MISSING_ACTIVE_RECIPE -> true;
                case IDLE, WAITING_TICK_INPUT_TO_START, WAITING_TICK_OUTPUT_TO_START -> false;
            };
        }
    }
}
