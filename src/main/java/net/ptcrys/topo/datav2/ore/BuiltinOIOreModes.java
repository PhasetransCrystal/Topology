package net.ptcrys.topo.datav2.ore;

import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;
import net.ptcrys.topo.apiv2.plugin.OreDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.ore.common.mode.FeatureVeinMode;
import net.ptcrys.topo.datav2.ore.common.mode.GridVeinMode;

/** Built-in placement channels: vanilla feature scatter and deterministic grid. */
public final class BuiltinOIOreModes {

    private static final OreDomainRegistration ORE = OfficialOIPlugin.INSTANCE.ore();

    public static final OreVeinMode FEATURE = ORE.mode("feature", FeatureVeinMode.INSTANCE);
    public static final OreVeinMode GRID = ORE.mode("grid", GridVeinMode.INSTANCE);

    private BuiltinOIOreModes() {}

    public static void init() {}
}
