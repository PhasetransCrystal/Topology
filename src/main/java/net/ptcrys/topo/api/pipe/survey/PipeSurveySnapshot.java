package net.ptcrys.topo.api.pipe.survey;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * 一帧管网勘测快照:服务端按勘测范围裁剪后发给客户端渲染的全部数据。节点四列平行数组
 * (位置/逐 tick 吞吐/上一展示窗口占用/端口位),可选端点 A/B 与两点间车道(节点位置序列
 * + 瓶颈下标)及静态容量。空节点表 = 清屏指令。纯原语编码,无注册表依赖。
 */
public record PipeSurveySnapshot(long anchor, int windowTicks, boolean truncated,
                                 long[] nodePos, int[] nodeThroughput, long[] nodeUsed, short[] nodePortBits,
                                 long pointA, long pointB, List<Lane> lanes, int pairCapacity) {

    /** 端点未选择的哨兵。 */
    public static final long NO_POINT = Long.MIN_VALUE;
    /** 单帧快照节点硬上限(防爆包);超限置 truncated。 */
    public static final int MAX_NODES = 2048;

    /** 一条 A→B 车道:节点位置序列(A 端在前)与瓶颈节点在序列中的下标。 */
    public record Lane(long[] positions, int bottleneckOrdinal) {}

    public static PipeSurveySnapshot empty() {
        return new PipeSurveySnapshot(0L, 1, false,
                new long[0], new int[0], new long[0], new short[0],
                NO_POINT, NO_POINT, List.of(), 0);
    }

    /** 有节点即视为激活;空快照让客户端清屏淡出。 */
    public boolean active() {
        return nodePos.length > 0;
    }

    /** 端口位编码:低 6 位=抽取面(Direction ordinal),高 6 位=接收面。 */
    public static short portBits(int extractMask, int destinationMask) {
        return (short) ((extractMask & 0x3F) | ((destinationMask & 0x3F) << 6));
    }

    public static int extractMask(short bits) {
        return bits & 0x3F;
    }

    public static int destinationMask(short bits) {
        return (bits >> 6) & 0x3F;
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeLong(anchor);
        buffer.writeVarInt(windowTicks);
        buffer.writeBoolean(truncated);
        buffer.writeVarInt(nodePos.length);
        for (int i = 0; i < nodePos.length; i++) {
            buffer.writeLong(nodePos[i]);
            buffer.writeVarInt(nodeThroughput[i]);
            buffer.writeVarLong(nodeUsed[i]);
            buffer.writeShort(nodePortBits[i]);
        }
        buffer.writeLong(pointA);
        buffer.writeLong(pointB);
        buffer.writeVarInt(lanes.size());
        for (Lane lane : lanes) {
            buffer.writeVarInt(lane.positions.length);
            for (long position : lane.positions) {
                buffer.writeLong(position);
            }
            buffer.writeVarInt(lane.bottleneckOrdinal);
        }
        buffer.writeVarInt(pairCapacity);
    }

    public static PipeSurveySnapshot read(FriendlyByteBuf buffer) {
        long anchor = buffer.readLong();
        int windowTicks = buffer.readVarInt();
        boolean truncated = buffer.readBoolean();
        int count = Math.min(buffer.readVarInt(), MAX_NODES);
        long[] nodePos = new long[count];
        int[] nodeThroughput = new int[count];
        long[] nodeUsed = new long[count];
        short[] nodePortBits = new short[count];
        for (int i = 0; i < count; i++) {
            nodePos[i] = buffer.readLong();
            nodeThroughput[i] = buffer.readVarInt();
            nodeUsed[i] = buffer.readVarLong();
            nodePortBits[i] = buffer.readShort();
        }
        long pointA = buffer.readLong();
        long pointB = buffer.readLong();
        int laneCount = Math.min(buffer.readVarInt(), 16);
        List<Lane> lanes = new ArrayList<>(laneCount);
        for (int l = 0; l < laneCount; l++) {
            int length = Math.min(buffer.readVarInt(), MAX_NODES);
            long[] positions = new long[length];
            for (int i = 0; i < length; i++) {
                positions[i] = buffer.readLong();
            }
            lanes.add(new Lane(positions, buffer.readVarInt()));
        }
        return new PipeSurveySnapshot(anchor, windowTicks, truncated,
                nodePos, nodeThroughput, nodeUsed, nodePortBits,
                pointA, pointB, List.copyOf(lanes), buffer.readVarInt());
    }
}
