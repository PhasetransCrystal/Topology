package net.ptcrys.topo.data.ore;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.ore.OreEnvironment;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;

/**
 * Product ore vein table. All current product veins are vanilla-feature scatter; grid veins are
 * contributed by GameTest fixtures when enabled. Declarations use the sealed feature channel only —
 * no silent grid fields.
 */
public final class BuiltinTopoOreVeins {

    private static final int IRON_SCALE_ATTEMPTS = 20;

    public static final OreVein TIN_OVERWORLD = overworld(
            "tin_overworld", "Tin Ore", "锡矿", BuiltinTopoMaterials.TIN, 0, 80, scale(1.5), 9);
    public static final OreVein TIN_NETHER = nether(
            "tin_nether", "Nether Tin Ore", "下界锡矿", BuiltinTopoMaterials.TIN, 10, 100, scale(0.3), 7);

    public static final OreVein LEAD_OVERWORLD = overworld(
            "lead_overworld", "Lead Ore", "铅矿", BuiltinTopoMaterials.LEAD, -32, 32, scale(1.0), 8);
    public static final OreVein LEAD_NETHER = nether(
            "lead_nether", "Nether Lead Ore", "下界铅矿", BuiltinTopoMaterials.LEAD, 10, 80, scale(0.4), 7);

    public static final OreVein NICKEL_OVERWORLD = overworld(
            "nickel_overworld", "Nickel Ore", "镍矿", BuiltinTopoMaterials.NICKEL, -64, 16, scale(0.7), 8);

    public static final OreVein ZINC_OVERWORLD = overworld(
            "zinc_overworld", "Zinc Ore", "锌矿", BuiltinTopoMaterials.ZINC, 0, 64, scale(1.0), 8);

    public static final OreVein URANIUM_OVERWORLD = overworld(
            "uranium_overworld", "Uranium Ore", "铀矿", BuiltinTopoMaterials.URANIUM, -64, -16, scale(0.3), 6);
    public static final OreVein URANIUM_NETHER = nether(
            "uranium_nether", "Nether Uranium Ore", "下界铀矿", BuiltinTopoMaterials.URANIUM, 10, 40, scale(0.1), 5);

    private BuiltinTopoOreVeins() {}

    public static void init() {}

    private static OreVein overworld(
                                     String id,
                                     String en,
                                     String cn,
                                     Material material,
                                     int minY,
                                     int maxY,
                                     int attempts,
                                     int size) {
        return featureVein(
                id,
                en,
                cn,
                BuiltinTopoOreEnvironments.OVERWORLD_STONE_AND_DEEPSLATE,
                material,
                minY,
                maxY,
                attempts,
                size);
    }

    private static OreVein nether(
                                  String id,
                                  String en,
                                  String cn,
                                  Material material,
                                  int minY,
                                  int maxY,
                                  int attempts,
                                  int size) {
        return featureVein(
                id,
                en,
                cn,
                BuiltinTopoOreEnvironments.NETHER_NETHERRACK,
                material,
                minY,
                maxY,
                attempts,
                size);
    }

    private static OreVein featureVein(
                                       String id,
                                       String en,
                                       String cn,
                                       OreEnvironment environment,
                                       Material material,
                                       int minY,
                                       int maxY,
                                       int attempts,
                                       int size) {
        return OfficialTopoPlugin.INSTANCE.ore().vein(id)
                .lang(en, cn)
                .environment(environment)
                .airExposure(BuiltinTopoOrePolicies.ALLOW, 0.0)
                .entry(material, 1)
                .feature(BuiltinTopoOreModes.FEATURE, size, attempts)
                .triangularHeight(minY, maxY)
                .build();
    }

    private static int scale(double ironMultiplier) {
        return Math.max(1, (int) Math.round(IRON_SCALE_ATTEMPTS * ironMultiplier));
    }
}
