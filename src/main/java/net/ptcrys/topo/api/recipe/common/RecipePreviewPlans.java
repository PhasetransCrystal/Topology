package net.ptcrys.topo.api.recipe.common;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.multiblock.MultiblockControllerMetadata;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.api.recipe.RecipePreviewPlan;
import net.ptcrys.topo.api.recipe.RecipePreviewPlan.SlotPlan;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.api.recipe.capability.SlottedRecipeCapability;

import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 预览计划 planner（data 侧行为决策）：为每个配方类型挑一台"规范机"，把它的端口元数据折算成
 * 预览槽位计划。选型策略 —— 支持该配方类型、有自有预览端口（多方块控制器的配方 IO 在部件块上，
 * 不能代表布局）、按"配方类型集最小 → 资源面最小 → id 字典序"取最专一的机器。
 *
 * <p>
 * bootstrap 在 {@code Machines.freeze()} 之后调用
 * {@code TopoRecipeTypes.installPreviewPlans(RecipePreviewPlans::canonical)}；没有任何合格机器的
 * 配方类型得到 {@link RecipePreviewPlan#EMPTY}（这是真实事实，不是时序事故——安装点保证机器
 * 注册表已冻结）。
 */
public final class RecipePreviewPlans {

    private RecipePreviewPlans() {}

    public static RecipePreviewPlan canonical(TopoRecipeType<?> recipeType) {
        MachineDefinition machine = selectCanonicalMachine(recipeType);
        if (machine == null) {
            return RecipePreviewPlan.EMPTY;
        }
        return new RecipePreviewPlan(
                slotCountsFromMetadata(machine.resourcePorts()),
                countImplicitCapabilities(machine));
    }

    private static @Nullable MachineDefinition selectCanonicalMachine(TopoRecipeType<?> recipeType) {
        return Machines.registered().stream()
                .filter(definition -> definition.supportsRecipeType(recipeType))
                // A multiblock controller's recipe IO lives on its hatch parts: whatever buffer
                // ports the controller block itself carries (e.g. an energy feed), it cannot stand
                // in for the recipe layout.
                .filter(definition -> definition.metadata(MultiblockControllerMetadata.TYPE).isEmpty())
                .filter(definition -> resourceFootprint(definition) > 0)
                .min(Comparator
                        .comparingInt((MachineDefinition definition) -> recipeTypeFootprint(definition, recipeType))
                        .thenComparingInt(RecipePreviewPlans::resourceFootprint)
                        .thenComparing(definition -> definition.id().toString()))
                .orElse(null);
    }

    private static int recipeTypeFootprint(MachineDefinition definition, TopoRecipeType<?> recipeType) {
        int footprint = Integer.MAX_VALUE;
        for (var metadata : definition.recipeLogicMounts()) {
            if (metadata.recipeTypes().contains(recipeType)) {
                footprint = Math.min(footprint, metadata.recipeTypes().size());
            }
        }
        return footprint;
    }

    private static int resourceFootprint(MachineDefinition definition) {
        int slots = 0;
        for (ResourcePortMetadata metadata : definition.resourcePorts()) {
            slots += previewSlotFootprint(metadata);
        }
        return slots;
    }

    private static int previewSlotFootprint(ResourcePortMetadata metadata) {
        int slots = 0;
        for (RecipeCapability<?, ?> capability : RecipeCapabilities.registered()) {
            if (capability.resourceType() != metadata.resourceType()) {
                continue;
            }
            // Implicit lanes (scalar bars) still count as preview footprint: a machine whose ports
            // are all scalar (e.g. an electric boiler) must stay eligible as the canonical machine.
            int footprint = capability instanceof SlottedRecipeCapability<?, ?, ?> slotted ? slotted.countPreviewSlots(metadata) : 1;
            slots = Math.max(slots, footprint);
        }
        return slots;
    }

    private static Map<SlottedRecipeCapability<?, ?, ?>, SlotPlan> slotCountsFromMetadata(
                                                                                          List<ResourcePortMetadata> ports) {
        Map<SlottedRecipeCapability<?, ?, ?>, SlotPlan> counts = new LinkedHashMap<>();
        for (RecipeCapability<?, ?> capability : RecipeCapabilities.registered()) {
            if (!(capability instanceof SlottedRecipeCapability<?, ?, ?> slotted)) {
                continue;
            }
            int in = 0;
            int out = 0;
            for (ResourcePortMetadata metadata : ports) {
                if (metadata.resourceType() != slotted.resourceType()) {
                    continue;
                }
                int slots = slotted.countPreviewSlots(metadata);
                if (metadata.recipeIo().allows(RecipeRole.INPUT)) {
                    in += slots;
                }
                if (metadata.recipeIo().allows(RecipeRole.OUTPUT)) {
                    out += slots;
                }
            }
            if (in > 0 || out > 0) {
                counts.put(slotted, new SlotPlan(in, out));
            }
        }
        return counts;
    }

    private static int countImplicitCapabilities(MachineDefinition machine) {
        int lanes = 0;
        for (ResourcePortMetadata port : machine.resourcePorts()) {
            if (isImplicitCapability(port)) {
                lanes++;
            }
        }
        return lanes;
    }

    private static boolean isImplicitCapability(ResourcePortMetadata port) {
        for (RecipeCapability<?, ?> capability : RecipeCapabilities.registered()) {
            if (!(capability instanceof SlottedRecipeCapability) && capability.resourceType() == port.resourceType()) {
                return true;
            }
        }
        return false;
    }
}
