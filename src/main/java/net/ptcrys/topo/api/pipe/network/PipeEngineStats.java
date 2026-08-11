package net.ptcrys.topo.api.pipe.network;

import java.util.Locale;

/**
 * Probe-only phase accumulators for the pipe engine hot path. Disabled (single branch per phase)
 * unless the in-game probe flips {@link #enabled}; never enabled in normal play or tests.
 */
public final class PipeEngineStats {

    public static volatile boolean enabled;

    public static long capabilityNanos;
    public static long gateNanos;
    public static long prepareNanos;
    public static long bucketNanos;
    /** 增广车道残量 BFS 的搜索耗时(仅批预算超出主车道额度时发生)。 */
    public static long routeNanos;
    public static long transactionNanos;
    public static long bookNanos;
    public static long transactions;
    public static long portTicks;
    public static long openNanos;
    public static long extractNanos;
    public static long insertNanos;
    public static long commitNanos;
    public static long boundNanos;
    public static long directMoves;
    public static long directSkipType;
    public static long directSkipReady;
    public static long directSkipLifecycle;

    private PipeEngineStats() {}

    public static String snapshotAndReset() {
        String line = String.format(Locale.ROOT,
                "phases(ns): capability=%d gate=%d prepare=%d bucket=%d route=%d transaction=%d book=%d tx=%d direct=%d" + " portTicks=%d | tx-breakdown: bound=%d open=%d extract=%d insert=%d commit=%d" + " | directSkip: type=%d ready=%d lifecycle=%d",
                capabilityNanos, gateNanos, prepareNanos, bucketNanos, routeNanos, transactionNanos, bookNanos,
                transactions, directMoves, portTicks, boundNanos, openNanos, extractNanos, insertNanos, commitNanos,
                directSkipType, directSkipReady, directSkipLifecycle);
        capabilityNanos = 0;
        gateNanos = 0;
        prepareNanos = 0;
        bucketNanos = 0;
        routeNanos = 0;
        transactionNanos = 0;
        bookNanos = 0;
        transactions = 0;
        portTicks = 0;
        boundNanos = 0;
        openNanos = 0;
        extractNanos = 0;
        insertNanos = 0;
        commitNanos = 0;
        directMoves = 0;
        directSkipType = 0;
        directSkipReady = 0;
        directSkipLifecycle = 0;
        return line;
    }
}
