package net.ptcrys.topo.data.ore;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.ore.common.mode.FeatureVeinMode;
import net.ptcrys.topo.data.ore.common.mode.GridVeinMode;

/** Built-in placement channels: vanilla feature scatter and deterministic grid. */
public final class BuiltinTopoOreModes {

    private static final OreDomainRegistration ORE = OfficialTopoPlugin.INSTANCE.ore();

    public static final OreVeinMode FEATURE = ORE.mode("feature", FeatureVeinMode.INSTANCE);
    public static final OreVeinMode GRID = ORE.mode("grid", GridVeinMode.INSTANCE);

    private BuiltinTopoOreModes() {}

    public static void init() {}
}
