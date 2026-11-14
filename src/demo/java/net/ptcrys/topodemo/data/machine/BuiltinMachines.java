package net.ptcrys.topodemo.data.machine;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.machine.*;
import net.ptcrys.topo.api.machine.component.*;
import net.ptcrys.topo.api.machine.multiblock.MultiblockController;
import net.ptcrys.topo.api.machine.multiblock.MultiblockModule;
import net.ptcrys.topo.api.machine.multiblock.MultiblockUi;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.api.machine.multiblock.pattern.PropertyRules;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topodemo.api.lang.ContentLang;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemPort;
import net.ptcrys.topodemo.data.machine.multiblock.BuiltinPartRoles;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;

import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 单方块设备、输入输出部件，以及最小尺寸不低于 4×4×6 的多方块验收机器。 */
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
            .component(ItemPort.mount(ItemPort.INPUT, 3, PortAccess.input()).role(BuiltinPartRoles.ITEM_INPUT)).build();
    public static final MachineDefinition OUTPUT_BUS = MACHINE.machine("output_bus").displayName("Item Output Bus", "物品输出总线")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey()).render(BuiltinMachineTypes.CUBE.cube("block/dropper_front"))
            .component(ItemPort.mount(ItemPort.OUTPUT, 3, PortAccess.output()).role(BuiltinPartRoles.ITEM_OUTPUT)).build();

    // 4×4×6 合金炉；输入输出总线各一个，内腔保持空气。
    public static final Blueprint ALLOY_BLUEPRINT = Blueprint.of()
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .aisle("C@IC", "C##C", "C##C", "COCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .where('I', CellPredicates.role(BuiltinPartRoles.ITEM_INPUT))
            .where('O', CellPredicates.role(BuiltinPartRoles.ITEM_OUTPUT))
            .where('C', CellPredicates.block(Blocks.IRON_BLOCK, Blocks.COPPER_BLOCK))
            .count(BuiltinPartRoles.ITEM_INPUT, 1, 1)
            .count(BuiltinPartRoles.ITEM_OUTPUT, 1, 1)
            .build();

    // 结构验收机器使用标准控制器和结构页，可在游戏内检查预览、重复段及模块。

    /** 5×5×6～12 组合塔：下重复段、双层上重复组、移动顶盖、可选横梁和方向规则。 */
    public static final Blueprint REPEATABLE_BLUEPRINT = Blueprint.of()
            .aisle("SSSSS", "SSSSS", "SSSSS", "SSSSS", "SSSSS")
            .beginRepeatable(1, 2)
            .aisle("CCCCC", "C###C", "C###C", "C###C", "CCCCC")
            .endRepeatable()
            .aisle("C@ICC", "C###C", "C###C", "C###C", "CCOCC")
            .beginRepeatable(1, 3)
            .aisle("CCCCC", "C###C", "C#D#C", "C###C", "CCCCC")
            .aisle("GGGGG", "G###G", "G###G", "G###G", "GGGGG")
            .endRepeatable()
            .aisle("TTTTT", "T###T", "T###T", "T###T", "TTTTT")
            .beginRepeatable(0, 1)
            .aisle("  L  ", "  L  ", "  L  ", "  L  ", "  L  ")
            .endRepeatable()
            .where('S', Blocks.STONE_BRICKS)
            .where('C', Blocks.COPPER_BLOCK)
            .where('G', Blocks.GLASS)
            .where('T', Blocks.GOLD_BLOCK)
            .where('I', CellPredicates.role(BuiltinPartRoles.ITEM_INPUT))
            .where('O', CellPredicates.role(BuiltinPartRoles.ITEM_OUTPUT))
            .where('D', Blocks.DISPENSER,
                    Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST),
                    PropertyRules.direction(BlockStateProperties.FACING))
            .where('L', Blocks.OAK_LOG,
                    Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X),
                    PropertyRules.axis(BlockStateProperties.AXIS))
            .count(BuiltinPartRoles.ITEM_INPUT, 1, 1)
            .count(BuiltinPartRoles.ITEM_OUTPUT, 1, 1)
            .build();

    public static final MachineDefinition REPEATABLE_TOWER = MACHINE.machine("repeatable_tower")
            .displayName("Repeatable Tower", "可重复塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, REPEATABLE_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** 最小 4×4×6；实心底座上方为 2～4 层铁壳，最上方可选一层金壳。 */
    public static final Blueprint LAYERED_BLUEPRINT = Blueprint.of()
            .aisle("F@FF", "FFFF", "FFFF", "FFFF")
            .aisle("FFFF", "FFFF", "FFFF", "FFFF")
            .aisle("FFFF", "FFFF", "FFFF", "FFFF")
            .aisle("FFFF", "FFFF", "FFFF", "FFFF")
            .beginRepeatable(2, 4)
            .aisle("IIII", "I##I", "I##I", "IIII")
            .endRepeatable()
            .beginRepeatable(0, 1)
            .aisle("TTTT", "T##T", "T##T", "TTTT")
            .endRepeatable()
            .where('F', Blocks.STONE_BRICKS)
            .where('I', Blocks.IRON_BLOCK)
            .where('T', Blocks.GOLD_BLOCK)
            .build();

    public static final MachineDefinition LAYERED_TOWER = MACHINE.machine("layered_tower")
            .displayName("Layered Tower", "分层可变塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, LAYERED_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** 最小 6×4×4；frame 将堆叠轴转到控制器局部 X 轴。 */
    public static final Blueprint AXIS_BLUEPRINT = Blueprint.of()
            .frame(Direction.EAST, Direction.SOUTH)
            .aisle("F@FF", "FFFF", "FFFF", "FFFF")
            .aisle("FFFF", "FFFF", "FFFF", "FFFF")
            .beginRepeatable(4, 8)
            .aisle("IIII", "I##I", "I##I", "IIII")
            .endRepeatable()
            .where('F', Blocks.STONE_BRICKS)
            .where('I', Blocks.IRON_BLOCK)
            .build();

    public static final MachineDefinition AXIS_TOWER = MACHINE.machine("axis_tower")
            .displayName("Axis Tower", "方向重复塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/iron_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, AXIS_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** 最小 4×4×7；铁壳和铜壳两层为一个重复组，后接随组数移动的实心金顶。 */
    public static final Blueprint GROUPED_BLUEPRINT = Blueprint.of()
            .aisle("F@FF", "FFFF", "FFFF", "FFFF")
            .aisle("FFFF", "FFFF", "FFFF", "FFFF")
            .beginRepeatable(2, 4)
            .aisle("AAAA", "A##A", "A##A", "AAAA")
            .aisle("BBBB", "B##B", "B##B", "BBBB")
            .endRepeatable()
            .aisle("TTTT", "TTTT", "TTTT", "TTTT")
            .where('F', Blocks.STONE_BRICKS)
            .where('A', Blocks.IRON_BLOCK)
            .where('B', Blocks.COPPER_BLOCK)
            .where('T', Blocks.GOLD_BLOCK)
            .build();

    public static final MachineDefinition GROUPED_TOWER = MACHINE.machine("grouped_tower")
            .displayName("Grouped Tower", "分组重复塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/copper_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, GROUPED_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** 最小 4×4×6；铜壳中段可变长，实心金顶随之移动，走实时视图检测。 */
    public static final Blueprint VARIABLE_BLUEPRINT = Blueprint.of()
            .aisle("C@CC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .beginRepeatable(3, 7)
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .endRepeatable()
            .aisle("TTTT", "TTTT", "TTTT", "TTTT")
            .where('C', Blocks.COPPER_BLOCK)
            .where('T', Blocks.GOLD_BLOCK)
            .build();

    public static final MachineDefinition VARIABLE_TOWER = MACHINE.machine("variable_tower")
            .displayName("Variable Tower", "可变高度塔")
            .creativeTab(BuiltinCreativeTabs.MACHINES.getKey())
            .render(BuiltinMachineTypes.CUBE.cube("block/copper_block"))
            .block(FormedMachineBlock::new)
            .component(MultiblockController.mount(CONTROLLER, VARIABLE_BLUEPRINT))
            .component(MultiblockUi.mount(STRUCTURE_UI, CONTROLLER))
            .build();

    /** 主体和左右舱体均为 4×4×6；模块只共享 @ 锚点，用空格跳过主体。 */
    public static final Blueprint OPTIONAL_MODULE_CORE = Blueprint.of()
            .aisle("C@CC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .where('C', Blocks.IRON_BLOCK)
            .build();
    public static final Blueprint GOLD_MODULE = Blueprint.of()
            .aisle(" @    MMMM", "      MMMM", "      MMMM", "      MMMM")
            .aisle("      MMMM", "      M##M", "      M##M", "      MMMM")
            .aisle("      MMMM", "      M##M", "      M##M", "      MMMM")
            .aisle("      MMMM", "      M##M", "      M##M", "      MMMM")
            .aisle("      MMMM", "      M##M", "      M##M", "      MMMM")
            .aisle("      MMMM", "      MMMM", "      MMMM", "      MMMM")
            .where('M', Blocks.GOLD_BLOCK)
            .build();
    public static final Blueprint DIAMOND_MODULE = Blueprint.of()
            .aisle("NNNN    @ ", "NNNN      ", "NNNN      ", "NNNN      ")
            .aisle("NNNN      ", "N##N      ", "N##N      ", "NNNN      ")
            .aisle("NNNN      ", "N##N      ", "N##N      ", "NNNN      ")
            .aisle("NNNN      ", "N##N      ", "N##N      ", "NNNN      ")
            .aisle("NNNN      ", "N##N      ", "N##N      ", "NNNN      ")
            .aisle("NNNN      ", "NNNN      ", "NNNN      ", "NNNN      ")
            .where('N', Blocks.DIAMOND_BLOCK)
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

    /** 左侧钻石舱依赖右侧金舱；两者都完整时才启用钻石模块。 */
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

    /** 4×4×6 谓词验收舱：空气、精确状态、未点燃熔炉、方块标签及组合谓词。 */
    public static final Blueprint PREDICATE_BLUEPRINT = Blueprint.of()
            .aisle("C@CC", "CCCC", "CCCC", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "CSTC", "C##C", "CCCC")
            .aisle("CCCC", "CLPC", "C##C", "CCCC")
            .aisle("CCCC", "C##C", "C##C", "CCCC")
            .aisle("CCCC", "CCCC", "CCCC", "CCCC")
            .where('C', Blocks.STONE_BRICKS)
            .where('S', CellPredicates.state(Blocks.STONE.defaultBlockState()))
            .where('L', CellPredicates.allOf(CellPredicates.block(Blocks.FURNACE), CellPredicates.property(BlockStateProperties.LIT, false)))
            .where('T', CellPredicates.blockTag(BlockTags.BASE_STONE_OVERWORLD))
            .where('P', CellPredicates.allOf(CellPredicates.not(CellPredicates.air()), CellPredicates.block(Blocks.ANDESITE)))
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
