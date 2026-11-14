package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.component.RecipeUi;
import net.ptcrys.topo.api.machine.resource.AutomationIo;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.common.component.BoilerFuel;
import net.ptcrys.topodemo.data.machine.common.component.BurnTimeModifier;
import net.ptcrys.topodemo.data.machine.common.component.PerformanceRecipeModifiers;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.Ports;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 燃烧发电机和锅炉的等级注册。 */
public final class BuiltinFuelMachines {

    private static final int FLUID_CAPACITY = 16_000;
    private static final PortAccess INPUT = PortAccess.input().onAllSides()
            .withAutomationIo(AutomationIo.BOTH).withDefaultAutomationIo(AutomationIo.INSERT)
            .withAllowedRuntimeModes(AutomationIo.NONE, AutomationIo.INSERT, AutomationIo.BOTH)
            .withPlayerConfigurableSides();
    private static final PortAccess OUTPUT = PortAccess.output().onAllSides().withPlayerConfigurableSides();

    public static final MachineDefinition COMBUSTION_GENERATOR_T1 = combustionGenerator(MachineTier.T1);
    public static final MachineDefinition COMBUSTION_GENERATOR_T2 = combustionGenerator(MachineTier.T2);
    public static final MachineDefinition COMBUSTION_GENERATOR_T3 = combustionGenerator(MachineTier.T3);
    public static final MachineDefinition BOILER_T1 = boiler(MachineTier.T1);
    public static final MachineDefinition BOILER_T2 = boiler(MachineTier.T2);
    public static final MachineDefinition BOILER_T3 = boiler(MachineTier.T3);

    private BuiltinFuelMachines() {}

    public static void init() {}

    private static MachineDefinition combustionGenerator(MachineTier tier) {
        return TopoDemoPlugin.INSTANCE.machine().machine("combustion_generator_" + tier.pathSuffix())
                .block(OrientedActiveMachineBlock::new).blockEntity(MachineBlockEntity::new)
                .displayName("Combustion Generator" + tier.displaySuffixEn(), "燃烧发电机" + tier.displaySuffixCn())
                .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
                .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 1, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 1, FLUID_CAPACITY, INPUT))
                .component(Ports.scalar(ScalarResourcePort.ENERGY_OUTPUT_1, BuiltinTopoResourceIntegrations.ENERGY,
                        Math.multiplyExact(100_000, tier.ratedPowerMultiplier()), OUTPUT))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, BuiltinRecipeTypes.COMBUSTION_GENERATOR))
                .component(RecipeUi.mount(RecipeUi.RECIPE_UI_1, RecipeLogic.RECIPE_LOGIC_1))
                .component(PerformanceRecipeModifiers.EnergyGenerationTierModifier.of(tier))
                .build();
    }

    private static MachineDefinition boiler(MachineTier tier) {
        return TopoDemoPlugin.INSTANCE.machine().machine("boiler_" + tier.pathSuffix())
                .block(OrientedActiveMachineBlock::new).blockEntity(MachineBlockEntity::new)
                .displayName("Boiler" + tier.displaySuffixEn(), "锅炉" + tier.displaySuffixCn())
                .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
                .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
                .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 1, INPUT))
                .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 2, FLUID_CAPACITY, INPUT))
                .component(Ports.scalar(ScalarResourcePort.HEAT_OUTPUT_1, BuiltinTopoResourceIntegrations.HEAT,
                        Math.multiplyExact(100_000, tier.ratedPowerMultiplier()), OUTPUT))
                .component(Ports.scalar(ScalarResourcePort.BURN_TIME_INPUT, BuiltinTopoResourceIntegrations.BURN_TIME,
                        Integer.MAX_VALUE, PortAccess.input().withAutomationIo(AutomationIo.NONE)))
                .component(BoilerFuel.mount(tier))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, BuiltinRecipeTypes.BOILER))
                .component(RecipeUi.mount(RecipeUi.RECIPE_UI_1, RecipeLogic.RECIPE_LOGIC_1))
                .component(PerformanceRecipeModifiers.HeatGenerationTierModifier.of(tier))
                .component(BurnTimeModifier.of(tier))
                .build();
    }
}
