package net.ptcrys.topo.api.ore.policy;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * KHS handle for air-exposure policy: decides whether an air-adjacent ore block is discarded, and
 * contributes its own JEI label (no path-string switch in the panel).
 */
public final class OreAirExposurePolicy {

    public interface Strategy {

        boolean discardExposed(double discardChance, double randomSample);

        /** JEI / info-card label for the configured discard chance. */
        Component describe(double discardChance);
    }

    private final Identifier id;
    private final Strategy strategy;

    OreAirExposurePolicy(Identifier id, Strategy strategy) {
        this.id = Objects.requireNonNull(id, "id");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public Identifier id() {
        return id;
    }

    public boolean discardExposed(double discardChance, double randomSample) {
        return strategy.discardExposed(discardChance, randomSample);
    }

    public Component describe(double discardChance) {
        return strategy.describe(discardChance);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
