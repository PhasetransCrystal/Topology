package net.ptcrys.topo.datav2.ore.common.mode;

import net.ptcrys.topo.apiv2.ore.OrePlacement;
import net.ptcrys.topo.apiv2.ore.OreVein;
import net.ptcrys.topo.apiv2.ore.OreVeinCollector;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;

/**
 * Vanilla-feature (small) channel: validates {@link OrePlacement.Feature}, self-collects into the
 * datagen feature bridge, contributes nothing to the grid planner.
 */
public final class FeatureVeinMode implements OreVeinMode.Strategy {

    public static final FeatureVeinMode INSTANCE = new FeatureVeinMode();

    private FeatureVeinMode() {}

    @Override
    public void validate(OrePlacement placement) {
        if (!(placement instanceof OrePlacement.Feature)) {
            throw new IllegalArgumentException(
                    "feature mode requires OrePlacement.Feature, got " + placement.getClass().getSimpleName());
        }
    }

    @Override
    public void collectGrid(OreVein vein, OreVeinCollector collector) {
        // Feature veins are placed by vanilla worldgen.
    }

    @Override
    public void collectFeature(OreVein vein, OreVeinCollector collector) {
        collector.add(vein);
    }
}
