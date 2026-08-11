package net.ptcrys.topo.api.pipe.network;

import java.util.Arrays;

/**
 * Per-network future reservation ledger: a ring of {@code window} future ticks, each column
 * holding per-node reserved flow. An aggregation batch never inflates any instantaneous limit —
 * it reserves {@code amount} spread (evenly, headroom-capped) over the next {@code interval}
 * ticks, so per-tick node flow stays {@code <= throughput} and ports of different intervals
 * compete on every future tick exactly like per-tick ports used to ("no surprise buffer tank").
 *
 * <p>
 * Pure array math, no MC types: unit-tested directly. The window length is a constructor
 * argument derived from registration data (never a constant). Advancing is lazy: callers
 * {@code advance(now)} before peeking/committing; idle networks never touch the arrays.
 */
final class PipeFutureLedger {

    private final int[] throughput;
    private final int nodeCount;
    private final int window;
    /** {@code column(tick) * nodeCount + node}; column = floorMod(tick, window). */
    private final int[] reserved;
    private final int[] headroomScratch;
    private long baseTick = Long.MIN_VALUE;

    PipeFutureLedger(int[] throughput, int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be >= 1 (was " + window + ")");
        }
        this.throughput = throughput;
        this.nodeCount = throughput.length;
        this.window = window;
        this.reserved = new int[window * nodeCount];
        this.headroomScratch = new int[window];
    }

    int window() {
        return window;
    }

    /** Roll the ring to {@code now}: columns of expired ticks become fresh future columns. */
    void advance(long now) {
        if (baseTick == Long.MIN_VALUE || now - baseTick >= window) {
            Arrays.fill(reserved, 0);
            baseTick = now;
            return;
        }
        for (long tick = baseTick; tick < now; tick++) {
            int column = (int) Math.floorMod(tick, window);
            Arrays.fill(reserved, column * nodeCount, (column + 1) * nodeCount, 0);
        }
        if (now > baseTick) {
            baseTick = now;
        }
    }

    /**
     * Headroom over the next {@code interval} ticks along {@code path} (node indices, both
     * endpoints included), capped at {@code requested}. Fills the per-tick scratch a following
     * {@link #commit} relies on — call pairs must not interleave with other peeks.
     *
     * <p>
     * {@code share} is the count of extraction ports on the network. Each node tick applies
     * TWO ceilings taken together: total room ({@code throughput - reserved}, the hard physical
     * limit) and the batch's own fair slice ({@code throughput / share}). The self-limit makes
     * an early booker leave room that later ports' own slices exactly claim — no port can
     * monopolize a saturated shared node, and a single-port network ({@code share == 1}) keeps
     * the full ceiling bit for bit. The cost is honest and documented: with N ports declared,
     * one batch never books more than 1/N of a node tick even while the others idle.
     */
    int peekAvailable(long now, int[] path, int pathLength, int interval, int requested, int share) {
        return peekAvailable(now, path, pathLength, interval, requested, share, null);
    }

    /**
     * 带"本批已落账"记账的询价:{@code alreadyBooked}(按节点下标,可空)累计同一批此前各车道
     * 已提交的量,每个节点的批内总额被钳到 {@code 公平片 × interval − 已落账}——多车道批次
     * 合计仍守住"单批不超份额"的公平不变量(逐 tick 精确度由均匀摊布保持,贪婪补余受物理
     * 余量兜底)。
     */
    int peekAvailable(long now, int[] path, int pathLength, int interval, int requested, int share,
                      int[] alreadyBooked) {
        if (interval < 1 || interval > window) {
            throw new IllegalArgumentException(
                    "interval " + interval + " outside ledger window " + window);
        }
        int divisor = Math.max(1, share);
        advance(now);
        int bounded = requested;
        if (alreadyBooked != null) {
            for (int p = 0; p < pathLength && bounded > 0; p++) {
                int node = path[p];
                int sliceTotal = Math.max(1, throughput[node] / divisor) * interval - alreadyBooked[node];
                if (sliceTotal < bounded) {
                    bounded = Math.max(0, sliceTotal);
                }
            }
            if (bounded <= 0) {
                return 0;
            }
        }
        long total = 0;
        int column = (int) Math.floorMod(now, window);
        for (int k = 0; k < interval; k++) {
            int columnBase = column * nodeCount;
            int headroom = Integer.MAX_VALUE;
            for (int p = 0; p < pathLength; p++) {
                int node = path[p];
                int room = throughput[node] - reserved[columnBase + node];
                int slice = Math.max(1, throughput[node] / divisor);
                headroom = Math.min(headroom, Math.min(room, slice));
                if (headroom <= 0) {
                    headroom = 0;
                    break;
                }
            }
            headroomScratch[k] = headroom;
            total += headroom;
            column = column + 1 == window ? 0 : column + 1;
        }
        return (int) Math.min(total, bounded);
    }

    /**
     * Reserve {@code amount} (must be {@code <=} the preceding peek) over the same ticks:
     * first pass spreads evenly (ceil of the remainder over the ticks left, headroom-capped),
     * second pass greedily places whatever the cap pushed out.
     */
    void commit(long now, int[] path, int pathLength, int interval, int amount) {
        int remaining = amount;
        int column = (int) Math.floorMod(now, window);
        for (int k = 0; k < interval && remaining > 0; k++) {
            int ticksLeft = interval - k;
            int target = (remaining + ticksLeft - 1) / ticksLeft;
            int grant = Math.min(target, headroomScratch[k]);
            if (grant > 0) {
                writeColumn(column * nodeCount, path, pathLength, grant);
                headroomScratch[k] -= grant;
                remaining -= grant;
            }
            column = column + 1 == window ? 0 : column + 1;
        }
        column = (int) Math.floorMod(now, window);
        for (int k = 0; k < interval && remaining > 0; k++) {
            int grant = Math.min(remaining, headroomScratch[k]);
            if (grant > 0) {
                writeColumn(column * nodeCount, path, pathLength, grant);
                headroomScratch[k] -= grant;
                remaining -= grant;
            }
            column = column + 1 == window ? 0 : column + 1;
        }
        if (remaining > 0) {
            throw new IllegalStateException(
                    "Pipe ledger over-commit: " + remaining + " of " + amount + " did not fit");
        }
    }

    /**
     * 增广车道的可通行判定:未来 {@code interval} 个 tick 内任一 tick 该节点仍有物理余量
     * ({@code reserved < throughput})。故意不应用公平份额上限——份额至少为 1,永远不会把
     * 一个有物理余量的节点判成不可通行;额度的精确钳制仍由随后的 peek/commit 对负责。
     */
    boolean hasHeadroom(long now, int node, int interval) {
        advance(now);
        for (int k = 0; k < interval; k++) {
            int columnBase = (int) Math.floorMod(now + k, window) * nodeCount;
            if (reserved[columnBase + node] < throughput[node]) {
                return true;
            }
        }
        return false;
    }

    private void writeColumn(int columnBase, int[] path, int pathLength, int grant) {
        for (int p = 0; p < pathLength; p++) {
            reserved[columnBase + path[p]] += grant;
        }
    }

    /**
     * 把未来 {@code interval} 个 tick 的列基址写入 {@code out}(先 advance);返回写入数。
     * 残量搜索整轮复用,免去每节点每 tick 的 floorMod 长除法。
     */
    int headroomBases(long now, int interval, int[] out) {
        advance(now);
        int column = (int) Math.floorMod(now, window);
        for (int k = 0; k < interval; k++) {
            out[k] = column * nodeCount;
            column = column + 1 == window ? 0 : column + 1;
        }
        return interval;
    }

    /** {@link #hasHeadroom} 的预解析列基址变体:任一列仍有物理余量即可通行。 */
    boolean hasHeadroomAt(int node, int[] columnBases, int count) {
        int cap = throughput[node];
        for (int k = 0; k < count; k++) {
            if (reserved[columnBases[k] + node] < cap) {
                return true;
            }
        }
        return false;
    }

    /** Test/diagnostic read of one node's reservation {@code offset} ticks after {@code now}. */
    int reservedAt(long now, int node, int offset) {
        return reserved[(int) Math.floorMod(now + offset, window) * nodeCount + node];
    }
}
