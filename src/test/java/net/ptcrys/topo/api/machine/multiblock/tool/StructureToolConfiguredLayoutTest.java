package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 最小 4×4×6，控制器前后都有重复段，上段为双层组，另有可选横梁；世界样本按独立施工坐标生成。 */
class StructureToolConfiguredLayoutTest {

    private static final BlockPos ANCHOR = new BlockPos(15, 64, -17);
    private static final String[] SOLID = { "SSSS", "SSSS", "SSSS", "SSSS" };
    private static final String[] SHELL = { "CCCC", "C##C", "C##C", "CCCC" };
    private static final String[] CONTROL = { "C@CC", "C##C", "C##C", "CCCC" };
    private static final String[] PROCESS = { "CDCC", "C##C", "C##C", "CCCC" };
    private static final String[] GLASS = { "GGGG", "G##G", "G##G", "GGGG" };
    private static final String[] ROOF = { "TTTT", "TTTT", "TTTT", "TTTT" };
    private static final String[] BEAM = { " L  ", " L  ", " L  ", " L  " };

    private static Blueprint blueprint(boolean sideways) {
        var builder = Blueprint.of();
        if (sideways) builder.frame(Direction.EAST, Direction.SOUTH);
        return builder.aisle(SOLID).beginRepeatable(1, 2).aisle(SHELL).endRepeatable().aisle(CONTROL)
                .beginRepeatable(1, 3).aisle(PROCESS).aisle(GLASS).endRepeatable().aisle(ROOF)
                .beginRepeatable(0, 1).aisle(BEAM).endRepeatable()
                .where('S', Blocks.STONE_BRICKS).where('C', Blocks.COPPER_BLOCK).where('G', Blocks.GLASS).where('T', Blocks.GOLD_BLOCK)
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), PropertyRules.direction(BlockStateProperties.FACING))
                .where('L', Blocks.OAK_LOG, Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), PropertyRules.axis(BlockStateProperties.AXIS))
                .build();
    }

    @Test
    void incompleteWorldDoesNotShortenConfiguredRepeatsOrMoveItsRoof() {
        Blueprint blueprint = blueprint(false);
        World world = new World();
        var counts = new RepeatResolution(new int[] { 2, 3, 1 });
        var settings = new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY);
        settings.repeats(counts);
        // 自动识别在空世界中只能得到最小布局，不能用它覆盖施工配置。
        assertEquals(blueprint.minRepeats(), StructureEngine.recognize(world, blueprint, Orientations.IDENTITY).repeats());
        var result = StructureEngine.recognize(world, blueprint, settings.orientation(), settings.repeats());
        assertEquals(counts, result.repeats());
        assertFalse(result.formed());
        assertEquals(180, result.matchedCount() + result.missingCount());
        var plan = StructureToolPlan.create(world, blueprint, settings, block -> 1000);
        assertTrue(plan.buildable(), plan.problems().toString());
        assertEquals(179, plan.targets().size());
        assertEquals(Blocks.GOLD_BLOCK.defaultBlockState(), target(plan, ANCHOR.offset(-1, 7, 0)).state());
        assertEquals(Blocks.STONE_BRICKS.defaultBlockState(), target(plan, ANCHOR.offset(-1, -3, 0)).state());
        assertEquals(Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, Direction.Axis.X), target(plan, ANCHOR.offset(0, 8, 0)).state());
    }

    @Test
    void mirrorSwitchRemainsExplicitEvenWhenTheOtherLayoutIsAlreadyFormed() {
        Blueprint blueprint = blueprint(false);
        World world = complete(false, false, 1, 1, 0);
        var settings = new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY);
        assertTrue(StructureEngine.recognize(world, blueprint, settings.orientation(), settings.repeats()).formed());
        settings.mirror(true);
        assertTrue(settings.orientation().mirror());
        assertFalse(StructureEngine.recognize(world, blueprint, settings.orientation(), settings.repeats()).formed());
        settings.mirror(false);
        assertFalse(settings.orientation().mirror());
        assertTrue(StructureEngine.recognize(world, blueprint, settings.orientation(), settings.repeats()).formed());
    }

    @Test
    void configuredLayoutMatchesIndependentWorldWithMirrorFrameAndEveryRepeatCombination() {
        for (boolean sideways : List.of(false, true)) for (boolean mirror : List.of(false, true)) {
            Blueprint blueprint = blueprint(sideways);
            for (int lower = 1; lower <= 2; lower++) for (int groups = 1; groups <= 3; groups++) for (int optional = 0; optional <= 1; optional++) {
                var settings = new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY);
                settings.mirror(mirror);
                settings.repeats(new RepeatResolution(new int[] { lower, groups, optional }));
                var result = StructureEngine.recognize(complete(sideways, mirror, lower, groups, optional), blueprint, settings.orientation(), settings.repeats());
                assertTrue(result.formed(), sideways + "/" + mirror + "/" + settings.repeats());
                assertEquals(16 * (lower + groups * 2 + 3) + optional * 4, result.matchedCount());
            }
        }
    }

    @Test
    void repeatedStructureCanBeBuiltInMultipleBatchesBeforeItForms() {
        Blueprint blueprint = blueprint(false);
        World world = new World();
        var settings = new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY);
        settings.mirror(true);
        settings.repeats(new RepeatResolution(new int[] { 2, 3, 1 }));
        var plan = StructureToolPlan.create(world, blueprint, settings, block -> 1000);
        var batch = StructureToolPlacement.selectBatch(plan.targets(), List.of(new StructureToolPlacement.Supply(0, Items.STONE_BRICKS, 8)), false);
        assertEquals(8, batch.targets().size());
        apply(world, batch);
        assertFalse(StructureEngine.recognize(world, blueprint, settings.orientation(), settings.repeats()).formed());
        int batches = 1;
        while (true) {
            plan = StructureToolPlan.create(world, blueprint, settings, block -> 1000);
            assertTrue(plan.buildable(), plan.problems().toString());
            if (plan.materials().isEmpty()) break;
            var entry = plan.materials().entrySet().iterator().next();
            batch = StructureToolPlacement.selectBatch(plan.targets(), List.of(new StructureToolPlacement.Supply(0, entry.getKey().asItem(), Math.min(64, entry.getValue()))), false);
            assertFalse(batch.targets().isEmpty());
            assertEquals(batch.targets().size(), batch.allocation().values().stream().mapToInt(Integer::intValue).sum());
            apply(world, batch);
            assertTrue(++batches < 30);
        }
        var result = StructureEngine.recognize(world, blueprint, settings.orientation());
        assertTrue(result.formed());
        assertEquals(settings.repeats(), result.repeats());
    }

    private static StructureToolPlan.Target target(StructureToolPlan plan, BlockPos pos) {
        return plan.targets().stream().filter(target -> target.pos().equals(pos)).findFirst().orElseThrow();
    }

    private static void apply(World world, StructureToolPlacement.Batch batch) {
        for (var target : batch.targets()) {
            assertTrue(world.blockState(target.pos()).isAir());
            world.states.put(target.pos(), target.state());
        }
    }

    private static World complete(boolean sideways, boolean mirror, int lower, int groups, int optional) {
        World world = new World();
        plane(world, sideways, mirror, -lower - 1, SOLID);
        for (int y = -lower; y < 0; y++) plane(world, sideways, mirror, y, SHELL);
        plane(world, sideways, mirror, 0, CONTROL);
        for (int group = 0; group < groups; group++) {
            plane(world, sideways, mirror, group * 2 + 1, PROCESS);
            plane(world, sideways, mirror, group * 2 + 2, GLASS);
        }
        plane(world, sideways, mirror, groups * 2 + 1, ROOF);
        if (optional == 1) plane(world, sideways, mirror, groups * 2 + 2, BEAM);
        return world;
    }

    private static void plane(World world, boolean sideways, boolean mirror, int y, String[] rows) {
        for (int z = 0; z < 4; z++) for (int col = 0; col < 4; col++) {
            char symbol = rows[z].charAt(col);
            if (symbol == '@' || symbol == ' ') continue;
            int x = col - 1;
            // frame(EAST,SOUTH): (x,y,z) -> (y,-x,z)，随后在控制器局部 X 镜像。
            int worldX = (sideways ? y : x) * (mirror ? -1 : 1), worldY = sideways ? -x : y;
            BlockState state = switch (symbol) {
                case 'S' -> Blocks.STONE_BRICKS.defaultBlockState();
                case 'C' -> Blocks.COPPER_BLOCK.defaultBlockState();
                case 'G' -> Blocks.GLASS.defaultBlockState();
                case 'T' -> Blocks.GOLD_BLOCK.defaultBlockState();
                case 'D' -> Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, sideways ? Direction.DOWN : mirror ? Direction.WEST : Direction.EAST);
                case 'L' -> Blocks.OAK_LOG.defaultBlockState().setValue(BlockStateProperties.AXIS, sideways ? Direction.Axis.Y : Direction.Axis.X);
                default -> Blocks.AIR.defaultBlockState();
            };
            world.states.put(ANCHOR.offset(worldX, worldY, z), state);
        }
    }

    private static final class World implements StructureView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();

        public BlockPos controllerPos() {
            return ANCHOR;
        }

        public boolean isLoaded(BlockPos pos) {
            return true;
        }

        public BlockState blockState(BlockPos pos) {
            return states.getOrDefault(pos, pos.equals(ANCHOR) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        public Set<PartRole> roles(BlockPos pos) {
            return Set.of();
        }
    }
}
