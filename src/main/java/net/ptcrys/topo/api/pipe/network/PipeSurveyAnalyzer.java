package net.ptcrys.topo.api.pipe.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 勘测仪的两点静态容量分析:在网络邻接上做贪心最短路 + 点不相交迭代(与引擎增广车道同族
 * 但**只读不记账**,容量取节点逐 tick 吞吐,不看账本实时占用——实时占用由节点配色表达,
 * 两者互补)。每条车道的瓶颈 = 吞吐最小节点;pairCapacity = Σ 车道瓶颈吞吐。冷路径,
 * 每次分析自带分配,不污染引擎 scratch。
 */
public final class PipeSurveyAnalyzer {

    /** 车道集:每条为节点位置序列(A 端在前)+ 瓶颈下标;capacity = Σ 瓶颈吞吐。 */
    public record Lanes(List<long[]> lanePositions, int[] bottleneckOrdinals, int capacity) {

        public static final Lanes EMPTY = new Lanes(List.of(), new int[0], 0);
    }

    private PipeSurveyAnalyzer() {}

    public static Lanes analyze(PipeNetwork<?> network, long aPos, long bPos, int maxLanes) {
        int start = network.nodeIndexOf(aPos);
        int target = network.nodeIndexOf(bPos);
        if (start < 0 || target < 0 || start == target) {
            return Lanes.EMPTY;
        }
        int count = network.nodeCount();
        boolean[] consumed = new boolean[count];
        int[] previous = new int[count];
        int[] queue = new int[count];
        boolean[] seen = new boolean[count];
        List<long[]> lanePositions = new ArrayList<>();
        List<Integer> bottlenecks = new ArrayList<>();
        int capacity = 0;
        for (int lane = 0; lane < maxLanes; lane++) {
            Arrays.fill(seen, false);
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            boolean reached = false;
            while (head < tail && !reached) {
                int current = queue[head++];
                for (int d = 0; d < 6; d++) {
                    int neighbor = network.neighborOf(current, d);
                    if (neighbor < 0 || seen[neighbor] || consumed[neighbor]) {
                        continue;
                    }
                    seen[neighbor] = true;
                    previous[neighbor] = current;
                    if (neighbor == target) {
                        reached = true;
                        break;
                    }
                    queue[tail++] = neighbor;
                }
            }
            if (!reached) {
                break;
            }
            // 回溯成 A 端在前的下标序列;中间节点标记占用保证车道点不相交(端点可复用)。
            int length = 1;
            for (int node = target; node != start; node = previous[node]) {
                length++;
            }
            int[] laneNodes = new int[length];
            int write = length - 1;
            for (int node = target; node != start; node = previous[node]) {
                laneNodes[write--] = node;
            }
            laneNodes[0] = start;
            long[] positions = new long[length];
            int bottleneck = 0;
            int bottleneckThroughput = Integer.MAX_VALUE;
            for (int i = 0; i < length; i++) {
                int node = laneNodes[i];
                positions[i] = network.nodePositions()[node];
                int throughput = network.throughputOf(node);
                if (throughput < bottleneckThroughput) {
                    bottleneckThroughput = throughput;
                    bottleneck = i;
                }
                if (i != 0 && i != length - 1) {
                    consumed[node] = true;
                }
            }
            lanePositions.add(positions);
            bottlenecks.add(bottleneck);
            capacity += bottleneckThroughput == Integer.MAX_VALUE ? 0 : bottleneckThroughput;
        }
        int[] bottleneckOrdinals = new int[bottlenecks.size()];
        for (int i = 0; i < bottleneckOrdinals.length; i++) {
            bottleneckOrdinals[i] = bottlenecks.get(i);
        }
        return new Lanes(List.copyOf(lanePositions), bottleneckOrdinals, capacity);
    }
}
