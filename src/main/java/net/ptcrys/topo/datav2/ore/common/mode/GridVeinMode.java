package net.ptcrys.topo.datav2.ore.common.mode;

import net.ptcrys.topo.apiv2.ore.OrePlacement;
import net.ptcrys.topo.apiv2.ore.OreVein;
import net.ptcrys.topo.apiv2.ore.OreVeinCollector;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;

/**
 * Deterministic grid (large) channel: validates {@link OrePlacement.Grid}, self-collects into the
 * per-chunk planner, contributes nothing to the vanilla feature pipeline.
 */
public final class GridVeinMode implements OreVeinMode.Strategy {

    public static final GridVeinMode INSTANCE = new GridVeinMode();

    private GridVeinMode() {}

    @Override
    public void validate(OrePlacement placement) {
        if (!(placement instanceof OrePlacement.Grid)) {
            throw new IllegalArgumentException(
                    "grid mode requires OrePlacement.Grid, got " + placement.getClass().getSimpleName());
        }
    }

    @Override
    public void collectGrid(OreVein vein, OreVeinCollector collector) {
        collector.add(vein);
    }

    @Override
    public void collectFeature(OreVein vein, OreVeinCollector collector) {
        // Grid veins are not vanilla features.
    }
}
