package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.RecipeCondition;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-side multiblock controller: a thin {@link MachineTicker} component that owns the machine's
 * formed {@link MultiblockState} and the shared collaborators, and delegates the recheck pipeline to
 * {@link MultiblockRecheck} (the machine system's component pattern: heavy logic lives beside the
 * component, not inside it).
 *
 * <p>
 * The recheck pipeline is a flat template of five steps — {@code snapshot} → {@code recognize} →
 * {@code claim} → {@code apply} → {@code publish} — and is fail-closed. Correctness does not depend
 * on event coverage: formed controllers self-mark dirty every
 * {@link net.ptcrys.topo.Topology#config} MULTIBLOCK.backstopIntervalTicks ticks, staggered by
 * position hash, so a missed world change is repaired by the next backstop at the latest.
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

    private final FormedRuntime runtime;
    private final MultiblockRecipeModifiers recipeModifiers;
    private final MultiblockState state;
    private final MultiblockRecheck recheck;

    private MultiblockController(
                                 ComponentContext<MultiblockController> context,
                                 Blueprint blueprint,
                                 List<MultiblockModule> moduleSpecs) {
        super(context);
        Objects.requireNonNull(blueprint, "blueprint");
        List<MultiblockModule> specs = List.copyOf(Objects.requireNonNull(moduleSpecs, "multiblock modules"));
        Map<String, MultiblockModule> byId = new LinkedHashMap<>();
        for (MultiblockModule module : specs) {
            byId.put(module.id(), module);
        }
        Map<String, MultiblockModule> modulesById = Map.copyOf(byId);
        this.runtime = new FormedRuntime(machine());
        this.recipeModifiers = new MultiblockRecipeModifiers(machine(), runtime);
        this.state = new MultiblockState();
        this.recheck = new MultiblockRecheck(machine(), blueprint, specs, modulesById, runtime, state);
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
        MultiblockModuleEvaluator.validateGraph(specs);
        return key.mount(
                context -> new MultiblockController(context, blueprint, specs),
                new MultiblockControllerMetadata(blueprint, specs));
    }

    public boolean formed() {
        return state.formedShape != null;
    }

    /** The resolved repeat count per blueprint segment, or {@code null} when unformed. */
    public @Nullable RepeatResolution repeatResolution() {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? null : shape.repeats();
    }

    /** Resolved copy count of one blueprint repeat section, or {@code 0} when unformed. */
    public int repeatCount(int sectionIndex) {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? 0 : shape.repeats().count(sectionIndex);
    }

    /** Latest module reports keyed by module id, including optional modules. */
    public Map<String, RecognitionResult.ModuleReport> moduleReports() {
        return state.modules;
    }

    /** Module ids whose full cell set currently matches. */
    public Set<String> satisfiedModules() {
        Set<String> satisfied = new LinkedHashSet<>();
        for (Map.Entry<String, RecognitionResult.ModuleReport> entry : state.modules.entrySet()) {
            if (entry.getValue().satisfied()) {
                satisfied.add(entry.getKey());
            }
        }
        return Set.copyOf(satisfied);
    }

    /** One module report, or {@code null} when the id is not declared on this blueprint. */
    public RecognitionResult.@Nullable ModuleReport moduleReport(String id) {
        return state.modules.get(Objects.requireNonNull(id, "module id"));
    }

    /** Resolved repeat counts of one module blueprint, or {@code null} when unformed or absent. */
    public @Nullable RepeatResolution moduleRepeatResolution(String id) {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? null : shape.moduleRepeats().get(Objects.requireNonNull(id, "module id"));
    }

    /** Resolved repeat counts of every satisfied module, keyed by module id; empty when unformed. */
    public Map<String, RepeatResolution> moduleRepeatResolutions() {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? Map.of() : shape.moduleRepeats();
    }

    /** Resolved copy count of one repeat section inside a module, or {@code 0} when unformed/absent. */
    public int moduleRepeatCount(String id, int sectionIndex) {
        RepeatResolution repeats = moduleRepeatResolution(id);
        return repeats == null ? 0 : repeats.count(sectionIndex);
    }

    /** World-space bounds of the whole machine — main blueprint plus every satisfied module. */
    public RecognitionResult.Bounds machineBounds() {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? RecognitionResult.Bounds.empty() : shape.bounds();
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        runtime.pollMemberResourceVersions();
        recheck.tick(gameTime);
    }

    private void markDirtyAndAlert() {
        if (machine().getLevel() instanceof ServerLevel level) {
            recheck.markDirty(level);
        }
        TickHandle handle = handle();
        if (handle != null) {
            handle.alert();
        }
    }

    private RecipeModifier memberFoldModifier(@Nullable RecipeLogic context) {
        return recipeModifiers.memberFoldModifier(context);
    }

    @Override
    protected void onAttached(TickHandle handle) {
        markDirtyAndAlert();
    }

    @Override
    public void onMachineLoad() {
        markDirtyAndAlert();
    }

    @Override
    public void onMachineUnload() {
        recheck.onUnload();
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
}
