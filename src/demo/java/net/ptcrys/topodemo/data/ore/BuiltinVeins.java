package net.ptcrys.topodemo.data.ore;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.ore.OreEnvironment;
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

    public static final OreVein TIN_OVERWORLD = feature("tin_overworld", "Tin Ore", "锡矿",
            BuiltinEnvironments.OVERWORLD_ROCK, BuiltinMaterials.TIN, 0, 80, 30, 9);
    public static final OreVein TIN_NETHER = feature("tin_nether", "Nether Tin Ore", "下界锡矿",
            BuiltinEnvironments.NETHER_ROCK, BuiltinMaterials.TIN, 10, 100, 6, 7);
    public static final OreVein LEAD_OVERWORLD = feature("lead_overworld", "Lead Ore", "铅矿",
            BuiltinEnvironments.OVERWORLD_ROCK, BuiltinMaterials.LEAD, -32, 32, 20, 8);
    public static final OreVein LEAD_NETHER = feature("lead_nether", "Nether Lead Ore", "下界铅矿",
            BuiltinEnvironments.NETHER_ROCK, BuiltinMaterials.LEAD, 10, 80, 8, 7);
    public static final OreVein NICKEL_OVERWORLD = feature("nickel_overworld", "Nickel Ore", "镍矿",
            BuiltinEnvironments.OVERWORLD_ROCK, BuiltinMaterials.NICKEL, -64, 16, 14, 8);
    public static final OreVein ZINC_OVERWORLD = feature("zinc_overworld", "Zinc Ore", "锌矿",
            BuiltinEnvironments.OVERWORLD_ROCK, BuiltinMaterials.ZINC, 0, 64, 20, 8);
    public static final OreVein URANIUM_OVERWORLD = feature("uranium_overworld", "Uranium Ore", "铀矿",
            BuiltinEnvironments.OVERWORLD_ROCK, BuiltinMaterials.URANIUM, -64, -16, 6, 6);
    public static final OreVein URANIUM_NETHER = feature("uranium_nether", "Nether Uranium Ore", "下界铀矿",
            BuiltinEnvironments.NETHER_ROCK, BuiltinMaterials.URANIUM, 10, 40, 2, 5);

    private BuiltinVeins() {}

    public static void init() {}

    private static OreVein feature(String path, String en, String cn, OreEnvironment environment,
                                   Material material, int minY, int maxY, int attempts, int size) {
        return ORE.vein(path).lang(en, cn).environment(environment)
                .airExposure(BuiltinOreTypes.PROBABILISTIC, 0.0)
                .entry(material, 1).feature(BuiltinOreTypes.FEATURE, size, attempts)
                .triangularHeight(minY, maxY).build();
    }
}
