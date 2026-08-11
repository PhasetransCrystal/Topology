package net.ptcrys.topo.gametest;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import java.util.Arrays;

/**
 * GameTest 专用的标量机器/配方副本。测试只依赖这里的固定数值,正式机器与正式配方
 * ({@code BuiltinTopoMachines} 等正式内容)可以随版本随意调平衡,不影响测试精度。
 *
 * <p>
 * 门控:{@code neoforge.enabledGameTestNamespaces} 系统属性(dev client/server 与 gameTestServer
 * 运行配置都会设置;发布环境没有,夹具不进正式游戏)。亦可用 {@code -Doi.gametest.fixtures=true}
 * 单独打开。注册分两段进引导:配方类型在 {@code TopoPluginEngine#bootstrapRecipe} 冻结前,机器在
 * {@code TopoPluginEngine#bootstrapMachine} 冻结前;两段都不得在 {@link #enabled()} 为 false 时调用。
 *
 * <p>
 * 固定数值(测试断言依赖,勿调):
 * <ul>
 * <li>发电机:煤 → 5 能量/t × 20t(共 100,恰好填满 100 容量的输出缓存);能量口仅 DOWN 暴露;</li>
 * <li>高级发生器:10 能量/t → 1 高级能量/t × 10t(10:1,整单消耗 100);</li>
 * <li>锅炉:煤 + 100mB 水 → 4 热量/t × 10t(共 40);</li>
 * <li>电锅炉:8 能量/t → 2 热量/t × 10t(整单消耗 80、产出 20)。</li>
 * </ul>
 */
public final class TopoScalarGameTestFixtures {

    public static final int GENERATOR_ENERGY_PER_TICK = 5;
    public static final int GENERATOR_DURATION = 20;
    public static final int GENERATOR_ENERGY_CAPACITY = 100;
    public static final int ADVANCED_ENERGY_IN_PER_TICK = 10;
    public static final int ADVANCED_OUT_PER_TICK = 1;
    public static final int ADVANCED_DURATION = 10;
    public static final int BOILER_HEAT_PER_TICK = 4;
    public static final int BOILER_DURATION = 10;
    public static final int BOILER_WATER_MB = 100;
    public static final int ELECTRIC_ENERGY_IN_PER_TICK = 8;
    public static final int ELECTRIC_HEAT_PER_TICK = 2;
    public static final int ELECTRIC_DURATION = 10;
    public static final int HISTORY_GATE_ENERGY = 10;

    private static TopoRecipeType<TopoRecipe> energyGeneratorType;
    private static TopoRecipeType<TopoRecipe> advancedGeneratorType;
    private static TopoRecipeType<TopoRecipe> boilerType;
    private static TopoRecipeType<TopoRecipe> electricBoilerType;
    private static TopoRecipeType<TopoRecipe> historyGateFallbackType;

    private static MachineDefinition energyGenerator;
    private static MachineDefinition advancedGenerator;
    private static MachineDefinition boiler;
    private static MachineDefinition electricBoiler;
    private static MachineDefinition historyGateFallback;

    private TopoScalarGameTestFixtures() {}

    /** GameTest 夹具是否启用;false 时严禁调用任何 init/getter。 */
    public static boolean enabled() {
        if (Boolean.getBoolean("oi.gametest.fixtures")) {
            return true;
        }
        String namespaces = System.getProperty("neoforge.enabledGameTestNamespaces", "");
        return Arrays.stream(namespaces.split(","))
                .map(String::trim)
                .anyMatch(Topology.MODID::equals);
    }

    /** 在 {@code TopoRecipeTypes.freeze()} 之前调用。 */
    public static void initRecipeTypes() {
        if (energyGeneratorType != null) {
            return;
        }
        RecipeDomainRegistration recipe = OfficialTopoPlugin.INSTANCE.recipe();
        energyGeneratorType = recipe.recipeType("gametest_energy_generator");
        advancedGeneratorType = recipe.recipeType("gametest_advanced_energy_generator");
        boilerType = recipe.recipeType("gametest_boiler");
        electricBoilerType = recipe.recipeType("gametest_electric_boiler");
        historyGateFallbackType = recipe.recipeType("gametest_history_gate_fallback");
    }

