package net.ptcrys.topo.datav2.machine;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.apiv2.machine.component.RecipeUi;
import net.ptcrys.topo.apiv2.machine.resource.AutomationIo;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.common.component.PerformanceRecipeModifiers;
import net.ptcrys.topo.datav2.machine.common.component.PreviewRecipeModifierComponent;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.Ports;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.material.BuiltinOIProcessDies;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.helper.ResourceFilterHelper;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Builtin single-block machines from {@code docs/design/production_line.md} / machine construction. */
public final class BuiltinOIMachines {

    private static final int ITEM_SLOTS = 4;
    private static final int FLUID_TANKS = 4;
    private static final int FLUID_CAPACITY = 16_000;
    private static final int BASE_SCALAR_CAPACITY = 100_000;

    private static final PortAccess INPUT = PortAccess.input()
            .onAllSides()
            .withAutomationIo(AutomationIo.BOTH)
            .withDefaultAutomationIo(AutomationIo.INSERT)
            .withAllowedRuntimeModes(AutomationIo.NONE, AutomationIo.INSERT, AutomationIo.BOTH)
            .withPlayerConfigurableSides();
    private static final PortAccess OUTPUT = PortAccess.output()
            .onAllSides()
            .withPlayerConfigurableSides();

    // ---- T1 native ----

    public static final MachineDefinition COMBUSTION_GENERATOR_T1 = combustionGenerator(MachineTier.T1);
    public static final MachineDefinition COMBUSTION_GENERATOR_T2 = combustionGenerator(MachineTier.T2);
    public static final MachineDefinition COMBUSTION_GENERATOR_T3 = combustionGenerator(MachineTier.T3);

    public static final MachineDefinition BOILER_T1 = boiler(MachineTier.T1);
    public static final MachineDefinition BOILER_T2 = boiler(MachineTier.T2);
    public static final MachineDefinition BOILER_T3 = boiler(MachineTier.T3);

    public static final MachineDefinition RESISTIVE_HEATER_T1 = resistiveHeater(MachineTier.T1);
    public static final MachineDefinition RESISTIVE_HEATER_T2 = resistiveHeater(MachineTier.T2);
    public static final MachineDefinition RESISTIVE_HEATER_T3 = resistiveHeater(MachineTier.T3);

    public static final MachineDefinition COMPONENT_PROCESSOR_T1 = componentProcessor(MachineTier.T1);
    public static final MachineDefinition COMPONENT_PROCESSOR_T2 = componentProcessor(MachineTier.T2);
    public static final MachineDefinition COMPONENT_PROCESSOR_T3 = componentProcessor(MachineTier.T3);

    public static final MachineDefinition WIRE_MILL_T1 = wireMill(MachineTier.T1);
    public static final MachineDefinition WIRE_MILL_T2 = wireMill(MachineTier.T2);
    public static final MachineDefinition WIRE_MILL_T3 = wireMill(MachineTier.T3);

    public static final MachineDefinition AIR_COLLECTOR_T1 = airCollector(MachineTier.T1);
    public static final MachineDefinition AIR_COLLECTOR_T2 = airCollector(MachineTier.T2);
    public static final MachineDefinition AIR_COLLECTOR_T3 = airCollector(MachineTier.T3);

    public static final MachineDefinition MACERATOR_T1 = macerator(MachineTier.T1);
    public static final MachineDefinition MACERATOR_T2 = macerator(MachineTier.T2);
    public static final MachineDefinition MACERATOR_T3 = macerator(MachineTier.T3);

    public static final MachineDefinition FINE_GRINDER_T1 = fineGrinder(MachineTier.T1);
    public static final MachineDefinition FINE_GRINDER_T2 = fineGrinder(MachineTier.T2);
    public static final MachineDefinition FINE_GRINDER_T3 = fineGrinder(MachineTier.T3);

    public static final MachineDefinition ORE_WASHER_T1 = oreWasher(MachineTier.T1);
    public static final MachineDefinition ORE_WASHER_T2 = oreWasher(MachineTier.T2);
    public static final MachineDefinition ORE_WASHER_T3 = oreWasher(MachineTier.T3);

    public static final MachineDefinition ELECTRIC_FURNACE_T1 = electricFurnace(MachineTier.T1);
    public static final MachineDefinition ELECTRIC_FURNACE_T2 = electricFurnace(MachineTier.T2);
    public static final MachineDefinition ELECTRIC_FURNACE_T3 = electricFurnace(MachineTier.T3);

    // ---- T2 native ----

    public static final MachineDefinition ENERGY_COMPRESSOR_T2 = energyCompressor(MachineTier.T2);
    public static final MachineDefinition ENERGY_COMPRESSOR_T3 = energyCompressor(MachineTier.T3);

