package net.ptcrys.topo.api.pipe;

/**
 * Aggregation interval envelope of one strategy mounted on one pipe — all four numbers are
 * registration-time literals written out at the registration site (no global constants, no
 * implicit values). {@code initialInterval} is what a freshly created extraction port starts
 * with; {@code coarseStep} is the big-step size of the port screen's adjuster.
 *
 * <p>
 * Aggregation semantics: a port executes once every {@code interval} ticks and moves
 * {@code rate x interval} in one batch; node ledgers account the batch spread over the next
 * {@code interval} ticks, so instantaneous per-tick node limits never inflate.
 */
public record AggregationWindow(int minInterval, int maxInterval, int initialInterval, int coarseStep) {

    public AggregationWindow {
        if (minInterval < 1) {
            throw new IllegalArgumentException("minInterval must be >= 1 (was " + minInterval + ")");
        }
        if (maxInterval < minInterval) {
            throw new IllegalArgumentException(
                    "maxInterval " + maxInterval + " must be >= minInterval " + minInterval);
        }
        if (initialInterval < minInterval || initialInterval > maxInterval) {
            throw new IllegalArgumentException(
                    "initialInterval " + initialInterval + " must be within [" + minInterval + ", " + maxInterval + "]");
        }
        if (coarseStep < 1) {
            throw new IllegalArgumentException("coarseStep must be >= 1 (was " + coarseStep + ")");
        }
    }

    /** Clamp an arbitrary interval into this window. */
    public int clamp(int interval) {
        return Math.clamp(interval, minInterval, maxInterval);
    }

    /** True when the window is a single value — the port screen renders a read-only label. */
    public boolean fixed() {
        return minInterval == maxInterval;
    }
}
