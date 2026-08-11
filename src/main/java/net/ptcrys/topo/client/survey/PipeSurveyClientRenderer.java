package net.ptcrys.topo.client.survey;

import net.ptcrys.topo.api.pipe.survey.PipeSurveyRefreshC2SPayload;
import net.ptcrys.topo.api.pipe.survey.PipeSurveySnapshot;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyTool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 勘测叠加层的世界渲染:节点线框按占用率配色(绿→黄→红),抽取/接收端口面框(橙/蓝),
 * 端点 A/B 立柱(金/紫),A→B 车道折线(逐车道色相)与瓶颈节点红色脉冲;准星指向快照内
 * 节点时浮签显示 占用/容量,选齐端点后 B 上方浮签显示静态容量×车道数。几何经
 * {@link SubmitCustomGeometryEvent} 提交(26.1 自定义世界几何正道),坐标平移到相机空间。
 * 另挂客户端 tick 轮询:激活期间每 20t 发刷新空包,收起工具即停(快照 80t 后淡出)。
 */
public final class PipeSurveyClientRenderer {

    private static final int POLL_INTERVAL_TICKS = 20;
    /** 线宽(像素):26.1 的 lines 顶点格式带 LineWidth 元素,每个顶点必须设,否则崩。 */
    private static final float LINE_WIDTH = 2.5f;
    /** 车道调色板(ARGB),逐车道循环取色。 */
    private static final int[] LANE_COLORS = {
            0xFF35C4E6, 0xFFE6A535, 0xFF9B6BE6, 0xFF4FE05A, 0xFFE05ABF, 0xFFB8C92F };

