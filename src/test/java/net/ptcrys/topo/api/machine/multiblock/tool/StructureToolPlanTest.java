package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.*;

import net.minecraft.core.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 4×4×6 中空舱体，独立提供世界数据，规划阶段不创建 ItemStack、不改世界。 */
class StructureToolPlanTest {

    private static final BlockPos ANCHOR = new BlockPos(15, 65, -17);

    private static Blueprint chamber(boolean frame) {
        var builder = Blueprint.of();
        if (frame) builder.frame(Direction.EAST, Direction.SOUTH);
        return builder.aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("@CCC", "C##C", "C##C", "CCCC")
                .aisle("CCCC", "C##C", "C##C", "CCCC")
                .aisle("CCCC", "C##C", "C##C", "CCCC")
                .aisle("CCCC", "C##C", "C##C", "CCCC")
                .aisle("CDCC", "CCCC", "CCCC", "CCCC")
                .where('C', CellPredicates.anyOf(CellPredicates.block(Blocks.STONE), CellPredicates.block(Blocks.DIRT)))
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST), PropertyRules.direction(BlockStateProperties.FACING))
                .build();
    }

    @Test
    void plansAll24RotationsAndMirrorsInBothDeclarationFrames() {
        for (boolean frame : new boolean[] { false, true }) for (Orientation rotation : Orientations.rotations()) for (boolean mirror : new boolean[] { false, true }) {
            Blueprint blueprint = chamber(frame);
            Orientation orientation = mirror ? rotation.mirrored() : rotation;
            World world = new World();
            var settings = new StructureToolOptions.Settings(blueprint, orientation);
            var plan = StructureToolPlan.create(world, blueprint, settings, block -> Integer.MAX_VALUE);
            assertTrue(plan.buildable(), plan.problems().toString());
            assertEquals(95, plan.targets().size());
            assertEquals(78, plan.materials().get(Blocks.STONE));
            assertEquals(1, plan.materials().get(Blocks.DISPENSER));
            assertEquals(16, plan.targets().stream().filter(target -> target.status() == StructureToolPlan.Status.MATCHED).count());
            Orientation effective = blueprint.effectiveOrientation(orientation);
            BlockPos dispenser = effective.apply(new BlockPos(1, 4, 0), ANCHOR);
            var target = plan.targets().stream().filter(cell -> cell.pos().equals(dispenser)).findFirst().orElseThrow();
            assertSame(Orientations.rotated(effective, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST)), target.state());
            assertTrue(world.states.isEmpty());
        }
    }

    @Test
    void existingValidBlocksAreRetainedAndWrongBlocksNeverBecomePlacementTargets() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        BlockPos casing = ANCHOR.offset(1, -1, 0), air = ANCHOR.offset(1, 1, 1);
        world.states.put(casing, Blocks.STONE.defaultBlockState());
        var plan = StructureToolPlan.create(world, blueprint, new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY), block -> 1000);
        assertTrue(plan.buildable());
        assertEquals(77, plan.materials().get(Blocks.STONE));
        world.states.put(casing, Blocks.GOLD_BLOCK.defaultBlockState());
        world.states.put(air, Blocks.DIRT.defaultBlockState());
        plan = StructureToolPlan.create(world, blueprint, new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY), block -> 1000);
        assertFalse(plan.buildable());
        assertEquals(2, plan.problems().size());
        assertTrue(plan.targets().stream().filter(target -> target.pos().equals(casing) || target.pos().equals(air)).allMatch(target -> target.status() == StructureToolPlan.Status.BLOCKED));
        assertSame(Blocks.GOLD_BLOCK.defaultBlockState(), world.states.get(casing));
        assertSame(Blocks.DIRT.defaultBlockState(), world.states.get(air));
    }

    @Test
    void unavailablePositionBlocksTheWholePlan() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        world.unloaded.add(ANCHOR.offset(1, -1, 0));
        var plan = StructureToolPlan.create(world, blueprint, new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY), block -> 1000);
        assertFalse(plan.buildable());
        assertEquals(StructureToolPlan.Status.UNKNOWN, plan.problems().getFirst().status());
        assertEquals(1, plan.problems().size());
    }

    @Test
    void survivalPlanningPrefersAnAvailableAllowedAlternative() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        var plan = StructureToolPlan.create(world, blueprint, new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY), block -> block == Blocks.STONE ? 0 : 1000);
        assertTrue(plan.buildable());
        assertEquals(78, plan.materials().get(Blocks.DIRT));
        assertFalse(plan.materials().containsKey(Blocks.STONE));
    }

    @Test
    void availableAlternativeCountsAreSharedAcrossAllMissingCells() {
        Blueprint blueprint = chamber(false);
        var plan = StructureToolPlan.create(new World(), blueprint, new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY),
                block -> block == Blocks.STONE ? 40 : block == Blocks.DIRT ? 38 : 1);
        assertTrue(plan.buildable());
        assertEquals(40, plan.materials().get(Blocks.STONE));
        assertEquals(38, plan.materials().get(Blocks.DIRT));
    }

    @Test
    void extensionCandidatesStillHaveToSatisfyTheBlueprint() {
        Blueprint blueprint = chamber(false);
        World world = new World();
        var settings = new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY);
        settings.candidates(cell -> List.of(Blocks.GOLD_BLOCK.defaultBlockState()));
        var plan = StructureToolPlan.create(world, blueprint, settings, block -> 1000);
        assertTrue(plan.buildable());
        assertFalse(plan.materials().containsKey(Blocks.GOLD_BLOCK));
        assertThrows(UnsupportedOperationException.class, () -> plan.targets().clear());
        assertThrows(UnsupportedOperationException.class, () -> plan.materials().clear());
    }

    @Test
    void invalidRepeatConfigurationIsRejectedBeforePlanning() {
        Blueprint blueprint = chamber(false);
        var settings = new StructureToolOptions.Settings(blueprint, Orientations.IDENTITY);
        settings.repeats(new RepeatResolution(new int[] { 1 }));
        assertThrows(IllegalArgumentException.class, () -> StructureToolPlan.create(new World(), blueprint, settings, block -> 1000));
    }

    private static final class World implements StructureView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();

        public BlockPos controllerPos() {
            return ANCHOR;
        }

        public boolean isLoaded(BlockPos pos) {
            return !unloaded.contains(pos);
        }

        public BlockState blockState(BlockPos pos) {
            return states.getOrDefault(pos, pos.equals(ANCHOR) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }

        public Set<PartRole> roles(BlockPos pos) {
            return Set.of();
        }
    }
}
