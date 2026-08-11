package net.ptcrys.topo.api.pipe.survey;

import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.network.PipeNodeRecord;
import net.ptcrys.topo.api.pipe.network.PipeSurveyAnalyzer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管网勘测的服务端权威状态:按玩家持有活动勘测(维度/锚点/范围/端点 A/B),物品交互在
 * 服务端直接驱动状态变更并回发快照;客户端只渲染快照、按 20t 轮询刷新(空包 C2S),
 * 不持仪/越限刷新即清。主线程独占。
 *
 * <p>
 * 同步设计:管道图纯服务端(SavedData+运行时图,无 DataField 通道),勘测走独立的
 * 请求-快照协议——{@link PipeSurveySnapshot} 按范围裁剪、自带占用/端口/车道,客户端
 * 80t 无刷新自然淡出,服务端无需追踪客户端叠加层生命周期。
 */
public final class PipeSurveyManager {

    /** 勘测车道数上限(分析与渲染共用)。 */
    public static final int MAX_LANES = 6;
    /** 两次快照下发的最小间隔(tick),限频客户端轮询。 */
    private static final int MIN_SEND_INTERVAL = 10;

    private static final Map<UUID, Survey> SURVEYS = new HashMap<>();

    /** 一名玩家的活动勘测;端点选择循环 A→B→重选 A。 */
    public static final class Survey {

        public final ResourceKey<Level> dimension;
        public final long anchor;
        public final int range;
        public long pointA = PipeSurveySnapshot.NO_POINT;
        public long pointB = PipeSurveySnapshot.NO_POINT;
        long lastSendGameTime = Long.MIN_VALUE;

        Survey(ResourceKey<Level> dimension, long anchor, int range) {
            this.dimension = dimension;
            this.anchor = anchor;
            this.range = range;
        }
    }

    private PipeSurveyManager() {}

    /** 锚定并激活勘测:重置端点选择,立即下发首帧快照。服务端线程调用(物品 useOn)。 */
    public static void activate(net.minecraft.world.entity.player.Player player, BlockPos pos, int range) {
        Survey survey = new Survey(player.level().dimension(), pos.asLong(), range);
        SURVEYS.put(player.getUUID(), survey);
        player.sendOverlayMessage(
                TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_ANCHORED.getComponent(range));
        send(player, survey);
    }

    /** 端点选择循环:无活动勘测时提示先锚定。 */
    public static void selectPoint(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        Survey survey = SURVEYS.get(player.getUUID());
        if (survey == null || player.level().dimension() != survey.dimension) {
            player.sendOverlayMessage(
                    TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR.getComponent());
            return;
        }
        long packed = pos.asLong();
        if (survey.pointA == PipeSurveySnapshot.NO_POINT || survey.pointB != PipeSurveySnapshot.NO_POINT) {
            survey.pointA = packed;
            survey.pointB = PipeSurveySnapshot.NO_POINT;
            player.sendOverlayMessage(
                    TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_POINT_A.getComponent());
        } else if (packed != survey.pointA) {
            survey.pointB = packed;
            player.sendOverlayMessage(
                    TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_POINT_B.getComponent());
        }
        survey.lastSendGameTime = Long.MIN_VALUE; // 选点立即可见,不受限频
        send(player, survey);
    }

    /** 结束勘测并让客户端清屏。 */
    public static void clear(net.minecraft.world.entity.player.Player player) {
        if (SURVEYS.remove(player.getUUID()) != null) {
            player.sendOverlayMessage(
                    TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_CLEARED.getComponent());
            sendPayload(player, PipeSurveySnapshot.empty());
        }
    }

    /** 玩家退出清状态(防 UUID 表残留)。 */
    public static void onLoggedOut(ServerPlayer player) {
        SURVEYS.remove(player.getUUID());
    }

    /**
     * 客户端轮询入口:限频重建快照;不再手持勘测仪(主/副手谓词由物品侧给定)或换维度
     * 即清状态,客户端随之淡出。
     */
    public static void refresh(ServerPlayer player, java.util.function.Predicate<ServerPlayer> stillHolding) {
        Survey survey = SURVEYS.get(player.getUUID());
        if (survey == null) {
            return;
        }
        if (!stillHolding.test(player) || player.level().dimension() != survey.dimension) {
            SURVEYS.remove(player.getUUID());
            sendPayload(player, PipeSurveySnapshot.empty());
            return;
        }
        long gameTime = player.level().getGameTime();
        if (survey.lastSendGameTime != Long.MIN_VALUE && gameTime - survey.lastSendGameTime < MIN_SEND_INTERVAL) {
            return;
        }
        send(player, survey);
    }

