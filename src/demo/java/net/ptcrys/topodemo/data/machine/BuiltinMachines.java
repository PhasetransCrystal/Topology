package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.machine.*;
import net.ptcrys.topo.api.machine.component.*;
import net.ptcrys.topo.api.machine.multiblock.MultiblockController;
import net.ptcrys.topo.api.machine.multiblock.MultiblockModule;
import net.ptcrys.topo.api.machine.multiblock.MultiblockUi;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.api.machine.common.ItemPort;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.ContentLang;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;

import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** A standalone grinder, two role-bearing buses, a storage buffer and a bus-fed alloying multiblock. */
public final class BuiltinMachines {

    private static final MachineDomainRegistration MACHINE = TopoDemoPlugin.INSTANCE.machine();

    private static final ComponentKey<RecipeLogic> LOGIC = ComponentKey.of(MACHINE.id("recipe_logic"), RecipeLogic.class);
    private static final ComponentKey<RecipeUi> RECIPE_UI = ComponentKey.of(MACHINE.id("recipe_ui"), RecipeUi.class);
    private static final ComponentKey<MultiblockController> CONTROLLER = ComponentKey.of(MACHINE.id("multiblock_controller"), MultiblockController.class);
    private static final ComponentKey<MultiblockUi> STRUCTURE_UI = ComponentKey.of(MACHINE.id("structure_ui"), MultiblockUi.class);

    public static final MachineDefinition GRINDER = MACHINE.machine("grinder").displayName("Grinder", "粉碎机")
            .block(OrientedActiveMachineBlock::new)
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/stonecutter_side"))
            .component(ItemPort.mount(ItemPort.INPUT, 2, PortAccess.input()))
            .component(ItemPort.mount(ItemPort.OUTPUT, 2, PortAccess.output()))
            .component(RecipeLogic.mount(LOGIC, BuiltinRecipeTypes.GRINDING))
            .component(RecipeUi.mount(RECIPE_UI, LOGIC)).build();
    public static final MachineDefinition BUFFER = MACHINE.machine("buffer").displayName("Item Buffer", "物品缓存")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/barrel_side"))
            .component(ItemPort.mount(ItemPort.STORAGE, 9, PortAccess.storage())).build();
    public static final MachineDefinition INPUT_BUS = MACHINE.machine("input_bus").displayName("Item Input Bus", "物品输入总线")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/hopper_outside"))
            .component(ItemPort.mount(ItemPort.INPUT, 3, PortAccess.input()).role(BuiltinMachineTypes.INPUT)).build();
    public static final MachineDefinition OUTPUT_BUS = MACHINE.machine("output_bus").displayName("Item Output Bus", "物品输出总线")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/dropper_front"))
            .component(ItemPort.mount(ItemPort.OUTPUT, 3, PortAccess.output()).role(BuiltinMachineTypes.OUTPUT)).build();

    // Three-wide, two-high, one-deep. @ is the controller; input/output buses supply recipe IO.
    public static final Blueprint ALLOY_BLUEPRINT = Blueprint.of()
            .aisle("CCC", "I@O")
            .aisle("CCC", "CCC")
            .where('@', CellPredicates.controller())
            .where('I', CellPredicates.role(BuiltinMachineTypes.INPUT))
            .where('O', CellPredicates.role(BuiltinMachineTypes.OUTPUT))
            .where('C', CellPredicates.block(Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK)).build();

    // --- Multiblock structure test machines ------------------------------------------------
    // These intentionally carry no recipe logic: they exist to exercise repeat/module/predicate
    // combinations in-game. Each uses the standard formed controller block and structure UI.

    /** Trailing repeat plus a fixed cap: the cap shifts with the resolved count. */
    public static final Blueprint REPEATABLE_BLUEPRINT = Blueprint.of()
            .aisle("F@F", "FFF", "FFF")
            .beginRepeatable(1, 4)
            .aisle("OOO", "O#O", "OOO")
            .endRepeatable()
            .aisle("OOO", "OTO", "OOO")
            .where('@', CellPredicates.controller())
            .where('#', CellPredicates.air())
            .where('O', CellPredicates.anyOf(CellPredicates.block(Blocks.IRON_BLOCK), CellPredicates.role(BuiltinMachineTypes.OUTPUT)))
            .where('F', CellPredicates.anyOf(CellPredicates.block(Blocks.IRON_BLOCK), CellPredicates.role(BuiltinMachineTypes.INPUT)))
            .where('T', CellPredicates.block(Blocks.IRON_BLOCK))
            .build();

