package net.ptcrys.topo.api.ore.engine;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.List;

/**
 * Writes planned ore blocks into a generating chunk. Invoked from the {@code ChunkGenerator}
 * decoration tail; the planner decides, this only commits.
 */
public final class OreChunkPlacer {

    private OreChunkPlacer() {}

    public static void place(WorldGenLevel level, ChunkAccess chunk) {
        ResourceKey<Level> dimension = level.getLevel().dimension();
        OreVeinPlanner planner = OreWorldgenService.planner(level.registryAccess());
        List<PlannedOreBlock> planned = planner.planChunk(level.getSeed(), dimension, chunk.getPos(), level::getBlockState);
        for (PlannedOreBlock block : planned) {
            if (level.ensureCanWrite(block.pos())) {
                level.setBlock(block.pos(), block.blockState(), 2);
            }
        }
    }
}