    /** 在配方类型冻结之后调用(配方列表本身不冻结,与材料处理器同窗口)。 */
    public static void initRecipes() {
        ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        FluidRecipeCapability fluids = BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
        ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
        ScalarRecipeCapability advancedEnergy = BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
        ScalarRecipeCapability heat = BuiltinTopoResourceIntegrations.HEAT.recipeCapability();

        energyGeneratorType().recipe("gametest_energy_from_coal")
                .input(items.in(Items.COAL, 1))
                .tickOutput(energy.out(GENERATOR_ENERGY_PER_TICK))
                .duration(GENERATOR_DURATION)
                .save();
        advancedGeneratorType().recipe("gametest_advanced_energy_conversion")
                .tickInput(energy.in(ADVANCED_ENERGY_IN_PER_TICK))
                .tickOutput(advancedEnergy.out(ADVANCED_OUT_PER_TICK))
                .duration(ADVANCED_DURATION)
                .save();
        boilerType().recipe("gametest_heat_from_coal")
                .input(items.in(Items.COAL, 1))
                .input(fluids.in(Fluids.WATER, BOILER_WATER_MB))
                .tickOutput(heat.out(BOILER_HEAT_PER_TICK))
                .duration(BOILER_DURATION)
                .save();
        electricBoilerType().recipe("gametest_heat_from_energy")
                .tickInput(energy.in(ELECTRIC_ENERGY_IN_PER_TICK))
                .tickOutput(heat.out(ELECTRIC_HEAT_PER_TICK))
                .duration(ELECTRIC_DURATION)
                .save();
        historyGateFallbackType().recipe("a_unblocked_coal_and_redstone")
                .input(items.in(Items.COAL, 1))
                .input(items.in(Items.REDSTONE, 1))
                .duration(20)
                .save();
        historyGateFallbackType().recipe("b_history_coal_with_tick_output")
                .input(items.in(Items.COAL, 1))
                .tickOutput(energy.out(HISTORY_GATE_ENERGY))
                .duration(1)
                .save();
    }

    /** 在 {@code Machines.freeze()} 之前调用。 */
    public static void initMachines() {
        if (energyGenerator != null) {
            return;
        }
        energyGenerator = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_energy_generator")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Energy Generator", "GameTest Energy Generator")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.input(ItemResourcePort.ITEM_INPUT_1, 1))
                .component(ScalarResourcePort.mount(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        GENERATOR_ENERGY_CAPACITY,
                        PortAccess.output(Direction.DOWN)))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, energyGeneratorType()))
                .build();
        advancedGenerator = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_advanced_energy_generator")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Advanced Energy Generator", "GameTest Advanced Energy Generator")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.input(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        1_000))
                .component(ScalarResourcePort.output(
                        ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.ADVANCED_ENERGY,
                        1_000))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, advancedGeneratorType()))
                .build();
        boiler = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_boiler")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Boiler", "GameTest Boiler")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.input(ItemResourcePort.ITEM_INPUT_1, 1))
                .component(FluidResourcePort.input(FluidResourcePort.FLUID_INPUT_1, 1, 1_000))
                .component(ScalarResourcePort.output(
                        ScalarResourcePort.HEAT_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.HEAT,
                        1_000))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, boilerType()))
                .build();
        electricBoiler = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_electric_boiler")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Electric Boiler", "GameTest Electric Boiler")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.input(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        1_000))
                .component(ScalarResourcePort.output(
                        ScalarResourcePort.HEAT_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.HEAT,
                        1_000))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, electricBoilerType()))
                .build();
        historyGateFallback = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_history_gate_fallback")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Recipe History Gate Fallback", "GameTest Recipe History Gate Fallback")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.input(ItemResourcePort.ITEM_INPUT_1, 2))
                .component(ScalarResourcePort.output(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        HISTORY_GATE_ENERGY))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, historyGateFallbackType()))
                .build();
    }

    public static TopoRecipeType<TopoRecipe> energyGeneratorType() {
        return requireInitialized(energyGeneratorType, "energy generator recipe type");
    }

    public static TopoRecipeType<TopoRecipe> advancedGeneratorType() {
        return requireInitialized(advancedGeneratorType, "advanced generator recipe type");
    }

    public static TopoRecipeType<TopoRecipe> boilerType() {
        return requireInitialized(boilerType, "boiler recipe type");
    }

    public static TopoRecipeType<TopoRecipe> electricBoilerType() {
        return requireInitialized(electricBoilerType, "electric boiler recipe type");
    }

    public static TopoRecipeType<TopoRecipe> historyGateFallbackType() {
        return requireInitialized(historyGateFallbackType, "recipe history gate fallback type");
    }

    public static MachineDefinition energyGenerator() {
        return requireInitialized(energyGenerator, "energy generator machine");
    }

    public static MachineDefinition advancedGenerator() {
        return requireInitialized(advancedGenerator, "advanced generator machine");
    }

    public static MachineDefinition boiler() {
        return requireInitialized(boiler, "boiler machine");
    }

    public static MachineDefinition electricBoiler() {
        return requireInitialized(electricBoiler, "electric boiler machine");
    }

    public static MachineDefinition historyGateFallback() {
        return requireInitialized(historyGateFallback, "recipe history gate fallback machine");
    }

    private static <T> T requireInitialized(T value, String what) {
        if (value == null) {
            throw new IllegalStateException(
                    "Scalar gametest fixture " + what + " is not initialized; fixtures are gated by " + "neoforge.enabledGameTestNamespaces / -Doi.gametest.fixtures=true");
        }
        return value;
    }
}
