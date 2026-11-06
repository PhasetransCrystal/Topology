package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureEngineTest {

    @Test
    void everyLayerMustHaveTheSameDimensions() {
        assertThrows(IllegalArgumentException.class, () -> Blueprint.of()
                .aisle("@")
                .aisle("AB")
                .where('@', CellPredicates.controller())
                .where('A', CellPredicates.block(Blocks.STONE))
                .where('B', CellPredicates.block(Blocks.DIRT))
                .build());
    }

    @Test
    void interiorRepeatableSegmentShiftsFollowingSegments() {
        Blueprint blueprint = Blueprint.of()
                .aisle("@")
                .beginRepeatable(1, 3)
                .aisle("R")
                .endRepeatable()
                .aisle("C")
                .where('@', CellPredicates.controller())
                .where('R', CellPredicates.block(Blocks.STONE))
                .where('C', CellPredicates.block(Blocks.DIRT))
                .build();

        TestView view = new TestView();
        view.put(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState());
        view.put(new BlockPos(0, 2, 0), Blocks.STONE.defaultBlockState());
        view.put(new BlockPos(0, 3, 0), Blocks.DIRT.defaultBlockState());

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void fixedCapThatSharesTheRepeatSymbolStopsGrowth() {
        Blueprint blueprint = Blueprint.of()
                .aisle("C@C")
                .beginRepeatable(1, 4)
                .aisle("CCC")
                .endRepeatable()
                .aisle("CCC")
                .where('@', CellPredicates.controller())
                .where('C', CellPredicates.block(Blocks.COPPER_BLOCK))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(-1, 0, 0), Blocks.COPPER_BLOCK.defaultBlockState());
        view.put(new BlockPos(1, 0, 0), Blocks.COPPER_BLOCK.defaultBlockState());
        for (int y = 1; y <= 3; y++) {
            for (int x = -1; x <= 1; x++) {
                view.put(new BlockPos(x, y, 0), Blocks.COPPER_BLOCK.defaultBlockState());
            }
        }

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void repeatableSegmentBelowControllerUsesInwardCounts() {
        Blueprint blueprint = Blueprint.of()
                .beginRepeatable(1, 3)
                .aisle("C")
                .endRepeatable()
                .aisle("@")
                .where('@', CellPredicates.controller())
                .where('C', CellPredicates.block(Blocks.STONE))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(0, -2, 0), Blocks.STONE.defaultBlockState());
        view.put(new BlockPos(0, -1, 0), Blocks.STONE.defaultBlockState());

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void unloadedGrowthAreaBelowControllerIsUnknown() {
        Blueprint blueprint = Blueprint.of()
                .beginRepeatable(1, 3)
                .aisle("C")
                .endRepeatable()
                .aisle("@")
                .where('@', CellPredicates.controller())
                .where('C', CellPredicates.block(Blocks.STONE))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(0, -1, 0), Blocks.STONE.defaultBlockState());
        view.setLoaded(new BlockPos(0, -2, 0), false);

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertFalse(result.formed());
        assertEquals(1, result.unloadedCount());
        assertEquals(RecognitionResult.FailureReason.CHUNK_UNLOADED, result.failureReason());
    }

    @Test
    void snapshotViewRecognizesPartialRepeatTower() {
        Blueprint blueprint = Blueprint.of()
                .aisle("@")
                .beginRepeatable(1, 4)
                .aisle("I")
                .endRepeatable()
                .where('@', CellPredicates.controller())
                .where('I', CellPredicates.block(Blocks.IRON_BLOCK))
                .build();
        CompiledBlueprint compiled = blueprint.compiled(Orientations.IDENTITY);
        CompiledSnapshot.Builder builder = CompiledSnapshot.builder(compiled, BlockPos.ZERO);
        builder.put(
                compiled.cellIndexOfWorld(BlockPos.ZERO, BlockPos.ZERO),
                BlockPos.ZERO.asLong(),
                Blocks.AIR.defaultBlockState(),
                null);
        for (int y = 1; y <= 4; y++) {
            BlockPos pos = new BlockPos(0, y, 0);
            builder.put(
                    compiled.cellIndexOfWorld(pos, BlockPos.ZERO),
                    pos.asLong(),
                    y <= 2 ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                    null);
        }

        RecognitionResult result = StructureEngine.recognize(builder.build(), blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void repeatableTowerWithAirCoreAndCapFormsAtEveryHeight() {
        Blueprint blueprint = Blueprint.of()
                .aisle("F@F", "FFF", "FFF")
                .beginRepeatable(1, 4)
                .aisle("OOO", "O#O", "OOO")
                .endRepeatable()
                .aisle("OOO", "OTO", "OOO")
                .where('@', CellPredicates.controller())
                .where('#', CellPredicates.air())
                .where('O', CellPredicates.block(Blocks.IRON_BLOCK))
                .where('F', CellPredicates.block(Blocks.IRON_BLOCK))
                .where('T', CellPredicates.block(Blocks.IRON_BLOCK))
                .build();
        for (int height = 1; height <= 4; height++) {
            TestView view = new TestView();
            for (int y = 0; y <= height + 1; y++) {
                for (int row = 0; row < 3; row++) {
                    for (int col = 0; col < 3; col++) {
                        int x = col - 1;
                        int z = row;
                        boolean middle = x == 0 && z == 1;
                        BlockState state;
                        if (y == 0) {
                            state = Blocks.IRON_BLOCK.defaultBlockState();
                        } else if (middle) {
                            state = y == height + 1 ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        } else {
                            state = Blocks.IRON_BLOCK.defaultBlockState();
                        }
                        view.put(new BlockPos(x, y, z), state);
                    }
                }
            }

            RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

            assertTrue(result.formed(), "height " + height);
            assertEquals(height, result.repeats().count(0), "height " + height);
            assertEquals(new RecognitionResult.Bounds(-1, 0, 0, 1, height + 1, 2), result.bounds(), "height " + height);
        }
    }

    @Test
    void frameRotatesRepeatGroupOntoLocalX() {
        Blueprint blueprint = Blueprint.of()
                .frame(Direction.EAST, Direction.SOUTH)
                .aisle("@")
                .beginRepeatable(1, 3)
                .aisle("A")
                .endRepeatable()
                .where('@', CellPredicates.controller())
                .where('A', CellPredicates.block(Blocks.STONE))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(1, 0, 0), Blocks.STONE.defaultBlockState());
        view.put(new BlockPos(2, 0, 0), Blocks.STONE.defaultBlockState());

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void frameRotatesRepeatGroupOntoLocalZ() {
        Blueprint blueprint = Blueprint.of()
                .frame(Direction.SOUTH, Direction.UP)
                .aisle("@")
                .beginRepeatable(1, 3)
                .aisle("A")
                .endRepeatable()
                .where('@', CellPredicates.controller())
                .where('A', CellPredicates.block(Blocks.STONE))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(0, 0, 1), Blocks.STONE.defaultBlockState());
        view.put(new BlockPos(0, 0, 2), Blocks.STONE.defaultBlockState());

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void repeatedLayerGroupRepeatsLayersAsAUnit() {
        Blueprint blueprint = Blueprint.of()
                .aisle("@")
                .beginRepeatable(1, 3)
                .aisle("A")
                .aisle("B")
                .endRepeatable()
                .aisle("C")
                .where('@', CellPredicates.controller())
                .where('A', CellPredicates.block(Blocks.STONE))
                .where('B', CellPredicates.block(Blocks.DIRT))
                .where('C', CellPredicates.block(Blocks.GOLD_BLOCK))
                .build();
        TestView view = new TestView();
        int[] layers = { 1, 2, 3, 4 };
        BlockState[] states = {
                Blocks.STONE.defaultBlockState(),
                Blocks.DIRT.defaultBlockState(),
                Blocks.STONE.defaultBlockState(),
                Blocks.DIRT.defaultBlockState()
        };
        for (int i = 0; i < layers.length; i++) {
            view.put(new BlockPos(0, layers[i], 0), states[i]);
        }
        view.put(new BlockPos(0, 5, 0), Blocks.GOLD_BLOCK.defaultBlockState());

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(2, result.repeats().count(0));
    }

    @Test
    void multipleRepeatableSegmentsUseIndependentCounts() {
        Blueprint blueprint = Blueprint.of()
                .aisle("@")
                .beginRepeatable(1, 2)
                .aisle("R")
                .endRepeatable()
                .beginRepeatable(1, 2)
                .aisle("S")
                .endRepeatable()
                .aisle("C")
                .where('@', CellPredicates.controller())
                .where('R', CellPredicates.block(Blocks.STONE))
                .where('S', CellPredicates.block(Blocks.DIRT))
                .where('C', CellPredicates.block(Blocks.GOLD_BLOCK))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState());
        view.put(new BlockPos(0, 2, 0), Blocks.DIRT.defaultBlockState());
        view.put(new BlockPos(0, 3, 0), Blocks.DIRT.defaultBlockState());
        view.put(new BlockPos(0, 4, 0), Blocks.GOLD_BLOCK.defaultBlockState());

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);

        assertTrue(result.formed());
        assertEquals(1, result.repeats().count(0));
        assertEquals(2, result.repeats().count(1));
    }

    @Test
    void unloadedCellIsUnknownNotBroken() {
        Blueprint blueprint = Blueprint.of()
                .aisle("@")
                .aisle("C")
                .where('@', CellPredicates.controller())
                .where('C', CellPredicates.block(Blocks.STONE))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(0, 1, 0), Blocks.STONE.defaultBlockState());
        view.setLoaded(new BlockPos(0, 1, 0), false);

        RecognitionResult result = StructureEngine.recognize(view, blueprint, Orientations.IDENTITY);
        assertFalse(result.formed());
        assertEquals(1, result.unloadedCount());
        assertEquals(RecognitionResult.FailureReason.CHUNK_UNLOADED, result.failureReason());

        StructureEngine.Verification verification = StructureEngine.verify(view, blueprint, Orientations.IDENTITY);
        assertFalse(verification.formed());
        assertTrue(verification.unloaded());
    }

    @Test
    void predicateCombinatorsMatchExpectedStates() {
        assertTrue(CellPredicates.air().test(Blocks.AIR.defaultBlockState(), null));
        assertFalse(CellPredicates.air().test(Blocks.STONE.defaultBlockState(), null));
        assertTrue(CellPredicates.not(CellPredicates.air()).test(Blocks.STONE.defaultBlockState(), null));
        assertTrue(CellPredicates.allOf(
                CellPredicates.block(Blocks.STONE),
                CellPredicates.not(CellPredicates.air())).test(Blocks.STONE.defaultBlockState(), null));
    }

    private static final class TestView implements StructureView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();
        private final Set<BlockPos> members = new HashSet<>();
        private final Map<BlockPos, Set<PartRole>> roles = new HashMap<>();

        private void put(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
        }

        private void setLoaded(BlockPos pos, boolean loaded) {
            if (!loaded) {
                unloaded.add(pos.immutable());
            } else {
                unloaded.remove(pos);
            }
        }

        @Override
        public BlockPos controllerPos() {
            return BlockPos.ZERO;
        }

        @Override
        public @Nullable BlockState blockState(BlockPos worldPos) {
            return states.get(worldPos);
        }

        @Override
        public boolean isLoaded(BlockPos worldPos) {
            return !unloaded.contains(worldPos);
        }

        @Override
        public Set<PartRole> roles(BlockPos worldPos) {
            return roles.getOrDefault(worldPos, Set.of());
        }

        @Override
        public boolean isMember(BlockPos worldPos) {
            return members.contains(worldPos);
        }
    }
}
