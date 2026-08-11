package net.ptcrys.topo.apiv2.ore;

/**
 * Sink a {@link net.ptcrys.topo.apiv2.ore.mode.OreVeinMode.Strategy} adds a vein to when the vein belongs to
 * that mode's placement channel. Each mode self-sorts veins by behavior; the engine never switches on
 * mode identity.
 */
@FunctionalInterface
public interface OreVeinCollector {

    void add(OreVein vein);
}
