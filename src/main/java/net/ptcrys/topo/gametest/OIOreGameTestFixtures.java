package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.ore.OreVein;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreEnvironments;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreModes;
import net.ptcrys.topo.datav2.ore.BuiltinOIOrePolicies;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreShapes;

/**
 * GameTest-only grid vein for deterministic planner coverage. Gated by the same fixture property as
 * other GameTest domains; registered before {@link OreVeins#freeze()}.
 */
public final class OIOreGameTestFixtures {

    private static OreVein leadBlob;

    private OIOreGameTestFixtures() {}

    public static boolean enabled() {
        return OIScalarGameTestFixtures.enabled();
    }

    public static void initVeins() {
        if (leadBlob != null) {
            return;
        }
        leadBlob = OfficialOIPlugin.INSTANCE.ore().vein("gametest_lead_blob")
                .lang("GameTest Lead Blob", "测试铅矿团")
                .environment(BuiltinOIOreEnvironments.OVERWORLD_DEEPSLATE)
                .airExposure(BuiltinOIOrePolicies.ALLOW, 0.0)
                .entry(BuiltinOIMaterials.LEAD, 1)
                .grid(BuiltinOIOreModes.GRID, BuiltinOIOreShapes.SPHERE)
                .radius(8)
                .spacing(1, 0)
                .density(1.0)
                .weight(100)
                .priority(0)
                .conflict(BuiltinOIOrePolicies.REPLACE_LOWER_PRIORITY)
                .height(-16, 16)
                .build();
    }

    public static OreVein leadBlob() {
        if (leadBlob == null) {
            throw new IllegalStateException("ore gametest fixtures are not initialized");
        }
        return leadBlob;
    }
}
