package net.ptcrys.topo.apiv2.ore.policy;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Owner facade for the overlap-conflict policy KHS table. */
public final class OreConflictPolicies {

    private static final FreezableStrategyRegistry<Identifier, OreConflictPolicy, OreConflictPolicy.Strategy> REGISTRY = FreezableStrategyRegistry.create("ore conflict policies");

    private OreConflictPolicies() {}

    /** Single write entry for {@link net.ptcrys.topo.apiv2.plugin.OreDomainRegistration#conflict}. */
    public static OreConflictPolicy begin(Identifier id, OreConflictPolicy.Strategy strategy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(strategy, "strategy");
        return REGISTRY.register(id, new OreConflictPolicy(id, strategy), strategy);
    }

    public static OreConflictPolicy require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<OreConflictPolicy> view() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
