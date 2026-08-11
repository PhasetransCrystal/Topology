package net.ptcrys.topo.apiv2.ore;

import net.ptcrys.topo.apiv2.material.Material;

import java.util.Objects;

/**
 * One weighted material slot of a vein. Pure value created by the vein builder; material is a strong
 * handle (no string sidecar).
 */
public record OreVeinEntry(Material material, int weight) {

    public OreVeinEntry {
        Objects.requireNonNull(material, "material");
        if (weight <= 0) {
            throw new IllegalArgumentException("Ore vein entry weight must be positive: " + weight);
        }
    }
}
