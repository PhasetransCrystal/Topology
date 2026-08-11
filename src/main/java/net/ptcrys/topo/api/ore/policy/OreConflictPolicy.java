package net.ptcrys.topo.api.ore.policy;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * KHS handle for overlap conflict: decides whether a candidate grid vein replaces a block already
 * planned by another vein at the same position.
 */
public final class OreConflictPolicy {

    @FunctionalInterface
    public interface Strategy {

        boolean canReplace(int candidatePriority, int existingPriority);
    }

    private final Identifier id;
    private final Strategy strategy;

    OreConflictPolicy(Identifier id, Strategy strategy) {
        this.id = Objects.requireNonNull(id, "id");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public Identifier id() {
        return id;
    }

    public boolean canReplace(int candidatePriority, int existingPriority) {
        return strategy.canReplace(candidatePriority, existingPriority);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
