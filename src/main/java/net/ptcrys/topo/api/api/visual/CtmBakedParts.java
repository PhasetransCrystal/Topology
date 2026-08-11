package net.ptcrys.topo.api.api.visual;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Objects;

/** Pre-baked CTM part tables: 64 neighbor-mask shells plus per-facing overlay plates. */
@SuppressWarnings("deprecation")
final class CtmBakedParts {

    private static final List<Direction> ALL_DIRECTIONS = List.of(CtmMaskGeometry.DIRECTIONS);

    private CtmBakedParts() {}

    static Overlay overlay(Material.Baked sprite, float depth) {
        return new Overlay(sprite, depth);
    }

    static BlockStateModelPart[] bakeMaskParts(Material.Baked baseSprite, Material.Baked ctmSprite) {
        return bakeMaskParts(baseSprite, ctmSprite, baseSprite);
    }

    static BlockStateModelPart[] bakeMaskParts(
                                               Material.Baked baseSprite,
                                               Material.Baked ctmSprite,
                                               Material.Baked particle) {
        BlockStateModelPart[] byMask = new BlockStateModelPart[CtmMaskGeometry.MASK_COUNT];
        for (int mask = 0; mask < CtmMaskGeometry.MASK_COUNT; mask++) {
            byMask[mask] = wrap(CtmMaskGeometry.bakeMaskQuads(mask, baseSprite, ctmSprite), particle);
        }
        return byMask;
    }

    static BlockStateModelPart[] bakeOverlayParts(
                                                  Iterable<Direction> facings,
                                                  Material.Baked particle,
                                                  Overlay... overlays) {
        BlockStateModelPart[] byFacing = new BlockStateModelPart[CtmMaskGeometry.DIRECTION_COUNT];
        for (Direction facing : facings) {
            QuadCollection.Builder builder = new QuadCollection.Builder();
            for (Overlay overlay : overlays) {
                CtmMaskGeometry.addOverlayQuad(builder, facing, overlay.sprite(), overlay.depth());
            }
            byFacing[facing.ordinal()] = wrap(builder.build(), particle);
        }
        return byFacing;
    }

    static BlockStateModelPart[] bakeOverlayParts(
                                                  Material.Baked particle,
                                                  Overlay... overlays) {
        return bakeOverlayParts(ALL_DIRECTIONS, particle, overlays);
    }

    /** Standalone hatch look: plain bottom/side/top casing with the pipe + icon overlays on the front. */
    static BlockStateModelPart[] bakeInactiveHatchParts(
                                                        Material.Baked bottom,
                                                        Material.Baked side,
                                                        Material.Baked top,
                                                        Material.Baked pipe,
                                                        Material.Baked icon,
                                                        Material.Baked particle) {
        BlockStateModelPart[] byFacing = new BlockStateModelPart[CtmMaskGeometry.DIRECTION_COUNT];
        for (Direction facing : CtmMaskGeometry.DIRECTIONS) {
            QuadCollection.Builder builder = new QuadCollection.Builder();
            builder.addCulledFace(Direction.DOWN, CtmMaskGeometry.bakeFullFace(Direction.DOWN, bottom));
            builder.addCulledFace(Direction.UP, CtmMaskGeometry.bakeFullFace(Direction.UP, top));
            for (Direction sideFace : Direction.Plane.HORIZONTAL) {
                builder.addCulledFace(sideFace, CtmMaskGeometry.bakeFullFace(sideFace, side));
            }
            CtmMaskGeometry.addOverlayQuad(builder, facing, pipe, 0.01f);
            CtmMaskGeometry.addOverlayQuad(builder, facing, icon, 0.02f);
            byFacing[facing.ordinal()] = wrap(builder.build(), particle);
        }
        return byFacing;
    }

    static void addMasked(
                          BlockAndTintGetter level,
                          BlockPos pos,
                          ConnectedTextureFamily family,
                          List<BlockStateModelPart> parts,
                          BlockStateModelPart[] byMask) {
        int mask = CtmMaskGeometry.computeMask(level, pos, family);
        parts.add(byMask[mask]);
    }

    static void addFacing(
                          List<BlockStateModelPart> parts,
                          BlockStateModelPart[] byFacing,
                          Direction facing) {
        BlockStateModelPart part = byFacing[facing.ordinal()];
        if (part != null) {
            parts.add(part);
        }
    }

    private static BlockStateModelPart wrap(QuadCollection quads, Material.Baked particle) {
        return new SimpleModelWrapper(quads, true, particle);
    }

    record Overlay(Material.Baked sprite, float depth) {

        Overlay {
            Objects.requireNonNull(sprite, "sprite");
        }
    }
}
