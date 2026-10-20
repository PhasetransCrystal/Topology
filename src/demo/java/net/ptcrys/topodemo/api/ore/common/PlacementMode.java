package net.ptcrys.topodemo.api.ore.common;

import net.ptcrys.topo.api.ore.OrePlacement;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.ore.OreVeinCollector;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;

/** A mode routes only the placement type it validated. */
public enum PlacementMode implements OreVeinMode.Strategy {

    FEATURE,
    GRID;

    @Override
    public void validate(OrePlacement placement) {
        boolean feature = placement instanceof OrePlacement.Feature;
        if (feature != (this == FEATURE)) throw new IllegalArgumentException(this + " rejects " + placement);
    }

    @Override
    public void collectGrid(OreVein vein, OreVeinCollector collector) {
        if (this == GRID) collector.add(vein);
    }

    @Override
    public void collectFeature(OreVein vein, OreVeinCollector collector) {
        if (this == FEATURE) collector.add(vein);
    }
}
