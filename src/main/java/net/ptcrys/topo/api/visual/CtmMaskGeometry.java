package net.ptcrys.topo.api.visual;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

import org.jspecify.annotations.Nullable;

/**
 * CTM mask geometry: 6-bit neighbor mask → 24 quadrant quads per cube.
 *
 * <p>
 * {@link #computeMask} queries neighbors from {@link BlockAndTintGetter} at render time;
 * {@link #bakeMaskQuads} pre-bakes all 64 mask variants at model-bake time.
 */
public final class CtmMaskGeometry {

    static final Direction[] DIRECTIONS = Direction.values();
    static final int DIRECTION_COUNT = DIRECTIONS.length;

    /** 6 个方向 → 64 个 mask 组合。 */
    public static final int MASK_COUNT = 1 << 6;

    /**
     * 4 个 quadrant 在 16-tile CTM atlas 中的"基础"sub-tile 索引：
     * quadrant 0 (左下) → 4、quadrant 1 (右下) → 5、quadrant 2 (右上) → 1、quadrant 3 (左上) → 0。
     * 与 {@link #ctmUv} 一起把 16-tile 编号 (row × 4 + col) 映射到 4×4 像素 UV。
     */
    private static final int[] SUBMAP_OFFSETS = { 4, 5, 1, 0 };
    private static final Direction[][] QUADRANT_EDGE_A = createQuadrantEdges(0);
    private static final Direction[][] QUADRANT_EDGE_B = createQuadrantEdges(1);
    private static final float[][] BASE_UVS = {
            { 0f, 8f, 8f, 16f },
            { 8f, 8f, 16f, 16f },
            { 8f, 0f, 16f, 8f },
            { 0f, 0f, 8f, 8f }
    };
    private static final float[][] CTM_UVS = createCtmUvs();

    /** Z-fight 防止：象限 quad 沿法向略向外推 0.01 像素。 */
    private static final float FACE_EPSILON = 0.01f;
    private static final ThreadLocal<BlockPos.MutableBlockPos> MASK_CURSOR = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private CtmMaskGeometry() {}

    public static int computeMask(BlockAndTintGetter level, BlockPos pos,
                                  @Nullable ConnectedTextureFamily family) {
        if (family == null) return 0;
        int mask = 0;
        BlockPos.MutableBlockPos cursor = MASK_CURSOR.get();
        for (Direction dir : DIRECTIONS) {
            cursor.setWithOffset(pos, dir);
            BlockState neighbor = level.getBlockState(cursor);
            if (neighbor.getBlock() instanceof ConnectedTextureHost host && family.equals(host.connectedTextureFamily(neighbor))) {
                mask |= ConnectedTextureProperties.bit(dir);
            }
        }
        return mask;
    }