    private PipeSurveyClientRenderer() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT) {
            return;
        }
        NeoForge.EVENT_BUS.addListener(PipeSurveyClientRenderer::onSubmitGeometry);
        NeoForge.EVENT_BUS.addListener(PipeSurveyClientRenderer::onClientTick);
    }

    // --- 轮询 --------------------------------------------------------------------------------

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            PipeSurveyClientState.clear();
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (!PipeSurveyClientState.fresh(gameTime) || gameTime % POLL_INTERVAL_TICKS != 0) {
            return;
        }
        if (holdsSurveyor(minecraft.player)) {
            ClientPacketDistributor.sendToServer(new PipeSurveyRefreshC2SPayload());
        }
    }

    private static boolean holdsSurveyor(Player player) {
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof PipeSurveyTool || player.getItemInHand(InteractionHand.OFF_HAND).getItem() instanceof PipeSurveyTool;
    }

    // --- 世界渲染 ----------------------------------------------------------------------------

    private static void onSubmitGeometry(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        PipeSurveySnapshot snapshot = PipeSurveyClientState.current();
        if (snapshot == null || minecraft.level == null) {
            return;
        }
        long gameTime = minecraft.level.getGameTime();
        if (!PipeSurveyClientState.fresh(gameTime)) {
            return;
        }
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        Vec3 cameraPos = camera.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        event.getSubmitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.lines(),
                (pose, consumer) -> drawSurvey(pose, consumer, snapshot, gameTime));
        poseStack.popPose();
        submitLabels(event.getSubmitNodeCollector(), poseStack, camera, cameraPos, snapshot, minecraft);
    }

    private static void drawSurvey(PoseStack.Pose pose, VertexConsumer consumer,
                                   PipeSurveySnapshot snapshot, long gameTime) {
        long window = Math.max(1, snapshot.windowTicks());
        for (int i = 0; i < snapshot.nodePos().length; i++) {
            long pos = snapshot.nodePos()[i];
            float x = BlockPos.getX(pos);
            float y = BlockPos.getY(pos);
            float z = BlockPos.getZ(pos);
            long capacity = (long) snapshot.nodeThroughput()[i] * window;
            float ratio = capacity <= 0 ? 0 : Mth.clamp(snapshot.nodeUsed()[i] / (float) capacity, 0f, 1f);
            wireBox(pose, consumer, x + 0.14f, y + 0.14f, z + 0.14f,
                    x + 0.86f, y + 0.86f, z + 0.86f, occupancyColor(ratio));
            short bits = snapshot.nodePortBits()[i];
            int extractMask = PipeSurveySnapshot.extractMask(bits);
            int destinationMask = PipeSurveySnapshot.destinationMask(bits);
            for (Direction direction : Direction.values()) {
                int bit = 1 << direction.ordinal();
                if ((extractMask & bit) != 0) {
                    faceFrame(pose, consumer, x, y, z, direction, 0xFFFFA227);
                }
                if ((destinationMask & bit) != 0) {
                    faceFrame(pose, consumer, x, y, z, direction, 0xFF3D8BFF);
                }
            }
        }
        drawMarker(pose, consumer, snapshot.pointA(), 0xFFFFD24A);
        drawMarker(pose, consumer, snapshot.pointB(), 0xFFC65BFF);
        for (int l = 0; l < snapshot.lanes().size(); l++) {
            PipeSurveySnapshot.Lane lane = snapshot.lanes().get(l);
            int color = LANE_COLORS[l % LANE_COLORS.length];
            long[] positions = lane.positions();
            for (int i = 0; i + 1 < positions.length; i++) {
                line(pose, consumer,
                        BlockPos.getX(positions[i]) + 0.5f, BlockPos.getY(positions[i]) + 0.5f,
                        BlockPos.getZ(positions[i]) + 0.5f,
                        BlockPos.getX(positions[i + 1]) + 0.5f, BlockPos.getY(positions[i + 1]) + 0.5f,
                        BlockPos.getZ(positions[i + 1]) + 0.5f, color);
            }
            if (lane.bottleneckOrdinal() >= 0 && lane.bottleneckOrdinal() < positions.length) {
                long bottleneck = positions[lane.bottleneckOrdinal()];
                // 红色脉冲:gameTime 相位调 alpha,瓶颈一眼可辨。
                int alpha = 150 + (int) (105 * Math.abs(Math.sin((gameTime + l * 7) * 0.25)));
                int pulse = (alpha << 24) | 0xE63E3E;
                float bx = BlockPos.getX(bottleneck);
                float by = BlockPos.getY(bottleneck);
                float bz = BlockPos.getZ(bottleneck);
                wireBox(pose, consumer, bx + 0.05f, by + 0.05f, bz + 0.05f,
                        bx + 0.95f, by + 0.95f, bz + 0.95f, pulse);
            }
        }
    }

    /** 占用率配色:0=绿,0.5=黄,1=红(两段线性)。 */
    private static int occupancyColor(float ratio) {
        int red;
        int green;
        if (ratio < 0.5f) {
            red = (int) (0x2B + (0xE6 - 0x2B) * (ratio * 2));
            green = 0xD9;
        } else {
            red = 0xE6;
            green = (int) (0xD9 - (0xD9 - 0x3E) * ((ratio - 0.5f) * 2));
        }
        return 0xFF000000 | (red << 16) | (green << 8) | 0x3E;
    }

    private static void drawMarker(PoseStack.Pose pose, VertexConsumer consumer, long point, int color) {
        if (point == PipeSurveySnapshot.NO_POINT) {
            return;
        }
        float x = BlockPos.getX(point);
        float y = BlockPos.getY(point);
        float z = BlockPos.getZ(point);
        wireBox(pose, consumer, x + 0.34f, y, z + 0.34f, x + 0.66f, y + 1.6f, z + 0.66f, color);
    }

    /** 在节点的 {@code direction} 面上画端口方框(贴面外移一点防 z-fighting)。 */
    private static void faceFrame(PoseStack.Pose pose, VertexConsumer consumer,
                                  float x, float y, float z, Direction direction, int color) {
        float cx = x + 0.5f + direction.getStepX() * 0.52f;
        float cy = y + 0.5f + direction.getStepY() * 0.52f;
        float cz = z + 0.5f + direction.getStepZ() * 0.52f;
        // 取与法线正交的两轴铺成正方形:竖直面(±Y)用 X/Z,其余面用 Y 与剩余水平轴。
        float r = 0.22f;
        float ax;
        float ay;
        float az;
        float bx;
        float by;
        float bz;
        if (direction.getStepY() != 0) {
            ax = r;
            ay = 0;
            az = 0;
            bx = 0;
            by = 0;
            bz = r;
        } else {
            ax = 0;
            ay = r;
            az = 0;
            bx = direction.getStepX() != 0 ? 0 : r;
            by = 0;
            bz = direction.getStepZ() != 0 ? 0 : r;
        }
        line(pose, consumer, cx - ax - bx, cy - ay - by, cz - az - bz,
                cx + ax - bx, cy + ay - by, cz + az - bz, color);
        line(pose, consumer, cx + ax - bx, cy + ay - by, cz + az - bz,
                cx + ax + bx, cy + ay + by, cz + az + bz, color);
        line(pose, consumer, cx + ax + bx, cy + ay + by, cz + az + bz,
                cx - ax + bx, cy - ay + by, cz - az + bz, color);
        line(pose, consumer, cx - ax + bx, cy - ay + by, cz - az + bz,
                cx - ax - bx, cy - ay - by, cz - az - bz, color);
    }

    private static void wireBox(PoseStack.Pose pose, VertexConsumer consumer,
                                float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        line(pose, consumer, x0, y0, z0, x1, y0, z0, color);
        line(pose, consumer, x1, y0, z0, x1, y0, z1, color);
        line(pose, consumer, x1, y0, z1, x0, y0, z1, color);
        line(pose, consumer, x0, y0, z1, x0, y0, z0, color);
        line(pose, consumer, x0, y1, z0, x1, y1, z0, color);
        line(pose, consumer, x1, y1, z0, x1, y1, z1, color);
        line(pose, consumer, x1, y1, z1, x0, y1, z1, color);
        line(pose, consumer, x0, y1, z1, x0, y1, z0, color);
        line(pose, consumer, x0, y0, z0, x0, y1, z0, color);
        line(pose, consumer, x1, y0, z0, x1, y1, z0, color);
        line(pose, consumer, x1, y0, z1, x1, y1, z1, color);
        line(pose, consumer, x0, y0, z1, x0, y1, z1, color);
    }

    private static void line(PoseStack.Pose pose, VertexConsumer consumer,
                             float x0, float y0, float z0, float x1, float y1, float z1, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float dz = z1 - z0;
        float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0e-4f) {
            return;
        }
        float nx = dx / length;
        float ny = dy / length;
        float nz = dz / length;
        consumer.addVertex(pose, x0, y0, z0).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
        consumer.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz).setLineWidth(LINE_WIDTH);
    }

    // --- 浮签 --------------------------------------------------------------------------------

    private static void submitLabels(SubmitNodeCollector collector, PoseStack poseStack,
                                     CameraRenderState camera, Vec3 cameraPos, PipeSurveySnapshot snapshot, Minecraft minecraft) {
        if (minecraft.hitResult instanceof BlockHitResult blockHit) {
            long target = blockHit.getBlockPos().asLong();
            for (int i = 0; i < snapshot.nodePos().length; i++) {
                if (snapshot.nodePos()[i] == target) {
                    long window = Math.max(1, snapshot.windowTicks());
                    long capacity = (long) snapshot.nodeThroughput()[i] * window;
                    long used = snapshot.nodeUsed()[i];
                    int percent = capacity <= 0 ? 0 : (int) (used * 100 / capacity);
                    submitLabel(collector, poseStack, camera, cameraPos, target, 1.2f,
                            used + " / " + capacity + " (" + percent + "%)", minecraft);
                    break;
                }
            }
        }
        if (snapshot.pointB() != PipeSurveySnapshot.NO_POINT && !snapshot.lanes().isEmpty()) {
            submitLabel(collector, poseStack, camera, cameraPos, snapshot.pointB(), 1.9f,
                    snapshot.pairCapacity() + "/t x " + snapshot.lanes().size(), minecraft);
        }
    }

    private static void submitLabel(SubmitNodeCollector collector, PoseStack poseStack,
                                    CameraRenderState camera, Vec3 cameraPos, long pos, float lift, String text,
                                    Minecraft minecraft) {
        poseStack.pushPose();
        poseStack.translate(
                BlockPos.getX(pos) + 0.5 - cameraPos.x,
                BlockPos.getY(pos) + lift - cameraPos.y,
                BlockPos.getZ(pos) + 0.5 - cameraPos.z);
        poseStack.mulPose(camera.orientation);
        poseStack.scale(0.025f, -0.025f, 0.025f);
        float width = minecraft.font.width(text);
        collector.submitText(poseStack, -width / 2f, 0f,
                Component.literal(text).getVisualOrderText(), false,
                Font.DisplayMode.SEE_THROUGH, LightCoordsUtil.FULL_BRIGHT, 0xFFFFFFFF, 0x66000000, 0);
        poseStack.popPose();
    }
}
