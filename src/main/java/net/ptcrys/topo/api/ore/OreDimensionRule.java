package net.ptcrys.topo.api.ore;

import net.ptcrys.topo.api.api.lang.LangKey;

import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.Objects;

/**
 * One dimension a vein environment may generate in, with the biome tag used by the vanilla-feature
 * bridge and a display name for JEI. Declared once — no path-string switch at render or datagen.
 */
public record OreDimensionRule(ResourceKey<Level> dimension, TagKey<Biome> biomeTag, LangKey displayName) {

    public OreDimensionRule {
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(biomeTag, "biomeTag");
        Objects.requireNonNull(displayName, "displayName");
    }
}
