package net.ptcrys.topo.datav2.ore;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.ore.OreEnvironment;
import net.ptcrys.topo.apiv2.ore.OreVein;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;

/**
 * Product ore vein table. All current product veins are vanilla-feature scatter; grid veins are
 * contributed by GameTest fixtures when enabled. Declarations use the sealed feature channel only —
 * no silent grid fields.
 */
public final class BuiltinOIOreVeins {

    private static final int IRON_SCALE_ATTEMPTS = 20;

    public static final OreVein TIN_OVERWORLD = overworld(
            "tin_overworld", "Tin Ore", "锡矿", BuiltinOIMaterials.TIN, 0, 80, scale(1.5), 9);
    public static final OreVein TIN_NETHER = nether(
            "tin_nether", "Nether Tin Ore", "下界锡矿", BuiltinOIMaterials.TIN, 10, 100, scale(0.3), 7);

    public static final OreVein LEAD_OVERWORLD = overworld(
            "lead_overworld", "Lead Ore", "铅矿", BuiltinOIMaterials.LEAD, -32, 32, scale(1.0), 8);
    public static final OreVein LEAD_NETHER = nether(
            "lead_nether", "Nether Lead Ore", "下界铅矿", BuiltinOIMaterials.LEAD, 10, 80, scale(0.4), 7);

    public static final OreVein NICKEL_OVERWORLD = overworld(
            "nickel_overworld", "Nickel Ore", "镍矿", BuiltinOIMaterials.NICKEL, -64, 16, scale(0.7), 8);

    public static final OreVein ZINC_OVERWORLD = overworld(
            "zinc_overworld", "Zinc Ore", "锌矿", BuiltinOIMaterials.ZINC, 0, 64, scale(1.0), 8);

    public static final OreVein URANIUM_OVERWORLD = overworld(
            "uranium_overworld", "Uranium Ore", "铀矿", BuiltinOIMaterials.URANIUM, -64, -16, scale(0.3), 6);
    public static final OreVein URANIUM_NETHER = nether(
            "uranium_nether", "Nether Uranium Ore", "下界铀矿", BuiltinOIMaterials.URANIUM, 10, 40, scale(0.1), 5);

    private BuiltinOIOreVeins() {}

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
                BuiltinOIOreEnvironments.OVERWORLD_STONE_AND_DEEPSLATE,
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
                BuiltinOIOreEnvironments.NETHER_NETHERRACK,
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
        return OfficialOIPlugin.INSTANCE.ore().vein(id)
                .lang(en, cn)
                .environment(environment)
                .airExposure(BuiltinOIOrePolicies.ALLOW, 0.0)
                .entry(material, 1)
                .feature(BuiltinOIOreModes.FEATURE, size, attempts)
                .triangularHeight(minY, maxY)
                .build();
    }

    private static int scale(double ironMultiplier) {
        return Math.max(1, (int) Math.round(IRON_SCALE_ATTEMPTS * ironMultiplier));
    }
}
