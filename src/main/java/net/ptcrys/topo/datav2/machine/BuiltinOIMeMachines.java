package net.ptcrys.topo.datav2.machine;

import net.ptcrys.topo.apiv2.machine.ConnectedTextureOrientedMachineBlock;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderUse;
import net.ptcrys.topo.apiv2.machine.resource.AutomationIo;
import net.ptcrys.topo.apiv2.machine.resource.PlayerAccess;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.Ports;
import net.ptcrys.topo.datav2.machine.common.render.CtmHatchRenderType;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOICasingBlocks;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOIPartRoles;
import net.ptcrys.topo.integration.ae2.AeFluidBufferPort;
import net.ptcrys.topo.integration.ae2.AeFluidInputSync;
import net.ptcrys.topo.integration.ae2.AeFluidOutputPush;
import net.ptcrys.topo.integration.ae2.AeGridNode;
import net.ptcrys.topo.integration.ae2.AeItemBufferPort;
import net.ptcrys.topo.integration.ae2.AeItemInputSync;
import net.ptcrys.topo.integration.ae2.AeItemOutputPush;
import net.ptcrys.topo.integration.ae2.AePatternProvider;
import net.ptcrys.topo.integration.ae2.AeStockingFluidPort;
import net.ptcrys.topo.integration.ae2.AeStockingItemPort;
import net.ptcrys.topo.integration.ae2.ui.AeBufferPageUi;
import net.ptcrys.topo.integration.ae2.ui.AeConfigPageUi;
import net.ptcrys.topo.integration.ae2.ui.AePatternProviderUi;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Builtin ME (AE2-integrated) multiblock part machines. Every machine mounts one {@link AeGridNode};
 * {@code AeIntegration} exposes that component as the in-world grid node host.
 *
 * <p>
 * Visuals: standalone <b>T3</b> shell + ME interface front overlay (same icon for every ME part,
 * matching the historical GTOdyssey ME hatch look); formed structure uses the grinder-casing CTM
 * shell with front overlays retained.
 */
public final class BuiltinOIMeMachines {

    private static final Identifier HATCH_PIPE_IN = OfficialOIPlugin.INSTANCE.machine().id("block/machines/hatch/pipe_in");
    private static final Identifier HATCH_PIPE_OUT = OfficialOIPlugin.INSTANCE.machine().id("block/machines/hatch/pipe_out");
    /**
     * ME interface front overlay shared by every ME hatch/bus. Texture is AE2's
     * {@code ae2:textures/part/interface.png} (byte-identical to the historical
     * {@code me_interface_front} asset).
     */
    private static final Identifier ME_INTERFACE_FRONT = OfficialOIPlugin.INSTANCE.machine().id("block/machines/hatch/me_interface_front");

    /** Pattern input buffers receive content from AE pushes only: no automation capability. */
    private static final PortAccess PATTERN_BUFFER_POLICY = PortAccess.input()
            .withAutomationIo(AutomationIo.NONE)
            .withPlayerSlotAccess(PlayerAccess.VIEW_ONLY);

    /** Drawing caches are filled by the AE sync component; players watch, the network manages content. */
    private static final PortAccess DRAWING_CACHE_POLICY = PortAccess.input().withPlayerSlotAccess(PlayerAccess.VIEW_ONLY);

