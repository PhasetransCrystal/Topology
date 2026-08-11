package net.ptcrys.topo.data.ore;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.ore.shape.OreVeinShape;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.ore.common.shape.OreVeinShapeMath;

/**
 * Built-in grid vein shapes. Containment math lives in {@link OreVeinShapeMath}; this table only
 * registers handles. Register a shape here only when a product or test vein will reference it.
 */
public final class BuiltinTopoOreShapes {

    private static final OreDomainRegistration ORE = OfficialTopoPlugin.INSTANCE.ore();

    public static final OreVeinShape SPHERE = ORE.shape("sphere", OreVeinShapeMath::sphere);
    public static final OreVeinShape FILLED_PENTAGRAM = ORE.shape("filled_pentagram", OreVeinShapeMath::filledPentagram);
    public static final OreVeinShape VARIABLE_WIDTH_RING = ORE.shape("variable_width_ring", OreVeinShapeMath::variableWidthRing);
    public static final OreVeinShape TRIANGLE = ORE.shape("triangle", OreVeinShapeMath::triangle);
    public static final OreVeinShape THREE_STAR_SYSTEM = ORE.shape("three_star_system", OreVeinShapeMath::threeStarSystem);

    private BuiltinTopoOreShapes() {}

    public static void init() {}
}
