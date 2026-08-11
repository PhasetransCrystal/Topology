package net.ptcrys.topo.mixin;

import net.ptcrys.topo.apiv2.ore.engine.OreChunkPlacer;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the deterministic ore-vein placer after vanilla biome decoration, so large veins are written
 * into the chunk during generation. Small (vanilla-feature) veins are placed by the vanilla feature
 * pipeline and are not touched here.
 */
@Mixin(ChunkGenerator.class)
public abstract class OreChunkGeneratorMixin {

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void topo$placeDeterministicOres(
                                                          WorldGenLevel level,
                                                          ChunkAccess chunk,
                                                          StructureManager structureManager,
                                                          CallbackInfo callbackInfo) {
        OreChunkPlacer.place(level, chunk);
    }
}