    public static final MachineDefinition ME_DRAWING_ITEM_INPUT_BUS = OfficialOIPlugin.INSTANCE.machine().machine("me_drawing_item_input_bus")
            .block(BuiltinOIMeMachines::ctmHatchBlock)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Drawing Item Input Bus", "ME抽取物品输入总线")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(meHatchRender(HATCH_PIPE_IN, ME_INTERFACE_FRONT))
            .component(AeGridNode.mount(1.0))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 9, DRAWING_CACHE_POLICY)
                    .role(BuiltinOIPartRoles.ITEM_INPUT))
            .component(AeItemInputSync.mount(9, ItemResourcePort.ITEM_INPUT_1))
            .component(AeConfigPageUi.mount(AeItemInputSync.AE_ITEM_INPUT_SYNC))
            .build();

    public static final MachineDefinition ME_DRAWING_FLUID_INPUT_HATCH = OfficialOIPlugin.INSTANCE.machine().machine("me_drawing_fluid_input_hatch")
            .block(BuiltinOIMeMachines::ctmHatchBlock)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Drawing Fluid Input Hatch", "ME抽取流体输入仓")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(meHatchRender(HATCH_PIPE_IN, ME_INTERFACE_FRONT))
            .component(AeGridNode.mount(1.0))
            .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 9, 64_000, DRAWING_CACHE_POLICY)
                    .role(BuiltinOIPartRoles.FLUID_INPUT))
            .component(AeFluidInputSync.mount(9, FluidResourcePort.FLUID_INPUT_1))
            .component(AeConfigPageUi.mount(AeFluidInputSync.AE_FLUID_INPUT_SYNC))
            .build();

    public static final MachineDefinition ME_DIRECT_ITEM_INPUT_BUS = OfficialOIPlugin.INSTANCE.machine().machine("me_direct_item_input_bus")
            .block(BuiltinOIMeMachines::ctmHatchBlock)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Direct Item Input Bus", "ME直通物品输入总线")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(meHatchRender(HATCH_PIPE_IN, ME_INTERFACE_FRONT))
            .component(AeGridNode.mount(0.5))
            .component(AeStockingItemPort.mount(9).role(BuiltinOIPartRoles.ITEM_INPUT))
            .component(AeConfigPageUi.mount(AeStockingItemPort.AE_STOCKING_ITEM_PORT))
            .build();

    public static final MachineDefinition ME_DIRECT_FLUID_INPUT_HATCH = OfficialOIPlugin.INSTANCE.machine().machine("me_direct_fluid_input_hatch")
            .block(BuiltinOIMeMachines::ctmHatchBlock)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Direct Fluid Input Hatch", "ME直通流体输入仓")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(meHatchRender(HATCH_PIPE_IN, ME_INTERFACE_FRONT))
            .component(AeGridNode.mount(0.5))
            .component(AeStockingFluidPort.mount(9).role(BuiltinOIPartRoles.FLUID_INPUT))
            .component(AeConfigPageUi.mount(AeStockingFluidPort.AE_STOCKING_FLUID_PORT))
            .build();

    public static final MachineDefinition ME_PATTERN_PROVIDER = OfficialOIPlugin.INSTANCE.machine().machine("me_pattern_provider")
            .block(BuiltinOIMeMachines::ctmHatchBlock)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Pattern Hatch", "ME样板仓")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(meHatchRender(HATCH_PIPE_IN, ME_INTERFACE_FRONT))
            .component(AeGridNode.mount(1.0))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, 9, PATTERN_BUFFER_POLICY)
                    .role(BuiltinOIPartRoles.ITEM_INPUT))
            .component(Ports.fluid(FluidResourcePort.FLUID_INPUT_1, 4, 64_000, PATTERN_BUFFER_POLICY)
                    .role(BuiltinOIPartRoles.FLUID_INPUT))
            .component(AePatternProvider.mount(
                    36, ItemResourcePort.ITEM_INPUT_1, FluidResourcePort.FLUID_INPUT_1))
            .component(AePatternProviderUi.mount())
            .build();

    /**
     * ME export hatch: recipe outputs land in sparse item/fluid buffers and are pushed into the AE
     * network. Registry id {@code me_export_hatch} (formerly {@code me_pushing_output_assembly}).
     */
    public static final MachineDefinition ME_EXPORT_HATCH = OfficialOIPlugin.INSTANCE.machine().machine("me_export_hatch")
            .block(BuiltinOIMeMachines::ctmHatchBlock)
            .blockEntity(MachineBlockEntity::new)
            .displayName("ME Export Hatch", "ME输出仓")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(meHatchRender(HATCH_PIPE_OUT, ME_INTERFACE_FRONT))
            .component(AeGridNode.mount(1.0))
            .component(AeItemBufferPort.mount(36).role(BuiltinOIPartRoles.ITEM_OUTPUT))
            .component(AeFluidBufferPort.mount(9).role(BuiltinOIPartRoles.FLUID_OUTPUT))
            .component(AeItemOutputPush.mount())
            .component(AeFluidOutputPush.mount())
            .component(AeBufferPageUi.mount())
            .build();

    private static ConnectedTextureOrientedMachineBlock ctmHatchBlock(
                                                                      BlockBehaviour.Properties properties, Identifier id) {
        return new ConnectedTextureOrientedMachineBlock(
                properties, id, BuiltinOICasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family());
    }

    private static MachineBlockRenderUse<CtmHatchRenderType.Data> meHatchRender(Identifier pipe, Identifier icon) {
        return BuiltinOIMachineRenderTypes.CTM_HATCH.hatch(
                BuiltinOICasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                MachineTier.T3.shellMaterial(),
                pipe,
                icon);
    }

    private BuiltinOIMeMachines() {}

    public static void init() {}
}
