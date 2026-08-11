package net.ptcrys.topo.api.pipe;

/**
 * Marker for one extraction port's strategy-owned configuration. Each
 * {@link PipeDistributionStrategy} defines its own immutable config record plus the codec that
 * persists it inside the pipe saved data.
 *
 * <p>
 * Configs are validated/clamped against the port's {@link PipeStrategyOffer} on decode and on
 * GUI submission; switching a port to another strategy resets the config to that strategy's
 * {@code initialPortConfig}.
 */
public interface PipePortStrategyConfig {

    /** The owning strategy; also the codec dispatch key. */
    PipeDistributionStrategy strategy();

    /**
     * Aggregation interval in ticks: the port executes once per interval moving one batch of up
     * to {@link #amountOr}. Always within the offer's window after the runtime clamp step.
     */
    int interval();

    /** A copy with the given aggregation interval. */
    PipePortStrategyConfig withInterval(int interval);

    /**
     * The configured per-window amount — how much one batch may move per {@link #interval} —
     * or {@code fallback} for configs without an amount concept. Clamped by the runtime into
     * {@code [0, maxExtractRate × interval]}, so sub-per-tick effective rates are expressible
     * (e.g. 30 per 20 ticks = 1.5/t).
     */
    default int amountOr(int fallback) {
        return fallback;
    }

    /**
     * A copy with the given per-window amount; configs without an amount concept return
     * themselves. The port screen's amount buttons go through this, so the engine stays
     * strategy-agnostic.
     */
    default PipePortStrategyConfig withAmount(int amount) {
        return this;
    }
}
