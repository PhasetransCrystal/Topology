package net.ptcrys.topo.api.ore.mode;

import net.ptcrys.topo.api.ore.OrePlacement;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.ore.OreVeinCollector;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * KHS handle for a placement channel (feature scatter vs deterministic grid). The strategy validates
 * that a vein's sealed {@link OrePlacement} matches the channel, and self-collects into the matching
 * sink so engines never compare mode identity.
 */
public final class OreVeinMode {

    public interface Strategy {

        /** Rejects placements that do not belong to this channel. */
        void validate(OrePlacement placement);

        /** Grid modes add the vein to the per-chunk planner working set; others no-op. */
        void collectGrid(OreVein vein, OreVeinCollector collector);

        /** Feature modes add the vein to the datagen ore-feature bridge; others no-op. */
        void collectFeature(OreVein vein, OreVeinCollector collector);
    }

    private final Identifier id;
    private final Strategy strategy;

    OreVeinMode(Identifier id, Strategy strategy) {
        this.id = Objects.requireNonNull(id, "id");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public Identifier id() {
        return id;
    }

    public Strategy strategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
