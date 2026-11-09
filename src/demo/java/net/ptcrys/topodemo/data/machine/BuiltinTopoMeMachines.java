package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.resource.AutomationIo;
import net.ptcrys.topo.api.machine.resource.PlayerAccess;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.integration.ae2.AeGridNode;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topodemo.data.machine.common.component.resource.Ports;
import net.ptcrys.topodemo.data.machine.multiblock.BuiltinPartRoles;
import net.ptcrys.topodemo.integration.ae2.AeFluidBufferPort;
import net.ptcrys.topodemo.integration.ae2.AeFluidInputSync;
import net.ptcrys.topodemo.integration.ae2.AeFluidOutputPush;
import net.ptcrys.topodemo.integration.ae2.AeItemBufferPort;
import net.ptcrys.topodemo.integration.ae2.AeItemInputSync;
import net.ptcrys.topodemo.integration.ae2.AeItemOutputPush;
import net.ptcrys.topodemo.integration.ae2.AePatternProvider;
import net.ptcrys.topodemo.integration.ae2.AeStockingFluidPort;
import net.ptcrys.topodemo.integration.ae2.AeStockingItemPort;
import net.ptcrys.topodemo.integration.ae2.ui.AeBufferPageUi;
import net.ptcrys.topodemo.integration.ae2.ui.AeConfigPageUi;
import net.ptcrys.topodemo.integration.ae2.ui.AePatternProviderUi;

/**
 * 从重构前实现迁入的 ME 部件；资源、端口及界面均由 Demo 实现，AE 泛型调度由框架提供。
 * Demo 使用现有方块渲染器，部件输入、独立样板池与输出缓冲行为保持旧实现。
 */
public final class BuiltinTopoMeMachines {

    /** Pattern input buffers receive content from AE pushes only: no automation capability. */
    private static final PortAccess PATTERN_BUFFER_POLICY = PortAccess.input()
            .withAutomationIo(AutomationIo.NONE)
            .withPlayerSlotAccess(PlayerAccess.VIEW_ONLY);

    /** Drawing caches are filled by the AE sync component; players watch, the network manages content. */
    private static final PortAccess DRAWING_CACHE_POLICY = PortAccess.input().withPlayerSlotAccess(PlayerAccess.VIEW_ONLY);

    public static final MachineDefinition ME_DRAWING_ITEM_INPUT_BUS = TopoDemoPlugin.INSTANCE.machine().machine("me_drawing_item_input_bus")
            .block(OrientedMachineBlock::new)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Drawing Item Input Bus", "ME抽取物品输入总线")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(AeGridNode.mount(1.0))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 9, DRAWING_CACHE_POLICY)
                    .role(BuiltinPartRoles.ITEM_INPUT))
            .component(AeItemInputSync.mount(9, ItemResourcePort.ITEM_INPUT_1))
            .component(AeConfigPageUi.mount(AeItemInputSync.AE_ITEM_INPUT_SYNC))
            .build();

    public static final MachineDefinition ME_DRAWING_FLUID_INPUT_HATCH = TopoDemoPlugin.INSTANCE.machine().machine("me_drawing_fluid_input_hatch")
            .block(OrientedMachineBlock::new)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Drawing Fluid Input Hatch", "ME抽取流体输入仓")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(AeGridNode.mount(1.0))
            .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 9, 64_000, DRAWING_CACHE_POLICY)
                    .role(BuiltinPartRoles.FLUID_INPUT))
            .component(AeFluidInputSync.mount(9, FluidResourcePort.FLUID_INPUT_1))
            .component(AeConfigPageUi.mount(AeFluidInputSync.AE_FLUID_INPUT_SYNC))
            .build();

    public static final MachineDefinition ME_DIRECT_ITEM_INPUT_BUS = TopoDemoPlugin.INSTANCE.machine().machine("me_direct_item_input_bus")
            .block(OrientedMachineBlock::new)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Direct Item Input Bus", "ME直通物品输入总线")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(AeGridNode.mount(0.5))
            .component(AeStockingItemPort.mount(9).role(BuiltinPartRoles.ITEM_INPUT))
            .component(AeConfigPageUi.mount(AeStockingItemPort.AE_STOCKING_ITEM_PORT))
            .build();

    public static final MachineDefinition ME_DIRECT_FLUID_INPUT_HATCH = TopoDemoPlugin.INSTANCE.machine().machine("me_direct_fluid_input_hatch")
            .block(OrientedMachineBlock::new)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Direct Fluid Input Hatch", "ME直通流体输入仓")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(AeGridNode.mount(0.5))
            .component(AeStockingFluidPort.mount(9).role(BuiltinPartRoles.FLUID_INPUT))
            .component(AeConfigPageUi.mount(AeStockingFluidPort.AE_STOCKING_FLUID_PORT))
            .build();

    public static final MachineDefinition ME_PATTERN_PROVIDER = TopoDemoPlugin.INSTANCE.machine().machine("me_pattern_provider")
            .block(OrientedMachineBlock::new)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Pattern Hatch", "ME样板仓")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(AeGridNode.mount(1.0))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 9, PATTERN_BUFFER_POLICY)
                    .role(BuiltinPartRoles.ITEM_INPUT))
            .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 4, 64_000, PATTERN_BUFFER_POLICY)
                    .role(BuiltinPartRoles.FLUID_INPUT))
            .component(AePatternProvider.mount(
                    36, ItemResourcePort.ITEM_INPUT_1, FluidResourcePort.FLUID_INPUT_1))
            .component(AePatternProviderUi.mount())
            .build();

    /**
     * ME export hatch: recipe outputs land in sparse item/fluid buffers and are pushed into the AE
     * network. Registry id {@code me_export_hatch} (formerly {@code me_pushing_output_assembly}).
     */
    public static final MachineDefinition ME_EXPORT_HATCH = TopoDemoPlugin.INSTANCE.machine().machine("me_export_hatch")
            .block(OrientedMachineBlock::new)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Export Hatch", "ME输出仓")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .component(AeGridNode.mount(1.0))
            .component(AeItemBufferPort.mount(36).role(BuiltinPartRoles.ITEM_OUTPUT))
            .component(AeFluidBufferPort.mount(9).role(BuiltinPartRoles.FLUID_OUTPUT))
            .component(AeItemOutputPush.mount())
            .component(AeFluidOutputPush.mount())
            .component(AeBufferPageUi.mount())
            .build();

    private BuiltinTopoMeMachines() {}

    public static void init() {}
}