    public static final MachineDefinition INDUSTRIAL_COMBUSTION_FURNACE_T2 = industrialCombustionFurnace(MachineTier.T2);
    public static final MachineDefinition INDUSTRIAL_COMBUSTION_FURNACE_T3 = industrialCombustionFurnace(MachineTier.T3);

    public static final MachineDefinition CHEMICAL_REACTOR_T2 = chemicalReactor(MachineTier.T2);
    public static final MachineDefinition CHEMICAL_REACTOR_T3 = chemicalReactor(MachineTier.T3);

    public static final MachineDefinition MIXING_TANK_T2 = mixingTank(MachineTier.T2);
    public static final MachineDefinition MIXING_TANK_T3 = mixingTank(MachineTier.T3);

    public static final MachineDefinition ELECTROLYZER_T2 = electrolyzer(MachineTier.T2);
    public static final MachineDefinition ELECTROLYZER_T3 = electrolyzer(MachineTier.T3);

    public static final MachineDefinition EVAPORATOR_T2 = evaporator(MachineTier.T2);
    public static final MachineDefinition EVAPORATOR_T3 = evaporator(MachineTier.T3);

    // ---- T3 native ----

    public static final MachineDefinition HIGH_PRESSURE_CRYSTALLIZER_T3 = highPressureCrystallizer(MachineTier.T3);

    private BuiltinOIMachines() {}

    public static void init() {}

    private static MachineDefinition combustionGenerator(MachineTier tier) {
        return base(
                "combustion_generator",
                "Combustion Generator",
                "燃烧发电机",
                "combustion_generator",
                tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 1, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 1, FLUID_CAPACITY, INPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        OUTPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.COMBUSTION_GENERATOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyGenerationTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition boiler(MachineTier tier) {
        return base("boiler", "Boiler", "锅炉", "boiler", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 1, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 2, FLUID_CAPACITY, INPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.HEAT_OUTPUT_1,
                        BuiltinOIResourceIntegrations.HEAT,
                        scalarCapacity(tier),
                        OUTPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.BOILER))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.HeatGenerationTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition resistiveHeater(MachineTier tier) {
        return base("resistive_heater", "Resistive Heater", "电阻加热器", "electric_boiler", tier)
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.HEAT_OUTPUT_1,
                        BuiltinOIResourceIntegrations.HEAT,
                        scalarCapacity(tier),
                        OUTPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.RESISTIVE_HEATER))
                .component(recipeUi())
                // ENERGY in owns duration; HEAT out uses consumer power curve with 产热 UI (not 耗热).
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .component(PerformanceRecipeModifiers.HeatTierModifier.outputAmounts(tier))
                .build();
    }

