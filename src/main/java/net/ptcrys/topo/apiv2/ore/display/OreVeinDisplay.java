package net.ptcrys.topo.apiv2.ore.display;

import net.ptcrys.topo.apiv2.ore.OreVein;

import net.minecraft.resources.Identifier;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.Objects;

/**
 * KHS handle for a vein's JEI preview renderer. Keyed by the placement mode's id so each channel
 * renders through its paired plug. Client-side only; kept out of the worldgen engine.
 */
public final class OreVeinDisplay {

    @FunctionalInterface
    public interface Strategy {

        UIElement buildPreview(OreVein vein);
    }

    private final Identifier id;
    private final Strategy strategy;

    OreVeinDisplay(Identifier id, Strategy strategy) {
        this.id = Objects.requireNonNull(id, "id");
        this.strategy = Objects.requireNonNull(strategy, "strategy");
    }

    public Identifier id() {
        return id;
    }

    public UIElement buildPreview(OreVein vein) {
        return strategy.buildPreview(vein);
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
