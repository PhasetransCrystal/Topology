package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.pipe.PipeDistributionStrategy;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Shared amount+interval config of the single-knob strategies. */
public record RateConfig(PipeDistributionStrategy strategy, int amount, int interval)
        implements PipePortStrategyConfig {

    static MapCodec<RateConfig> codec(PipeDistributionStrategy strategy) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("amount").forGetter(RateConfig::amount),
                Codec.INT.fieldOf("interval").forGetter(RateConfig::interval)).apply(instance, (amount, interval) -> new RateConfig(strategy, amount, interval)));
    }

    @Override
    public int amountOr(int fallback) {
        return amount;
    }

    @Override
    public PipePortStrategyConfig withAmount(int newAmount) {
        return new RateConfig(strategy, newAmount, interval);
    }

    @Override
    public PipePortStrategyConfig withInterval(int newInterval) {
        return new RateConfig(strategy, amount, newInterval);
    }
}
