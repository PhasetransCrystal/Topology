package net.ptcrys.topo.api.ore;

import net.ptcrys.topo.api.material.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable derived indexes over the registered vein table. */
final class OreVeinSnapshot {

    private final List<OreEnvironment> environments;
    private final Map<Material, List<OreVein>> byMaterial;

    private OreVeinSnapshot(List<OreEnvironment> environments, Map<Material, List<OreVein>> byMaterial) {
        this.environments = environments;
        this.byMaterial = byMaterial;
    }

    static OreVeinSnapshot of(List<OreVein> veins) {
        Objects.requireNonNull(veins, "ore veins");
        var environments = new LinkedHashSet<OreEnvironment>();
        var materialIndex = new LinkedHashMap<Material, LinkedHashSet<OreVein>>();
        for (OreVein vein : veins) {
            environments.add(vein.environment());
            for (OreVeinEntry entry : vein.entries()) {
                materialIndex.computeIfAbsent(entry.material(), ignored -> new LinkedHashSet<>()).add(vein);
            }
        }
        var byMaterial = new LinkedHashMap<Material, List<OreVein>>();
        materialIndex.forEach((material, matches) -> byMaterial.put(material, List.copyOf(matches)));
        return new OreVeinSnapshot(
                List.copyOf(environments),
                Collections.unmodifiableMap(byMaterial));
    }

    List<OreEnvironment> environments() {
        return environments;
    }

    List<OreVein> byMaterial(Material material) {
        return byMaterial.getOrDefault(Objects.requireNonNull(material, "material"), List.of());
    }
}
