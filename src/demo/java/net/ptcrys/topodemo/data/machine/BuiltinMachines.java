package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.machine.*;
import net.ptcrys.topo.api.machine.component.*;
import net.ptcrys.topo.api.machine.multiblock.MultiblockController;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.api.machine.common.ItemPort;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;

import net.minecraft.world.level.block.Blocks;

/** A standalone grinder, two role-bearing buses, a storage buffer and a bus-fed alloying multiblock. */
public final class BuiltinMachines {

    private static final MachineDomainRegistration MACHINE = TopoDemoPlugin.INSTANCE.machine();
    private static final ComponentKey<RecipeLogic> LOGIC = ComponentKey.of(MACHINE.id("recipe_logic"), RecipeLogic.class);
    public static final MachineDefinition GRINDER = MACHINE.machine("grinder").displayName("Grinder", "粉碎机")
            .block(OrientedActiveMachineBlock::new)
            .creativeTab(BuiltinCreativeTabs.KEY.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/stonecutter_side"))
            .component(ItemPort.mount(ItemPort.INPUT, 2, PortAccess.input()))
            .component(ItemPort.mount(ItemPort.OUTPUT, 2, PortAccess.output()))
            .component(RecipeLogic.mount(LOGIC, BuiltinRecipeTypes.GRINDING)).build();
    public static final MachineDefinition BUFFER = MACHINE.machine("buffer").displayName("Item Buffer", "物品缓存")
            .creativeTab(BuiltinCreativeTabs.KEY.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/barrel_side"))
            .component(ItemPort.mount(ItemPort.STORAGE, 9, PortAccess.storage())).build();
    public static final MachineDefinition INPUT_BUS = MACHINE.machine("input_bus").displayName("Item Input Bus", "物品输入总线")
            .creativeTab(BuiltinCreativeTabs.KEY.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/hopper_outside"))
            .component(ItemPort.mount(ItemPort.INPUT, 3, PortAccess.input()).role(BuiltinMachineTypes.INPUT)).build();
    public static final MachineDefinition OUTPUT_BUS = MACHINE.machine("output_bus").displayName("Item Output Bus", "物品输出总线")
            .creativeTab(BuiltinCreativeTabs.KEY.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/dropper_front"))
            .component(ItemPort.mount(ItemPort.OUTPUT, 3, PortAccess.output()).role(BuiltinMachineTypes.OUTPUT)).build();
    // Three-wide, two-high, one-deep. @ is the controller; input/output buses supply recipe IO.
    public static final Blueprint ALLOY_BLUEPRINT = Blueprint.of().aisle("I@O").aisle("CCC")
            .where('I', CellPredicates.role(BuiltinMachineTypes.INPUT))
            .where('O', CellPredicates.role(BuiltinMachineTypes.OUTPUT))
            .where('C', CellPredicates.block(Blocks.IRON_BLOCK)).build();
    public static final MachineDefinition ALLOYER = MACHINE.machine("alloyer").displayName("Alloy Mixer", "合金混合器")
            .creativeTab(BuiltinCreativeTabs.KEY.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/blast_furnace_front"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(ALLOY_BLUEPRINT))
            .component(RecipeLogic.mount(LOGIC, BuiltinRecipeTypes.ALLOYING)).build();

    private BuiltinMachines() {}

    public static void init() {}
}
