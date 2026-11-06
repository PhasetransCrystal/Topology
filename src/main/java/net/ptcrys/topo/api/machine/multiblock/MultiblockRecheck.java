package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * The recheck pipeline — the whole {@code snapshot → recognize → claim → apply → publish} template,
 * plus upkeep verify, targeted invalidation, async submission and the fail-closed recovery. It is a
 * plain collaborator (the machine system's component pattern: heavy logic sits beside the thin
 * {@link MultiblockController} component) holding the machine and the shared {@link MultiblockState}.
 *
 * <p>
 * Fail-closed: any step failure logs, forces the unformed state and releases this controller's
 * claims, so a broken recheck can never crash the tick or leave a stale {@code FORMED} block state.
 */
final class MultiblockRecheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiblockRecheck.class);

    private final MachineBlockEntity machine;
    private final Blueprint blueprint;
    private final List<MultiblockModule> moduleSpecs;
    private final Map<String, MultiblockModule> modulesById;
    private final FormedRuntime runtime;
    private final MultiblockState state;

    /** Last footprint registration, so an unchanged (orientation, mirror) pair skips the watch rebuild. */
    private @Nullable MultiblockClaimIndex watchedClaims;
    private @Nullable Orientation watchedOrientation;
    /** Per-level services, cached so the every-tick path skips the binder's synchronized map lookup. */
    private MultiblockLevelBinder.@Nullable Services cachedServices;
    /** In-flight async recognition; results commit on the server thread with staleness checks. */
    private @Nullable PendingRecognition pending;
    /** In-flight async upkeep verification (formed backstop); commits like {@link #pending}. */
    private @Nullable PendingVerification pendingVerify;
    /** Set when an upkeep verify failed: the next consumed mark pays the full recognize path. */
    private boolean forceFullRecheck;
    private long submissionCounter;
    /** Earliest game time at which an unloaded-chunk retry may run. */
    private long retryAfterGameTime;

    MultiblockRecheck(
                      MachineBlockEntity machine,
                      Blueprint blueprint,
                      List<MultiblockModule> moduleSpecs,
                      Map<String, MultiblockModule> modulesById,
                      FormedRuntime runtime,
                      MultiblockState state) {
        this.machine = Objects.requireNonNull(machine, "machine");
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.moduleSpecs = Objects.requireNonNull(moduleSpecs, "module specs");
        this.modulesById = Objects.requireNonNull(modulesById, "modules by id");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.state = Objects.requireNonNull(state, "state");
    }

    /** One tick of the recheck state machine: drain the dirty note and run the cheapest sufficient path. */
    void tick(long gameTime) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MultiblockLevelBinder.Services services = services(level);
        MultiblockRecheckDirtySet rechecks = services.rechecks();
        commitPendingRecognition(level, services);
        commitPendingVerification(level, services);
        if (gameTime < retryAfterGameTime) {
            return;
        }
        if (state.formedShape != null && Math.floorMod(machine.getBlockPos().hashCode() + gameTime, Topology.config.MULTIBLOCK.backstopIntervalTicks) == 0) {
            rechecks.markDirty(machine.getBlockPos());
        }
        MultiblockRecheckDirtySet.Note note = rechecks.consume(machine.getBlockPos());
        if (note == null) {
            return;
        }
        // Targeted invalidation: a formed structure re-tests exactly the changed cells — O(changes)
        // instead of O(footprint) — and unforms within this tick on the first mismatch. Bounded
        // notes that touch positions outside the formed footprint (repeat growth area) escalate.
        boolean escalated = false;
        if (Topology.config.MULTIBLOCK.enableTargetedRecheck && state.formedShape != null && note.bounded()) {
            try {
                TargetedVerdict verdict = targetedRecheck(level, note.changedCells());
                if (verdict == TargetedVerdict.RETAINED) {
                    return;
                }
                if (verdict == TargetedVerdict.BROKEN) {
                    unformNow(level, services);
                    rechecks.markDirty(machine.getBlockPos());
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
            rechecks.markDirty(machine.getBlockPos());
            return;
        }
        // Formed upkeep fast path: a reason-less full mark (backstop, load) on an intact structure
        // only pays the cheap maintained-orientation verify — single capture, no mirror twin,
        // short-circuit walk. Only a failing verify escalates to the full recognize below.
        if (!escalated && !forceFullRecheck && state.formedShape != null && moduleSpecs.isEmpty()) {
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
            Orientation orientation = orientationFromBlockState(machine.getBlockState());
            watchFootprint(services.claims(), orientation);
            boolean snapshotLayout = blueprint.supportsSnapshot();
            CompiledSnapshot primary = snapshotLayout ? services.claims().snapshot(machine.getBlockPos(), blueprint, orientation) : null;
            StructureView primaryView = primary != null ? primary : new MultiblockLiveView(level, machine.getBlockPos());
            AsyncStructureService.Outcome outcome = new AsyncStructureService.Outcome(
                    StructureEngine.recognize(primaryView, blueprint, orientation), orientation);
            if (outcome.result().failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) {
                deferUnloaded(services);
                return;
            }
            if (!outcome.result().formed() && blueprint.allowMirror()) {
                Orientation mirrored = Orientations.mirrorOf(orientation);
                StructureView mirrorView = snapshotLayout ? services.claims().snapshot(machine.getBlockPos(), blueprint, mirrored) : new MultiblockLiveView(level, machine.getBlockPos());
                RecognitionResult mirrorResult = StructureEngine.recognize(mirrorView, blueprint, mirrored);
                if (mirrorResult.failureReason() == RecognitionResult.FailureReason.CHUNK_UNLOADED) {
                    deferUnloaded(services);
                    return;
                }
                if (mirrorResult.formed()) {
                    outcome = new AsyncStructureService.Outcome(mirrorResult, mirrored);
                }
            }
            MultiblockModuleEvaluator.Evaluation moduleEvaluation = evaluateModules(level, outcome.orientation());
            if (moduleEvaluation.unloaded()) {
                deferUnloaded(services);
                return;
            }
            boolean formed = outcome.result().formed();
            ClaimResult claimResult = claim(level, services, formed, outcome, moduleEvaluation);
            apply(level, claimResult.formed(), outcome, claimResult.modules());
            publish(level, claimResult.formed());
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    /** Main-thread snapshot, worker recognition; the verdict commits on a later tick with staleness checks. */
    private void submitAsyncRecheck(MultiblockLevelBinder.Services services) {
        Orientation orientation = orientationFromBlockState(machine.getBlockState());
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
            MultiblockState.FormedShape shape = Objects.requireNonNull(state.formedShape);
            Orientation orientation = shape.orientation();
            watchFootprint(services.claims(), orientationFromBlockState(machine.getBlockState()));
            StructureView capture = blueprint.supportsSnapshot() ? services.claims().snapshot(machine.getBlockPos(), blueprint, orientation) : new MultiblockLiveView(level, machine.getBlockPos());
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
        MultiblockState.FormedShape shape = state.formedShape;
        if (verification.formed() && shape != null && verification.repeats().equals(shape.repeats())) {
            return;
        }
        if (!verification.formed()) {
            unformNow(level, services);
        } else {
            forceFullRecheck = true;
        }
        services.rechecks().markDirty(machine.getBlockPos());
    }

    private void commitPendingVerification(ServerLevel level, MultiblockLevelBinder.Services services) {
        PendingVerification inFlight = pendingVerify;
        if (inFlight == null || !inFlight.future().isDone()) {
            return;
        }
        pendingVerify = null;
        try {
            StructureEngine.Verification verification = inFlight.future().join();
            MultiblockState.FormedShape shape = state.formedShape;
            boolean stale = inFlight.submissionId() != submissionCounter || shape == null || !inFlight.orientation().equals(shape.orientation()) || services.rechecks().isDirty(machine.getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine.getBlockPos());
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
            Orientation current = orientationFromBlockState(machine.getBlockState());
            boolean stale = inFlight.submissionId() != submissionCounter || !inFlight.orientation().equals(current) || services.rechecks().isDirty(machine.getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine.getBlockPos());
                return;
            }
            MultiblockModuleEvaluator.Evaluation moduleEvaluation = evaluateModules(level, outcome.orientation());
            if (moduleEvaluation.unloaded()) {
                deferUnloaded(services);
                return;
            }
            boolean formed = outcome.result().formed();
            ClaimResult claimResult = claim(level, services, formed, outcome, moduleEvaluation);
            apply(level, claimResult.formed(), outcome, claimResult.modules());
            publish(level, claimResult.formed());
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
        CompiledSnapshot primary = claims.snapshot(machine.getBlockPos(), blueprint, orientation);
        CompiledSnapshot mirrored = blueprint.allowMirror() ? claims.snapshot(machine.getBlockPos(), blueprint, Orientations.mirrorOf(orientation)) : null;
        return new AsyncStructureService.Captures(primary, mirrored);
    }

    /**
     * Evaluates every module as an independent blueprint. Modules are optional: their formation
     * never gates the main structure. A module is satisfied only when its own cells/counts are
     * complete and every prerequisite module is satisfied; partial or blocked modules are absent.
     */
    private MultiblockModuleEvaluator.Evaluation evaluateModules(ServerLevel level, Orientation orientation) {
        return MultiblockModuleEvaluator.evaluateOverlays(
                new MultiblockLiveView(level, machine.getBlockPos()),
                moduleSpecs,
                orientation);
    }

    /**
     * Claim step. Structural cells and shared parts are never exclusive; only the members whose
     * definition carries {@code ExclusivePartAttachment} are reserved. A conflict on a main-blueprint
     * exclusive part prevents the machine from forming; a conflict on a module-only exclusive part
     * drops that module (and its dependents) while the main structure still forms. Formed machines
     * keep their reservation until they unform.
     */
    private ClaimResult claim(
                              ServerLevel level,
                              MultiblockLevelBinder.Services services,
                              boolean formed,
                              AsyncStructureService.Outcome outcome,
                              MultiblockModuleEvaluator.Evaluation moduleEvaluation) {
        if (!formed) {
            releaseClaimsAndWakeWatchers(services);
            return new ClaimResult(false, moduleEvaluation);
        }
        StructureView view = new MultiblockLiveView(level, machine.getBlockPos());
        Set<BlockPos> mainExclusive = exclusiveCells(view, outcome.result().members());
        if (!services.claims().conflictingExclusive(machine.getBlockPos(), mainExclusive).isEmpty()) {
            releaseClaimsAndWakeWatchers(services);
            return new ClaimResult(false, moduleEvaluation);
        }
        MultiblockModuleEvaluator.Evaluation adjustedModules = resolveModuleExclusiveConflicts(view, moduleEvaluation, services.claims());
        Set<BlockPos> allExclusive = new LinkedHashSet<>(mainExclusive);
        allExclusive.addAll(exclusiveCells(view, adjustedModules.members()));
        if (!services.claims().tryClaim(
                machine.getBlockPos(),
                blueprint,
                outcome.orientation(),
                outcome.result().repeats(),
                allExclusive)) {
            releaseClaimsAndWakeWatchers(services);
            return new ClaimResult(false, adjustedModules);
        }
        return new ClaimResult(true, adjustedModules);
    }

    private static Set<BlockPos> exclusiveCells(StructureView view, List<BlockPos> members) {
        Set<BlockPos> exclusive = new LinkedHashSet<>();
        for (BlockPos member : members) {
            StructureView.Member info = view.member(member);
            if (info != null && info.exclusive()) {
                exclusive.add(info.pos());
            }
        }
        return exclusive;
    }

    /**
     * Drops modules whose exclusive parts are already held by another formed machine. Dropping a
     * prerequisite propagates to its dependents; the module report stays usable for diagnostics
     * ({@code matched/total}) but the module becomes absent.
     */
    private MultiblockModuleEvaluator.Evaluation resolveModuleExclusiveConflicts(
                                                                                 StructureView view,
                                                                                 MultiblockModuleEvaluator.Evaluation evaluation,
                                                                                 MultiblockClaimIndex claims) {
        if (moduleSpecs.isEmpty()) {
            return evaluation;
        }
        Map<String, MultiblockModule> byId = new LinkedHashMap<>();
        for (MultiblockModule module : moduleSpecs) {
            byId.put(module.id(), module);
        }
        Map<String, RecognitionResult.ModuleReport> computed = new LinkedHashMap<>();
        Map<String, Boolean> satisfiedById = new HashMap<>();
        Map<String, List<BlockPos>> keptMembers = new LinkedHashMap<>();
        List<BlockPos> members = new ArrayList<>();
        for (String id : evaluation.order()) {
            MultiblockModule module = byId.get(id);
            RecognitionResult.ModuleReport report = evaluation.reports().get(id);
            if (module == null || report == null) {
                continue;
            }
            List<String> blockedBy = new ArrayList<>();
            for (String dependency : module.dependencies()) {
                if (!satisfiedById.getOrDefault(dependency, false)) {
                    blockedBy.add(dependency);
                }
            }
            boolean satisfied = report.satisfied() && blockedBy.isEmpty();
            if (satisfied) {
                List<BlockPos> moduleMembers = evaluation.membersByModule().getOrDefault(id, List.of());
                if (!claims.conflictingExclusive(machine.getBlockPos(), exclusiveCells(view, moduleMembers)).isEmpty()) {
                    satisfied = false;
                    blockedBy = List.of();
                } else {
                    keptMembers.put(id, moduleMembers);
                    members.addAll(moduleMembers);
                }
            }
            computed.put(id, new RecognitionResult.ModuleReport(id, satisfied, report.matched(), report.total(), blockedBy, report.repeats(), report.bounds()));
            satisfiedById.put(id, satisfied);
        }
        Map<String, RecognitionResult.ModuleReport> reports = new LinkedHashMap<>();
        for (MultiblockModule module : moduleSpecs) {
            RecognitionResult.ModuleReport report = computed.get(module.id());
            if (report != null) {
                reports.put(module.id(), report);
            }
        }
        return new MultiblockModuleEvaluator.Evaluation(reports, List.copyOf(members), keptMembers, evaluation.order(), evaluation.unloaded());
    }

    /** Claim outcome: whether the machine formed and the module reports after exclusive conflicts. */
    private record ClaimResult(boolean formed, MultiblockModuleEvaluator.Evaluation modules) {}

    /** Apply step: member set, formed geometry, module reports, visuals, and the formation flip. */
    private void apply(
                       ServerLevel level,
                       boolean formed,
                       AsyncStructureService.Outcome outcome,
                       MultiblockModuleEvaluator.Evaluation moduleEvaluation) {
        if (!formed) {
            clearFormed(level);
            return;
        }
        boolean wasFormed = state.formedShape != null;
        Map<String, RecognitionResult.ModuleReport> previousModules = state.modules;
        RecognitionResult result = outcome.result();
        List<BlockPos> previousMembers = runtime.memberPositions();
        List<BlockPos> members = new ArrayList<>(result.members());
        for (BlockPos member : moduleEvaluation.members()) {
            if (!members.contains(member)) {
                members.add(member);
            }
        }
        runtime.updateMembers(members);
        RecognitionResult.Bounds bounds = result.bounds();
        Map<String, RepeatResolution> moduleRepeats = new LinkedHashMap<>();
        for (Map.Entry<String, RecognitionResult.ModuleReport> entry : moduleEvaluation.reports().entrySet()) {
            RecognitionResult.ModuleReport report = entry.getValue();
            if (report.satisfied()) {
                moduleRepeats.put(report.id(), report.repeats());
                bounds = bounds.union(report.bounds());
            }
        }
        state.formedShape = new MultiblockState.FormedShape(outcome.orientation(), result.repeats(), bounds, Map.copyOf(moduleRepeats));
        state.modules = moduleEvaluation.reports();
        MultiblockVisuals.writeMemberCtmActive(level, previousMembers, runtime.memberPositions());
        if (!wasFormed || !previousModules.equals(state.modules)) {
            machine.machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine.getBlockPos());
        }
    }

    /** Clears all formed runtime state and publishes the unformed transition exactly once. */
    private void clearFormed(ServerLevel level) {
        boolean wasFormed = state.formedShape != null;
        List<BlockPos> previousMembers = runtime.memberPositions();
        runtime.clearMembers();
        state.formedShape = null;
        state.modules = Map.of();
        MultiblockVisuals.writeMemberCtmActive(level, previousMembers, List.of());
        if (wasFormed) {
            machine.machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine.getBlockPos());
        }
    }

    /** Publish step: writes the {@code FORMED} visual block state. */
    private void publish(ServerLevel level, boolean formed) {
        MultiblockVisuals.writeFormedBlockState(level, machine.getBlockPos(), formed);
    }

    /**
     * Re-tests only the changed cells against the live level: each position resolves to its
     * compiled definition by pure arithmetic ({@code cellIndexOfWorld}); no per-cell index exists at
     * any structure size. Positions inside the maximal footprint but beyond the formed repeats
     * (growth area) escalate to a full recheck; positions outside the footprint are not ours.
     */
    private TargetedVerdict targetedRecheck(ServerLevel level, Set<BlockPos> changedCells) {
        MultiblockState.FormedShape shape = Objects.requireNonNull(state.formedShape);
        Orientation orientation = shape.orientation();
        BlockPos controllerPos = machine.getBlockPos();
        CompiledBlueprint mainCompiled = blueprint.compiled(orientation);
        MultiblockLiveView view = new MultiblockLiveView(level, controllerPos);
        for (BlockPos changed : changedCells) {
            if (!view.isLoaded(changed)) {
                return TargetedVerdict.ESCALATE;
            }
            // Main blueprint: a mismatch unforms immediately; the growth area forces a full recognize.
            int mainIndex = mainCompiled.cellIndexOfWorld(changed, controllerPos, shape.repeats());
            if (mainIndex >= 0) {
                if (!StructureEngine.matchesCompiled(view, changed, mainCompiled.definitionAt(mainIndex))) {
                    return TargetedVerdict.BROKEN;
                }
            } else if (mainCompiled.containsWorldAnyLayout(changed, controllerPos)) {
                return TargetedVerdict.ESCALATE; // A change can grow the repeat layout.
            }
            // Satisfied modules: a mismatch only loses that module (main stays formed), so escalate to
            // a full recognize rather than unform; their growth areas escalate the same way.
            for (Map.Entry<String, RepeatResolution> entry : shape.moduleRepeats().entrySet()) {
                MultiblockModule module = modulesById.get(entry.getKey());
                if (module == null) {
                    continue;
                }
                CompiledBlueprint moduleCompiled = module.blueprint().compiled(orientation);
                int moduleIndex = moduleCompiled.cellIndexOfWorld(changed, controllerPos, entry.getValue());
                if (moduleIndex >= 0) {
                    if (!StructureEngine.matchesCompiled(view, changed, moduleCompiled.definitionAt(moduleIndex))) {
                        return TargetedVerdict.ESCALATE;
                    }
                } else if (moduleCompiled.containsWorldAnyLayout(changed, controllerPos)) {
                    return TargetedVerdict.ESCALATE;
                }
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
                machine.getBlockPos(),
                failure);
        try {
            pending = null;
            releaseClaimsAndWakeWatchers(services);
            clearFormed(level);
            publish(level, false);
        } catch (Exception cleanupFailure) {
            LOGGER.error(
                    "Multiblock fail-closed cleanup also failed for controller at {}",
                    machine.getBlockPos(),
                    cleanupFailure);
        }
    }

    /**
     * Keeps the current formation state when a footprint cell is in an unloaded chunk. Unloaded
     * chunks are unknown rather than broken; retry after the configured interval instead of
     * unforming a possibly intact structure.
     */
    private void deferUnloaded(MultiblockLevelBinder.Services services) {
        long now = machine.getLevel() == null ? 0L : machine.getLevel().getGameTime();
        retryAfterGameTime = now + Math.max(1, Topology.config.MULTIBLOCK.unloadedRetryIntervalTicks);
        services.rechecks().markDirty(machine.getBlockPos());
    }

    /**
     * Releases this controller's claim and marks every watcher whose region intersects the freed
     * region dirty, so competing controllers re-race the cells immediately. O(live controllers),
     * never O(cells). Composing {@link MultiblockClaimIndex} and {@link MultiblockRecheckDirtySet} happens here; the
     * two
     * services never call each other directly.
     */
    private void releaseClaimsAndWakeWatchers(MultiblockLevelBinder.Services services) {
        MultiblockClaimIndex.Region freed = services.claims().unclaim(machine.getBlockPos());
        if (freed == null) {
            return;
        }
        for (BlockPos watcher : services.claims().watchersIntersecting(freed, machine.getBlockPos())) {
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
        List<Blueprint> moduleBlueprints = moduleBlueprints();
        if (blueprint.allowMirror()) {
            claims.watch(machine.getBlockPos(), blueprint, moduleBlueprints, orientation, Orientations.mirrorOf(orientation));
        } else {
            claims.watch(machine.getBlockPos(), blueprint, moduleBlueprints, orientation);
        }
        watchedClaims = claims;
        watchedOrientation = orientation;
    }

    private List<Blueprint> moduleBlueprints() {
        if (moduleSpecs.isEmpty()) {
            return List.of();
        }
        List<Blueprint> blueprints = new ArrayList<>(moduleSpecs.size());
        for (MultiblockModule module : moduleSpecs) {
            blueprints.add(module.blueprint());
        }
        return List.copyOf(blueprints);
    }

    /** Refreshes the watched footprint for the current orientation and schedules a recheck. */
    void markDirty(ServerLevel level) {
        MultiblockLevelBinder.Services services = services(level);
        watchFootprint(services.claims(), orientationFromBlockState(machine.getBlockState()));
        services.rechecks().markDirty(machine.getBlockPos());
    }

    /**
     * Claim lifetime contract: claims are memory-only. They are released here when the block entity
     * is removed or its chunk unloads, and re-taken by the recheck that the load path schedules.
     */
    void onUnload() {
        if (machine.getLevel() instanceof ServerLevel level) {
            MultiblockLevelBinder.Services services = MultiblockLevelBinder.servicesIfPresent(level);
            if (services != null) {
                releaseClaimsAndWakeWatchers(services);
                services.claims().unwatch(machine.getBlockPos());
                // Drop our own pending dirty mark: a controller removed while dirty would otherwise
                // leave its entry (pos + change note) in the per-level dirty set forever.
                services.rechecks().consume(machine.getBlockPos());
            }
            watchedClaims = null;
            watchedOrientation = null;
            cachedServices = null;
            level.invalidateCapabilities(machine.getBlockPos());
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
        state.formedShape = null;
        state.modules = Map.of();
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
}
