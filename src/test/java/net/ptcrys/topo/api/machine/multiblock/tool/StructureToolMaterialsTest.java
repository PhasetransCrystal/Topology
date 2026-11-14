package net.ptcrys.topo.api.machine.multiblock.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class StructureToolMaterialsTest {

    @Test
    void splitsRequirementsAcrossSlotsWithoutMutatingInputs() {
        var supplies = List.of(new StructureToolPlacement.Supply(0, Items.STONE, 64), new StructureToolPlacement.Supply(8, Items.STONE, 14), new StructureToolPlacement.Supply(9, Items.DISPENSER, 1));
        var required = Map.of(Items.STONE, 78, Items.DISPENSER, 1);
        assertEquals(Map.of(0, 64, 8, 14, 9, 1), StructureToolPlacement.allocate(supplies, required));
        assertEquals(64, supplies.getFirst().count());
        assertEquals(78, required.get(Items.STONE));
    }

    @Test
    void insufficientMaterialsDoNotProduceAPartialAllocation() {
        assertNull(StructureToolPlacement.allocate(List.of(new StructureToolPlacement.Supply(1, Items.STONE, 77)), Map.of(Items.STONE, 78)));
        assertEquals(Map.of(), StructureToolPlacement.allocate(List.of(), Map.of()));
    }

    @Test
    void rejectsAmbiguousSlotsAndInvalidAmounts() {
        assertThrows(IllegalArgumentException.class, () -> StructureToolPlacement.allocate(List.of(new StructureToolPlacement.Supply(1, Items.STONE, 4), new StructureToolPlacement.Supply(1, Items.STONE, 5)), Map.of(Items.STONE, 9)));
        assertThrows(IllegalArgumentException.class, () -> StructureToolPlacement.allocate(List.of(), Map.of(Items.STONE, -1)));
    }

    @Test
    void structureLargerThanAFullInventoryCanBeCompletedInTwoTrips() {
        var targets = targets(3000, Blocks.STONE.defaultBlockState());
        List<StructureToolPlacement.Supply> supplies = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) supplies.add(new StructureToolPlacement.Supply(slot, Items.STONE, 64));
        var first = StructureToolPlacement.selectBatch(targets, supplies, false);
        assertEquals(2304, first.targets().size());
        assertEquals(2304, first.allocation().values().stream().mapToInt(Integer::intValue).sum());
        Set<BlockPos> done = new HashSet<>();
        first.targets().forEach(target -> done.add(target.pos()));
        var remaining = targets.stream().filter(target -> !done.contains(target.pos())).toList();
        supplies.clear();
        for (int slot = 0; slot < 11; slot++) supplies.add(new StructureToolPlacement.Supply(slot, Items.STONE, slot == 10 ? 56 : 64));
        var second = StructureToolPlacement.selectBatch(remaining, supplies, false);
        assertEquals(696, second.targets().size());
        assertEquals(696, second.allocation().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(second.targets().stream().noneMatch(target -> done.contains(target.pos())));
        assertEquals(3000, targets.size());
    }

    @Test
    void unavailableMaterialDoesNotBlockOtherAvailableMaterials() {
        List<StructureToolPlan.Target> targets = new ArrayList<>(targets(16, Blocks.GOLD_BLOCK.defaultBlockState()));
        targets.addAll(targets(96, Blocks.STONE.defaultBlockState()));
        var batch = StructureToolPlacement.selectBatch(targets, List.of(new StructureToolPlacement.Supply(0, Items.STONE, 10)), false);
        assertEquals(10, batch.targets().size());
        assertTrue(batch.targets().stream().allMatch(target -> target.state().is(Blocks.STONE)));
        assertEquals(Map.of(0, 10), batch.allocation());
        var empty = StructureToolPlacement.selectBatch(targets, List.of(new StructureToolPlacement.Supply(0, Items.DIRT, 64)), false);
        assertTrue(empty.targets().isEmpty());
        assertTrue(empty.allocation().isEmpty());
    }

    @Test
    void creativeNeedsNoSupplyAndMatchedOrBlockedPositionsNeverEnterBatch() {
        var missing = targets(96, Blocks.WATER.defaultBlockState());
        List<StructureToolPlan.Target> targets = new ArrayList<>(missing);
        targets.add(new StructureToolPlan.Target(new BlockPos(-1, 0, 0), Blocks.STONE.defaultBlockState(), StructureToolPlan.Status.MATCHED));
        targets.add(new StructureToolPlan.Target(new BlockPos(-2, 0, 0), Blocks.STONE.defaultBlockState(), StructureToolPlan.Status.BLOCKED));
        var batch = StructureToolPlacement.selectBatch(targets, List.of(), true);
        assertEquals(missing, batch.targets());
        assertTrue(batch.allocation().isEmpty());
        assertTrue(StructureToolPlacement.selectBatch(targets, List.of(), false).targets().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> batch.targets().clear());
    }

    private static List<StructureToolPlan.Target> targets(int count, BlockState state) {
        List<StructureToolPlan.Target> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(new StructureToolPlan.Target(new BlockPos(i % 16, i / 256, i / 16 % 16), state, StructureToolPlan.Status.MISSING));
        return result;
    }
}
