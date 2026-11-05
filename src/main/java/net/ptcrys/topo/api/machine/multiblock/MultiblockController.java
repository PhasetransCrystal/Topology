package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.RecipeCondition;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Server-side multiblock controller state machine.
 *
 * <p>
 * The recheck pipeline is a flat template of five steps — {@code snapshot} → {@code recognize} →
 * {@code claim} → {@code apply} → {@code publish} — and is fail-closed: any step failure logs, forces
 * the unformed state and releases this controller's claims, so a broken recheck can never crash the
 * tick or leave a stale {@code FORMED} block state behind.
 *
 * <p>
 * Correctness does not depend on event coverage: formed controllers additionally self-mark dirty
 * every {@link net.ptcrys.topo.Topology#config} MULTIBLOCK.backstopIntervalTicks ticks, staggered by
 * position hash, so a missed world change is repaired by the next backstop recheck at the latest.
 * Backstop upkeep on an intact structure is the cheap path — a single formed-orientation capture plus
 * the zero-allocation {@link StructureEngine#verify}; the full recognize (mirror twin, members,
 * diagnostics) only runs on formation transitions and verify failures.
 */
public final class MultiblockController extends MachineTicker {

    public static final ComponentKey<MultiblockController> CONTROLLER = ComponentKey.id("multiblock_controller", MultiblockController.class)
            .service(RecipeCondition.KEY, (trait, unused) -> trait::formed)
            // Roles bridge: the controller-mounted recipe logic folds this single modifier,
            // which in turn folds every member-mounted RecipeModifier capability through
            // FormedRuntime.roles(...) in deterministic member order. Recipe logic itself
            // stays multiblock-blind. UI lists only this machine's providers (this entry),
            // not the members' own labels.
            .service(RecipeModifier.KEY, MultiblockController::memberFoldModifier);

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiblockController.class);

    private final Blueprint blueprint;
    private final List<MultiblockModule> moduleSpecs;
    private final FormedRuntime runtime;
    private final MultiblockRecipeModifiers recipeModifiers;
    /** Last footprint registration, so an unchanged (orientation, mirror) pair skips the watch rebuild. */
    private @Nullable MultiblockClaimIndex watchedClaims;
    private @Nullable Orientation watchedOrientation;
    /** Per-level services, cached so the every-tick path skips the binder's synchronized map lookup. */
    private MultiblockLevelBinder.@Nullable Services cachedServices;
    /**
     * The orientation/repeats the formed structure recognized under; null when unformed. This is
     * the single source of truth for both formation status and formed geometry, so the two cannot
     * diverge. Targeted invalidation re-derives any cell from it arithmetically — no per-cell index
     * exists.
     */
    private @Nullable FormedShape formedShape;
    /** Latest per-module match state; updated even when optional modules change without unforming. */
    private Map<String, RecognitionResult.ModuleReport> modules = Map.of();
    /** In-flight async recognition; results commit on the server thread with staleness checks. */
    private @Nullable PendingRecognition pending;
    /** In-flight async upkeep verification (formed backstop); commits like {@link #pending}. */
    private @Nullable PendingVerification pendingVerify;
    /** Set when an upkeep verify failed: the next consumed mark pays the full recognize path. */
    private boolean forceFullRecheck;
    private long submissionCounter;
    /** Earliest game time at which an unloaded-chunk retry may run. */
    private long retryAfterGameTime;

    private MultiblockController(
                                 ComponentContext<MultiblockController> context,
                                 Blueprint blueprint,
                                 List<MultiblockModule> moduleSpecs) {
        super(context);
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.moduleSpecs = List.copyOf(Objects.requireNonNull(moduleSpecs, "multiblock modules"));
        this.runtime = new FormedRuntime(machine());
        this.recipeModifiers = new MultiblockRecipeModifiers(machine(), runtime);
    }

    public static ComponentMount<MultiblockController> mount(Blueprint blueprint) {
        return mount(CONTROLLER, blueprint);
    }

    public static ComponentMount<MultiblockController> mount(Blueprint blueprint, MultiblockModule... modules) {
        return mount(CONTROLLER, blueprint, modules);
    }

    public static ComponentMount<MultiblockController> mount(
                                                             ComponentKey<MultiblockController> key,
                                                             Blueprint blueprint) {
        return mount(key, blueprint, new MultiblockModule[0]);
    }

    public static ComponentMount<MultiblockController> mount(
                                                             ComponentKey<MultiblockController> key,
                                                             Blueprint blueprint,
                                                             MultiblockModule... modules) {
        Objects.requireNonNull(key, "multiblock controller trait key");
        Objects.requireNonNull(blueprint, "blueprint");
        List<MultiblockModule> specs = List.of(modules);
        return key.mount(
                context -> new MultiblockController(context, blueprint, specs),
                new MultiblockControllerMetadata(blueprint, specs));
    }

    public boolean formed() {
        return formedShape != null;
    }

    /** The resolved repeat count per blueprint segment, or {@code null} when unformed. */
    public @Nullable RepeatResolution repeatResolution() {
        FormedShape shape = formedShape;
        return shape == null ? null : shape.repeats();
    }

    /** Resolved copy count of one blueprint repeat section, or {@code 0} when unformed. */
    public int repeatCount(int sectionIndex) {
        FormedShape shape = formedShape;
        return shape == null ? 0 : shape.repeats().count(sectionIndex);
    }

    /** Latest module reports keyed by module id, including optional modules. */
    public Map<String, RecognitionResult.ModuleReport> moduleReports() {
        return modules;
    }

    /** Module ids whose full cell set currently matches. */
    public Set<String> satisfiedModules() {
        Set<String> satisfied = new LinkedHashSet<>();
        for (Map.Entry<String, RecognitionResult.ModuleReport> entry : modules.entrySet()) {
            if (entry.getValue().satisfied()) {
                satisfied.add(entry.getKey());
            }
        }
        return Set.copyOf(satisfied);
    }

    /** One module report, or {@code null} when the id is not declared on this blueprint. */
    public RecognitionResult.@Nullable ModuleReport moduleReport(String id) {
        return modules.get(Objects.requireNonNull(id, "module id"));
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        if (!(machine().getLevel() instanceof ServerLevel level)) {
            return;
        }
        runtime.pollMemberResourceVersions();
        MultiblockLevelBinder.Services services = services(level);
        MultiblockRecheckDirtySet rechecks = services.rechecks();
        commitPendingRecognition(level, services);
        commitPendingVerification(level, services);
        if (gameTime < retryAfterGameTime) {
            return;
        }
        if (formed() && Math.floorMod(machine().getBlockPos().hashCode() + gameTime, Topology.config.MULTIBLOCK.backstopIntervalTicks) == 0) {
            rechecks.markDirty(machine().getBlockPos());
        }
        MultiblockRecheckDirtySet.Note note = rechecks.consume(machine().getBlockPos());
        if (note == null) {
            return;
        }
        // Targeted invalidation: a formed structure re-tests exactly the changed cells — O(changes)
        // instead of O(footprint) — and unforms within this tick on the first mismatch. Bounded
        // notes that touch positions outside the formed footprint (repeat growth area) escalate.
        boolean escalated = false;
        if (moduleSpecs.isEmpty() && Topology.config.MULTIBLOCK.enableTargetedRecheck && formedShape != null && note.bounded()) {
            try {
                TargetedVerdict verdict = targetedRecheck(level, note.changedCells());
                if (verdict == TargetedVerdict.RETAINED) {
                    return;
                }
                if (verdict == TargetedVerdict.BROKEN) {
                    unformNow(level, services);
                    rechecks.markDirty(machine().getBlockPos());
                    return;
                }
                escalated = true; // Growth area touched: repeats may change — pay the full recognize.
            } catch (Exception failure) {
                failClosed(level, services, failure);
                return;
            }
        }
        if (pending != null || pendingVerify != null) {
            // A full recheck or upkeep verify is already in flight; keep the mark so its
            // commit/discard reschedules.
            rechecks.markDirty(machine().getBlockPos());
            return;
        }
        // Formed upkeep fast path: a reason-less full mark (backstop, load) on an intact structure
        // only pays the cheap maintained-orientation verify — single capture, no mirror twin,
        // short-circuit walk. Only a failing verify escalates to the full recognize below.
        if (!escalated && !forceFullRecheck && formedShape != null && moduleSpecs.isEmpty()) {
            upkeepVerify(level, services);
            return;
        }
        forceFullRecheck = false;
        if (blueprint.supportsSnapshot() && blueprint.maxCellCount() >= Topology.config.MULTIBLOCK.asyncCellThreshold) {
            submitAsyncRecheck(services);
        } else {
            recheck(level);
        }
    }

    /**
     * The synchronous recheck template: snapshot → recognize → claim → apply → publish. The mirror
     * twin is lazy here — captured and recognized only after the primary pass failed — unlike the
     * async path, whose worker cannot call back to the main thread for a second capture.
     */
    private void recheck(ServerLevel level) {
        MultiblockLevelBinder.Services services = services(level);
        try {
            Orientation orientation = orientationFromBlockState(machine().getBlockState());
            watchFootprint(services.claims(), orientation);
            boolean snapshotLayout = blueprint.supportsSnapshot();
            CompiledSnapshot primary = snapshotLayout ? services.claims().snapshot(machine().getBlockPos(), blueprint, orientation) : null;
            StructureView primaryView = primary != null ? primary : new MultiblockLiveView(level, machine().getBlockPos());
            AsyncStructureService.Outcome outcome = new AsyncStructureService.Outcome(
                    StructureEngine.recognize(primaryView, blueprint, orientation), orientation);
            if (outcome.result().failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) {
                deferUnloaded(services);
                return;
            }
            if (!outcome.result().formed() && blueprint.allowMirror()) {
                Orientation mirrored = Orientations.mirrorOf(orientation);
                StructureView mirrorView = snapshotLayout ? services.claims().snapshot(machine().getBlockPos(), blueprint, mirrored) : new MultiblockLiveView(level, machine().getBlockPos());
                RecognitionResult mirrorResult = StructureEngine.recognize(mirrorView, blueprint, mirrored);
                if (mirrorResult.failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) {
                    deferUnloaded(services);
                    return;
                }
                if (mirrorResult.formed()) {
                    outcome = new AsyncStructureService.Outcome(mirrorResult, mirrored);
                }
            }
            ModuleEvaluation moduleEvaluation = evaluateModules(level, outcome.orientation());
            if (moduleEvaluation.unloaded()) {
                deferUnloaded(services);
                return;
            }
            boolean formed = outcome.result().formed() && moduleEvaluation.valid();
            boolean claimed = claim(services, formed, outcome);
            apply(level, claimed, outcome, moduleEvaluation);
            publish(level, claimed);
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    /** Main-thread snapshot, worker recognition; the verdict commits on a later tick with staleness checks. */
    private void submitAsyncRecheck(MultiblockLevelBinder.Services services) {
        Orientation orientation = orientationFromBlockState(machine().getBlockState());
        AsyncStructureService.Captures captures = snapshot(services.claims(), orientation);
        long submissionId = ++submissionCounter;
        pending = new PendingRecognition(
                submissionId,
                orientation,
                AsyncStructureService.recognizeAsync(captures, blueprint));
    }

    /**
     * Upkeep verify for a formed structure: captures the formed orientation only and runs the
     * zero-allocation {@link StructureEngine#verify} — synchronously under the async threshold, on
     * the worker pool above it. An intact verdict retains everything as-is; anything else escalates
     * to the full recognize path on the next tick.
     */
    private void upkeepVerify(ServerLevel level, MultiblockLevelBinder.Services services) {
        try {
            FormedShape shape = Objects.requireNonNull(formedShape);
            Orientation orientation = shape.orientation();
            watchFootprint(services.claims(), orientationFromBlockState(machine().getBlockState()));
            StructureView capture = blueprint.supportsSnapshot() ? services.claims().snapshot(machine().getBlockPos(), blueprint, orientation) : new MultiblockLiveView(level, machine().getBlockPos());
            if (blueprint.supportsSnapshot() && blueprint.maxCellCount() >= Topology.config.MULTIBLOCK.asyncCellThreshold) {
                long submissionId = ++submissionCounter;
                pendingVerify = new PendingVerification(
                        submissionId,
                        orientation,
                        AsyncStructureService.verifyAsync((CompiledSnapshot) capture, blueprint));
                return;
            }
            StructureEngine.Verification verification = StructureEngine.verify(capture, blueprint, orientation);
            if (verification.unloaded()) {
                deferUnloaded(services);
                return;
            }
            applyVerification(level, services, verification);
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    /**
     * Applies an upkeep verdict: intact with unchanged repeats retains silently; a broken structure
     * unforms this tick (same flow as a targeted break); a still-formed structure whose greedy
     * repeats changed (a repeatable aisle grew or shrank legally) re-runs the full recognize so the
     * member set refreshes without an unform flicker.
     */
    private void applyVerification(
                                   ServerLevel level, MultiblockLevelBinder.Services services, StructureEngine.Verification verification) {
        FormedShape shape = formedShape;
        if (verification.formed() && shape != null && verification.repeats().equals(shape.repeats())) {
            return;
        }
        if (!verification.formed()) {
            unformNow(level, services);
        } else {
            forceFullRecheck = true;
        }
        services.rechecks().markDirty(machine().getBlockPos());
    }

    private void commitPendingVerification(ServerLevel level, MultiblockLevelBinder.Services services) {
        PendingVerification inFlight = pendingVerify;
        if (inFlight == null || !inFlight.future().isDone()) {
            return;
        }
        pendingVerify = null;
        try {
            StructureEngine.Verification verification = inFlight.future().join();
            FormedShape shape = formedShape;
            boolean stale = inFlight.submissionId() != submissionCounter || shape == null || !inFlight.orientation().equals(shape.orientation()) || services.rechecks().isDirty(machine().getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine().getBlockPos());
                return;
            }
            if (verification.unloaded()) {
                deferUnloaded(services);
                return;
            }
            applyVerification(level, services, verification);
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    private void commitPendingRecognition(ServerLevel level, MultiblockLevelBinder.Services services) {
        PendingRecognition inFlight = pending;
        if (inFlight == null || !inFlight.future().isDone()) {
            return;
        }
        pending = null;
        try {
            AsyncStructureService.Outcome outcome = inFlight.future().join();
            if (outcome.result().failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) {
                deferUnloaded(services);
                return;
            }
            Orientation current = orientationFromBlockState(machine().getBlockState());
            boolean stale = inFlight.submissionId() != submissionCounter || !inFlight.orientation().equals(current) || services.rechecks().isDirty(machine().getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine().getBlockPos());
                return;
            }
            ModuleEvaluation moduleEvaluation = evaluateModules(level, outcome.orientation());
            if (moduleEvaluation.unloaded()) {
                deferUnloaded(services);
                return;
            }
            boolean formed = outcome.result().formed() && moduleEvaluation.valid();
            boolean claimed = claim(services, formed, outcome);
            apply(level, claimed, outcome, moduleEvaluation);
            publish(level, claimed);
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    /**
     * Snapshot step for the async path: refreshes the watched footprint and captures the dense
     * view(s) recognition consumes — the primary orientation, plus its mirror twin when the
     * blueprint allows mirroring. The mirror is captured eagerly here because the worker thread can
     * never call back to the main thread for a second capture; the synchronous path captures it
     * lazily instead.
     */
    private AsyncStructureService.Captures snapshot(MultiblockClaimIndex claims, Orientation orientation) {
        watchFootprint(claims, orientation);
        CompiledSnapshot primary = claims.snapshot(machine().getBlockPos(), blueprint, orientation);
        CompiledSnapshot mirrored = blueprint.allowMirror() ? claims.snapshot(machine().getBlockPos(), blueprint, Orientations.mirrorOf(orientation)) : null;
        return new AsyncStructureService.Captures(primary, mirrored);
    }

    /**
     * Evaluates all overlay modules against the current controller anchor. Modules are ordered;
     * once an optional module is absent, a later module may not be present. A partially present
     * module also invalidates formation.
     */
    private ModuleEvaluation evaluateModules(ServerLevel level, Orientation orientation) {
        return evaluateModuleOverlays(
                new MultiblockLiveView(level, machine().getBlockPos()),
                moduleSpecs,
                orientation,
                machine().getBlockPos());
    }

    /**
     * Pure module-overlay evaluation shared by the controller and tests. The caller supplies the
     * structure view; the controller anchor is the module origin.
     */
    static ModuleEvaluation evaluateModuleOverlays(
                                                   StructureView view,
                                                   List<MultiblockModule> moduleSpecs,
                                                   Orientation orientation,
                                                   BlockPos controllerPos) {
        if (moduleSpecs.isEmpty()) {
            return new ModuleEvaluation(Map.of(), List.of(), true, false);
        }
        Map<String, RecognitionResult.ModuleReport> reports = new LinkedHashMap<>();
        List<BlockPos> members = new ArrayList<>();
        boolean valid = true;
        boolean unloaded = false;
        boolean prefixClosed = false;
        for (MultiblockModule module : moduleSpecs) {
            StructureEngine.OverlayResult overlay = StructureEngine.evaluateOverlay(
                    view, module.blueprint(), orientation, controllerPos);
            unloaded |= overlay.unloaded();
            RecognitionResult.ModuleReport report = new RecognitionResult.ModuleReport(
                    module.id(), module.required(), overlay.matched(), overlay.total());
            reports.put(module.id(), report);
            boolean present = overlay.present();
            if (!present) {
                if (module.required()) {
                    valid = false;
                }
                prefixClosed = true;
            } else {
                if (prefixClosed || !overlay.satisfied()) {
                    valid = false;
                }
                members.addAll(overlay.members());
            }
        }
        return new ModuleEvaluation(Map.copyOf(reports), List.copyOf(members), valid, unloaded);
    }

    /** Result of evaluating every overlay module for one orientation. */
    record ModuleEvaluation(
                            Map<String, RecognitionResult.ModuleReport> reports,
                            List<BlockPos> members,
                            boolean valid,
                            boolean unloaded) {}

    /** Claim step: a formed verdict claims the footprint; failure (or an unformed verdict) releases ours. */
    private boolean claim(MultiblockLevelBinder.Services services, boolean formed, AsyncStructureService.Outcome outcome) {
        if (formed && services.claims()
                .tryClaim(
                        machine().getBlockPos(),
                        blueprint,
                        outcome.orientation(),
                        outcome.result().repeats())) {
            return true;
        }
        releaseClaimsAndWakeWatchers(services);
        return false;
    }

    /** Apply step: member set, formed geometry, module reports, visuals, and the formation flip. */
    private void apply(
                       ServerLevel level,
                       boolean formed,
                       AsyncStructureService.Outcome outcome,
                       ModuleEvaluation moduleEvaluation) {
        if (!formed) {
            clearFormed(level);
            modules = moduleEvaluation.reports();
            return;
        }
        boolean wasFormed = formedShape != null;
        Map<String, RecognitionResult.ModuleReport> previousModules = modules;
        RecognitionResult result = outcome.result();
        List<BlockPos> previousMembers = runtime.memberPositions();
        List<BlockPos> members = new ArrayList<>(result.members());
        for (BlockPos member : moduleEvaluation.members()) {
            if (!members.contains(member)) {
                members.add(member);
            }
        }
        runtime.updateMembers(members);
        formedShape = new FormedShape(outcome.orientation(), result.repeats());
        modules = moduleEvaluation.reports();
        MultiblockVisuals.writeMemberCtmActive(level, previousMembers, runtime.memberPositions());
        if (!wasFormed || !previousModules.equals(modules)) {
            machine().machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine().getBlockPos());
        }
    }

    /** Clears all formed runtime state and publishes the unformed transition exactly once. */
    private void clearFormed(ServerLevel level) {
        boolean wasFormed = formedShape != null;
        List<BlockPos> previousMembers = runtime.memberPositions();
        runtime.clearMembers();
        formedShape = null;
        modules = Map.of();
        MultiblockVisuals.writeMemberCtmActive(level, previousMembers, List.of());
        if (wasFormed) {
            machine().machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine().getBlockPos());
        }
    }

    /** Publish step: writes the {@code FORMED} visual block state. */
    private void publish(ServerLevel level, boolean formed) {
        MultiblockVisuals.writeFormedBlockState(level, machine().getBlockPos(), formed);
    }

    /**
     * Re-tests only the changed cells against the live level: each position resolves to its
     * compiled definition by pure arithmetic ({@code cellIndexOfWorld}); no per-cell index exists at
     * any structure size. Positions inside the maximal footprint but beyond the formed repeats
     * (growth area) escalate to a full recheck; positions outside the footprint are not ours.
     */
    private TargetedVerdict targetedRecheck(ServerLevel level, Set<BlockPos> changedCells) {
        FormedShape shape = Objects.requireNonNull(formedShape);
        CompiledBlueprint compiled = blueprint.compiled(shape.orientation());
        BlockPos controllerPos = machine().getBlockPos();
        MultiblockLiveView view = new MultiblockLiveView(level, controllerPos);
        for (BlockPos changed : changedCells) {
            if (!view.isLoaded(changed)) {
                return TargetedVerdict.ESCALATE;
            }
            int cellIndex = compiled.cellIndexOfWorld(changed, controllerPos, shape.repeats());
            if (cellIndex < 0) {
                if (compiled.containsWorldAnyLayout(changed, controllerPos)) {
                    return TargetedVerdict.ESCALATE; // A change can grow a repeat/module layout.
                }
                continue; // Outside every possible footprint — not one of our cells.
            }
            CompiledBlueprint.CompiledDefinition definition = compiled.definitionAt(cellIndex);
            if (!StructureEngine.matchesCompiled(view, changed, definition)) {
                return TargetedVerdict.BROKEN;
            }
        }
        return TargetedVerdict.RETAINED;
    }

    /**
     * Immediate unform on a targeted mismatch: releases claims, clears members and visuals, and
     * publishes the unformed state this tick — the full diagnostic recheck runs on the re-mark.
     */
    private void unformNow(ServerLevel level, MultiblockLevelBinder.Services services) {
        releaseClaimsAndWakeWatchers(services);
        clearFormed(level);
        publish(level, false);
    }

    /**
     * Fail-closed recovery: any recheck failure logs, releases claims (waking their watchers), clears
     * members and forces the unformed state. The tick never crashes and a stale {@code FORMED} block
     * state is never left behind.
     */
    private void failClosed(ServerLevel level, MultiblockLevelBinder.Services services, Exception failure) {
        LOGGER.error(
                "Multiblock recheck failed for controller at {}; forcing unformed (fail-closed)",
                machine().getBlockPos(),
                failure);
        try {
            pending = null;
            releaseClaimsAndWakeWatchers(services);
            clearFormed(level);
            publish(level, false);
        } catch (Exception cleanupFailure) {
            LOGGER.error(
                    "Multiblock fail-closed cleanup also failed for controller at {}",
                    machine().getBlockPos(),
                    cleanupFailure);
        }
    }

    /**
     * Keeps the current formation state when a footprint cell is in an unloaded chunk. Unloaded
     * chunks are unknown rather than broken; retry after the configured interval instead of
     * unforming a possibly intact structure.
     */
    private void deferUnloaded(MultiblockLevelBinder.Services services) {
        long now = machine().getLevel() == null ? 0L : machine().getLevel().getGameTime();
        retryAfterGameTime = now + Math.max(1, Topology.config.MULTIBLOCK.unloadedRetryIntervalTicks);
        services.rechecks().markDirty(machine().getBlockPos());
    }

    /**
     * Releases this controller's claim and marks every watcher whose region intersects the freed
     * region dirty, so competing controllers re-race the cells immediately. O(live controllers),
     * never O(cells). Composing {@link MultiblockClaimIndex} and {@link MultiblockRecheckDirtySet} happens here in the
     * controller template; the two services never call each other directly.
     */
    private void releaseClaimsAndWakeWatchers(MultiblockLevelBinder.Services services) {
        MultiblockClaimIndex.Region freed = services.claims().unclaim(machine().getBlockPos());
        if (freed == null) {
            return;
        }
        for (BlockPos watcher : services.claims().watchersIntersecting(freed, machine().getBlockPos())) {
            services.rechecks().markDirty(watcher);
        }
    }

    /**
     * Registers the watched footprint for the given orientation (plus its mirror when allowed). The
     * (orientation, mirror) pair fully determines the watched cell set, so an unchanged pair against
     * the same {@link MultiblockClaimIndex} skips the rebuild.
     */
    private void watchFootprint(MultiblockClaimIndex claims, Orientation orientation) {
        if (claims == watchedClaims && orientation.equals(watchedOrientation)) {
            return;
        }
        if (blueprint.allowMirror()) {
            claims.watch(machine().getBlockPos(), blueprint, orientation, Orientations.mirrorOf(orientation));
        } else {
            claims.watch(machine().getBlockPos(), blueprint, orientation);
        }
        watchedClaims = claims;
        watchedOrientation = orientation;
    }

    private void markDirtyAndAlert() {
        if (machine().getLevel() instanceof ServerLevel level) {
            MultiblockLevelBinder.Services services = services(level);
            watchFootprint(services.claims(), orientationFromBlockState(machine().getBlockState()));
            services.rechecks().markDirty(machine().getBlockPos());
        }
        TickHandle handle = handle();
        if (handle != null) {
            handle.alert();
        }
    }

    private RecipeModifier memberFoldModifier(@Nullable RecipeLogic context) {
        return recipeModifiers.memberFoldModifier(context);
    }

    /** Resolves and caches the per-level services; cleared on unload, so the cache never spans levels. */
    private MultiblockLevelBinder.Services services(ServerLevel level) {
        MultiblockLevelBinder.Services current = cachedServices;
        if (current == null) {
            current = MultiblockLevelBinder.services(level);
            cachedServices = current;
        }
        return current;
    }

    private static Orientation orientationFromBlockState(BlockState state) {
        if (state.hasProperty(OrientedMachineBlock.FACING)) {
            return Orientations.forFacing(state.getValue(OrientedMachineBlock.FACING));
        }
        return Orientations.IDENTITY;
    }

    @Override
    protected void onAttached(TickHandle handle) {
        markDirtyAndAlert();
    }

    @Override
    public void onMachineLoad() {
        markDirtyAndAlert();
    }

    /**
     * Claim lifetime contract: claims are memory-only. They are released here when the block entity is
     * removed or its chunk unloads, and re-taken by the recheck that {@link #onMachineLoad()} schedules
     * on the next load. Overlap windows between competing controllers converge through the unclaim
     * wake-up in {@link #releaseClaimsAndWakeWatchers}.
     */
    @Override
    public void onMachineUnload() {
        if (machine().getLevel() instanceof ServerLevel level) {
            MultiblockLevelBinder.Services services = MultiblockLevelBinder.servicesIfPresent(level);
            if (services != null) {
                releaseClaimsAndWakeWatchers(services);
                services.claims().unwatch(machine().getBlockPos());
                // Drop our own pending dirty mark: a controller removed while dirty would otherwise
                // leave its entry (pos + change note) in the per-level dirty set forever.
                services.rechecks().consume(machine().getBlockPos());
            }
            watchedClaims = null;
            watchedOrientation = null;
            cachedServices = null;
            level.invalidateCapabilities(machine().getBlockPos());
        }
        PendingRecognition inFlight = pending;
        if (inFlight != null) {
            inFlight.future().cancel(false);
            pending = null;
        }
        PendingVerification verifyInFlight = pendingVerify;
        if (verifyInFlight != null) {
            verifyInFlight.future().cancel(false);
            pendingVerify = null;
        }
        formedShape = null;
        modules = Map.of();
        runtime.clearMembers();
    }

    /**
     * Multiblock recipe inputs are contributed as <em>raw member handlers</em> into the controller's
     * {@link net.ptcrys.topo.api.machine.resource.RecipeSearchPoolRouter} — same-pool handlers merge there.
     * This trait does not expose a single pre-combined handler.
     */
    @Override
    protected <R extends Resource> void collectRecipeResourceHandlers(
                                                                      MachineResourceType<R> resourceType,
                                                                      RecipeRole recipeIo,
                                                                      java.util.function.Consumer<RecipeResourceContribution<R>> out) {
        runtime.collectRecipeResourceHandlers(resourceType, recipeIo, out);
    }

    @Override
    protected <R extends Resource> @Nullable ResourceHandler<R> transferHandler(
                                                                                MachineResourceType<R> resourceType,
                                                                                @Nullable Direction side) {
        if (resourceType.blockCapability() == null) {
            return null;
        }
        return runtime.capabilityHandler(resourceType, side);
    }

    private enum TargetedVerdict {
        RETAINED,
        BROKEN,
        ESCALATE
    }

    private record PendingRecognition(
                                      long submissionId,
                                      Orientation orientation,
                                      CompletableFuture<AsyncStructureService.Outcome> future) {}

    private record PendingVerification(
                                       long submissionId,
                                       Orientation orientation,
                                       CompletableFuture<StructureEngine.Verification> future) {}

    private record FormedShape(Orientation orientation, RepeatResolution repeats) {}
}
