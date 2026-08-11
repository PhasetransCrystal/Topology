package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;
import net.ptcrys.topo.api.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole recipe-domain registration entry for one {@link TopoPlugin}. Paths are bare; namespace and
 * {@link RegistryCore} are fixed by the plugin. One method per concern — no product-facing
 * overloads.
 */
public final class RecipeDomainRegistration {

    private final TopoPlugin plugin;
    private final RegistryCore registry;

    private RecipeDomainRegistration(TopoPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static RecipeDomainRegistration of(TopoPlugin plugin) {
        return new RecipeDomainRegistration(plugin, plugin.registry());
    }

    public TopoPlugin plugin() {
        return plugin;
    }

    public RegistryCore registry() {
        return registry;
    }

    public String modId() {
        return plugin.modId();
    }

    /** Stable id under this plugin namespace (recipe types, production lines, progress bars, …). */
    public Identifier id(String path) {
        return PluginIds.id(plugin, path);
    }

    /** Begin a default {@link TopoRecipe} machine recipe type under this plugin's namespace. */
    public TopoRecipeType<TopoRecipe> recipeType(String path) {
        return TopoRecipeTypes.begin(registry, id(path), plugin.lang());
    }

    /**
     * Bind and register a custom {@link TopoRecipeType} subclass. {@code recipeType.id()} must equal
     * {@link #id(path)}.
     */
    public <R extends TopoRecipe, T extends TopoRecipeType<R>> T recipeType(String path, T recipeType) {
        Identifier key = id(path);
        Objects.requireNonNull(recipeType, "recipe type");
        if (!key.equals(recipeType.id())) {
            throw new IllegalArgumentException(
                    "recipe type id mismatch: plugin path " + key + " != handle id " + recipeType.id());
        }
        return TopoRecipeTypes.begin(registry, recipeType, plugin.lang());
    }

    /** Register a recipe capability (item/fluid/scalar families, …). */
    public <C extends RecipeCapability<?, ?>> C capability(C capability) {
        return RecipeCapabilities.begin(capability);
    }

    /** Register a production line under this plugin's namespace. */
    public ProductionLine productionLine(String path) {
        return ProductionLines.begin(id(path));
    }
}
