package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void orderedOptionalModulesAllowPrefixWithoutGaps() {
        TestView goldOnly = new TestView();
        goldOnly.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        MultiblockController.ModuleEvaluation first = evaluate(goldOnly,
                MultiblockModule.optional("gold", GOLD),
                MultiblockModule.optional("diamond", DIAMOND));
        assertTrue(first.valid());
        assertTrue(first.reports().get("gold").satisfied());
        assertFalse(first.reports().get("diamond").present());

        TestView both = new TestView();
        both.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        both.put(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertTrue(evaluate(both,
                MultiblockModule.optional("gold", GOLD),
                MultiblockModule.optional("diamond", DIAMOND)).valid());
    }

    @Test
    void laterModuleCannotBePresentAfterAnAbsentOptionalModule() {
        TestView diamondOnly = new TestView();
        diamondOnly.put(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        MultiblockController.ModuleEvaluation result = evaluate(diamondOnly,
                MultiblockModule.optional("gold", GOLD),
                MultiblockModule.optional("diamond", DIAMOND));
        assertFalse(result.valid());
        assertFalse(result.reports().get("gold").present());
        assertTrue(result.reports().get("diamond").present());
    }

    @Test
    void requiredModuleGatesEvenWhenItIsFirst() {
        TestView diamondOnly = new TestView();
        diamondOnly.put(new BlockPos(-1, 0, 0), Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertFalse(evaluate(diamondOnly,
                MultiblockModule.required("gold", GOLD),
                MultiblockModule.optional("diamond", DIAMOND)).valid());
    }

    @Test
    void partialModuleIsPresentButInvalid() {
        Blueprint twoCells = Blueprint.of()
                .aisle("@MM")
                .where('@', CellPredicates.controller())
                .where('M', CellPredicates.block(Blocks.GOLD_BLOCK))
                .build();
        TestView partial = new TestView();
        partial.put(new BlockPos(1, 0, 0), Blocks.GOLD_BLOCK.defaultBlockState());
        MultiblockController.ModuleEvaluation result = evaluate(partial,
                MultiblockModule.optional("gold", twoCells));
        assertFalse(result.valid());
        assertTrue(result.reports().get("gold").present());
        assertFalse(result.reports().get("gold").satisfied());
    }

    private static MultiblockController.ModuleEvaluation evaluate(TestView view, MultiblockModule... modules) {
        return MultiblockController.evaluateModuleOverlays(
                view, List.of(modules), Orientations.IDENTITY, BlockPos.ZERO);
    }

    private static final class TestView implements StructureView {

        private final Map<BlockPos, BlockState> states = new HashMap<>();

        private void put(BlockPos pos, BlockState state) {
            states.put(pos, state);
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
        public Set<PartRole> roles(BlockPos worldPos) {
            return Set.of();
        }

        @Override
        public boolean isMember(BlockPos worldPos) {
            return false;
        }
    }
}
