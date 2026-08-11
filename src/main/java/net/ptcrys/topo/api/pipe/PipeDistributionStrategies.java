package net.ptcrys.topo.api.pipe;

import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import com.mojang.serialization.Codec;

import java.util.List;
import java.util.Objects;

/**
 * Owner facade for the pipe distribution strategy table (K = {@link Identifier},
 * H == S = {@link PipeDistributionStrategy}). Builtins contribute through
 * {@code BuiltinTopoPipeDistributionStrategies}; addons use the same {@link #register} before
 * freeze.
 */
public final class PipeDistributionStrategies {

    private static final FreezableStrategyRegistry<Identifier, PipeDistributionStrategy, PipeDistributionStrategy> REGISTRY = FreezableStrategyRegistry.create("pipe_distribution_strategies");

    /**
     * Port config codec, dispatched on the owning strategy's stable ID. Decoding a config whose
     * strategy is no longer registered fails that single entry; the saved-data layer drops it and
     * the port falls back to its definition's default strategy on next access.
     */
    public static final Codec<PipePortStrategyConfig> CONFIG_CODEC = Identifier.CODEC.dispatch(
            "strategy",
            config -> config.strategy().id(),
            id -> REGISTRY.require(id).configCodec());

    private PipeDistributionStrategies() {}

    public static PipeDistributionStrategy register(PipeDistributionStrategy strategy) {
        Objects.requireNonNull(strategy, "pipe distribution strategy");
        Identifier id = Objects.requireNonNull(strategy.id(), "strategy id");
        return REGISTRY.register(id, strategy, strategy);
    }

    public static PipeDistributionStrategy require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<PipeDistributionStrategy> registered() {
        return REGISTRY.handlesView();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
