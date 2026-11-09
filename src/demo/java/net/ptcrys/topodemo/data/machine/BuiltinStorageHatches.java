package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.common.component.StoragePageUi;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.Ports;
import net.ptcrys.topodemo.data.machine.multiblock.BuiltinPartRoles;

import net.minecraft.core.Direction;

/** 旧版物品、流体和混合输入仓；端口容量与访问策略按原声明配置。 */
public final class BuiltinStorageHatches {

    private static final PortAccess INPUT = PortAccess.inputHatch();
    private static final PortAccess OUTPUT = PortAccess.output(Direction.DOWN).withPlayerConfigurableSides();

    public static final MachineDefinition ITEM_INPUT = TopoDemoPlugin.INSTANCE.machine().machine("item_input_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Item Input Hatch", "物品输入仓").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 27, INPUT).role(BuiltinPartRoles.ITEM_INPUT))
            .component(StoragePageUi.mount(ItemResourcePort.ITEM_INPUT_1)).build();

    public static final MachineDefinition ITEM_OUTPUT = TopoDemoPlugin.INSTANCE.machine().machine("item_output_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Item Output Hatch", "物品输出仓").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, 27, OUTPUT).role(BuiltinPartRoles.ITEM_OUTPUT))
            .component(StoragePageUi.mount(ItemResourcePort.ITEM_OUTPUT_1)).build();

    public static final MachineDefinition FLUID_INPUT = TopoDemoPlugin.INSTANCE.machine().machine("fluid_input_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Fluid Input Hatch", "流体输入仓").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 9, 64_000, INPUT).role(BuiltinPartRoles.FLUID_INPUT))
            .component(StoragePageUi.mount(FluidResourcePort.FLUID_INPUT_1)).build();

    public static final MachineDefinition FLUID_OUTPUT = TopoDemoPlugin.INSTANCE.machine().machine("fluid_output_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Fluid Output Hatch", "流体输出仓").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.fluid(FluidResourcePort.FLUID_OUTPUT_1, 9, 64_000, OUTPUT).role(BuiltinPartRoles.FLUID_OUTPUT))
            .component(StoragePageUi.mount(FluidResourcePort.FLUID_OUTPUT_1)).build();

    public static final MachineDefinition ITEM_FLUID_INPUT = TopoDemoPlugin.INSTANCE.machine().machine("item_fluid_input_hatch")
            .block(OrientedMachineBlock::new).blockEntity(MachineBlockEntity::new)
            .displayName("Item & Fluid Input Hatch", "物品流体输入仓").creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 27, INPUT).role(BuiltinPartRoles.ITEM_INPUT))
            .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 9, 64_000, INPUT).role(BuiltinPartRoles.FLUID_INPUT))
            .component(StoragePageUi.mount(ItemResourcePort.ITEM_INPUT_1, FluidResourcePort.FLUID_INPUT_1)).build();

    private BuiltinStorageHatches() {}

    public static void init() {}
}
