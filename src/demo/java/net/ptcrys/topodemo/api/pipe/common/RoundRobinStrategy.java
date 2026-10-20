package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.pipe.*;

import net.minecraft.resources.Identifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

/** Visits each destination once, preserving the cursor between batches. */
public record RoundRobinStrategy(Identifier id, LangKey nameLang, LangKey descriptionLang, LangKey shortNameLang)
        implements PipeDistributionStrategy {

    public RoundRobinStrategy {
        Objects.requireNonNull(id);
        Objects.requireNonNull(nameLang);
        Objects.requireNonNull(descriptionLang);
        Objects.requireNonNull(shortNameLang);
    }

    public record Config(PipeDistributionStrategy strategy, int amount, int interval) implements PipePortStrategyConfig {

        @Override
        public int amountOr(int fallback) {
            return amount;
        }

        @Override
        public Config withAmount(int value) {
            return new Config(strategy, value, interval);
        }

        @Override
        public Config withInterval(int value) {
            return new Config(strategy, amount, value);
        }
    }

    @Override
    public MapCodec<Config> configCodec() {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("amount").forGetter(Config::amount),
                Codec.INT.fieldOf("interval").forGetter(Config::interval))
                .apply(instance, (amount, interval) -> new Config(this, amount, interval)));
    }

    @Override
    public Config initialPortConfig(PipeDefinition definition, AggregationWindow window) {
        return new Config(this, definition.maxExtractRate() * window.initialInterval(), window.initialInterval());
    }

    @Override
    public void distribute(PipeDistributionContext context) {
        int count = context.destinationCount();
        if (count == 0) return;
        int start = Math.floorMod(context.cursor(), count);
        boolean served = false;
        for (int offset = 0; offset < count && context.budgetRemaining() > 0; offset++) {
            int target = (start + offset) % count;
            int accepted = context.destinationAcceptance(target);
            if (accepted > 0 && context.transfer(target, Math.min(context.budgetRemaining(), accepted)) > 0) {
                if (!served) {
                    context.setCursor((target + 1) % count);
                    served = true;
                }
            }
        }
    }
}
