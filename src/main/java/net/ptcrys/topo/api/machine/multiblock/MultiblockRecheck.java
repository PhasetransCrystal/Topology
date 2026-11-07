package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.async.TopoAsyncExecutors;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.data.DataTransaction;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.BlueprintLayout;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

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
 * 控制器的检测协调器：采集、整体识别、独占预留、元数据提交与成员接入，另提供周期和定点校验。
 * 工作线程仅处理快照，世界线程核对提交代次、当前朝向与变更标记后应用结果。
 * 未知保留旧状态，异常则释放预留并解体；业务能力由组件组合，不要求继承控制器。
 */
final class MultiblockRecheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiblockRecheck.class);

    private final MachineBlockEntity machine;
    private final Blueprint blueprint;
    private final List<MultiblockModule> moduleSpecs;
    private final Map<String, MultiblockModule> modulesById;
    private final FormedRuntime runtime;
    private final MultiblockState state;
    private final StructureMetadata metadata;

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
    private boolean freshProbe;

    MultiblockRecheck(
                      MachineBlockEntity machine,
                      Blueprint blueprint,
                      List<MultiblockModule> moduleSpecs,
                      Map<String, MultiblockModule> modulesById,
                      FormedRuntime runtime,
                      MultiblockState state, StructureMetadata metadata) {
        this.metadata = metadata;
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
        if (blueprint.supportsSnapshot() && moduleSpecs.stream().allMatch(module -> module.blueprint().supportsSnapshot()) && blueprint.maxCellCount() >= Topology.config.MULTIBLOCK.asyncCellThreshold) {
            try {
                submitAsyncRecheck(level, services);
            } catch (Exception failure) {
                failClosed(level, services, failure);
            }
        } else {
            recheck(level);
        }
    }

    /** 同步检测整个结构，两个镜像各有隔离工作区，本体与模块共同确定最终变换。 */
    private void recheck(ServerLevel level) {
        MultiblockLevelBinder.Services services = services(level);
        try {
            Orientation orientation = orientationFromBlockState(machine.getBlockState());
            watchFootprint(services.claims(), orientation);
            StructureDetection.Outcome outcome = StructureDetection.detect(
                    (definition, transform) -> definition.supportsSnapshot() ? StructureCaptures.capture(level, machine.getBlockPos(), definition, transform) : new MultiblockLiveView(level, machine.getBlockPos()),
                    blueprint, moduleSpecs, orientation, detectionSource(), verifyingBaseline());
            if (StructureDetection.unknown(outcome)) {
                deferUnloaded(services);
                return;
            }
            MultiblockModuleEvaluator.Evaluation moduleEvaluation = outcome.modules();
            boolean formed = outcome.result().formed();
            ClaimResult claimResult = claim(level, services, formed, outcome, moduleEvaluation);
            apply(level, claimResult.formed(), outcome, claimResult.modules());
            publish(level, claimResult.formed());
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    /** 世界线程采集快照，工作线程计算；后续 tick 通过过期检查才允许提交。 */
    private void submitAsyncRecheck(ServerLevel level, MultiblockLevelBinder.Services services) {
        Orientation orientation = orientationFromBlockState(machine.getBlockState());
        StructureDetection.Views captures = snapshot(level, services.claims(), orientation);
        DataSnapshot source = detectionSource();
        boolean verifying = verifyingBaseline();
        long submissionId = ++submissionCounter;
        pending = new PendingRecognition(
                submissionId,
                orientation,
                CompletableFuture.supplyAsync(() -> StructureDetection.detect(captures, blueprint, moduleSpecs, orientation, source, verifying), TopoAsyncExecutors.structureExecutor()));
    }

    /** 周期检查只读取已确认元数据和成型朝向，失败再进入完整重检；未知延后处理。 */
    private void upkeepVerify(ServerLevel level, MultiblockLevelBinder.Services services) {
        try {
            MultiblockState.FormedShape shape = Objects.requireNonNull(state.formedShape);
            Orientation orientation = shape.orientation();
            Orientation controllerOrientation = orientationFromBlockState(machine.getBlockState());
            if (!formedOrientationMatches(orientation, controllerOrientation)) {
                recheck(level);
                return;
            }
            watchFootprint(services.claims(), controllerOrientation);
            StructureView capture = blueprint.supportsSnapshot() ? StructureCaptures.capture(level, machine.getBlockPos(), blueprint, orientation) : new MultiblockLiveView(level, machine.getBlockPos());
            if (blueprint.supportsSnapshot() && moduleSpecs.stream().allMatch(module -> module.blueprint().supportsSnapshot()) && blueprint.maxCellCount() >= Topology.config.MULTIBLOCK.asyncCellThreshold) {
                long submissionId = ++submissionCounter;
                pendingVerify = new PendingVerification(
                        submissionId,
                        orientation,
                        controllerOrientation,
                        AsyncStructureService.verifyAsync((CompiledSnapshot) capture, blueprint, metadata.snapshot()));
                return;
            }
            StructureEngine.Verification verification = StructureEngine.verify(capture, blueprint, orientation, metadata.snapshot().verify());
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
            boolean stale = inFlight.submissionId() != submissionCounter || shape == null || !inFlight.orientation().equals(shape.orientation()) || !inFlight.controllerOrientation().equals(orientationFromBlockState(machine.getBlockState())) || services.rechecks().isDirty(machine.getBlockPos());
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
            StructureDetection.Outcome outcome = inFlight.future().join();
            Orientation current = orientationFromBlockState(machine.getBlockState());
            boolean stale = inFlight.submissionId() != submissionCounter || !inFlight.orientation().equals(current) || services.rechecks().isDirty(machine.getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine.getBlockPos());
                return;
            }
            if (StructureDetection.unknown(outcome)) {
                deferUnloaded(services);
                return;
            }
            MultiblockModuleEvaluator.Evaluation moduleEvaluation = outcome.modules();
            boolean formed = outcome.result().formed();
            ClaimResult claimResult = claim(level, services, formed, outcome, moduleEvaluation);
            apply(level, claimResult.formed(), outcome, claimResult.modules());
            publish(level, claimResult.formed());
        } catch (Exception failure) {
            failClosed(level, services, failure);
        }
    }

    /** 世界线程采集本体与全部模块的两个整体变换，工作线程不会再访问世界。 */
    private StructureDetection.Views snapshot(ServerLevel level, MultiblockClaimIndex claims, Orientation orientation) {
        watchFootprint(claims, orientation);
        Map<Blueprint, Map<Orientation, CompiledSnapshot>> captures = new LinkedHashMap<>();
        List<Blueprint> definitions = new ArrayList<>(List.of(blueprint));
        definitions.addAll(moduleBlueprints());
        for (Blueprint definition : definitions) {
            captures.put(definition, Map.of(
                    orientation, StructureCaptures.capture(level, machine.getBlockPos(), definition, orientation),
                    orientation.mirrored(), StructureCaptures.capture(level, machine.getBlockPos(), definition, orientation.mirrored())));
        }
        Map<Blueprint, Map<Orientation, CompiledSnapshot>> immutable = Map.copyOf(captures);
        return (definition, transform) -> Objects.requireNonNull(immutable.get(definition).get(transform));
    }

    private DataSnapshot detectionSource() {
        return freshProbe ? metadata.defaults() : metadata.snapshot();
    }

    private boolean verifyingBaseline() {
        return !freshProbe && metadata.snapshot().assignedKeys().contains(StructureMetadata.ORIENTATION);
    }

    /** 显式开始新探测周期，未知或在途期间仍保留旧的正式元数据，只有完整提交才替换。 */
    void requestProbe(ServerLevel level) {
        cancelPending();
        freshProbe = true;
        forceFullRecheck = true;
        retryAfterGameTime = 0;
        markDirty(level);
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
                              StructureDetection.Outcome outcome,
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
        MultiblockModuleEvaluator.Evaluation adjustedModules = MultiblockModuleEvaluator.evaluateOverlays(
                definition -> view, moduleSpecs, outcome.orientation(),
                verifyingBaseline() ? outcome.result().metadata().verify() : outcome.result().metadata().edit(),
                members -> services.claims().conflictingExclusive(machine.getBlockPos(), exclusiveCells(view, members)).isEmpty());
        Set<BlockPos> allExclusive = new LinkedHashSet<>(mainExclusive);
        allExclusive.addAll(exclusiveCells(view, adjustedModules.members()));
        MultiblockClaimIndex.ClaimUpdate update = services.claims().tryClaim(
                machine.getBlockPos(),
                blueprint,
                outcome.orientation(),
                outcome.result().repeats(),
                allExclusive);
        if (!update.accepted()) {
            releaseClaimsAndWakeWatchers(services);
            return new ClaimResult(false, adjustedModules);
        }
        if (update.released() != null) wakeWatchers(services, update.released());
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

    /** Claim outcome: whether the machine formed and the module reports after exclusive conflicts. */
    private record ClaimResult(boolean formed, MultiblockModuleEvaluator.Evaluation modules) {}

    /** Apply step: member set, formed geometry, module reports, visuals, and the formation flip. */
    private void apply(
                       ServerLevel level,
                       boolean formed,
                       StructureDetection.Outcome outcome,
                       MultiblockModuleEvaluator.Evaluation moduleEvaluation) {
        if (!formed) {
            boolean hadBaseline = metadata.snapshot().assignedKeys().contains(StructureMetadata.ORIENTATION);
            clearFormed(level);
            freshProbe = false;
            if (hadBaseline) services(level).rechecks().markDirty(machine.getBlockPos());
            return;
        }
        freshProbe = false;
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
        DataTransaction confirmed = moduleEvaluation.metadata().edit();
        Map<String, List<Integer>> repeats = new LinkedHashMap<>();
        repeats.put("body", java.util.Arrays.stream(result.repeats().countsCopy()).boxed().toList());
        moduleRepeats.forEach((id, value) -> repeats.put("module/" + id, java.util.Arrays.stream(value.countsCopy()).boxed().toList()));
        confirmed.set(StructureMetadata.ORIENTATION, Orientations.all().indexOf(outcome.orientation()));
        confirmed.set(StructureMetadata.REPEATS, Map.copyOf(repeats));
        metadata.commit(confirmed.snapshot());
        MultiblockVisuals.writeMemberCtmActive(level, previousMembers, runtime.memberPositions());
        if (!wasFormed || !previousModules.equals(state.modules)) {
            machine.machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine.getBlockPos());
        }
    }

    /** 清空成型运行时和有效元数据，并发布一次解体变更。 */
    private void clearFormed(ServerLevel level) {
        boolean wasFormed = state.formedShape != null;
        List<BlockPos> previousMembers = runtime.memberPositions();
        runtime.clearMembers();
        state.formedShape = null;
        state.modules = Map.of();
        metadata.invalidate();
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
        if (!formedOrientationMatches(orientation, orientationFromBlockState(machine.getBlockState()))) return TargetedVerdict.ESCALATE;
        BlockPos controllerPos = machine.getBlockPos();
        CompiledBlueprint mainCompiled = blueprint.compiled(orientation);
        BlueprintLayout mainLayout = blueprint.layout(shape.repeats());
        MultiblockLiveView view = new MultiblockLiveView(level, controllerPos);
        for (BlockPos changed : changedCells) {
            if (!view.isLoaded(changed)) {
                return TargetedVerdict.ESCALATE;
            }
            // Main blueprint: a mismatch unforms immediately; the growth area forces a full recognize.
            int mainIndex = mainCompiled.cellIndexOfWorld(changed, controllerPos, mainLayout);
            if (mainIndex >= 0) {
                var cellResult = StructureEngine.evaluateCompiled(view, changed, mainCompiled.definitionAt(mainIndex), metadata.snapshot().verify());
                if (cellResult.status() == net.ptcrys.topo.api.machine.multiblock.pattern.CellResult.Status.UNKNOWN) return TargetedVerdict.ESCALATE;
                if (!cellResult.passed()) {
                    return TargetedVerdict.BROKEN;
                }
                // 状态仍匹配也可能发生部件替换或角色变化，必须刷新成员、计数及独占预留。
                if (!blueprint.countRequirements().isEmpty() || view.member(changed) != null || !view.roles(changed).isEmpty() || runtime.memberPositions().contains(changed)) return TargetedVerdict.ESCALATE;
            } else if (mainCompiled.containsWorldAnyLayout(changed, controllerPos)) {
                return TargetedVerdict.ESCALATE; // A change can grow the repeat layout.
            }
            // 缺失模块也监听完整可能占地；补齐最后一个方块必须在本轮重新求值。
            for (MultiblockModule module : moduleSpecs) {
                if (module.blueprint().compiled(orientation).containsWorldAnyLayout(changed, controllerPos)) return TargetedVerdict.ESCALATE;
            }
        }
        return TargetedVerdict.RETAINED;
    }

    /**
     * Immediate unform on a targeted mismatch: releases claims, clears members and visuals, and
     * publishes the unformed state this tick — the full diagnostic recheck runs on the re-mark.
     */
    private void unformNow(ServerLevel level, MultiblockLevelBinder.Services services) {
        cancelPending();
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
        cancelPending();
        try {
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
        wakeWatchers(services, freed);
    }

    private void wakeWatchers(MultiblockLevelBinder.Services services, MultiblockClaimIndex.Region freed) {
        for (BlockPos watcher : services.claims().watchersIntersecting(freed, machine.getBlockPos())) {
            services.rechecks().markDirty(watcher);
        }
    }

    /** 同时监听控制器主朝向和镜像的可能占地；同一索引和朝向下复用已有监听，不重复建立位置集合。 */
    private void watchFootprint(MultiblockClaimIndex claims, Orientation orientation) {
        if (claims == watchedClaims && orientation.equals(watchedOrientation)) {
            return;
        }
        List<Blueprint> moduleBlueprints = moduleBlueprints();
        claims.watch(machine.getBlockPos(), blueprint, moduleBlueprints, orientation, Orientations.mirrorOf(orientation));
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
        cancelPending();
        state.formedShape = null;
        state.modules = Map.of();
    }

    /** 失效所有在途结果；即使工作线程已经完成，也不允许其恢复已解体的机器。 */
    private void cancelPending() {
        submissionCounter++;
        if (pending != null) pending.future().cancel(false);
        if (pendingVerify != null) pendingVerify.future().cancel(false);
        pending = null;
        pendingVerify = null;
    }

    private boolean formedOrientationMatches(Orientation formed, Orientation current) {
        return formed.equals(current) || formed.equals(current.mirrored());
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
                                      CompletableFuture<StructureDetection.Outcome> future) {}

    private record PendingVerification(
                                       long submissionId,
                                       Orientation orientation,
                                       Orientation controllerOrientation,
                                       CompletableFuture<StructureEngine.Verification> future) {}
}
