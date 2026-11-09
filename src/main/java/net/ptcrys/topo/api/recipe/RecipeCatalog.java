package net.ptcrys.topo.api.recipe;

import net.ptcrys.topo.api.recipe.productionline.ProductionLine;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Declaration catalog for one recipe type: stable recipe keys, duplicate-aware factories, and
 * explicit replacement of imported recipes. This state is declaration/registry-time only; runtime
 * search state lives in {@link RecipeSearchState}.
 */
final class RecipeCatalog<R extends TopoRecipe> {

    private final TopoRecipeType<R> owner;
    private final Map<ResourceKey<Recipe<?>>, DuplicateAwareRecipeFactory<R>> recipesByKey = new LinkedHashMap<>();
    private final Set<ResourceKey<Recipe<?>>> replacingImported = new LinkedHashSet<>();

    RecipeCatalog(TopoRecipeType<R> owner) {
        this.owner = Objects.requireNonNull(owner, "recipe type");
    }

    void add(String recipeName, R recipe) {
        duplicateAwareRecipe(recipeName).addDirect(recipe);
    }

    void add(String recipeName, Function<HolderLookup.Provider, R> recipeFactory) {
        duplicateAwareRecipe(recipeName).add(recipeFactory);
    }

    List<R> registered() {
        List<R> recipes = new ArrayList<>(recipesByKey.size());
        for (DuplicateAwareRecipeFactory<R> factory : recipesByKey.values()) {
            R recipe = factory.registeredRecipe();
            if (recipe != null) {
                recipes.add(recipe);
            }
        }
        return List.copyOf(recipes);
    }

    void markReplacesImported(String recipeName) {
        replacingImported.add(owner.recipeKey(recipeName));
    }

    boolean replacesImported(ResourceKey<Recipe<?>> recipeKey) {
        return replacingImported.contains(recipeKey);
    }

    /**
     * GameTest fixtures are not present in a fresh developer checkout until datagen runs. In
     * fixture processes, expose declared recipes that the recipe manager does not contain while
     * keeping datapack entries authoritative when the same id exists.
     */
    void appendMissingGameTestRecipes(List<RecipeHolder<R>> holders) {
        if (!Boolean.getBoolean("topo.gametest.fixtures")) {
            return;
        }
        Set<ResourceKey<Recipe<?>>> seenIds = new LinkedHashSet<>();
        for (RecipeHolder<R> holder : holders) {
            seenIds.add(holder.id());
        }
        for (Map.Entry<ResourceKey<Recipe<?>>, DuplicateAwareRecipeFactory<R>> entry : recipesByKey.entrySet()) {
            R declared = entry.getValue().registeredRecipe();
            if (declared != null && seenIds.add(entry.getKey())) {
                holders.add(new RecipeHolder<>(entry.getKey(), declared));
            }
        }
    }

    private DuplicateAwareRecipeFactory<R> duplicateAwareRecipe(String recipeName) {
        ResourceKey<Recipe<?>> key = owner.recipeKey(recipeName);
        DuplicateAwareRecipeFactory<R> duplicateAware = recipesByKey.get(key);
        if (duplicateAware == null) {
            duplicateAware = new DuplicateAwareRecipeFactory(owner, key);
            recipesByKey.put(key, duplicateAware);
            owner.entry().addRecipe(recipeName, duplicateAware::createAndValidate);
        }
        return duplicateAware;
    }

    private static final class DuplicateAwareRecipeFactory<R extends TopoRecipe> {

        private final TopoRecipeType<R> recipeType;
        private final ResourceKey<Recipe<?>> recipeKey;
        private final List<Function<HolderLookup.Provider, R>> factories = new ArrayList<>();
        private @Nullable R registeredRecipe;

        private DuplicateAwareRecipeFactory(TopoRecipeType<R> recipeType, ResourceKey<Recipe<?>> recipeKey) {
            this.recipeType = recipeType;
            this.recipeKey = recipeKey;
        }

        private void add(Function<HolderLookup.Provider, R> factory) {
            factories.add(Objects.requireNonNull(factory, "recipe factory"));
        }

        private void addDirect(R recipe) {
            Objects.requireNonNull(recipe, "recipe");
            add(_provider -> recipe);
            registeredRecipe = merge(registeredRecipe, recipe);
        }

        private R createAndValidate(HolderLookup.Provider provider) {
            R primary = factories.getFirst().apply(provider);
            LinkedHashSet<ProductionLine> productionLines = new LinkedHashSet<>(primary.productionLines());
            for (int i = 1; i < factories.size(); i++) {
                R duplicate = factories.get(i).apply(provider);
                productionLines.addAll(duplicate.productionLines());
                if (!sameRecipeContents(primary, duplicate)) {
                    throw new IllegalStateException(
                            "Duplicate recipe " + recipeKey.identifier() + " has conflicting contents: first=" + recipeType.recipeJson(primary) + ", duplicate=" + recipeType.recipeJson(duplicate));
                }
            }
            R resolved = create(
                    primary.inputs(),
                    primary.outputs(),
                    primary.tickInputs(),
                    primary.tickOutputs(),
                    primary.duration(),
                    productionLines);
            registeredRecipe = resolved;
            return resolved;
        }

        private @Nullable R registeredRecipe() {
            return registeredRecipe;
        }

        private R merge(@Nullable R current, R incoming) {
            if (current == null) {
                return incoming;
            }
            if (!sameRecipeContents(current, incoming)) {
                throw new IllegalStateException(
                        "Duplicate recipe " + recipeKey.identifier() + " has conflicting contents: first=" + recipeType.recipeJson(current) + ", duplicate=" + recipeType.recipeJson(incoming));
            }
            LinkedHashSet<ProductionLine> productionLines = new LinkedHashSet<>(current.productionLines());
            productionLines.addAll(incoming.productionLines());
            return create(
                    current.inputs(),
                    current.outputs(),
                    current.tickInputs(),
                    current.tickOutputs(),
                    current.duration(),
                    productionLines);
        }

        private R create(
                         TopoRecipe.InputEntry<?>[] inputs,
                         TopoRecipe.OutputEntry<?>[] outputs,
                         TopoRecipe.InputEntry<?>[] tickInputs,
                         TopoRecipe.OutputEntry<?>[] tickOutputs,
                         int duration,
                         Set<ProductionLine> productionLines) {
            return recipeType.createRecipe(
                    inputs,
                    outputs,
                    tickInputs,
                    tickOutputs,
                    duration,
                    List.copyOf(productionLines));
        }

        private static <T extends TopoRecipe> boolean sameRecipeContents(T first, T second) {
            return first.duration() == second.duration() && sameInputs(first.inputs(), second.inputs()) && sameOutputs(first.outputs(), second.outputs()) && sameInputs(first.tickInputs(), second.tickInputs()) && sameOutputs(first.tickOutputs(), second.tickOutputs());
        }

        private static boolean sameInputs(TopoRecipe.InputEntry<?>[] first, TopoRecipe.InputEntry<?>[] second) {
            if (first.length != second.length) {
                return false;
            }
            for (int i = 0; i < first.length; i++) {
                if (!first[i].sameContents(second[i])) {
                    return false;
                }
            }
            return true;
        }

        private static boolean sameOutputs(TopoRecipe.OutputEntry<?>[] first, TopoRecipe.OutputEntry<?>[] second) {
            if (first.length != second.length) {
                return false;
            }
            for (int i = 0; i < first.length; i++) {
                if (!first[i].sameContents(second[i])) {
                    return false;
                }
            }
            return true;
        }
    }
}
