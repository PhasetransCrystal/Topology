package net.ptcrys.topo.apiv2.ore.engine;

import net.ptcrys.topo.apiv2.ore.OrePlacement;
import net.ptcrys.topo.apiv2.ore.OreVein;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** A concrete placement of one grid vein in the world: its center and reach in a given dimension. */
public record OreVeinPlacement(
                               OreVein vein,
                               OrePlacement.Grid grid,
                               ResourceKey<Level> dimension,
                               BlockPos center,
                               int radiusBlocks) {}
