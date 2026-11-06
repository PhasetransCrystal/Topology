package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiblockModuleEvaluationTest {

    private static final Blueprint GOLD = Blueprint.of()
            .aisle("@M")
            .where('@', CellPredicates.controller())
            .where('M', CellPredicates.block(Blocks.GOLD_BLOCK))
            .build();
    private static final Blueprint DIAMOND = Blueprint.of()
            .aisle("N@")
            .where('@', CellPredicates.controller())
            .where('N', CellPredicates.block(Blocks.DIAMOND_BLOCK))
            .build();

    @Test
    void modulesAreIndependentOptionalExtensions() {
        TestView goldOnly = new TestView();
        goldOnly.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        MultiblockModuleEvaluator.Evaluation first = evaluate(goldOnly,
                MultiblockModule.of("gold", GOLD),
                MultiblockModule.of("diamond", DIAMOND));
        assertTrue(first.reports().get("gold").satisfied());
        assertFalse(first.reports().get("diamond").satisfied());

        TestView diamondOnly = new TestView();
        diamondOnly.put(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        MultiblockModuleEvaluator.Evaluation second = evaluate(diamondOnly,
                MultiblockModule.of("gold", GOLD),
                MultiblockModule.of("diamond", DIAMOND));
        assertFalse(second.reports().get("gold").satisfied());
        assertTrue(second.reports().get("diamond").satisfied());
    }

    @Test
    void partialModuleIsConsideredAbsent() {
        Blueprint twoCells = Blueprint.of()
                .aisle("@MM")
                .where('@', CellPredicates.controller())
                .where('M', CellPredicates.block(Blocks.GOLD_BLOCK))
                .build();
        TestView partial = new TestView();
        partial.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        RecognitionResult.ModuleReport report = evaluate(partial, MultiblockModule.of("gold", twoCells))
                .reports().get("gold");
        assertFalse(report.satisfied());
        assertFalse(report.present());
        assertEquals(RecognitionResult.ModuleState.ABSENT, report.state());
        assertEquals(1, report.matched());
        assertEquals(2, report.total());
    }

    @Test
    void dependencyBlocksAModuleUntilItsPrerequisiteIsSatisfied() {
        TestView diamondOnly = new TestView();
        diamondOnly.put(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        MultiblockModuleEvaluator.Evaluation blocked = evaluate(diamondOnly,
                MultiblockModule.of("gold", GOLD),
                MultiblockModule.of("diamond", DIAMOND, "gold"));
        assertFalse(blocked.reports().get("diamond").satisfied());
        assertEquals(List.of("gold"), blocked.reports().get("diamond").blockedBy());

        TestView both = new TestView();
        both.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        both.put(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        MultiblockModuleEvaluator.Evaluation satisfied = evaluate(both,
                MultiblockModule.of("gold", GOLD),
                MultiblockModule.of("diamond", DIAMOND, "gold"));
        assertTrue(satisfied.reports().get("gold").satisfied());
        assertTrue(satisfied.reports().get("diamond").satisfied());
        assertTrue(satisfied.reports().get("diamond").blockedBy().isEmpty());
    }

    @Test
    void modulesMayOverlapEachOther() {
        Blueprint sameCell = Blueprint.of()
                .aisle("@M")
                .where('@', CellPredicates.controller())
                .where('M', CellPredicates.block(Blocks.GOLD_BLOCK))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        MultiblockModuleEvaluator.Evaluation evaluation = evaluate(view,
                MultiblockModule.of("left", GOLD),
                MultiblockModule.of("right", sameCell));
        assertTrue(evaluation.reports().get("left").satisfied());
        assertTrue(evaluation.reports().get("right").satisfied());
    }

    @Test
    void moduleRepeatSectionUsesTheFullBlueprintEngine() {
        Blueprint repeatable = Blueprint.of()
                .aisle("@")
                .beginRepeatable(1, 3)
                .aisle("M")
                .endRepeatable()
                .where('@', CellPredicates.controller())
                .where('M', CellPredicates.block(Blocks.GOLD_BLOCK))
                .build();
        TestView view = new TestView();
        view.put(new BlockPos(0, 1, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        view.put(new BlockPos(0, 2, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        RecognitionResult.ModuleReport report = evaluate(view, MultiblockModule.of("gold", repeatable))
                .reports().get("gold");
        assertTrue(report.satisfied());
        assertEquals(2, report.total());
        assertEquals(2, report.repeats().count(0));
        assertEquals(new RecognitionResult.Bounds(0, 0, 0, 0, 2, 0), report.bounds());
    }

    @Test
    void unloadedModuleCellsAreUnknown() {
        TestView view = new TestView();
        view.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        view.setLoaded(new BlockPos(1, 0, 0), false);
        MultiblockModuleEvaluator.Evaluation evaluation = evaluate(view, MultiblockModule.of("gold", GOLD));
        assertTrue(evaluation.unloaded());
    }

    private static MultiblockModuleEvaluator.Evaluation evaluate(TestView view, MultiblockModule... modules) {
        return MultiblockModuleEvaluator.evaluateOverlays(
                view, List.of(modules), Orientations.IDENTITY);
    }

    private static final class TestView implements StructureView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();
        private final Set<BlockPos> unloaded = new HashSet<>();

        private void put(BlockPos pos, BlockState state) {
            states.put(pos.immutable(), state);
        }

        private void setLoaded(BlockPos pos, boolean loaded) {
            if (loaded) {
                unloaded.remove(pos.immutable());
            } else {
                unloaded.add(pos.immutable());
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
            return Set.of();
        }

        @Override
        public boolean isMember(BlockPos worldPos) {
            return false;
        }
    }
}
