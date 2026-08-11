package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.ore.BuiltinTopoOreEnvironments;
import net.ptcrys.topo.data.ore.BuiltinTopoOreModes;
import net.ptcrys.topo.data.ore.BuiltinTopoOrePolicies;
import net.ptcrys.topo.data.ore.BuiltinTopoOreShapes;

/**
 * GameTest-only grid vein for deterministic planner coverage. Gated by the same fixture property as
 * other GameTest domains; registered before {@link OreVeins#freeze()}.
 */
public final class TopoOreGameTestFixtures {

    private static OreVein leadBlob;

    private TopoOreGameTestFixtures() {}

    public static boolean enabled() {
        return TopoScalarGameTestFixtures.enabled();
    }

    public static void initVeins() {
        if (leadBlob != null) {
            return;
        }
        leadBlob = OfficialTopoPlugin.INSTANCE.ore().vein("gametest_lead_blob")
                .lang("GameTest Lead Blob", "测试铅矿团")
                .environment(BuiltinTopoOreEnvironments.OVERWORLD_DEEPSLATE)
                .airExposure(BuiltinTopoOrePolicies.ALLOW, 0.0)
                .entry(BuiltinTopoMaterials.LEAD, 1)
                .grid(BuiltinTopoOreModes.GRID, BuiltinTopoOreShapes.SPHERE)
                .radius(8)
                .spacing(1, 0)
                .density(1.0)
                .weight(100)
                .priority(0)
                .conflict(BuiltinTopoOrePolicies.REPLACE_LOWER_PRIORITY)
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