    /** 6 face × 4 quadrant = 24 culled quads. CTM atlas selection per quadrant edge. */
    public static QuadCollection bakeMaskQuads(int mask, Material.Baked baseSprite, Material.Baked ctmSprite) {
        QuadCollection.Builder builder = new QuadCollection.Builder();
        for (Direction face : DIRECTIONS) {
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                boolean useCtm = textureUsesCtm(mask, face, quadrant);
                Material.Baked sprite = useCtm ? ctmSprite : baseSprite;
                float[] uv = uvFor(mask, face, quadrant, useCtm);
                float[] box = quadrantBounds(face, quadrant);
                builder.addCulledFace(face, bakeBoxFace(face, sprite, box, uv));
            }
        }
        return builder.build();
    }

    /** 把一个 overlay quad 添加到已有 builder，用于 body + overlay 合并 bake。 */
    public static void addOverlayQuad(QuadCollection.Builder builder, Direction face,
                                      Material.Baked sprite, float depth) {
        float d0 = depth;
        float d1 = depth + FACE_EPSILON;
        float[] box = switch (face) {
            case DOWN -> new float[] { 0f, -d1, 0f, 16f, -d0, 16f };
            case UP -> new float[] { 0f, 16f + d0, 0f, 16f, 16f + d1, 16f };
            case NORTH -> new float[] { 0f, 0f, -d1, 16f, 16f, -d0 };
            case SOUTH -> new float[] { 0f, 0f, 16f + d0, 16f, 16f, 16f + d1 };
            case WEST -> new float[] { -d1, 0f, 0f, -d0, 16f, 16f };
            case EAST -> new float[] { 16f + d0, 0f, 0f, 16f + d1, 16f, 16f };
        };
        builder.addUnculledFace(bakeBoxFace(face, sprite, box, new float[] { 0f, 0f, 16f, 16f }));
    }

    static boolean textureUsesCtm(int mask, Direction face, int quadrant) {
        return connected(mask, quadrantEdgeA(face, quadrant)) || connected(mask, quadrantEdgeB(face, quadrant));
    }

    static float[] uvFor(int mask, Direction face, int quadrant, boolean useCtm) {
        if (!useCtm) {
            return baseUv(quadrant);
        }
        boolean edgeA = connected(mask, quadrantEdgeA(face, quadrant));
        boolean edgeB = connected(mask, quadrantEdgeB(face, quadrant));
        int submap = SUBMAP_OFFSETS[quadrant] + (edgeA ? 2 : 0) + (edgeB ? 8 : 0);
        // 两边都连通时回退到 SUBMAP_OFFSETS 基址（corner tile），不要叠加 +2 +8 偏移。
        if (edgeA && edgeB) {
            submap = SUBMAP_OFFSETS[quadrant];
        }
        return ctmUv(submap);
    }

    static boolean connected(int mask, Direction direction) {
        return (mask & ConnectedTextureProperties.bit(direction)) != 0;
    }

    static Direction[] quadrantEdges(Direction face, int quadrant) {
        return new Direction[] { quadrantEdgeA(face, quadrant), quadrantEdgeB(face, quadrant) };
    }

    private static Direction quadrantEdgeA(Direction face, int quadrant) {
        return QUADRANT_EDGE_A[face.ordinal()][quadrant];
    }

    private static Direction quadrantEdgeB(Direction face, int quadrant) {
        return QUADRANT_EDGE_B[face.ordinal()][quadrant];
    }

    private static Direction localDown(Direction face) {
        // For Y-axis faces, quadrants 0/1 (b∈[0,8]) live on the NORTH (low-z) side of the face,
        // so the "down" edge direction must be NORTH for the geometric quadrant→neighbor mapping
        // to stay consistent with quadrantBounds().
        return face.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.DOWN;
    }

    private static Direction localUp(Direction face) {
        return face.getAxis() == Direction.Axis.Y ? Direction.SOUTH : Direction.UP;
    }

    private static Direction localLeft(Direction face) {
        return switch (face) {
            case EAST, WEST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    private static Direction localRight(Direction face) {
        return switch (face) {
            case EAST, WEST -> Direction.SOUTH;
            default -> Direction.EAST;
        };
    }

    private static float[] baseUv(int quadrant) {
        if (quadrant < 0 || quadrant >= BASE_UVS.length) {
            throw new IllegalArgumentException("quadrant " + quadrant);
        }
        return BASE_UVS[quadrant];
    }

    private static float[] ctmUv(int submap) {
        return CTM_UVS[submap];
    }

    private static Direction[][] createQuadrantEdges(int edge) {
        Direction[][] edges = new Direction[DIRECTION_COUNT][4];
        for (Direction face : DIRECTIONS) {
            for (int quadrant = 0; quadrant < 4; quadrant++) {
                Direction vertical = (quadrant == 0 || quadrant == 1) ? localDown(face) : localUp(face);
                Direction horizontal = (quadrant == 0 || quadrant == 3) ? localLeft(face) : localRight(face);
                edges[face.ordinal()][quadrant] = edge == 0 ? vertical : horizontal;
            }
        }
        return edges;
    }

    private static float[][] createCtmUvs() {
        float[][] uvs = new float[16][4];
        for (int submap = 0; submap < uvs.length; submap++) {
            int col = submap & 3;
            int row = submap >> 2;
            float u0 = col * 4f;
            float v0 = row * 4f;
            uvs[submap] = new float[] { u0, v0, u0 + 4f, v0 + 4f };
        }
        return uvs;
    }

    private static float[] quadrantBounds(Direction face, int quadrant) {
        boolean right = quadrant == 1 || quadrant == 2;
        boolean upper = quadrant == 2 || quadrant == 3;
        float a0 = right ? 8f : 0f;
        float a1 = right ? 16f : 8f;
        float b0 = upper ? 8f : 0f;
        float b1 = upper ? 16f : 8f;
        return switch (face) {
            case NORTH -> new float[] { a0, b0, 0f, a1, b1, FACE_EPSILON };
            case SOUTH -> new float[] { a0, b0, 16f - FACE_EPSILON, a1, b1, 16f };
            case WEST -> new float[] { 0f, b0, a0, FACE_EPSILON, b1, a1 };
            case EAST -> new float[] { 16f - FACE_EPSILON, b0, a0, 16f, b1, a1 };
            case DOWN -> new float[] { a0, 0f, b0, a1, FACE_EPSILON, b1 };
            case UP -> new float[] { a0, 16f - FACE_EPSILON, b0, a1, 16f, b1 };
        };
    }

    /**
     * Bake a single full-face quad (16×16) for the given direction. Package-visible for
     * {@link ConnectedTextureMachineModel} to build merged body+overlay collections.
     */
    static BakedQuad bakeFullFace(Direction face, Material.Baked sprite) {
        return bakeFullFaceQuad(face, sprite);
    }

    private static BakedQuad bakeFullFaceQuad(Direction face, Material.Baked sprite) {
        float[] box = switch (face) {
            case DOWN -> new float[] { 0f, 0f, 0f, 16f, FACE_EPSILON, 16f };
            case UP -> new float[] { 0f, 16f - FACE_EPSILON, 0f, 16f, 16f, 16f };
            case NORTH -> new float[] { 0f, 0f, 0f, 16f, 16f, FACE_EPSILON };
            case SOUTH -> new float[] { 0f, 0f, 16f - FACE_EPSILON, 16f, 16f, 16f };
            case WEST -> new float[] { 0f, 0f, 0f, FACE_EPSILON, 16f, 16f };
            case EAST -> new float[] { 16f - FACE_EPSILON, 0f, 0f, 16f, 16f, 16f };
        };
        return bakeBoxFace(face, sprite, box, new float[] { 0f, 0f, 16f, 16f });
    }

    /** 把一个 axis-aligned box 的一面 bake 成 {@link BakedQuad}。 */
    private static BakedQuad bakeBoxFace(Direction face, Material.Baked sprite, float[] box, float[] uv) {
        QuadBakingVertexConsumer consumer = new QuadBakingVertexConsumer();
        consumer.setDirection(face);
        consumer.setSprite(sprite, sprite.sprite().transparency());
        float[][] corners = faceCorners(face, box[0], box[1], box[2], box[3], box[4], box[5]);
        float nx = face.getStepX();
        float ny = face.getStepY();
        float nz = face.getStepZ();
        // getU/getV expect normalized 0-1; uv[] is in 0-16 pixel space (same as vanilla JSON).
        float u0 = sprite.sprite().getU(uv[0] / 16f);
        float v0 = sprite.sprite().getV(uv[1] / 16f);
        float u1 = sprite.sprite().getU(uv[2] / 16f);
        float v1 = sprite.sprite().getV(uv[3] / 16f);
        float[][] uvs = { { u0, v1 }, { u0, v0 }, { u1, v0 }, { u1, v1 } };
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(corners[i][0] / 16f, corners[i][1] / 16f, corners[i][2] / 16f)
                    .setColor(255, 255, 255, 255)
                    .setUv(uvs[i][0], uvs[i][1])
                    .setOverlay(0)
                    .setNormal(nx, ny, nz);
        }
        return consumer.bakeQuad();
    }

    /** 4 个顶点坐标，CCW winding from outside view (法线指向 outward) */
    private static float[][] faceCorners(Direction face, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (face) {
            case DOWN -> new float[][] { { x0, y0, z1 }, { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 } };
            case UP -> new float[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
            // Reversed winding for 4 side faces: previous order gave INWARD normals (GPU backface culled).
            case NORTH -> new float[][] { { x0, y0, z0 }, { x0, y1, z0 }, { x1, y1, z0 }, { x1, y0, z0 } };
            case SOUTH -> new float[][] { { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 }, { x0, y0, z1 } };
            case WEST -> new float[][] { { x0, y0, z1 }, { x0, y1, z1 }, { x0, y1, z0 }, { x0, y0, z0 } };
            case EAST -> new float[][] { { x1, y0, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x1, y0, z1 } };
        };
    }
}
