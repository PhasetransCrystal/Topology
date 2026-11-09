package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.common.component.resource.Ports;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topodemo.data.machine.multiblock.BuiltinPartRoles;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 旧版能量输入、输出部件；容量和访问策略保持原声明。 */
public final class BuiltinEnergyHatches {

    private static final int CAPACITY = 1_600_000;

    public static final MachineDefinition INPUT = TopoDemoPlugin.INSTANCE.machine().machine("energy_input_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Energy Input Hatch", "能量输入仓")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.scalar(ScalarResourcePort.ENERGY_INPUT_1, BuiltinTopoResourceIntegrations.ENERGY,
                    CAPACITY, PortAccess.inputHatch()).role(BuiltinPartRoles.ENERGY_INPUT))
            .build();

    public static final MachineDefinition OUTPUT = TopoDemoPlugin.INSTANCE.machine().machine("energy_output_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Energy Output Hatch", "能量输出仓")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.scalar(ScalarResourcePort.ENERGY_OUTPUT_1, BuiltinTopoResourceIntegrations.ENERGY,
                    CAPACITY, PortAccess.output().withPlayerConfigurableSides()).role(BuiltinPartRoles.ENERGY_OUTPUT))
            .build();

    private BuiltinEnergyHatches() {}

    public static void init() {}
}
