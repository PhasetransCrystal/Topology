package net.ptcrys.topo.api.machine.multiblock.ui

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
     * [renderedPositions] 为初始完整布局（包含空气），不受当前实建方块数量影响。
     * 布局增长、收缩或模块改变后，通过 [MultiblockScenePanel.Panel.swapBlocks] 同步替换范围。
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
