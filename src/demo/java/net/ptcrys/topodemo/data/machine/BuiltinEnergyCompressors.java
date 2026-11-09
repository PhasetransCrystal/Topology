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
import net.ptcrys.topodemo.data.machine.common.component.PerformanceRecipeModifiers;
import net.ptcrys.topodemo.data.machine.common.component.resource.Ports;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 能量压缩机的 T2 和 T3 注册。 */
public final class BuiltinEnergyCompressors {

    private static final PortAccess INPUT = PortAccess.input().onAllSides()
            .withAutomationIo(AutomationIo.BOTH).withDefaultAutomationIo(AutomationIo.INSERT)
            .withAllowedRuntimeModes(AutomationIo.NONE, AutomationIo.INSERT, AutomationIo.BOTH)
            .withPlayerConfigurableSides();
    private static final PortAccess OUTPUT = PortAccess.output().onAllSides().withPlayerConfigurableSides();

    public static final MachineDefinition T2 = create(MachineTier.T2);
    public static final MachineDefinition T3 = create(MachineTier.T3);

    private BuiltinEnergyCompressors() {}

    public static void init() {}

    private static MachineDefinition create(MachineTier tier) {
        int capacity = Math.multiplyExact(100_000, tier.ratedPowerMultiplier());
        return TopoDemoPlugin.INSTANCE.machine().machine("energy_compressor_" + tier.pathSuffix())
                .block(OrientedActiveMachineBlock::new).blockEntity(MachineBlockEntity::new)
                .displayName("Energy Compressor" + tier.displaySuffixEn(), "能量压缩机" + tier.displaySuffixCn())
                .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
                .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
                .component(Ports.scalar(ScalarResourcePort.ENERGY_INPUT_1, BuiltinTopoResourceIntegrations.ENERGY, capacity, INPUT))
                .component(Ports.scalar(ScalarResourcePort.ADVANCED_ENERGY_OUTPUT_1, BuiltinTopoResourceIntegrations.ADVANCED_ENERGY, capacity, OUTPUT))
                .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, BuiltinRecipeTypes.ENERGY_COMPRESSOR))
                .component(RecipeUi.mount(RecipeUi.RECIPE_UI_1, RecipeLogic.RECIPE_LOGIC_1))
                .component(PerformanceRecipeModifiers.EnergyConversionTierModifier.of(tier))
                .build();
    }
}
