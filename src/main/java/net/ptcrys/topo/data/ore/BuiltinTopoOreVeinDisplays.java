package net.ptcrys.topo.data.ore;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.ore.display.OreVeinDisplay;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.ore.common.display.FeatureVeinDisplay;
import net.ptcrys.topo.data.ore.common.display.GridVeinDisplay;

/**
 * JEI display plugs keyed by placement-mode id. Registered after modes so handles resolve; bootstrap
 * requires every mode has a display.
 */
public final class BuiltinTopoOreVeinDisplays {

    private static final OreDomainRegistration ORE = OfficialTopoPlugin.INSTANCE.ore();

    public static final OreVeinDisplay FEATURE = ORE.display(BuiltinTopoOreModes.FEATURE.id(), FeatureVeinDisplay.INSTANCE);
    public static final OreVeinDisplay GRID = ORE.display(BuiltinTopoOreModes.GRID.id(), GridVeinDisplay.INSTANCE);

    private BuiltinTopoOreVeinDisplays() {}

    public static void init() {}
}
