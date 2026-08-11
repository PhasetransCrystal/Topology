package net.ptcrys.topo.apiv2.machine.multiblock.ui

import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld

import java.util.ArrayList

/**
 * Builds the [InspectorScene] both preview surfaces share (JEI category and the controller's
 * structure page): blocks go into a [TrackedDummyWorld], neighbour-derived shapes (stair corners,
 * pane connections) are settled once, and the scene renders orthographic with hover block tips.
 */
object MultiblockScenePreview {

    @JvmStatic
    fun scene(blocks: Map<BlockPos, BlockState>): InspectorScene = scene(blocks, blocks.keys)

    /**
     * [renderedPositions] is the scene's permanent cell universe — pass the full blueprint footprint
     * even when [blocks] only covers part of it (the as-built view of a half-built structure), so a
     * later [InspectorScene.setBlocks] swap can light up cells that started empty.
     */
    @JvmStatic
    fun scene(blocks: Map<BlockPos, BlockState>, renderedPositions: Collection<BlockPos>): InspectorScene {
        val world = TrackedDummyWorld()
        blocks.forEach { (pos, state) ->
            world.setBlockAndUpdate(pos, state)
        }
        blocks.keys.forEach { pos ->
            val current = world.getBlockState(pos)
            val shaped = Block.updateFromNeighbourShapes(current, world, pos)
            if (shaped != current) {
                world.setBlock(pos, shaped, Block.UPDATE_NONE)
            }
        }

        return InspectorScene().apply {
            createScene(world)
            setShowHoverBlockTips(true)
            useOrtho()
                .setOrthoRange(0.5f)
                .setRenderedCore(ArrayList(renderedPositions))
                .useCacheBuffer()
        }
    }
}
