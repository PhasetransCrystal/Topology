package net.ptcrys.topo.api.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Foreign recipe import/export configuration and holder projection for one recipe type. This state
 * is declaration/runtime projection only; local recipe declarations live in {@link RecipeCatalog}.
 */
final class RecipeImports<R extends TopoRecipe> {

    private final TopoRecipeType<R> owner;
    private final List<RecipeType<?>> importedTypes = new ArrayList<>();
    private @Nullable ImportedRecipeAdapter<R> importedAdapter;
    private @Nullable ExportedRecipeAdapter<R> exportedAdapter;
    private @Nullable RecipeType<?> exportedForeignType;

    RecipeImports(TopoRecipeType<R> owner) {
        this.owner = Objects.requireNonNull(owner, "recipe type");
    }

    void importRecipesFrom(ImportedRecipeAdapter<R> adapter, RecipeType<?>... types) {
        Objects.requireNonNull(adapter, "imported recipe adapter");
        Objects.requireNonNull(types, "imported recipe types");
        if (types.length == 0) {
            throw new IllegalArgumentException(
                    "importRecipesFrom requires at least one foreign RecipeType for " + owner.id());
        }
        if (importedAdapter != null && importedAdapter != adapter) {
            throw new IllegalStateException(
                    "Recipe type " + owner.id() + " already imports with a different adapter; one type, one projection");
        }
        importedAdapter = adapter;
        for (RecipeType<?> type : types) {
            importedTypes.add(Objects.requireNonNull(type, "imported recipe type"));
        }
    }

    List<RecipeType<?>> importedTypes() {
        return Collections.unmodifiableList(importedTypes);
    }

    @Nullable
    ImportedRecipeAdapter<R> importedAdapter() {
        return importedAdapter;
    }

    void exportRecipesTo(ExportedRecipeAdapter<R> adapter, RecipeType<?> foreignType) {
        Objects.requireNonNull(adapter, "exported recipe adapter");
        Objects.requireNonNull(foreignType, "foreign recipe type");
        if (exportedAdapter != null && exportedAdapter != adapter) {
            throw new IllegalStateException(
                    "Recipe type " + owner.id() + " already exports with a different adapter");
        }
        exportedAdapter = adapter;
        exportedForeignType = foreignType;
    }

    @Nullable
    ExportedRecipeAdapter<R> exportedAdapter() {
        return exportedAdapter;
    }

    @Nullable
    RecipeType<?> exportedForeignType() {
        return exportedForeignType;
    }

    void exportRecipe(String recipeName, @Nullable R recipe, ExportHints hints, boolean vanillaFacing) {
        Objects.requireNonNull(recipeName, "recipeName");
        Objects.requireNonNull(hints, "hints");
        ExportedRecipeAdapter<R> adapter = exportedAdapter;
        if (adapter == null) {
            if (vanillaFacing) {
                throw new IllegalStateException(
                        "Recipe type " + owner.id() + " is vanilla-facing but has no exportRecipesTo adapter");
            }
            return;
        }
        Identifier foreignId = adapter.scheduleExport(recipeName, recipe, hints);
        if (foreignId == null) {
            throw new IllegalStateException(
                    "Export adapter returned null for " + owner.id() + " / " + recipeName + " (foreign path " + hints.foreignPath() + ")");
        }
        ExportRegistry.register(foreignId, owner.id());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    List<RecipeHolder<R>> collectHolders(MinecraftServer server, RecipeCatalog<R> catalog) {
        Objects.requireNonNull(server, "server");
        List<RecipeHolder<R>> own = new ArrayList<>(server.getRecipeManager().recipeMap().byType(owner.vanillaType()));
        catalog.appendMissingGameTestRecipes(own);
        if (importedTypes.isEmpty()) {
            return own;
        }
        if (importedAdapter == null) {
            throw new IllegalStateException(
                    "Recipe type " + owner.id() + " has import sources but no adapter (broken importRecipesFrom)");
        }

        Set<Object> ownKeys = new HashSet<>();
        Set<Object> replacedImportKeys = new HashSet<>();
        for (RecipeHolder<R> holder : own) {
            Set<Object> keys = importConflictKeys(holder.value());
            if (catalog.replacesImported(holder.id())) {
                replacedImportKeys.addAll(keys);
            } else {
                ownKeys.addAll(keys);
            }
        }

        List<RecipeHolder<R>> merged = new ArrayList<>(own);
        Set<ResourceKey<Recipe<?>>> seenIds = new HashSet<>();
        for (RecipeHolder<R> holder : own) {
            seenIds.add(holder.id());
        }
        for (RecipeType<?> importedType : importedTypes) {
            for (RecipeHolder<?> source : holdersOf(server, importedType)) {
                if (ExportRegistry.isExportedBy(source.id(), owner.id())) {
                    continue;
                }
                R adapted = importedAdapter.adapt(source.id().identifier(), source.value());
                if (adapted == null) {
                    continue;
                }
                Set<Object> keys = importConflictKeys(adapted);
                if (!keys.isEmpty() && !Collections.disjoint(keys, replacedImportKeys)) {
                    continue;
                }
                if (!keys.isEmpty() && !Collections.disjoint(keys, ownKeys)) {
                    throw new IllegalStateException(
                            "Recipe type " + owner.id() + ": imported " + source.id().identifier() + " conflicts with a written recipe on start-input keys " + keys + ". Mark the written recipe with replacesImported(), or change inputs.");
                }
                ResourceKey<Recipe<?>> importedId = importedHolderId(source.id());
                if (!seenIds.add(importedId)) {
                    throw new IllegalStateException("Recipe type " + owner.id() + ": imported " + source.id().identifier() + " collides with an existing recipe id " + importedId.identifier());
                }
                merged.add(new RecipeHolder<>(importedId, adapted));
            }
        }
        return merged;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Collection<RecipeHolder<?>> holdersOf(MinecraftServer server, RecipeType<?> type) {
        return (Collection) server.getRecipeManager().recipeMap().byType((RecipeType) type);
    }

    /**
     * Imported recipe id: {@code imports/<type path length>/<type path>/<source namespace>/<path>}.
     * Length and namespace segments preserve nested type/source boundaries.
     */
    ResourceKey<Recipe<?>> importedHolderId(ResourceKey<Recipe<?>> sourceId) {
        Identifier source = sourceId.identifier();
        String prefixedPath = "imports/" + owner.id().getPath().length() + "/" + owner.id().getPath() + "/" + source.getNamespace() + "/" + source.getPath();
        return ResourceKey.create(Registries.RECIPE, owner.id().withPath(prefixedPath));
    }

    private static Set<Object> importConflictKeys(TopoRecipe recipe) {
        Set<Object> keys = new HashSet<>();
        for (TopoRecipe.InputEntry<?> entry : recipe.inputs()) {
            keys.addAll(entry.importConflictKeys());
        }
        return keys;
    }
}
