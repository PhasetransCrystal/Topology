package net.ptcrys.topodemo.api.ore;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicy;
import net.ptcrys.topo.api.ore.policy.OreConflictPolicy;
import net.ptcrys.topo.api.ore.shape.OreVeinShape;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.ore.common.PlacementMode;

import net.minecraft.network.chat.Component;

public final class BuiltinOreTypes {

    private static final OreDomainRegistration ORE = TopoDemoApiPlugin.INSTANCE.ore();
    public static final OreVeinShape SPHERE = ORE.shape("sphere", (dx, dy, dz, radius) -> (long) dx * dx + (long) dy * dy + (long) dz * dz <= (long) radius * radius);
    public static final OreVeinMode FEATURE = ORE.mode("feature", PlacementMode.FEATURE);
    public static final OreVeinMode GRID = ORE.mode("grid", PlacementMode.GRID);
    private static final LangKey EXPOSURE = TopoDemoApiPlugin.INSTANCE.lang()
            .key("ore", "air_exposure", "Air exposure discard: %s%%", "空气暴露丢弃率：%s%%");
    public static final OreAirExposurePolicy PROBABILISTIC = ORE.airExposure("probabilistic", new OreAirExposurePolicy.Strategy() {

        @Override
        public boolean discardExposed(double chance, double sample) {
            return sample < chance;
        }

        @Override
        public Component describe(double chance) {
            return EXPOSURE.getComponent(Math.round(chance * 100));
        }
    });
    public static final OreConflictPolicy HIGHER_PRIORITY = ORE.conflict("higher_priority", (candidate, existing) -> candidate > existing);

    private BuiltinOreTypes() {}

    public static void init() {}
}
