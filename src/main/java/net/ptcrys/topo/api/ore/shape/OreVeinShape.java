package net.ptcrys.topo.api.ore.shape;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * KHS handle for a deterministic vein shape. The handle carries identity; its {@link Strategy}
 * answers whether a vein-local offset lies inside the envelope.
 */
public final class OreVeinShape {

    @FunctionalInterface
    public interface Strategy {

        boolean contains(int dx, int dy, int dz, int radius);
    }

    private final Identifier id;
    private final Strategy strategy;

    OreVeinShape(Identifier id, Strategy strategy) {
        this.id = Objects.requireNonNull(id, "id");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public Identifier id() {
        return id;
    }

    public boolean contains(int dx, int dy, int dz, int radius) {
        return strategy.contains(dx, dy, dz, radius);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
