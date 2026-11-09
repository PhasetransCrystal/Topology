package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.BlueprintLayout;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledBlueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 使用最小 4×4×6 主舱与远端模块验证纯索引，不创建或持有 Level。 */
class MultiblockClaimIndexTest {

    @Test
    void conflictingCommitIsRejectedWithoutReplacingThePreviousReservation() {
        MultiblockClaimIndex index = new MultiblockClaimIndex();
        Blueprint blueprint = chamber(false);
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(64, 0, 0);
        BlockPos observer = new BlockPos(128, 0, 0);
        BlockPos firstPart = new BlockPos(1, 1, 1);
        BlockPos secondPart = new BlockPos(65, 1, 1);
        assertTrue(index.tryClaim(first, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(firstPart)).accepted());
        assertTrue(index.tryClaim(second, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(secondPart)).accepted());

        MultiblockClaimIndex.ClaimUpdate rejected = index.tryClaim(first, blueprint, Orientations.IDENTITY, blueprint.maxRepeats(), Set.of(secondPart));
        assertFalse(rejected.accepted());
        assertNull(rejected.released());
        assertEquals(Set.of(firstPart, secondPart), index.conflictingExclusive(observer, Set.of(firstPart, secondPart)));
        assertEquals(Set.of(secondPart), index.conflictingExclusive(first, Set.of(firstPart, secondPart)));
        // 布局和旧预留也保持原样，失败申请的最大高度没有写入索引。
        assertEquals(Set.of(first), index.controllersForCell(new BlockPos(3, 5, 3)));
        assertTrue(index.controllersForCell(new BlockPos(3, 7, 3)).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> index.tryClaim(first, blueprint, Orientations.IDENTITY, new RepeatResolution(new int[0]), Set.of(firstPart)));
        assertEquals(Set.of(firstPart), index.conflictingExclusive(observer, Set.of(firstPart)));
    }

    @Test
    void replacingAClaimReportsReleasedPartsAndAllowsImmediateReuse() {
        MultiblockClaimIndex index = new MultiblockClaimIndex();
        Blueprint blueprint = chamber(false);
        BlockPos controller = BlockPos.ZERO;
        BlockPos competitor = new BlockPos(64, 0, 0);
        BlockPos formerPart = new BlockPos(1, 1, 1);
        BlockPos retainedPart = new BlockPos(2, 1, 1);
        assertTrue(index.tryClaim(controller, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(formerPart, retainedPart)).accepted());
        MultiblockClaimIndex.ClaimUpdate unchanged = index.tryClaim(controller, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(formerPart, retainedPart));
        assertTrue(unchanged.accepted());
        assertNull(unchanged.released());
        MultiblockClaimIndex.ClaimUpdate replaced = index.tryClaim(controller, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(retainedPart));
        assertTrue(replaced.accepted());
        assertNotNull(replaced.released());
        assertTrue(replaced.released().contains(formerPart));
        assertTrue(index.tryClaim(competitor, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(formerPart)).accepted());
        assertEquals(Set.of(formerPart), index.conflictingExclusive(controller, Set.of(formerPart, retainedPart)));
        assertEquals(Set.of(retainedPart), index.conflictingExclusive(competitor, Set.of(formerPart, retainedPart)));
    }

    @Test
    void releasingFarModuleWakesItsCompetitorEvenWhenMainRegionsDoNotIntersect() {
        MultiblockClaimIndex index = new MultiblockClaimIndex();
        Blueprint main = chamber(false);
        Blueprint farModule = Blueprint.of()
                .aisle("MMMM      @", "MMMM       ", "MMMM       ", "MMMM       ")
                .aisle("MMMM       ", "M##M       ", "M##M       ", "MMMM       ")
                .aisle("MMMM       ", "M##M       ", "M##M       ", "MMMM       ")
                .aisle("MMMM       ", "M##M       ", "M##M       ", "MMMM       ")
                .aisle("MMMM       ", "M##M       ", "M##M       ", "MMMM       ")
                .aisle("MMMM       ", "MMMM       ", "MMMM       ", "MMMM       ")
                .where('M', Blocks.GOLD_BLOCK).build();
        BlockPos owner = BlockPos.ZERO;
        BlockPos competitor = new BlockPos(20, 0, 0);
        BlockPos modulePart = new BlockPos(12, 2, 0);
        index.watch(competitor, main, List.of(farModule), Orientations.IDENTITY);
        assertTrue(index.tryClaim(owner, main, Orientations.IDENTITY, main.minRepeats(), Set.of(modulePart)).accepted());
        assertEquals(Set.of(owner, competitor), index.controllersForCell(modulePart));
        MultiblockClaimIndex.Region released = index.unclaim(owner);
        assertNotNull(released);
        assertTrue(released.contains(modulePart));
        assertEquals(Set.of(competitor), index.watchersIntersecting(released, owner));
        assertTrue(index.conflictingExclusive(competitor, Set.of(modulePart)).isEmpty());
        assertNull(index.unclaim(owner));
    }

