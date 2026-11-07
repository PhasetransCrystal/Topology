package net.ptcrys.topodemo.api.ore.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;

import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Datagen provider that rewrites vanilla ore configured features to target the native Topo ore
 * blocks. Placed features and biome membership stay vanilla, so the distribution is unchanged while
 * the generated blocks are Topo's. Copper is excluded from the rewrite because the demo already
 * declares a custom copper vein; its vanilla placed features are removed instead.
 */
public final class VanillaOreDatagenProvider implements DataProvider {

    private static final String PROVIDER_NAME = "TopoDemo vanilla ore replacement";
    private static final String COPPER_REMOVAL_PATH = "neoforge/biome_modifier/ore/remove_vanilla_copper";

    private final ResourceManager resources;
    private final Path dataRoot;
    private final List<ConfiguredOverride> overrides = createOverrides();

    public VanillaOreDatagenProvider(GatherDataEvent event) {
        this.resources = event.getResourceManager(PackType.SERVER_DATA);
        this.dataRoot = event.getGenerator().getPackOutput().getOutputFolder(PackOutput.Target.DATA_PACK);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (ConfiguredOverride override : overrides) {
            writes.add(writeOverride(cache, override));
        }
        writes.add(DataProvider.saveStable(cache, copperRemovalJson(), dataPath(TopoDemo.MOD_ID, COPPER_REMOVAL_PATH)));
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    private CompletableFuture<?> writeOverride(CachedOutput cache, ConfiguredOverride override) {
        Identifier sourceId = Identifier.withDefaultNamespace("worldgen/configured_feature/" + override.path() + ".json");
        Resource resource = resources.getResource(sourceId).orElseThrow(() -> new IllegalStateException(
                "missing vanilla configured feature " + sourceId));
        JsonObject root;
        try (Reader reader = resource.openAsReader()) {
            root = GsonHelper.parse(reader);
        } catch (Exception error) {
            throw new IllegalStateException("failed to read " + sourceId, error);
        }
        JsonArray targets = root.getAsJsonObject("config").getAsJsonArray("targets");
        for (var targetElement : targets) {
            JsonObject target = targetElement.getAsJsonObject();
            JsonObject state = target.getAsJsonObject("state");
            String vanillaBlock = GsonHelper.getAsString(state, "Name");
            Target replacement = override.targets().get(vanillaBlock);
            if (replacement == null) {
                throw new IllegalStateException(
                        "vanilla ore feature " + override.path() + " has unhandled target block " + vanillaBlock);
            }
            Identifier blockId = MaterialHelper.itemId(replacement.material(), replacement.form());
            state.remove("Properties");
            state.addProperty("Name", blockId.toString());
        }
        return DataProvider.saveStable(cache, root, dataPath("minecraft", "worldgen/configured_feature/" + override.path()));
    }

    private static JsonObject copperRemovalJson() {
        JsonObject root = new JsonObject();
        root.addProperty("type", "neoforge:remove_features");
        root.addProperty("biomes", "#minecraft:is_overworld");
        JsonArray features = new JsonArray();
        features.add("minecraft:ore_copper");
        features.add("minecraft:ore_copper_large");
        root.add("features", features);
        root.addProperty("steps", "underground_ores");
        return root;
    }

    private Path dataPath(String namespace, String path) {
        return dataRoot.resolve(namespace).resolve(path + ".json");
    }

    private static List<ConfiguredOverride> createOverrides() {
        List<ConfiguredOverride> result = new ArrayList<>();
        result.add(override("ore_iron_small",
                target("minecraft:iron_ore", BuiltinMaterials.IRON, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_iron_ore", BuiltinMaterials.IRON, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_iron",
                target("minecraft:iron_ore", BuiltinMaterials.IRON, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_iron_ore", BuiltinMaterials.IRON, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_gold_buried",
                target("minecraft:gold_ore", BuiltinMaterials.GOLD, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_gold_ore", BuiltinMaterials.GOLD, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_gold",
                target("minecraft:gold_ore", BuiltinMaterials.GOLD, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_gold_ore", BuiltinMaterials.GOLD, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_coal_buried",
                target("minecraft:coal_ore", BuiltinMaterials.COAL, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_coal_ore", BuiltinMaterials.COAL, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_coal",
                target("minecraft:coal_ore", BuiltinMaterials.COAL, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_coal_ore", BuiltinMaterials.COAL, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_redstone",
                target("minecraft:redstone_ore", BuiltinMaterials.REDSTONE, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_redstone_ore", BuiltinMaterials.REDSTONE, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_diamond_small",
                target("minecraft:diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_diamond_buried",
                target("minecraft:diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_diamond_large",
                target("minecraft:diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_diamond_medium",
                target("minecraft:diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_diamond_ore", BuiltinMaterials.DIAMOND, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_lapis",
                target("minecraft:lapis_ore", BuiltinMaterials.LAPIS, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_lapis_ore", BuiltinMaterials.LAPIS, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_lapis_buried",
                target("minecraft:lapis_ore", BuiltinMaterials.LAPIS, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_lapis_ore", BuiltinMaterials.LAPIS, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_emerald",
                target("minecraft:emerald_ore", BuiltinMaterials.EMERALD, BuiltinMaterialForms.ORE),
                target("minecraft:deepslate_emerald_ore", BuiltinMaterials.EMERALD, BuiltinMaterialForms.DEEPSLATE_ORE)));
        result.add(override("ore_quartz",
                target("minecraft:nether_quartz_ore", BuiltinMaterials.QUARTZ, BuiltinMaterialForms.NETHER_ORE)));
        result.add(override("ore_nether_gold",
                target("minecraft:nether_gold_ore", BuiltinMaterials.GOLD, BuiltinMaterialForms.ORE)));
        result.add(override("ore_ancient_debris_large",
                target("minecraft:ancient_debris", BuiltinMaterials.NETHERITE, BuiltinMaterialForms.NETHER_ORE)));
        result.add(override("ore_ancient_debris_small",
                target("minecraft:ancient_debris", BuiltinMaterials.NETHERITE, BuiltinMaterialForms.NETHER_ORE)));
        return List.copyOf(result);
    }

    private static ConfiguredOverride override(String path, Target... targets) {
        Map<String, Target> byBlock = new LinkedHashMap<>();
        for (Target target : targets) {
            byBlock.put(target.vanillaBlock(), target);
        }
        return new ConfiguredOverride(path, Map.copyOf(byBlock));
    }

    private static Target target(String vanillaBlock, Material material, MaterialForm form) {
        return new Target(vanillaBlock, material, form);
    }

    private record Target(String vanillaBlock, Material material, MaterialForm form) {}

    private record ConfiguredOverride(String path, Map<String, Target> targets) {}
}
