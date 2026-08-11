package net.ptcrys.topo.apiv2.ore.engine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Reads the pre-placement block state at a position. Backed by the generating world during real
 * worldgen, or by a synthetic provider in tests, so the planner stays a pure function of its inputs.
 */
@FunctionalInterface
public interface ExistingBlockProvider {

    BlockState blockState(BlockPos pos);
}
