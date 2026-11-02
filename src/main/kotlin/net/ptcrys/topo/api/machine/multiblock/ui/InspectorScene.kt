package net.ptcrys.topo.api.machine.multiblock.ui

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene
import com.lowdragmc.lowdraglib2.utils.data.BlockPosFace

/**
 * [Scene] variant for the multiblock preview/structure pages: a side-panel row (or the BOM dock) can
 * drive the native selected-block highlight, and the rendered block set can be swapped in place —
 * the structure page uses that to flip between the required and the as-built view without rebuilding
 * the widget tree. Ported from the old GTOdyssey inspector scene.
 */
class InspectorScene : Scene() {

    fun selectBlock(localPos: BlockPos?): InspectorScene = apply {
        lastSelectedPosFace = localPos?.let { BlockPosFace(it, Direction.UP) }
    }

    fun selectedBlock(): BlockPos? = lastSelectedPosFace?.pos()

    /** Replaces the displayed blocks in place; [reshape] re-settles neighbour-derived shapes (stairs). */
    fun setBlocks(blocks: Map<BlockPos, BlockState>, clear: Collection<BlockPos>, reshape: Boolean): InspectorScene = apply {
        val world = dummyWorld ?: return@apply
        for (pos in clear) {
            if (!blocks.containsKey(pos)) {
                world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_NONE)
            }
        }
        blocks.forEach { (pos, state) ->
            world.setBlock(pos, state, Block.UPDATE_NONE)
        }
        if (reshape) {
            blocks.keys.forEach { pos ->
                val current = world.getBlockState(pos)
                val shaped = Block.updateFromNeighbourShapes(current, world, pos)
                if (shaped != current) {
                    world.setBlock(pos, shaped, Block.UPDATE_NONE)
                }
            }
        }
        needCompileCache()
    }
}
