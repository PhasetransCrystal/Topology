package net.ptcrys.topo.datav2.recipe.craft;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.Objects;
import java.util.function.Supplier;

/** Compact token parsers for shaped / shapeless / cooking overloads (internal). */
final class VanillaCraftTokens {

    private VanillaCraftTokens() {}

    @SuppressWarnings("unchecked")
    static CookingRecipeBuilder applyCookingInput(CookingRecipeBuilder builder, Object input) {
        return switch (input) {
            case Supplier<?> supplier -> builder.input((Supplier<? extends ItemLike>) supplier);
            case ItemLike item -> builder.input(() -> item);
            case TagKey<?> tag -> builder.input((TagKey<Item>) tag);
            case Ingredient ingredient -> builder.input(ingredient);
            default -> throw new IllegalArgumentException(
                    "unsupported cooking input: " + (input == null ? "null" : input.getClass().getName()));
        };
    }

    @SuppressWarnings("unchecked")
    static void applyShaped(ShapedCraftingBuilder builder, Object... recipe) {
        Objects.requireNonNull(recipe, "recipe");
        for (int i = 0; i < recipe.length; i++) {
            Object token = recipe[i];
            if (token instanceof String row) {
                builder.pattern(row);
                continue;
            }
            if (token instanceof String[] rows) {
                builder.pattern(rows);
                continue;
            }
            if (token instanceof Character key) {
                if (i + 1 >= recipe.length) {
                    throw new IllegalArgumentException(
                            "shaped recipe ends after key '" + key + "' without ingredient");
                }
                Object content = recipe[++i];
                switch (content) {
                    case Supplier<?> supplier -> builder.define(key, (Supplier<? extends ItemLike>) supplier);
                    case ItemLike item -> builder.define(key, item);
                    case TagKey<?> tag -> builder.define(key, (TagKey<Item>) tag);
                    case Ingredient ingredient -> builder.define(key, ingredient);
                    default -> throw new IllegalArgumentException(
                            "unsupported shaped define for '" + key + "': " + content.getClass().getName());
                }
                continue;
            }
            throw new IllegalArgumentException(
                    "unsupported shaped recipe token: " + (token == null ? "null" : token.getClass().getName()));
        }
    }

    @SuppressWarnings("unchecked")
    static void applyShapeless(ShapelessCraftingBuilder builder, Object... ingredients) {
        Objects.requireNonNull(ingredients, "ingredients");
        for (int i = 0; i < ingredients.length; i++) {
            Object token = ingredients[i];
            int count = 1;
            if (i + 1 < ingredients.length && ingredients[i + 1] instanceof Integer n) {
                if (n <= 0) {
                    throw new IllegalArgumentException("ingredient count must be > 0");
                }
                count = n;
                i++;
            }
            switch (token) {
                case Supplier<?> supplier -> builder.requires((Supplier<? extends ItemLike>) supplier, count);
                case ItemLike item -> builder.requires(item, count);
                case TagKey<?> tag -> builder.requires((TagKey<Item>) tag, count);
                case Ingredient ingredient -> builder.requires(ingredient, count);
                default -> throw new IllegalArgumentException(
                        "unsupported shapeless ingredient: " + token.getClass().getName());
            }
        }
    }
}
