package net.ptcrys.topo.client.structure;

import net.ptcrys.topo.api.machine.multiblock.tool.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jspecify.annotations.Nullable;

import java.util.*;

/** 世界结构虚影。仅渲染服务端确认的状态，使用真实方块烘焙模型与半透明材质；收起工具、切换世界或超时后清除。 */
public final class StructureToolClient {

    private static @Nullable StructureToolSnapshot snapshot;
    private static long receivedTick;

    private StructureToolClient() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) return;
        NeoForge.EVENT_BUS.addListener(StructureToolClient::tick);
        NeoForge.EVENT_BUS.addListener(StructureToolClient::render);
    }

    public static void apply(StructureToolSnapshot value) {
        var level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().identifier().equals(value.dimension())) return;
        snapshot = value;
        receivedTick = level.getGameTime();
    }

    private static boolean holding(Minecraft minecraft) {
        return minecraft.player != null && (minecraft.player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof StructureToolItem || minecraft.player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof StructureToolItem);
    }

    private static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !holding(minecraft)) {
            snapshot = null;
            return;
        }
        if (snapshot == null) return;
        if (!snapshot.dimension().equals(minecraft.level.dimension().identifier()) || minecraft.level.getGameTime() < receivedTick || minecraft.level.getGameTime() - receivedTick > 80) {
            snapshot = null;
            return;
        }
        if (minecraft.level.getGameTime() % 20 == 0) ClientPacketDistributor.sendToServer(new StructureToolNetworking.Refresh());
    }

    private static void render(SubmitCustomGeometryEvent event) {
        var minecraft = Minecraft.getInstance();
        var value = snapshot;
        if (value == null || minecraft.level == null || !holding(minecraft) || !value.dimension().equals(minecraft.level.dimension().identifier())) return;
        var camera = event.getLevelRenderState().cameraRenderState;
        PoseStack poses = event.getPoseStack();
        Map<BlockState, List<BakedQuad>> meshes = new IdentityHashMap<>();
        for (var target : value.targets()) {
            if (target.status() == StructureToolPlan.Status.MATCHED || target.state().isAir()) continue;
            if (target.pos().distToCenterSqr(camera.pos) > 128 * 128) continue;
            List<BakedQuad> quads = meshes.computeIfAbsent(target.state(), state -> {
                List<BlockStateModelPart> parts = new ArrayList<>();
                minecraft.getModelManager().getBlockStateModelSet().get(state).collectParts(RandomSource.create(42), parts);
                List<BakedQuad> result = new ArrayList<>();
                for (var part : parts) {
                    result.addAll(part.getQuads(null));
                    for (Direction side : Direction.values()) result.addAll(part.getQuads(side));
                }
                return List.copyOf(result);
            });
            // 保留贴图原色与方块染色；状态通过线框表示，不能把模型统一乘成青色。
            IntList dynamicTints = new IntArrayList();
            if (minecraft.getBlockColors().getTintSources(target.state()).isEmpty()) {
                net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions.of(target.state())
                        .collectDynamicTintValues(target.state(), minecraft.level, target.pos(), dynamicTints);
            }
            int[] colors = new int[quads.size()];
            Map<Integer, Integer> tints = new HashMap<>();
            for (int i = 0; i < quads.size(); i++) {
                var quad = quads.get(i);
                int tintIndex = quad.materialInfo().tintIndex();
                int tint = tintIndex < 0 ? -1 : tints.computeIfAbsent(tintIndex, index -> {
                    var source = minecraft.getBlockColors().getTintSource(target.state(), index);
                    if (source != null) return minecraft.level.hasChunkAt(target.pos()) ? source.colorInWorld(target.state(), minecraft.level, target.pos()) : source.color(target.state());
                    return index < dynamicTints.size() ? dynamicTints.getInt(index) : -1;
                });
                var lighting = minecraft.level.cardinalLighting();
                float shade = quad.materialInfo().shade() ? lighting.byFace(quad.direction()) : lighting.up();
                colors[i] = 0x66000000 | (ARGB.scaleRGB(tint, shade) & 0xFFFFFF);
            }
            poses.pushPose();
            poses.translate(target.pos().getX() - camera.pos.x + 0.001, target.pos().getY() - camera.pos.y + 0.001, target.pos().getZ() - camera.pos.z + 0.001);
            poses.scale(0.998f, 0.998f, 0.998f);
            // 此透明管线不再叠加实体方向光，保持上面已经按世界计算的面亮度。
            event.getSubmitNodeCollector().submitCustomGeometry(poses, RenderTypes.eyes(TextureAtlas.LOCATION_BLOCKS), (pose, consumer) -> {
                QuadInstance instance = new QuadInstance();
                instance.setLightCoords(LightCoordsUtil.FULL_BRIGHT);
                instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                for (int i = 0; i < quads.size(); i++) {
                    instance.setColor(colors[i]);
                    consumer.putBakedQuad(pose, quads.get(i), instance);
                }
            });
            poses.popPose();
        }
        // 状态线框独立于模型着色，空气约束或无法确定候选状态的位置也可诊断。
        for (var target : value.targets()) {
            if (target.status() == StructureToolPlan.Status.MATCHED || target.pos().distToCenterSqr(camera.pos) > 128 * 128) continue;
            int color = switch (target.status()) {
                case BLOCKED -> 0xAAFF5555;
                case UNKNOWN -> 0xAAFFD54F;
                case UNRESOLVED -> 0xAAFF55FF;
                default -> 0x8833DDFF;
            };
            poses.pushPose();
            poses.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z);
            event.getSubmitNodeCollector().submitCustomGeometry(poses, RenderTypes.lines(), (pose, consumer) -> {
                float x = target.pos().getX(), y = target.pos().getY(), z = target.pos().getZ();
                float[][] points = { { x, y, z }, { x + 1, y, z }, { x + 1, y + 1, z }, { x, y + 1, z }, { x, y, z + 1 }, { x + 1, y, z + 1 }, { x + 1, y + 1, z + 1 }, { x, y + 1, z + 1 } };
                int[][] edges = { { 0, 1 }, { 1, 2 }, { 2, 3 }, { 3, 0 }, { 4, 5 }, { 5, 6 }, { 6, 7 }, { 7, 4 }, { 0, 4 }, { 1, 5 }, { 2, 6 }, { 3, 7 } };
                for (int[] edge : edges) {
                    float[] from = points[edge[0]], to = points[edge[1]];
                    consumer.addVertex(pose, from[0], from[1], from[2]).setColor(color).setNormal(pose, to[0] - from[0], to[1] - from[1], to[2] - from[2]).setLineWidth(2f);
                    consumer.addVertex(pose, to[0], to[1], to[2]).setColor(color).setNormal(pose, to[0] - from[0], to[1] - from[1], to[2] - from[2]).setLineWidth(2f);
                }
            });
            poses.popPose();
        }
        poses.pushPose();
        poses.translate(value.anchor().getX() + 0.5 - camera.pos.x, value.anchor().getY() + 1.8 - camera.pos.y, value.anchor().getZ() + 0.5 - camera.pos.z);
        poses.mulPose(camera.orientation);
        poses.scale(0.025f, -0.025f, 0.025f);
        event.getSubmitNodeCollector().submitText(poses, -minecraft.font.width(value.summary()) / 2f, 0, value.summary().getVisualOrderText(), false, Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0x66000000, 0);
        poses.popPose();
    }
}
