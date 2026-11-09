package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeUi;
import net.ptcrys.topo.api.machine.resource.AutomationIo;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.common.component.PerformanceRecipeModifiers;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.Ports;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 硫酸生产线使用的工业燃烧炉、反应器和混合槽。 */
public final class BuiltinChemicalMachines {

    private static final PortAccess INPUT = PortAccess.input().onAllSides()
            .withAutomationIo(AutomationIo.BOTH).withDefaultAutomationIo(AutomationIo.INSERT)
            .withAllowedRuntimeModes(AutomationIo.NONE, AutomationIo.INSERT, AutomationIo.BOTH)
            .withPlayerConfigurableSides();
    private static final PortAccess OUTPUT = PortAccess.output().onAllSides().withPlayerConfigurableSides();

    public static final MachineDefinition INDUSTRIAL_COMBUSTION_FURNACE_T2 = furnace(MachineTier.T2);
    public static final MachineDefinition INDUSTRIAL_COMBUSTION_FURNACE_T3 = furnace(MachineTier.T3);
    public static final MachineDefinition CHEMICAL_REACTOR_T2 = reactor(MachineTier.T2);
    public static final MachineDefinition CHEMICAL_REACTOR_T3 = reactor(MachineTier.T3);
    public static final MachineDefinition MIXING_TANK_T2 = mixingTank(MachineTier.T2);
    public static final MachineDefinition MIXING_TANK_T3 = mixingTank(MachineTier.T3);
    public static final MachineDefinition ELECTROLYZER_T2 = electrolyzer(MachineTier.T2);
    public static final MachineDefinition ELECTROLYZER_T3 = electrolyzer(MachineTier.T3);
    public static final MachineDefinition EVAPORATOR_T2 = evaporator(MachineTier.T2);
    public static final MachineDefinition EVAPORATOR_T3 = evaporator(MachineTier.T3);
    public static final MachineDefinition HIGH_PRESSURE_CRYSTALLIZER_T3 = crystallizer(MachineTier.T3);

    private BuiltinChemicalMachines() {}

    public static void init() {}

    private static MachineDefinition furnace(MachineTier tier) {
        return base("industrial_combustion_furnace", "Industrial Combustion Furnace", "工业燃烧炉",
                tier, 2, BuiltinRecipeTypes.INDUSTRIAL_COMBUSTION_FURNACE)
                .component(Ports.scalar(ScalarResourcePort.HEAT_INPUT_1, BuiltinTopoResourceIntegrations.HEAT, capacity(tier), INPUT))
                .component(PerformanceRecipeModifiers.HeatTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition reactor(MachineTier tier) {
        return base("chemical_reactor", "Chemical Reactor", "化学反应器", tier, 4, BuiltinRecipeTypes.CHEMICAL_REACTOR)
                .component(Ports.scalar(ScalarResourcePort.ENERGY_INPUT_1, BuiltinTopoResourceIntegrations.ENERGY, capacity(tier), INPUT))
                .component(Ports.scalar(ScalarResourcePort.HEAT_INPUT_1, BuiltinTopoResourceIntegrations.HEAT, capacity(tier), INPUT))
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .component(PerformanceRecipeModifiers.HeatTierModifier.amounts(tier))
                .build();
    }

    private static MachineDefinition mixingTank(MachineTier tier) {
        return base("mixing_tank", "Mixing Tank", "混合槽", tier, 4, BuiltinRecipeTypes.MIXING_TANK)
                .component(Ports.scalar(ScalarResourcePort.ENERGY_INPUT_1, BuiltinTopoResourceIntegrations.ENERGY, capacity(tier), INPUT))
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition electrolyzer(MachineTier tier) {
        return base("electrolyzer", "Electrolyzer", "电解槽", tier, 4, BuiltinRecipeTypes.ELECTROLYZER)
                .component(Ports.scalar(ScalarResourcePort.ADVANCED_ENERGY_INPUT_1,
                        BuiltinTopoResourceIntegrations.ADVANCED_ENERGY, capacity(tier), INPUT))
                .component(PerformanceRecipeModifiers.AdvancedEnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition evaporator(MachineTier tier) {
        return base("evaporator", "Evaporator", "蒸发器", tier, 2, BuiltinRecipeTypes.EVAPORATOR)
                .component(Ports.scalar(ScalarResourcePort.HEAT_INPUT_1, BuiltinTopoResourceIntegrations.HEAT, capacity(tier), INPUT))
                .component(PerformanceRecipeModifiers.HeatTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition crystallizer(MachineTier tier) {
        return base("high_pressure_crystallizer", "High Pressure Crystallizer", "高压晶化炉", tier, 4,
                BuiltinRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER)
                .component(Ports.scalar(ScalarResourcePort.ADVANCED_ENERGY_INPUT_1,
                        BuiltinTopoResourceIntegrations.ADVANCED_ENERGY, capacity(tier), INPUT))
                .component(PerformanceRecipeModifiers.AdvancedEnergyTierModifier.of(tier))
                .build();
    }

    private static Machines.Builder base(String path, String en, String cn, MachineTier tier, int tanks,
                                         TopoRecipeType<TopoRecipe> recipeType) {
        return TopoDemoPlugin.INSTANCE.machine().machine(path + "_" + tier.pathSuffix())
                .block(OrientedActiveMachineBlock::new).blockEntity(MachineBlockEntity::new)
                .displayName(en + tier.displaySuffixEn(), cn + tier.displaySuffixCn())
                .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
                .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 4, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, 4, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, tanks, 16_000, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, tanks, 16_000, OUTPUT))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, recipeType))
                .component(RecipeUi.mount(RecipeUi.RECIPE_UI_1, RecipeLogic.RECIPE_LOGIC_1));
    }

    private static int capacity(MachineTier tier) {
        return Math.multiplyExact(100_000, tier.ratedPowerMultiplier());
    }
}
