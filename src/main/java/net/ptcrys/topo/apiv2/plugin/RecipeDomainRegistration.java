package net.ptcrys.topo.apiv2.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.OIRecipeTypes;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLines;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Sole recipe-domain registration entry for one {@link OIPlugin}. Paths are bare; namespace and
 * {@link RegistryCore} are fixed by the plugin. One method per concern — no product-facing
 * overloads.
 */
public final class RecipeDomainRegistration {

    private final OIPlugin plugin;
    private final RegistryCore registry;

    private RecipeDomainRegistration(OIPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static RecipeDomainRegistration of(OIPlugin plugin) {
        return new RecipeDomainRegistration(plugin, plugin.registry());
    }

    public OIPlugin plugin() {
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

    /** Begin a default {@link OIRecipe} machine recipe type under this plugin's namespace. */
    public OIRecipeType<OIRecipe> recipeType(String path) {
        return OIRecipeTypes.begin(registry, id(path), plugin.lang());
    }

    /**
     * Bind and register a custom {@link OIRecipeType} subclass. {@code recipeType.id()} must equal
     * {@link #id(path)}.
     */
    public <R extends OIRecipe, T extends OIRecipeType<R>> T recipeType(String path, T recipeType) {
        Identifier key = id(path);
        Objects.requireNonNull(recipeType, "recipe type");
        if (!key.equals(recipeType.id())) {
            throw new IllegalArgumentException(
                    "recipe type id mismatch: plugin path " + key + " != handle id " + recipeType.id());
        }
        return OIRecipeTypes.begin(registry, recipeType, plugin.lang());
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
