package net.ptcrys.topo.datav2.recipe.craft;

import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;
import net.ptcrys.topo.apiv2.recipe.ExportHints;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.VanillaFacingPaths;
import net.ptcrys.topo.apiv2.recipe.content.OIItemInput;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shapeless craft builder: {@link #save()} mints {@link OIRecipe} then
 * {@link OIRecipeType#exportRecipe} — no direct foreign emit.
 */
public final class ShapelessCraftingBuilder {

    private final RecipeDomainRegistration domain;
    private final OIRecipeType<OIRecipe> recipeType;
    private final String productPath;
    private final Supplier<? extends ItemLike> result;
    private final int resultCount;
    private final List<ExportHints.CountedIngredient> requirements = new ArrayList<>();
    private RecipeCategory category = RecipeCategory.MISC;
    private Supplier<? extends ItemLike> unlockItem;
    private TagKey<Item> unlockTag;
    private String group;
    private boolean saved;

    ShapelessCraftingBuilder(
                             RecipeDomainRegistration domain,
                             OIRecipeType<OIRecipe> recipeType,
                             String recipePath,
                             Supplier<? extends ItemLike> result,
                             int count) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        this.productPath = VanillaFacingPaths.requireLowerPath(
                Objects.requireNonNull(recipePath, "recipePath"));
        this.result = Objects.requireNonNull(result, "result");
        if (count <= 0) {
            throw new IllegalArgumentException("result count must be > 0: " + recipePath);
        }
        this.resultCount = count;
    }

    public ShapelessCraftingBuilder category(RecipeCategory category) {
        this.category = Objects.requireNonNull(category, "category");
        return this;
    }

    public ShapelessCraftingBuilder group(String group) {
        this.group = group;
        return this;
    }

    public ShapelessCraftingBuilder requires(ItemLike item) {
        return requires(item, 1);
    }

    public ShapelessCraftingBuilder requires(ItemLike item, int count) {
        Objects.requireNonNull(item, "item");
        requireCount(count);
        requirements.add(new ExportHints.CountedIngredient(OiRecipeMint.itemSpec(item), count));
        return this;
    }

    public ShapelessCraftingBuilder requires(Supplier<? extends ItemLike> item) {
        return requires(item, 1);
    }

    public ShapelessCraftingBuilder requires(Supplier<? extends ItemLike> item, int count) {
        Objects.requireNonNull(item, "item");
        requireCount(count);
        requirements.add(new ExportHints.CountedIngredient(OiRecipeMint.itemSpec(item), count));
        return this;
    }

    public ShapelessCraftingBuilder requires(TagKey<Item> tag) {
        return requires(tag, 1);
    }

    public ShapelessCraftingBuilder requires(TagKey<Item> tag, int count) {
        Objects.requireNonNull(tag, "tag");
        requireCount(count);
        requirements.add(new ExportHints.CountedIngredient(OiRecipeMint.tagSpec(tag), count));
        return this;
    }

    public ShapelessCraftingBuilder requires(Ingredient ingredient) {
        return requires(ingredient, 1);
    }

    public ShapelessCraftingBuilder requires(Ingredient ingredient, int count) {
        Objects.requireNonNull(ingredient, "ingredient");
        requireCount(count);
        requirements.add(new ExportHints.CountedIngredient(OiRecipeMint.ingredientSpec(ingredient), count));
        return this;
    }

    private static void requireCount(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be > 0");
        }
    }

    public ShapelessCraftingBuilder unlockedBy(ItemLike item) {
        Objects.requireNonNull(item, "unlock item");
        this.unlockItem = () -> item;
        this.unlockTag = null;
        return this;
    }

    public ShapelessCraftingBuilder unlockedBy(Supplier<? extends ItemLike> item) {
        this.unlockItem = Objects.requireNonNull(item, "unlock item");
        this.unlockTag = null;
        return this;
    }

    public ShapelessCraftingBuilder unlockedBy(TagKey<Item> tag) {
        this.unlockTag = Objects.requireNonNull(tag, "unlock tag");
        this.unlockItem = null;
        return this;
    }

    public void save() {
        if (saved) {
            throw new IllegalStateException("Crafting recipe already saved: " + productPath);
        }
        if (requirements.isEmpty()) {
            throw new IllegalStateException("Shapeless recipe has no ingredients: " + productPath);
        }
        saved = true;

        String oiName = VanillaFacingPaths.craftingForeignPath(productPath);
        List<OIItemInput> inputs = new ArrayList<>(requirements.size());
        for (ExportHints.CountedIngredient req : requirements) {
            inputs.add(OiRecipeMint.toInput(req.ingredient(), req.count()));
        }
        OiRecipeMint.mintItemOnly(recipeType, oiName, result, resultCount, inputs, 1);

        ExportHints hints = ExportHints.shapeless(
                oiName,
                category,
                group,
                OiRecipeMint.unlockOf(unlockItem, unlockTag),
                result,
                resultCount,
                List.copyOf(requirements));
        recipeType.exportRecipe(oiName, null, hints);
    }
}
