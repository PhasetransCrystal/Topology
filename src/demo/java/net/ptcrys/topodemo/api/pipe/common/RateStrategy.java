package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.pipe.AggregationWindow;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;

import net.minecraft.resources.Identifier;

import com.mojang.serialization.MapCodec;

public abstract sealed class RateStrategy extends LangBoundStrategy
                                          permits EqualSplitStrategy, RoundRobinStrategy {

    private final Identifier id;
    private final MapCodec<RateConfig> codec;

    RateStrategy(Identifier id) {
        this.id = id;
        this.codec = RateConfig.codec(this);
    }

    @Override
    public Identifier id() {
        return id;
    }

    @Override
    public MapCodec<? extends PipePortStrategyConfig> configCodec() {
        return codec;
    }

    @Override
    public PipePortStrategyConfig initialPortConfig(PipeDefinition definition, AggregationWindow aggregation) {
        int interval = aggregation.initialInterval();
        return new RateConfig(this, definition.maxBatchAmount(interval), interval);
    }

    @Override
    public int budget(PipeDefinition definition, PipePortStrategyConfig config) {
        return config instanceof RateConfig rateConfig ? rateConfig.amount() : definition.maxBatchAmount(config.interval());
    }
}
