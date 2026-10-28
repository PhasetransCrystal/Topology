package net.ptcrys.topodemo.data.ore;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topodemo.api.ore.BuiltinOreTypes;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;

public final class BuiltinVeins {

    private static final OreDomainRegistration ORE = TopoDemoPlugin.INSTANCE.ore();
    // Vanilla Feature.ORE carries one material; weighted mixed deposits use the grid channel below.
    public static final OreVein COPPER = ORE.vein("copper_scatter").lang("Copper Deposit", "铜矿床")
            .environment(BuiltinEnvironments.OVERWORLD_ROCK)
            .feature(BuiltinOreTypes.FEATURE, 12, 8).entry(BuiltinMaterials.COPPER, 1)
            .triangularHeight(-16, 96).airExposure(BuiltinOreTypes.PROBABILISTIC, 0.25).build();
    public static final OreVein COPPER_TIN = ORE.vein("copper_tin_deposit").lang("Copper and Tin Deposit", "铜锡矿床")
            .environment(BuiltinEnvironments.OVERWORLD_ROCK)
            .grid(BuiltinOreTypes.GRID, BuiltinOreTypes.SPHERE).radius(12).spacing(3, 8)
            .density(0.45).weight(100).priority(1).conflict(BuiltinOreTypes.HIGHER_PRIORITY)
            .entry(BuiltinMaterials.COPPER, 3).entry(BuiltinMaterials.TIN, 1)
            .height(-48, 48).airExposure(BuiltinOreTypes.PROBABILISTIC, 0.5).build();

    private BuiltinVeins() {}

    public static void init() {}
}
