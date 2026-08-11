package net.ptcrys.topo.data.machine;

import net.ptcrys.topo.api.machine.ConnectedTextureOrientedMachineBlock;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.machine.common.component.StoragePageUi;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.Ports;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.machine.multiblock.BuiltinTopoCasingBlocks;
import net.ptcrys.topo.data.machine.multiblock.BuiltinTopoPartRoles;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Builtin multiblock <b>part</b> machines — hatches and buses that attach structural
 * {@link BuiltinTopoPartRoles} to a multiblock. Controllers live in {@link BuiltinTopoControllerMachines}.
 *
 * <p>
 * Visuals: standalone the hatch wears a <b>T2</b> shell with pipe + resource icon on the placement
 * front; formed into a structure it renders the dense-structure-casing CTM shell, keeping the front overlays.
 */
public final class BuiltinTopoPartMachines {

    private static final Identifier HATCH_PIPE_IN = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/pipe_in");
    private static final Identifier HATCH_PIPE_OUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/pipe_out");
    private static final Identifier HATCH_ITEM_INPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/item_input");
    private static final Identifier HATCH_ITEM_OUTPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/item_output");
    private static final Identifier HATCH_FLUID_INPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/fluid_input");
    private static final Identifier HATCH_FLUID_OUTPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/fluid_output");
    private static final Identifier HATCH_DUAL_INPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/dual_hatch");
    private static final Identifier HATCH_ENERGY_INPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/energy_input");
    private static final Identifier HATCH_ENERGY_OUTPUT = OfficialTopoPlugin.INSTANCE.machine().id("block/machines/hatch/energy_output");

    private static final int ITEM_SLOTS = 27;
    private static final int FLUID_TANKS = 9;
    private static final int FLUID_CAPACITY_MB = 64_000;
    /** Energy hatch buffer sized for T3-rated multiblock draw (base × T3 power multiplier). */
    private static final int ENERGY_HATCH_CAPACITY = Math.multiplyExact(100_000, MachineTier.T3.ratedPowerMultiplier());

    /**
     * 输入舱：左侧 side-IO 可配 禁止/输入/输出；默认仅正面输入。
     *
     * @see PortAccess#inputHatch()
     */
    private static final PortAccess INPUT_HATCH = PortAccess.inputHatch();

    /** 输出舱：默认仅底面输出，玩家可开/关各面输出。 */
    private static final PortAccess OUTPUT_HATCH = PortAccess.output(Direction.DOWN).withPlayerConfigurableSides();

    public static final MachineDefinition ITEM_INPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("item_input_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Item Input Hatch", "物品输入仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_IN,
                    HATCH_ITEM_INPUT))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT_HATCH)
                    .role(BuiltinTopoPartRoles.ITEM_INPUT))
            .component(StoragePageUi.mount(ItemResourcePort.ITEM_INPUT_1))
            .build();

    public static final MachineDefinition ITEM_OUTPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("item_output_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Item Output Hatch", "物品输出仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_OUT,
                    HATCH_ITEM_OUTPUT))
            .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT_HATCH)
                    .role(BuiltinTopoPartRoles.ITEM_OUTPUT))
            .component(StoragePageUi.mount(ItemResourcePort.ITEM_OUTPUT_1))
            .build();

    public static final MachineDefinition FLUID_INPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("fluid_input_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Fluid Input Hatch", "流体输入仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_IN,
                    HATCH_FLUID_INPUT))
            .component(Ports.fluid(
                    FluidResourcePort.FLUID_INPUT_1, FLUID_TANKS, FLUID_CAPACITY_MB, INPUT_HATCH)
                    .role(BuiltinTopoPartRoles.FLUID_INPUT))
            .component(StoragePageUi.mount(FluidResourcePort.FLUID_INPUT_1))
            .build();

    public static final MachineDefinition FLUID_OUTPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("fluid_output_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Fluid Output Hatch", "流体输出仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_OUT,
                    HATCH_FLUID_OUTPUT))
            .component(Ports.fluid(
                    FluidResourcePort.FLUID_OUTPUT_1, FLUID_TANKS, FLUID_CAPACITY_MB, OUTPUT_HATCH)
                    .role(BuiltinTopoPartRoles.FLUID_OUTPUT))
            .component(StoragePageUi.mount(FluidResourcePort.FLUID_OUTPUT_1))
            .build();

    /**
     * Combined item + fluid input hatch: one block carries two structural part roles
     * ({@code ITEM_INPUT} + {@code FLUID_INPUT}).
     */
    public static final MachineDefinition ITEM_FLUID_INPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("item_fluid_input_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Item & Fluid Input Hatch", "物品流体输入仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_IN,
                    HATCH_DUAL_INPUT))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT_HATCH)
                    .role(BuiltinTopoPartRoles.ITEM_INPUT))
            .component(Ports.fluid(
                    FluidResourcePort.FLUID_INPUT_1, FLUID_TANKS, FLUID_CAPACITY_MB, INPUT_HATCH)
                    .role(BuiltinTopoPartRoles.FLUID_INPUT))
            .component(StoragePageUi.mount(ItemResourcePort.ITEM_INPUT_1, FluidResourcePort.FLUID_INPUT_1))
            .build();

    public static final MachineDefinition ENERGY_INPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("energy_input_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Energy Input Hatch", "能量输入仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_IN,
                    HATCH_ENERGY_INPUT))
            .component(Ports.scalar(
                    ScalarResourcePort.ENERGY_INPUT_1,
                    BuiltinTopoResourceIntegrations.ENERGY,
                    ENERGY_HATCH_CAPACITY,
                    INPUT_HATCH)
                    .role(BuiltinTopoPartRoles.ENERGY_INPUT))
            .build();

    public static final MachineDefinition ENERGY_OUTPUT_HATCH = OfficialTopoPlugin.INSTANCE.machine().machine("energy_output_hatch")
            .block((properties, id) -> new ConnectedTextureOrientedMachineBlock(
                    properties, id, BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Energy Output Hatch", "能量输出仓")
            .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
            .render(BuiltinTopoMachineRenderTypes.CTM_HATCH.hatch(
                    BuiltinTopoCasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    MachineTier.T2.shellMaterial(),
                    HATCH_PIPE_OUT,
                    HATCH_ENERGY_OUTPUT))
            .component(Ports.scalar(
                    ScalarResourcePort.ENERGY_OUTPUT_1,
                    BuiltinTopoResourceIntegrations.ENERGY,
                    ENERGY_HATCH_CAPACITY,
                    PortAccess.output().withPlayerConfigurableSides())
                    .role(BuiltinTopoPartRoles.ENERGY_OUTPUT))
            .build();

    private BuiltinTopoPartMachines() {}

    public static void init() {}
}