    /** 测试探针:玩家当前活动勘测。 */
    public static @Nullable Survey activeSurvey(UUID player) {
        return SURVEYS.get(player);
    }

    private static void send(net.minecraft.world.entity.player.Player player, Survey survey) {
        survey.lastSendGameTime = player.level().getGameTime();
        if (player.level() instanceof ServerLevel level) {
            sendPayload(player, buildSnapshot(level, survey));
        }
    }

    private static void sendPayload(net.minecraft.world.entity.player.Player player, PipeSurveySnapshot snapshot) {
        // 模拟玩家(gametest)非 ServerPlayer 或无网络连接:状态机照常,只跳过真实下发。
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            PacketDistributor.sendToPlayer(serverPlayer, new PipeSurveySnapshotS2CPayload(snapshot));
        }
    }

    /** 按勘测范围裁剪构建一帧快照;锚点已无网络时返回空帧。测试直调验证服务端语义。 */
    public static PipeSurveySnapshot buildSnapshot(ServerLevel level, Survey survey) {
        PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
        PipeNetwork<?> network = runtime.networkAt(BlockPos.of(survey.anchor));
        if (network == null) {
            return PipeSurveySnapshot.empty();
        }
        int anchorX = BlockPos.getX(survey.anchor);
        int anchorY = BlockPos.getY(survey.anchor);
        int anchorZ = BlockPos.getZ(survey.anchor);
        long[] all = network.nodePositions();
        List<Long> inRange = new ArrayList<>();
        boolean truncated = false;
        for (long pos : all) {
            int dx = Math.abs(BlockPos.getX(pos) - anchorX);
            int dy = Math.abs(BlockPos.getY(pos) - anchorY);
            int dz = Math.abs(BlockPos.getZ(pos) - anchorZ);
            if (Math.max(dx, Math.max(dy, dz)) > survey.range) {
                continue;
            }
            if (inRange.size() >= PipeSurveySnapshot.MAX_NODES) {
                truncated = true;
                break;
            }
            inRange.add(pos);
        }
        int count = inRange.size();
        long[] nodePos = new long[count];
        int[] nodeThroughput = new int[count];
        long[] nodeUsed = new long[count];
        short[] nodePortBits = new short[count];
        for (int i = 0; i < count; i++) {
            long pos = inRange.get(i);
            int index = network.nodeIndexOf(pos);
            nodePos[i] = pos;
            nodeThroughput[i] = index >= 0 ? network.throughputOf(index) : 0;
            nodeUsed[i] = index >= 0 ? network.windowNodeUsedLast(index) : 0;
            nodePortBits[i] = portBitsOf(runtime, pos);
        }
        long pointA = network.nodeIndexOf(survey.pointA) >= 0 ? survey.pointA : PipeSurveySnapshot.NO_POINT;
        long pointB = network.nodeIndexOf(survey.pointB) >= 0 ? survey.pointB : PipeSurveySnapshot.NO_POINT;
        List<PipeSurveySnapshot.Lane> lanes = List.of();
        int pairCapacity = 0;
        if (pointA != PipeSurveySnapshot.NO_POINT && pointB != PipeSurveySnapshot.NO_POINT) {
            PipeSurveyAnalyzer.Lanes analyzed = PipeSurveyAnalyzer.analyze(network, pointA, pointB, MAX_LANES);
            List<PipeSurveySnapshot.Lane> built = new ArrayList<>(analyzed.lanePositions().size());
            for (int l = 0; l < analyzed.lanePositions().size(); l++) {
                built.add(new PipeSurveySnapshot.Lane(
                        analyzed.lanePositions().get(l), analyzed.bottleneckOrdinals()[l]));
            }
            lanes = List.copyOf(built);
            pairCapacity = analyzed.capacity();
        }
        return new PipeSurveySnapshot(survey.anchor, network.displayWindowTicks(), truncated,
                nodePos, nodeThroughput, nodeUsed, nodePortBits, pointA, pointB, lanes, pairCapacity);
    }

    private static short portBitsOf(PipeLevelRuntime runtime, long pos) {
        PipeNodeRecord record = runtime.data().node(pos);
        if (record == null) {
            return 0;
        }
        int extractMask = 0;
        int destinationMask = 0;
        for (Direction direction : Direction.values()) {
            PipeSideRole role = record.role(direction);
            if (role == PipeSideRole.EXTRACT) {
                extractMask |= 1 << direction.ordinal();
            } else if (role == PipeSideRole.DESTINATION) {
                destinationMask |= 1 << direction.ordinal();
            }
        }
        return PipeSurveySnapshot.portBits(extractMask, destinationMask);
    }
}
