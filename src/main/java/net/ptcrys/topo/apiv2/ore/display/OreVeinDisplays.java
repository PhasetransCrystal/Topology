package net.ptcrys.topo.apiv2.ore.display;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Objects;

/**
 * Owner facade for the ore vein display KHS table. Parallel client-facing table keyed by the same
 * id space as placement modes. Frozen after modes; bootstrap requires every mode has a display.
 */
public final class OreVeinDisplays {

    private static final FreezableStrategyRegistry<Identifier, OreVeinDisplay, OreVeinDisplay.Strategy> REGISTRY = FreezableStrategyRegistry.create("ore vein displays");

    private OreVeinDisplays() {}

    /** Single write entry for {@link net.ptcrys.topo.apiv2.plugin.OreDomainRegistration#display}. */
    public static OreVeinDisplay begin(Identifier id, OreVeinDisplay.Strategy strategy) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(strategy, "strategy");
        return REGISTRY.register(id, new OreVeinDisplay(id, strategy), strategy);
    }

    public static OreVeinDisplay require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<OreVeinDisplay> view() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
