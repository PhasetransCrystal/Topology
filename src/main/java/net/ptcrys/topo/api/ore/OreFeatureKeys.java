package net.ptcrys.topo.api.ore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Registry-aware keys for vanilla feature-channel veins, preserving the vein owner's namespace. */
public final class OreFeatureKeys {

    private static final String PREFIX = "ore/single/";

    private OreFeatureKeys() {}

    public static ResourceKey<ConfiguredFeature<?, ?>> configured(Identifier vein) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, vein.withPrefix(PREFIX));
    }

    public static ResourceKey<PlacedFeature> placed(Identifier vein) {
        return ResourceKey.create(Registries.PLACED_FEATURE, vein.withPrefix(PREFIX));
    }

    public static ResourceKey<BiomeModifier> biomeModifier(Identifier vein, TagKey<Biome> biomeTag) {
        Identifier biome = biomeTag.location();
        // Both paths may contain '/'. The length preserves the vein boundary; the
        // namespace is a single segment, followed by the unchanged biome path.
        String path = PREFIX + vein.getPath().length() + "/" + vein.getPath() + "/" + biome.getNamespace() + "/" + biome.getPath();
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                vein.withPath(path));
    }
}