    private static MachineDefinition componentProcessor(MachineTier tier) {
        return base(
                "component_processor",
                "Component Processor",
                "部件加工机",
                "forming_press",
                tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(
                        ItemResourcePort.ITEM_DIE_1,
                        1,
                        PortAccess.dieSlot(),
                        ResourceFilterHelper.itemTag(BuiltinOIProcessDies.DIES)))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.COMPONENT_PROCESSOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition wireMill(MachineTier tier) {
        return base("wire_mill", "Wire Mill", "线材轧机", "bender", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(
                        ItemResourcePort.ITEM_DIE_1,
                        1,
                        PortAccess.dieSlot(),
                        ResourceFilterHelper.itemTag(BuiltinOIProcessDies.DIES)))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.WIRE_MILL))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition energyCompressor(MachineTier tier) {
        return base(
                "energy_compressor",
                "Energy Compressor",
                "能量压缩机",
                "energy_compressor",
                tier)
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1,
                        BuiltinOIResourceIntegrations.ADVANCED_ENERGY,
                        scalarCapacity(tier),
                        OUTPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.ENERGY_COMPRESSOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyConversionTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition airCollector(MachineTier tier) {
        return base("air_collector", "Air Collector", "空气收集器", "air_collector", tier)
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, 1, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.AIR_COLLECTOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition macerator(MachineTier tier) {
        return base("macerator", "Macerator", "粉碎机", "macerator", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.MACERATOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                // UI preview only: multi-modifier list spacing (identity modify; remove later).
                .component(PreviewRecipeModifierComponent.parallel())
                .component(PreviewRecipeModifierComponent.overclock())
                .component(PreviewRecipeModifierComponent.catalyst())
                .build();
    }

    private static MachineDefinition fineGrinder(MachineTier tier) {
        return base("fine_grinder", "Fine Grinder", "精细研磨机", "cutting_mill", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.FINE_GRINDER))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition oreWasher(MachineTier tier) {
        return base("ore_washer", "Ore Washer", "洗矿机", "ore_washer", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 2, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, 1, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.ORE_WASHER))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition electricFurnace(MachineTier tier) {
        return base("electric_furnace", "Electric Furnace", "电炉", "electric_furnace", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, 1, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                // 熔炉导入(SMELTING)与高炉导入(BLASTING)拆成两个配方类型，电炉同时挂载。
                .component(recipeLogic(
                        BuiltinOIRecipeTypes.ELECTRIC_FURNACE,
                        BuiltinOIRecipeTypes.BLAST_FURNACE))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition industrialCombustionFurnace(MachineTier tier) {
        return base(
                "industrial_combustion_furnace",
                "Industrial Combustion Furnace",
                "工业燃烧炉",
                "industrial_combustion_furnace",
                tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 2, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, 2, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.HEAT_INPUT_1,
                        BuiltinOIResourceIntegrations.HEAT,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.INDUSTRIAL_COMBUSTION_FURNACE))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.HeatTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition chemicalReactor(MachineTier tier) {
        // Dual scalar: EnergyTierModifier owns duration; HeatTierModifier.amounts only scales HEAT.
        return base("chemical_reactor", "Chemical Reactor", "化学反应器", "chemical_reactor", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, FLUID_TANKS, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, FLUID_TANKS, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.HEAT_INPUT_1,
                        BuiltinOIResourceIntegrations.HEAT,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.CHEMICAL_REACTOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .component(PerformanceRecipeModifiers.HeatTierModifier.amounts(tier))
                .build();
    }

    private static MachineDefinition mixingTank(MachineTier tier) {
        return base("mixing_tank", "Mixing Tank", "混合槽", "mixing_tank", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, FLUID_TANKS, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, FLUID_TANKS, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.MIXING_TANK))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.EnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition electrolyzer(MachineTier tier) {
        return base("electrolyzer", "Electrolyzer", "电解槽", "electrolyzer", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, FLUID_TANKS, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, FLUID_TANKS, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ADVANCED_ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ADVANCED_ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.ELECTROLYZER))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.AdvancedEnergyTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition evaporator(MachineTier tier) {
        return base("evaporator", "Evaporator", "蒸发器", "evaporator", tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 2, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, 2, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.HEAT_INPUT_1,
                        BuiltinOIResourceIntegrations.HEAT,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.EVAPORATOR))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.HeatTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition highPressureCrystallizer(MachineTier tier) {
        return base(
                "high_pressure_crystallizer",
                "High Pressure Crystallizer",
                "高压晶化炉",
                "high_pressure_crystallizer",
                tier)
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
                .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, FLUID_TANKS, FLUID_CAPACITY, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, FLUID_TANKS, FLUID_CAPACITY, OUTPUT))
                .component(Ports.scalar(
                        ScalarResourcePort.ADVANCED_ENERGY_INPUT_1,
                        BuiltinOIResourceIntegrations.ADVANCED_ENERGY,
                        scalarCapacity(tier),
                        INPUT))
                .component(recipeLogic(BuiltinOIRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER))
                .component(recipeUi())
                .component(PerformanceRecipeModifiers.AdvancedEnergyTierModifier.of(tier))
                .build();
    }

    /** Shared single-block shell. Registry path is always {@code basePath_tN}. */
    private static Machines.Builder base(
                                         String basePath,
                                         String displayNameEn,
                                         String displayNameCn,
                                         String texturePath,
                                         MachineTier tier) {
        Objects.requireNonNull(tier, "tier");
        String path = basePath + "_" + tier.pathSuffix();
        return OfficialOIPlugin.INSTANCE.machine().machine(path)
                .block(OrientedActiveMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName(
                        displayNameEn + tier.displaySuffixEn(),
                        displayNameCn + tier.displaySuffixCn())
                .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
                .render(BuiltinOIMachineRenderTypes.SHELL_OVERLAY.overlay(
                        tier.shellMaterial(),
                        texture(texturePath, "overlay_front"),
                        texture(texturePath, "overlay_front_active")));
    }

    private static int scalarCapacity(MachineTier tier) {
        return Math.multiplyExact(BASE_SCALAR_CAPACITY, tier.ratedPowerMultiplier());
    }

    private static Identifier texture(String machine, String name) {
        return OfficialOIPlugin.INSTANCE.machine().id("block/machines/" + machine + "/" + name);
    }

    private static ComponentMount<RecipeLogic> recipeLogic(OIRecipeType<?>... recipeTypes) {
        return RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, recipeTypes);
    }

    private static ComponentMount<RecipeUi> recipeUi() {
        return RecipeUi.mount(RecipeUi.RECIPE_UI_1, RecipeLogic.RECIPE_LOGIC_1);
    }
}
