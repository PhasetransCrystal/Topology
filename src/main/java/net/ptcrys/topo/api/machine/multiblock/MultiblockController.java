package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.RecipeCondition;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeModifier;
import net.ptcrys.topo.api.machine.data.DataSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureMetadata;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 服务端多方块控制器组件。保存成型状态并协调运行时成员，检测流程委托给 MultiblockRecheck。
 * 流程包含采集、识别、独占预留、成员接入及成型外观发布；异常时解体并释放预留。
 * 变更事件触发定点或完整重检，周期兜底维护弥补遗漏事件。
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
    private final StructureMetadata metadata;

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
        List<Blueprint> definitions = new ArrayList<>(List.of(blueprint));
        specs.forEach(module -> definitions.add(module.blueprint()));
        this.metadata = new StructureMetadata(data().group("structure_metadata"), definitions);
        this.recheck = new MultiblockRecheck(machine(), blueprint, specs, modulesById, runtime, state, metadata);
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

    /** 已确认结构的隔离元数据快照；未成型时返回未探测的声明默认值，不暴露整个机器数据域。 */
    public DataSnapshot metadata() {
        return formed() ? metadata.snapshot() : metadata.defaults();
    }

    /** 请求从世界重新探测元数据；常规重检会校验原基线，此入口明确允许生成新的结构事实。 */
    public void requestProbe() {
        if (machine().getLevel() instanceof ServerLevel level) recheck.requestProbe(level);
    }

    public boolean formed() {
        return state.formedShape != null;
    }

    /** 成型布局的各段副本数；未成型时返回 null。 */
    public @Nullable RepeatResolution repeatResolution() {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? null : shape.repeats();
    }

    /** 指定重复段的副本数；未成型时返回 0。 */
    public int repeatCount(int sectionIndex) {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? 0 : shape.repeats().count(sectionIndex);
    }

    /** 按模块 ID 返回最近报告，包含缺失的可选模块。 */
    public Map<String, RecognitionResult.ModuleReport> moduleReports() {
        return state.modules;
    }

    /** 自身完整且所有依赖满足的模块 ID。 */
    public Set<String> satisfiedModules() {
        Set<String> satisfied = new LinkedHashSet<>();
        for (Map.Entry<String, RecognitionResult.ModuleReport> entry : state.modules.entrySet()) {
            if (entry.getValue().satisfied()) {
                satisfied.add(entry.getKey());
            }
        }
        return Set.copyOf(satisfied);
    }

    /** 指定模块报告；尚无报告或未声明时返回 null。 */
    public RecognitionResult.@Nullable ModuleReport moduleReport(String id) {
        return state.modules.get(Objects.requireNonNull(id, "module id"));
    }

    /** 已接入模块的重复次数；未成型或模块缺失时返回 null。 */
    public @Nullable RepeatResolution moduleRepeatResolution(String id) {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? null : shape.moduleRepeats().get(Objects.requireNonNull(id, "module id"));
    }

    /** 所有已接入模块的重复次数；未成型时为空。 */
    public Map<String, RepeatResolution> moduleRepeatResolutions() {
        MultiblockState.FormedShape shape = state.formedShape;
        return shape == null ? Map.of() : shape.moduleRepeats();
    }

    /** 指定模块段的副本数；未成型或模块缺失时返回 0。 */
    public int moduleRepeatCount(String id, int sectionIndex) {
        RepeatResolution repeats = moduleRepeatResolution(id);
        return repeats == null ? 0 : repeats.count(sectionIndex);
    }

    /** 主蓝图与所有已接入模块的世界边界并集；未成型时返回空边界。 */
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

    /** 将成员的原始配方资源处理器贡献给 RecipeSearchPoolRouter，由路由器按池聚合；控制器不提前合并成员处理器。 */
    @Override
    protected <R extends Resource> void collectRecipeResourceHandlers(
                                                                      MachineResourceType<R> resourceType,
                                                                      RecipeRole recipeIo,
                                                                      Consumer<RecipeResourceContribution<R>> out) {
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
