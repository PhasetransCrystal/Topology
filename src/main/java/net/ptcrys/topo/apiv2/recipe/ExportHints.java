package net.ptcrys.topo.apiv2.recipe;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Side-channel for write projection ({@link ExportedRecipeAdapter}). Not part of {@link OIRecipe}
 * codec — builders assemble hints, then {@link OIRecipeType#exportRecipe} hands them to the adapter.
 *
 * <p>
 * Use {@link RecipeCategory} (datagen / unlock advancement folder), never
 * {@link net.minecraft.world.item.crafting.RecipeBookCategory}.
 */
public record ExportHints(
                          String foreignPath,
                          RecipeCategory recipeCategory,
                          @Nullable String group,
                          @Nullable Unlock unlock,
                          Supplier<? extends ItemLike> result,
                          int resultCount,
                          Optional<ShapedLayout> shaped,
                          Optional<ShapelessLayout> shapeless,
                          Optional<CookingMeta> cooking) {

    public ExportHints {
        Objects.requireNonNull(foreignPath, "foreignPath");
        Objects.requireNonNull(recipeCategory, "recipeCategory");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(shaped, "shaped");
        Objects.requireNonNull(shapeless, "shapeless");
        Objects.requireNonNull(cooking, "cooking");
        if (foreignPath.isBlank()) {
            throw new IllegalArgumentException("foreignPath must not be blank");
        }
        if (resultCount <= 0) {
            throw new IllegalArgumentException("resultCount must be > 0");
        }
    }

    public static ExportHints shaped(
                                     String foreignPath,
                                     RecipeCategory category,
                                     @Nullable String group,
                                     @Nullable Unlock unlock,
                                     Supplier<? extends ItemLike> result,
                                     int resultCount,
                                     List<String> pattern,
                                     List<ShapedKey> keys) {
        return new ExportHints(
                foreignPath,
                category,
                group,
                unlock,
                result,
                resultCount,
                Optional.of(new ShapedLayout(List.copyOf(pattern), List.copyOf(keys))),
                Optional.empty(),
                Optional.empty());
    }

    public static ExportHints shapeless(
                                        String foreignPath,
                                        RecipeCategory category,
                                        @Nullable String group,
                                        @Nullable Unlock unlock,
                                        Supplier<? extends ItemLike> result,
                                        int resultCount,
                                        List<CountedIngredient> ingredients) {
        return new ExportHints(
                foreignPath,
                category,
                group,
                unlock,
                result,
                resultCount,
                Optional.empty(),
                Optional.of(new ShapelessLayout(List.copyOf(ingredients))),
                Optional.empty());
    }

    public static ExportHints cooking(
                                      String foreignPath,
                                      RecipeCategory category,
                                      CookingBookCategory cookingCategory,
                                      @Nullable String group,
                                      @Nullable Unlock unlock,
                                      Supplier<? extends ItemLike> result,
                                      IngredientSpec input,
                                      float experience,
                                      int cookingTime) {
        return new ExportHints(
                foreignPath,
                category,
                group,
                unlock,
                result,
                1,
                Optional.empty(),
                Optional.empty(),
                Optional.of(new CookingMeta(input, experience, cookingTime, cookingCategory)));
    }

    public record ShapedLayout(List<String> pattern, List<ShapedKey> keys) {

        public ShapedLayout {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(keys, "keys");
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("shaped pattern must not be empty");
            }
        }
    }

    public record ShapedKey(char key, IngredientSpec ingredient) {

        public ShapedKey {
            if (key == ' ') {
                throw new IllegalArgumentException("space is reserved");
            }
            Objects.requireNonNull(ingredient, "ingredient");
        }
    }

    public record ShapelessLayout(List<CountedIngredient> ingredients) {

        public ShapelessLayout {
            Objects.requireNonNull(ingredients, "ingredients");
            if (ingredients.isEmpty()) {
                throw new IllegalArgumentException("shapeless ingredients must not be empty");
            }
        }
    }

    public record CountedIngredient(IngredientSpec ingredient, int count) {

        public CountedIngredient {
            Objects.requireNonNull(ingredient, "ingredient");
            if (count <= 0) {
                throw new IllegalArgumentException("count must be > 0");
            }
        }
    }

    public record CookingMeta(
                              IngredientSpec input,
                              float experience,
                              int cookingTime,
                              CookingBookCategory cookingCategory) {

        public CookingMeta {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(cookingCategory, "cookingCategory");
            if (experience < 0.0F) {
                throw new IllegalArgumentException("experience must be >= 0");
            }
            if (cookingTime <= 0) {
                throw new IllegalArgumentException("cookingTime must be > 0");
            }
        }
    }

    public sealed interface Unlock permits Unlock.Item, Unlock.Tag {

        record Item(Supplier<? extends ItemLike> item) implements Unlock {

            public Item {
                Objects.requireNonNull(item, "item");
            }
        }

        /** Unlock by item tag (not confused with {@link IngredientSpec.Tag}). */
        record Tag(TagKey<net.minecraft.world.item.Item> tag) implements Unlock {

            public Tag {
                Objects.requireNonNull(tag, "tag");
            }
        }
    }

    /** Deferred ingredient identity for export + OI mirror minting. */
    public sealed interface IngredientSpec
                                           permits IngredientSpec.ItemLikeSupplier, IngredientSpec.Tag, IngredientSpec.IngredientRaw {

        record ItemLikeSupplier(Supplier<? extends ItemLike> item) implements IngredientSpec {

            public ItemLikeSupplier {
                Objects.requireNonNull(item, "item");
            }
        }

        record Tag(TagKey<Item> tag) implements IngredientSpec {

            public Tag {
                Objects.requireNonNull(tag, "tag");
            }
        }

        record IngredientRaw(Ingredient ingredient) implements IngredientSpec {

            public IngredientRaw {
                Objects.requireNonNull(ingredient, "ingredient");
            }
        }
    }
}
