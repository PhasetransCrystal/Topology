package net.ptcrys.topo.apiv2.ore;

import java.util.List;

/**
 * Value factory for {@link OreEnvironment}. Not a registry: environments are direct-singleton values
 * referenced by strong handle from vein declarations.
 */
public final class OreEnvironments {

    private OreEnvironments() {}

    public static OreEnvironment of(List<OreDimensionRule> dimensions, OreHostRule... hostRules) {
        return new OreEnvironment(dimensions, List.of(hostRules));
    }

    public static OreEnvironment of(OreDimensionRule dimension, OreHostRule... hostRules) {
        return of(List.of(dimension), hostRules);
    }
}
