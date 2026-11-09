package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ui.BlueprintPreview;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintPreviewCoordinatesTest {

    @Test
    void previewUsesControllerForwardRatherThanDeclarationFrame() {
        Blueprint blueprint = Blueprint.of().frame(Direction.EAST, Direction.SOUTH).aisle("@D")
                .where('D', Blocks.DISPENSER, Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), PropertyRules.direction(BlockStateProperties.FACING)).build();
        assertEquals(new BlockPos(2, -1, 3), BlueprintPreview.previewPosition(blueprint, new BlockPos(1, 2, 3), false));
        assertEquals(new BlockPos(-2, -1, 3), BlueprintPreview.previewPosition(blueprint, new BlockPos(1, 2, 3), true));
        Cell dispenser = blueprint.cells(blueprint.minRepeats()).stream().filter(cell -> !cell.localPos().equals(BlockPos.ZERO)).findFirst().orElseThrow();
        assertEquals(Direction.EAST, BlueprintPreview.previewState(blueprint, dispenser, dispenser.expectedState(), false).getValue(BlockStateProperties.FACING));
        assertEquals(Direction.WEST, BlueprintPreview.previewState(blueprint, dispenser, dispenser.expectedState(), true).getValue(BlockStateProperties.FACING));
        assertEquals(Direction.SOUTH, BlueprintPreview.controllerPreviewStateFrom(Blocks.DISPENSER.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP)).getValue(BlockStateProperties.FACING));
    }
}
