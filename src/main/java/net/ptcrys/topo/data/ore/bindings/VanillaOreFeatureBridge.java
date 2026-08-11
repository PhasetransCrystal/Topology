package net.ptcrys.topo.data.ore.bindings;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.ore.OreEnvironment;
import net.ptcrys.topo.api.ore.OreHostRule;
import net.ptcrys.topo.api.ore.OrePlacement;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.ore.OreVeinCollector;
import net.ptcrys.topo.api.ore.OreVeins;
import net.ptcrys.topo.api.ore.engine.OreBlockResolver;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Datagen bridge for feature-channel veins: emits {@code Feature.ORE} ConfiguredFeature +
 * PlacedFeature + BiomeModifier. Veins are selected through mode self-collection; dimension → biome
 * comes from declared {@link net.ptcrys.topo.api.ore.OreDimensionRule}s (no Level hard-switch).
 */
public final class VanillaOreFeatureBridge {

    private static final String PREFIX = "ore/single/";
    private static final OreBlockResolver RESOLVER = OreBlockResolver.materialHelper();

    private VanillaOreFeatureBridge() {}

    public static void registerDatagen(RegistryCore core) {
        core.getDataGenInitializer().add(Registries.CONFIGURED_FEATURE, VanillaOreFeatureBridge::bootstrapConfiguredFeatures);
        core.getDataGenInitializer().add(Registries.PLACED_FEATURE, VanillaOreFeatureBridge::bootstrapPlacedFeatures);
        core.getDataGenInitializer().add(NeoForgeRegistries.Keys.BIOME_MODIFIERS, VanillaOreFeatureBridge::bootstrapBiomeModifiers);
    }

    public static void bootstrapConfiguredFeatures(BootstrapContext<ConfiguredFeature<?, ?>> context) {
        for (FeatureVein vein : featureVeins()) {
            context.register(
                    configuredKey(vein.vein()),
                    new ConfiguredFeature<>(
                            Feature.ORE,
                            new OreConfiguration(
                                    targets(vein), vein.feature().size(), discardChance(vein.vein()))));
        }
    }

    public static void bootstrapPlacedFeatures(BootstrapContext<PlacedFeature> context) {
        HolderGetter<ConfiguredFeature<?, ?>> configured = context.lookup(Registries.CONFIGURED_FEATURE);
        for (FeatureVein vein : featureVeins()) {
            Holder<ConfiguredFeature<?, ?>> holder = configured.getOrThrow(configuredKey(vein.vein()));
            context.register(placedKey(vein.vein()), new PlacedFeature(holder, placement(vein)));
        }
    }

    public static void bootstrapBiomeModifiers(BootstrapContext<BiomeModifier> context) {
        HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
        HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
        for (FeatureVein vein : featureVeins()) {
            for (TagKey<Biome> biomeTag : biomeTags(vein.vein().environment())) {
                HolderSet<Biome> biomeSet = biomes.getOrThrow(biomeTag);
                HolderSet<PlacedFeature> featureSet = HolderSet.direct(placedFeatures.getOrThrow(placedKey(vein.vein())));
                context.register(
                        biomeModifierKey(vein.vein(), biomeTag),
                        new BiomeModifiers.AddFeaturesBiomeModifier(
                                biomeSet, featureSet, GenerationStep.Decoration.UNDERGROUND_ORES));
            }
        }
    }

    private static List<FeatureVein> featureVeins() {
        List<OreVein> collected = new ArrayList<>();
        OreVeinCollector collector = collected::add;
        for (OreVein vein : OreVeins.view()) {
            vein.mode().strategy().collectFeature(vein, collector);
        }
        List<FeatureVein> result = new ArrayList<>(collected.size());
        for (OreVein vein : collected) {
            if (!(vein.placement() instanceof OrePlacement.Feature feature)) {
                throw new IllegalStateException(
                        "feature channel collected non-feature vein: " + vein.id());
            }
            result.add(new FeatureVein(vein, feature));
        }
        return result;
    }

    private static List<PlacementModifier> placement(FeatureVein vein) {
        return List.of(
                CountPlacement.of(vein.feature().attemptsPerChunk()),
                InSquarePlacement.spread(),
                heightPlacement(vein.feature()),
                BiomeFilter.biome());
    }

    private static HeightRangePlacement heightPlacement(OrePlacement.Feature feature) {
        VerticalAnchor min = VerticalAnchor.absolute(feature.minY());
        VerticalAnchor max = VerticalAnchor.absolute(feature.maxY());
        return feature.triangularHeight() ? HeightRangePlacement.triangle(min, max) : HeightRangePlacement.uniform(min, max);
    }

    private static List<OreConfiguration.TargetBlockState> targets(FeatureVein vein) {
        Material material = vein.vein().entries().getFirst().material();
        List<OreConfiguration.TargetBlockState> result = new ArrayList<>();
        for (OreHostRule rule : vein.vein().environment().hostRules()) {
            RESOLVER.block(material, rule.generatedForm()).ifPresent(block -> result.add(OreConfiguration.target(
                    new TagMatchTest(rule.replaceableTag()), block.defaultBlockState())));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("No ore block targets for feature vein " + vein.vein().id());
        }
        return List.copyOf(result);
    }

    private static float discardChance(OreVein vein) {
        if (!vein.airExposurePolicy().discardExposed(vein.airExposureDiscardChance(), 0.0)) {
            return 0.0F;
        }
        if (vein.airExposurePolicy().discardExposed(vein.airExposureDiscardChance(), 1.0)) {
            return 1.0F;
        }
        return (float) vein.airExposureDiscardChance();
    }

    private static List<TagKey<Biome>> biomeTags(OreEnvironment environment) {
        List<TagKey<Biome>> tags = new ArrayList<>();
        for (var dimension : environment.dimensions()) {
            tags.add(dimension.biomeTag());
        }
        return tags;
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> configuredKey(OreVein vein) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, veinFeatureId(vein, PREFIX + vein.id().getPath()));
    }

    private static ResourceKey<PlacedFeature> placedKey(OreVein vein) {
        return ResourceKey.create(Registries.PLACED_FEATURE, veinFeatureId(vein, PREFIX + vein.id().getPath()));
    }

    private static ResourceKey<BiomeModifier> biomeModifierKey(OreVein vein, TagKey<Biome> biomeTag) {
        String path = PREFIX + vein.id().getPath() + "/" + biomeTag.location().toString().replace(':', '_').replace('/', '_');
        return ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS, veinFeatureId(vein, path));
    }

    /** Feature keys inherit the vein owner's namespace (not a hard-coded host mod id). */
    private static net.minecraft.resources.Identifier veinFeatureId(OreVein vein, String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(vein.id().getNamespace(), path);
    }

    private record FeatureVein(OreVein vein, OrePlacement.Feature feature) {}
}
