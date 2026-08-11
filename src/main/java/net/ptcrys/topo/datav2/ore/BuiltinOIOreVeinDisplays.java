package net.ptcrys.topo.datav2.ore;

import net.ptcrys.topo.apiv2.ore.display.OreVeinDisplay;
import net.ptcrys.topo.apiv2.plugin.OreDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.ore.common.display.FeatureVeinDisplay;
import net.ptcrys.topo.datav2.ore.common.display.GridVeinDisplay;

/**
 * JEI display plugs keyed by placement-mode id. Registered after modes so handles resolve; bootstrap
 * requires every mode has a display.
 */
public final class BuiltinOIOreVeinDisplays {

    private static final OreDomainRegistration ORE = OfficialOIPlugin.INSTANCE.ore();

    public static final OreVeinDisplay FEATURE = ORE.display(BuiltinOIOreModes.FEATURE.id(), FeatureVeinDisplay.INSTANCE);
    public static final OreVeinDisplay GRID = ORE.display(BuiltinOIOreModes.GRID.id(), GridVeinDisplay.INSTANCE);

    private BuiltinOIOreVeinDisplays() {}

    public static void init() {}
}