    @Test
    void sharedCasingsRemainShareableAndClaimsRouteOnlyTheirResolvedLayout() {
        MultiblockClaimIndex index = new MultiblockClaimIndex();
        Blueprint blueprint = chamber(false);
        BlockPos first = BlockPos.ZERO;
        BlockPos second = new BlockPos(1, 0, 0);
        assertTrue(index.tryClaim(first, blueprint, Orientations.IDENTITY, blueprint.minRepeats()).accepted());
        assertTrue(index.tryClaim(second, blueprint, Orientations.IDENTITY, blueprint.minRepeats()).accepted());
        assertEquals(Set.of(first, second), index.controllersForCell(new BlockPos(2, 4, 2)));
        assertEquals(Set.of(first), index.controllersForCell(new BlockPos(0, 4, 2)));
        assertTrue(index.controllersForCell(new BlockPos(2, 6, 2)).isEmpty());
        assertTrue(index.conflictingExclusive(new BlockPos(100, 0, 0), Set.of(new BlockPos(2, 4, 2))).isEmpty());
    }

    @Test
    void watchesCoverGrowthButUnwatchLeavesOnlyTheConcreteClaimAcross24Rotations() {
        BlockPos controller = new BlockPos(15, 65, -17);
        for (boolean sideways : List.of(false, true)) {
            Blueprint blueprint = chamber(sideways);
            Orientation frame = sideways ? Orientations.rotations().stream().filter(o -> o.apply(Direction.UP) == Direction.EAST && o.apply(Direction.SOUTH) == Direction.SOUTH).findFirst().orElseThrow() : Orientations.IDENTITY;
            for (Orientation machine : Orientations.rotations()) {
                MultiblockClaimIndex index = new MultiblockClaimIndex();
                Orientation effective = machine.compose(frame);
                index.watch(controller, blueprint, machine);
                assertTrue(index.tryClaim(controller, blueprint, machine, blueprint.minRepeats()).accepted());
                BlockPos growth = effective.apply(new BlockPos(2, 7, 3), controller);
                assertEquals(Set.of(controller), index.controllersForCell(growth));
                index.unwatch(controller);
                assertTrue(index.controllersForCell(growth).isEmpty());
                for (int x = 0; x < 4; x++) {
                    for (int y = 0; y < 6; y++) {
                        for (int z = 0; z < 4; z++) {
                            assertEquals(Set.of(controller), index.controllersForCell(effective.apply(new BlockPos(x, y, z), controller)));
                        }
                    }
                }
                index.unclaim(controller);
                assertTrue(index.controllersForCell(controller).isEmpty());
            }
        }
    }

    @Test
    void callerMutablePositionsCannotMoveAReservationAfterCommit() {
        MultiblockClaimIndex index = new MultiblockClaimIndex();
        Blueprint blueprint = chamber(false);
        BlockPos.MutableBlockPos controller = new BlockPos.MutableBlockPos(0, 0, 0);
        BlockPos.MutableBlockPos part = new BlockPos.MutableBlockPos(2, 1, 1);
        assertTrue(index.tryClaim(controller, blueprint, Orientations.IDENTITY, blueprint.minRepeats(), Set.of(part)).accepted());
        controller.set(90, 90, 90);
        part.set(91, 91, 91);
        BlockPos originalPart = new BlockPos(2, 1, 1);
        assertEquals(Set.of(originalPart), index.conflictingExclusive(controller, Set.of(originalPart)));
        assertEquals(Set.of(BlockPos.ZERO), index.controllersForCell(originalPart));
        index.unclaim(BlockPos.ZERO);
        assertTrue(index.conflictingExclusive(controller, Set.of(originalPart)).isEmpty());
    }

    @Test
    void cachedLayoutsAreBoundToTheirOwnBlueprint() {
        Blueprint first = chamber(false);
        Blueprint second = chamber(false);
        BlueprintLayout layout = first.layout(first.minRepeats());
        CompiledBlueprint compiled = first.compiled(Orientations.IDENTITY);
        assertEquals(0, compiled.cellIndexOfWorld(BlockPos.ZERO, BlockPos.ZERO, layout));
        assertThrows(IllegalArgumentException.class,
                () -> compiled.cellIndexOfWorld(BlockPos.ZERO, BlockPos.ZERO, second.layout(second.minRepeats())));
    }

    private static Blueprint chamber(boolean sideways) {
        Blueprint.Builder builder = Blueprint.of();
        if (sideways) builder.frame(Direction.EAST, Direction.SOUTH);
        return builder.aisle("@CCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .aisle("CCCC", "CCCC", "CCCC", "CCCC")
                .beginRepeatable(2, 4).aisle("CCCC", "C##C", "C##C", "CCCC").endRepeatable()
                .where('C', Blocks.IRON_BLOCK).build();
    }
}
