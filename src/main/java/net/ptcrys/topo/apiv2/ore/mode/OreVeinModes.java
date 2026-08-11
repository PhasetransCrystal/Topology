package net.ptcrys.topo.apiv2.ore.mode;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Owner facade for the ore vein mode KHS table. */
public final class OreVeinModes {

    private static final FreezableStrategyRegistry<Identifier, OreVeinMode, OreVeinMode.Strategy> REGISTRY = FreezableStrategyRegistry.create("ore vein modes");

    private OreVeinModes() {}

    /** Single write entry for {@link net.ptcrys.topo.apiv2.plugin.OreDomainRegistration#mode}. */
    public static OreVeinMode begin(Identifier id, OreVeinMode.Strategy strategy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(strategy, "strategy");
        return REGISTRY.register(id, new OreVeinMode(id, strategy), strategy);
    }

    public static OreVeinMode require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<OreVeinMode> view() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