    public static final MachineDefinition REPEATABLE_TOWER = MACHINE.machine("repeatable_tower")
            .displayName("Repeatable Tower", "可重复塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, REPEATABLE_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** @12 / @112 / @1112: one repeatable layer and an optional empty cap layer. */
    public static final Blueprint LAYERED_BLUEPRINT = Blueprint.of()
            .aisle("@")
            .beginRepeatable(1, 4)
            .aisle("1")
            .endRepeatable()
            .beginRepeatable(0, 1)
            .aisle("2")
            .endRepeatable()
            .where('@', CellPredicates.controller())
            .where('1', CellPredicates.block(Blocks.IRON_BLOCK))
            .where('2', CellPredicates.block(Blocks.GOLD_BLOCK))
            .build();

    public static final MachineDefinition LAYERED_TOWER = MACHINE.machine("layered_tower")
            .displayName("Layered Tower", "分层可变塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, LAYERED_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** The blueprint frame rotates the canonical layer stack onto the local X axis. */
    public static final Blueprint AXIS_BLUEPRINT = Blueprint.of()
            .frame(Direction.EAST, Direction.SOUTH)
            .aisle("@")
            .beginRepeatable(1, 3)
            .aisle("I")
            .endRepeatable()
            .where('@', CellPredicates.controller())
            .where('I', CellPredicates.block(Blocks.IRON_BLOCK))
            .build();

    public static final MachineDefinition AXIS_TOWER = MACHINE.machine("axis_tower")
            .displayName("Axis Tower", "方向重复塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, AXIS_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** Two distinct layers repeat as one group, followed by a fixed cap. */
    public static final Blueprint GROUPED_BLUEPRINT = Blueprint.of()
            .aisle("@")
            .beginRepeatable(1, 3)
            .aisle("A")
            .aisle("B")
            .endRepeatable()
            .aisle("C")
            .where('@', CellPredicates.controller())
            .where('A', CellPredicates.block(Blocks.IRON_BLOCK))
            .where('B', CellPredicates.block(Blocks.COPPER_BLOCK))
            .where('C', CellPredicates.block(Blocks.GOLD_BLOCK))
            .build();

    public static final MachineDefinition GROUPED_TOWER = MACHINE.machine("grouped_tower")
            .displayName("Grouped Tower", "分组重复塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/copper_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, GROUPED_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** Interior repeat: a repeatable middle aisle shifts a fixed cap, forcing the live-view path. */
    public static final Blueprint VARIABLE_BLUEPRINT = Blueprint.of()
            .aisle("C@C")
            .beginRepeatable(1, 4)
            .aisle("CCC")
            .endRepeatable()
            .aisle("TTT")
            .where('@', CellPredicates.controller())
            .where('C', CellPredicates.block(Blocks.COPPER_BLOCK))
            .where('T', CellPredicates.block(Blocks.GOLD_BLOCK))
            .build();

    public static final MachineDefinition VARIABLE_TOWER = MACHINE.machine("variable_tower")
            .displayName("Variable Tower", "可变高度塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/copper_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, VARIABLE_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** Optional modules: module blueprints are independent, overlaid on the same controller anchor. */
    public static final Blueprint OPTIONAL_MODULE_CORE = Blueprint.of()
            .aisle("@")
            .where('@', CellPredicates.controller())
            .build();
    public static final Blueprint GOLD_MODULE = Blueprint.of()
            .aisle("@M")
            .where('@', CellPredicates.controller())
            .where('M', CellPredicates.block(Blocks.GOLD_BLOCK))
            .build();
    public static final Blueprint DIAMOND_MODULE = Blueprint.of()
            .aisle("N@")
            .where('@', CellPredicates.controller())
            .where('N', CellPredicates.block(Blocks.DIAMOND_BLOCK))
            .build();

    public static final MachineDefinition OPTIONAL_MODULE_MACHINE = MACHINE.machine("optional_module_machine")
            .displayName("Optional Module Machine", "可选模块机器")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/gold_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(
                    CONTROLLER,
                    OPTIONAL_MODULE_CORE,
                    MultiblockModule.of("gold", GOLD_MODULE),
                    MultiblockModule.of("diamond", DIAMOND_MODULE)))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** Diamond depends on gold; both are optional extensions of the same main blueprint. */
    public static final MachineDefinition DEPENDENT_MODULE_MACHINE = MACHINE.machine("dependent_module_machine")
            .displayName("Dependent Module Machine", "依赖模块机器")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/diamond_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(
                    CONTROLLER,
                    OPTIONAL_MODULE_CORE,
                    MultiblockModule.of("gold", GOLD_MODULE),
                    MultiblockModule.of("diamond", DIAMOND_MODULE, "gold")))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** Predicate combination: air, exact state, property, block tag, allOf/not. */
    public static final Blueprint PREDICATE_BLUEPRINT = Blueprint.of()
            .aisle("@ASLTP")
            .where('@', CellPredicates.controller())
            .where('A', CellPredicates.air())
            .where('S', CellPredicates.state(Blocks.STONE.defaultBlockState()))
            .where('L', CellPredicates.property(BlockStateProperties.LIT, false))
            .where('T', CellPredicates.blockTag(BlockTags.BASE_STONE_OVERWORLD))
            .where('P', CellPredicates.allOf(
                    CellPredicates.not(CellPredicates.air()),
                    CellPredicates.block(Blocks.STONE)))
            .build();

    public static final MachineDefinition PREDICATE_MACHINE = MACHINE.machine("predicate_machine")
            .displayName("Predicate Machine", "谓词测试机器")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/stone"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, PREDICATE_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    public static final MachineDefinition ALLOYER = MACHINE.machine("alloyer").displayName("Alloy Mixer", "合金混合器")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/blast_furnace_front"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, ALLOY_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .component(RecipeLogic.mount(LOGIC, BuiltinRecipeTypes.ALLOYING))
            .component(RecipeUi.mount(RECIPE_UI, LOGIC, RecipeUi.RecipePageGate.of(
                    traits -> traits.require(CONTROLLER)::formed,
                    ContentLang.STRUCTURE_REQUIRED.getComponent())))
            .build();

    private BuiltinMachines() {}

    public static void init() {}
}
