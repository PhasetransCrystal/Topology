package net.ptcrys.topo.api.ore.policy;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/** Owner facade for the air-exposure policy KHS table. */
public final class OreAirExposurePolicies {

    private static final FreezableStrategyRegistry<Identifier, OreAirExposurePolicy, OreAirExposurePolicy.Strategy> REGISTRY = FreezableStrategyRegistry.create("ore air exposure policies");

    private OreAirExposurePolicies() {}

    /** Single write entry for {@link OreDomainRegistration#airExposure}. */
    public static OreAirExposurePolicy begin(Identifier id, OreAirExposurePolicy.Strategy strategy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(strategy, "strategy");
        return REGISTRY.register(id, new OreAirExposurePolicy(id, strategy), strategy);
    }

    public static OreAirExposurePolicy require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<OreAirExposurePolicy> view() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
