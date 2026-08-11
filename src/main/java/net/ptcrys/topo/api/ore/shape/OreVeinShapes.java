package net.ptcrys.topo.api.ore.shape;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Owner facade for the ore vein shape KHS table. */
public final class OreVeinShapes {

    private static final FreezableStrategyRegistry<Identifier, OreVeinShape, OreVeinShape.Strategy> REGISTRY = FreezableStrategyRegistry.create("ore vein shapes");

    private OreVeinShapes() {}

    /** Single write entry for {@link OreDomainRegistration#shape}. */
    public static OreVeinShape begin(Identifier id, OreVeinShape.Strategy strategy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(strategy, "strategy");
        return REGISTRY.register(id, new OreVeinShape(id, strategy), strategy);
    }

    public static OreVeinShape require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<OreVeinShape> view() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
