package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Gem property. The payload carries the variant set, so a gem material declares exactly which
 * quality items it produces; forms select a variant through {@link #has(GemQuality)}.
 */
public final class GemDataType extends MaterialDataType<GemDataType.GemData> {

    public enum GemQuality {
        FLAWED,
        BROKEN,
        FLAWLESS
    }

    public record GemData(Set<GemQuality> qualities) {

        public GemData {
            Objects.requireNonNull(qualities, "qualities");
            if (qualities.isEmpty()) {
                throw new IllegalArgumentException("gem data requires at least one quality");
            }
            qualities = Set.copyOf(qualities);
        }

        public boolean has(GemQuality quality) {
            return qualities.contains(quality);
        }
    }

    public GemDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<GemData> gem(GemQuality... qualities) {
        return use(new GemData(Set.of(qualities)));
    }

    public MaterialDataUse<GemData> allQualities() {
        return gem(GemQuality.values());
    }

    public Predicate<Material> has(GemQuality quality) {
        return matches(data -> data.has(quality));
    }
}
