package net.ptcrys.topo.api.pipe;

import java.util.Objects;

/**
 * One entry of a pipe's strategy offer list: the strategy handle plus the aggregation-interval
 * envelope it gets on this specific pipe. Offer order is GUI order; the first offer is what a
 * freshly created extraction port starts with. Offers are registration literals — never
 * persisted, never defaulted: every {@code Pipes} registration line provides them explicitly.
 */
public record PipeStrategyOffer(PipeDistributionStrategy strategy, AggregationWindow aggregation) {

    public PipeStrategyOffer {
        Objects.requireNonNull(strategy, "pipe distribution strategy");
        Objects.requireNonNull(aggregation, "aggregation window");
    }
}
