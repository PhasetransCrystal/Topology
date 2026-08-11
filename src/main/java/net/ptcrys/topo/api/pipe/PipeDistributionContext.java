package net.ptcrys.topo.api.pipe;

/**
 * Engine-provided view of one extraction port's tick, handed to
 * {@link PipeDistributionStrategy#distribute}. The strategy only decides ordering and amounts;
 * the engine owns capability access, path bucketing, transactions and ledger accounting.
 *
 * <p>
 * Destinations are indexed {@code 0..destinationCount()-1} and pre-sorted by BFS distance
 * from this extraction port, so index order is "nearest first". Unloaded or vanished
 * destinations simply transfer 0; they stay listed to keep cursor fairness stable.
 */
public interface PipeDistributionContext {

    PipeDefinition definition();

    PipePortStrategyConfig config();

    /** Remaining extraction budget this tick; starts at the strategy's clamped effective rate. */
    int budgetRemaining();

    /**
     * How much the source can actually yield this tick (probed once per port tick and cached).
     * Share-based strategies must size shares from {@code min(budgetRemaining, sourceAvailable)}:
     * splitting the nominal budget lets the first destination swallow a scarce supply whole.
     */
    int sourceAvailable();

    int destinationCount();

    /** Stable config key of the destination port ("x,y,z,side"), available to strategies that need identity. */
    String destinationKey(int index);

    /**
     * Read-only upper bound on what the destination could accept right now, capped at the
     * remaining budget; 0 means it cannot receive (unloaded, full, or an output-only view).
     * Share-based strategies divide among destinations with a positive bound, so dead
     * destinations stop eating shares. Probed with the source's first available resource.
     */
    int destinationAcceptance(int index);

    /**
     * Try to move up to {@code maxAmount} units to the destination. The engine clamps by the
     * remaining budget and the path bucket (min remaining node throughput along the route),
     * executes one atomic transaction and books the ledger. Returns the amount actually moved.
     */
    int transfer(int index, int maxAmount);

    /** Per-port persistent-for-this-session round-robin cursor (not saved to disk). */
    int cursor();

    void setCursor(int cursor);
}
