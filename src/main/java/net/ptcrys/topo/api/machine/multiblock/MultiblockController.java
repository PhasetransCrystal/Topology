package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.api.visual.ConnectedTextureProperties;
import net.ptcrys.topo.api.machine.FormedMachineBlock;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.RecipeCondition;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.component.ServiceMatch;
import net.ptcrys.topo.api.machine.data.DataEnum;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
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
import net.ptcrys.topo.api.machine.ui.LcdData;
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
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
 * every {@link #BACKSTOP_INTERVAL_TICKS} ticks, staggered by position hash, so a missed world change
 * is repaired by the next backstop recheck at the latest. Backstop upkeep on an intact structure is
 * the cheap path — a single formed-orientation capture plus the zero-allocation
 * {@link StructureEngine#verify}; the full recognize (mirror twin, members, diagnostics) only runs
 * on formation transitions and verify failures.
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
    /** Formed controllers self-recheck at least this often; events only lower reaction latency. */
    private static final int BACKSTOP_INTERVAL_TICKS = 150;

    private final Blueprint blueprint;
    private final FormedRuntime runtime;
    private final DataEnum<FormState> formState = data().enumField("form_state", FormState.class, FormState.UNFORMED)
            .saveNone()
            .syncNone()
            .done();
    /** Last footprint registration, so an unchanged (orientation, mirror) pair skips the watch rebuild. */
    private @Nullable MultiblockClaimIndex watchedClaims;
    private @Nullable Orientation watchedOrientation;
    /** Per-level services, cached so the every-tick path skips the binder's synchronized map lookup. */
    private MultiblockLevelBinder.@Nullable Services cachedServices;
    /**
     * The orientation/repeats the formed structure recognized under; null when unformed. Targeted
     * invalidation re-derives any cell from these arithmetically — no per-cell index exists.
     */
    private @Nullable Orientation formedOrientation;
    private @Nullable RepeatResolution formedRepeats;
    /** In-flight async recognition; results commit on the server thread with staleness checks. */
    private @Nullable PendingRecognition pending;
    /** In-flight async upkeep verification (formed backstop); commits like {@link #pending}. */
    private @Nullable PendingVerification pendingVerify;
    /** Set when an upkeep verify failed: the next consumed mark pays the full recognize path. */
    private boolean forceFullRecheck;
    private long submissionCounter;

    private MultiblockController(ComponentContext<MultiblockController> context, Blueprint blueprint) {
        super(context);
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.runtime = new FormedRuntime(machine());
    }

    public static ComponentMount<MultiblockController> mount(Blueprint blueprint) {
        return mount(CONTROLLER, blueprint);
    }

    public static ComponentMount<MultiblockController> mount(
                                                             ComponentKey<MultiblockController> key,
                                                             Blueprint blueprint) {
        Objects.requireNonNull(key, "multiblock controller trait key");
        Objects.requireNonNull(blueprint, "blueprint");
        return key.mount(
                context -> new MultiblockController(context, blueprint),
                new MultiblockControllerMetadata(blueprint));
    }

    public boolean formed() {
        return formState.value() == FormState.FORMED;
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
        if (formed() && Math.floorMod(machine().getBlockPos().hashCode() + gameTime, BACKSTOP_INTERVAL_TICKS) == 0) {
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
        if (formed() && note.bounded() && formedOrientation != null) {
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
        if (!escalated && !forceFullRecheck && formed() && formedOrientation != null) {
            upkeepVerify(level, services);
            return;
        }
        forceFullRecheck = false;
        if (blueprint.maxCellCount() >= AsyncStructureService.asyncCellThreshold()) {
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
            CompiledSnapshot primary = services.claims().snapshot(machine().getBlockPos(), blueprint, orientation);
            AsyncStructureService.Outcome outcome = new AsyncStructureService.Outcome(
                    StructureEngine.recognize(primary, blueprint, orientation), orientation);
            if (!outcome.result().formed() && blueprint.allowMirror()) {
                Orientation mirrored = Orientations.mirrorOf(orientation);
                CompiledSnapshot mirrorCapture = services.claims().snapshot(machine().getBlockPos(), blueprint, mirrored);
                RecognitionResult mirrorResult = StructureEngine.recognize(mirrorCapture, blueprint, mirrored);
                if (mirrorResult.formed()) {
                    outcome = new AsyncStructureService.Outcome(mirrorResult, mirrored);
                }
            }
            boolean claimed = claim(services, outcome);
            apply(level, claimed, outcome);
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
            Orientation orientation = Objects.requireNonNull(formedOrientation);
            watchFootprint(services.claims(), orientationFromBlockState(machine().getBlockState()));
            CompiledSnapshot capture = services.claims().snapshot(machine().getBlockPos(), blueprint, orientation);
            if (blueprint.maxCellCount() >= AsyncStructureService.asyncCellThreshold()) {
                long submissionId = ++submissionCounter;
                pendingVerify = new PendingVerification(
                        submissionId,
                        orientation,
                        AsyncStructureService.verifyAsync(capture, blueprint));
                return;
            }
            applyVerification(level, services, StructureEngine.verify(capture, blueprint, orientation));
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
        if (verification.formed() && verification.repeats().equals(formedRepeats)) {
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
            boolean stale = inFlight.submissionId() != submissionCounter || !formed() || !inFlight.orientation().equals(formedOrientation) || services.rechecks().isDirty(machine().getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine().getBlockPos());
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
            Orientation current = orientationFromBlockState(machine().getBlockState());
            boolean stale = inFlight.submissionId() != submissionCounter || !inFlight.orientation().equals(current) || services.rechecks().isDirty(machine().getBlockPos());
            if (stale) {
                // The world (or the controller) moved on while the verdict was in flight; rerun.
                services.rechecks().markDirty(machine().getBlockPos());
                return;
            }
            boolean claimed = claim(services, outcome);
            apply(level, claimed, outcome);
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

    /** Claim step: a formed verdict claims the footprint; failure (or an unformed verdict) releases ours. */
    private boolean claim(MultiblockLevelBinder.Services services, AsyncStructureService.Outcome outcome) {
        if (outcome.result().formed() && services.claims()
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

    /** Apply step: member set, formed orientation/repeats, member visuals, and the form state flip. */
    private void apply(ServerLevel level, boolean formed, AsyncStructureService.Outcome outcome) {
        RecognitionResult result = outcome.result();
        List<BlockPos> previousMembers = runtime.memberPositions();
        if (formed) {
            runtime.updateMembers(result.members());
            formedRepeats = result.repeats();
            formedOrientation = outcome.orientation();
        } else {
            runtime.clearMembers();
            formedRepeats = null;
            formedOrientation = null;
        }
        writeMemberCtmActive(level, previousMembers, runtime.memberPositions());
        FormState next = formed ? FormState.FORMED : FormState.UNFORMED;
        if (formState.value() != next) {
            formState.set(next);
            machine().machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine().getBlockPos());
        }
    }

    /** Publish step: writes the {@code FORMED} visual block state. */
    private void publish(ServerLevel level, boolean formed) {
        writeFormedBlockState(level, formed);
    }

    /**
     * Re-tests only the changed cells against the live level: each position resolves to its
     * compiled definition by pure arithmetic ({@code cellIndexOfWorld}); no per-cell index exists at
     * any structure size. Positions inside the maximal footprint but beyond the formed repeats
     * (growth area) escalate to a full recheck; positions outside the footprint are not ours.
     */
    private TargetedVerdict targetedRecheck(ServerLevel level, Set<BlockPos> changedCells) {
        Orientation orientation = Objects.requireNonNull(formedOrientation);
        RepeatResolution repeats = Objects.requireNonNull(formedRepeats);
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        BlockPos controllerPos = machine().getBlockPos();
        LiveStructureView view = new LiveStructureView(level, controllerPos);
        for (BlockPos changed : changedCells) {
            int cellIndex = compiled.cellIndexOfWorld(changed, controllerPos);
            if (cellIndex < 0) {
                continue; // Outside the maximal footprint — not one of our cells.
            }
            if (!compiled.withinRepeats(cellIndex, repeats)) {
                return TargetedVerdict.ESCALATE; // Growth area of a repeatable segment.
            }
            CompiledBlueprint.CompiledDefinition definition = compiled.definitions()[compiled.definitionOrdinalAt(cellIndex)];
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
        writeMemberCtmActive(level, runtime.memberPositions(), List.of());
        runtime.clearMembers();
        formedOrientation = null;
        formedRepeats = null;
        if (formState.value() != FormState.UNFORMED) {
            formState.set(FormState.UNFORMED);
            machine().machineComponents().noteResourceContentChanged();
            level.invalidateCapabilities(machine().getBlockPos());
        }
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
            formedOrientation = null;
            formedRepeats = null;
            releaseClaimsAndWakeWatchers(services);
            writeMemberCtmActive(level, runtime.memberPositions(), List.of());
            runtime.clearMembers();
            if (formState.value() != FormState.UNFORMED) {
                formState.set(FormState.UNFORMED);
                machine().machineComponents().noteResourceContentChanged();
                level.invalidateCapabilities(machine().getBlockPos());
            }
            publish(level, false);
        } catch (Exception cleanupFailure) {
            LOGGER.error(
                    "Multiblock fail-closed cleanup also failed for controller at {}",
                    machine().getBlockPos(),
                    cleanupFailure);
        }
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

    /**
     * Member visual sync: parts melting into the formed structure light their
     * {@code CTM_ACTIVE} runtime flag, dropped-out parts go back to the standalone look. The flag is
     * declared a runtime state property on the part block, so flipping it never re-dirties the
     * structure watcher. Diff-only: an identical member list (same instance — the runtime keeps the
     * old list when nothing changed) skips entirely, and unchanged members are never re-written, so
     * the formed backstop costs no block-state reads here.
     */
    private void writeMemberCtmActive(ServerLevel level, List<BlockPos> previousMembers, List<BlockPos> currentMembers) {
        if (previousMembers == currentMembers) {
            return;
        }
        Set<BlockPos> current = currentMembers.isEmpty() ? Set.of() : new HashSet<>(currentMembers);
        for (BlockPos member : previousMembers) {
            if (!current.contains(member)) {
                ConnectedTextureProperties.setActive(level, member, false);
            }
        }
        Set<BlockPos> previous = previousMembers.isEmpty() ? Set.of() : new HashSet<>(previousMembers);
        for (BlockPos member : currentMembers) {
            if (!previous.contains(member)) {
                ConnectedTextureProperties.setActive(level, member, true);
            }
        }
    }

    /** Controller-only writer of the {@link FormedMachineBlock#FORMED} visual block-state property. */
    private void writeFormedBlockState(ServerLevel level, boolean formed) {
        BlockPos pos = machine().getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(FormedMachineBlock.FORMED) || state.getValue(FormedMachineBlock.FORMED) == formed) {
            return;
        }
        level.setBlockAndUpdate(pos, state.setValue(FormedMachineBlock.FORMED, formed));
    }

    private RecipeModifier memberFoldModifier(@Nullable RecipeLogic context) {
        List<ServiceMatch<RecipeModifier>> memberModifiers = runtime.services(RecipeModifier.KEY, context);
        return new RecipeModifier() {

            @Override
            public Component title() {
                return TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_NAME.getComponent();
            }

            @Override
            public Component description() {
                return TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_DESC.getComponent();
            }

            @Override
            public UIElement createDetailsUi() {
                return MachineUiContainerTemplate.INSTANCE
                        .createLcdData(LcdData.Orientation.VERTICAL)
                        .addStaticEntry(
                                TopoApiLang.UI_RECIPE_MODIFIER_ATTR_SCOPE.getComponent(),
                                TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_SCOPE.getComponent(),
                                LcdData.LED_TEXT)
                        .addStaticEntry(
                                TopoApiLang.UI_RECIPE_MODIFIER_ATTR_EFFECT.getComponent(),
                                TopoApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_EFFECT.getComponent(),
                                LcdData.LED_RUNNING);
            }

            @Override
            public @NonNull TopoRecipe modify(@NonNull TopoRecipe recipe) {
                return foldMemberRecipeModifiers(recipe, memberModifiers);
            }

            @Override
            public @NonNull TopoRecipe modify(
                                              @NonNull TopoRecipe recipe, @Nullable MachineBlockEntity host) {
                return foldMemberRecipeModifiers(recipe, memberModifiers);
            }

            @Override
            public long parallelCap() {
                return maxParallelCap(memberModifiers);
            }
        };
    }

    private TopoRecipe foldMemberRecipeModifiers(
                                                 TopoRecipe recipe, List<ServiceMatch<RecipeModifier>> memberModifiers) {
        TopoRecipe modified = recipe;
        for (ServiceMatch<RecipeModifier> match : memberModifiers) {
            modified = match.value().modify(modified, machine());
        }
        return modified;
    }

    private static long maxParallelCap(List<ServiceMatch<RecipeModifier>> modifiers) {
        long cap = 1L;
        for (ServiceMatch<RecipeModifier> match : modifiers) {
            cap = Math.max(cap, match.value().parallelCap());
        }
        return cap;
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
        formedOrientation = null;
        formedRepeats = null;
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

    /** Live, read-only structure view over the server level — used for targeted single-cell re-tests. */
    private static final class LiveStructureView implements StructureView {

        private final ServerLevel level;
        private final BlockPos controllerPos;

        private LiveStructureView(ServerLevel level, BlockPos controllerPos) {
            this.level = level;
            this.controllerPos = controllerPos;
        }

        @Override
        public BlockPos controllerPos() {
            return controllerPos;
        }

        @Override
        public @Nullable BlockState blockState(BlockPos worldPos) {
            return StructureCaptures.captureCell(level, worldPos).state();
        }

        @Override
        public Set<PartRole> roles(BlockPos worldPos) {
            return StructureCaptures.captureCell(level, worldPos).roles();
        }

        @Override
        public boolean isMember(BlockPos worldPos) {
            return StructureCaptures.captureCell(level, worldPos).member();
        }
    }

    public enum FormState {
        UNFORMED,
        FORMED
    }
}
