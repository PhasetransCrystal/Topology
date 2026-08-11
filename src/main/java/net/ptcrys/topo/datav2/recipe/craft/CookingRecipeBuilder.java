package net.ptcrys.topo.datav2.recipe.craft;

import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;
import net.ptcrys.topo.apiv2.recipe.ExportHints;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.VanillaFacingPaths;
import net.ptcrys.topo.apiv2.recipe.content.OIItemInput;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;

import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cooking product builder: {@link #save()} mints {@link OIRecipe} then
 * {@link OIRecipeType#exportRecipe}. No direct foreign emit.
 */
public final class CookingRecipeBuilder {

    public enum Kind {

        SMELTING(200),
        BLASTING(100),
        SMOKING(100),
        CAMPFIRE(600);

        private final int defaultTime;

        Kind(int defaultTime) {
            this.defaultTime = defaultTime;
        }

        int defaultTime() {
            return defaultTime;
        }
    }

    private final RecipeDomainRegistration domain;
    private final OIRecipeType<OIRecipe> recipeType;
    private final String kindFolder;
    private final String productPath;
    private final Supplier<? extends ItemLike> result;
    private final int resultCount;
    private final Kind kind;
    private ExportHints.IngredientSpec input;
    private float experience = 0.1F;
    private int cookingTime = -1;
    private RecipeCategory category = RecipeCategory.MISC;
    private CookingBookCategory cookingCategory = CookingBookCategory.MISC;
    private Supplier<? extends ItemLike> unlockItem;
    private TagKey<Item> unlockTag;
    private String group;
    private final LinkedHashSet<ProductionLine> productionLines = new LinkedHashSet<>();
    private boolean saved;

    CookingRecipeBuilder(
                         RecipeDomainRegistration domain,
                         OIRecipeType<OIRecipe> recipeType,
                         Kind kind,
                         String kindFolder,
                         String recipePath,
                         Supplier<? extends ItemLike> result,
                         int count) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        this.kind = Objects.requireNonNull(kind, "kind");
        this.kindFolder = Objects.requireNonNull(kindFolder, "kindFolder");
        this.productPath = VanillaFacingPaths.requireLowerPath(
                Objects.requireNonNull(recipePath, "recipePath"));
        this.result = Objects.requireNonNull(result, "result");
        if (count <= 0) {
            throw new IllegalArgumentException("result count must be > 0: " + recipePath);
        }
        this.resultCount = count;
    }

    public CookingRecipeBuilder category(RecipeCategory category) {
        this.category = Objects.requireNonNull(category, "category");
        return this;
    }

    public CookingRecipeBuilder cookingCategory(CookingBookCategory cookingCategory) {
        this.cookingCategory = Objects.requireNonNull(cookingCategory, "cookingCategory");
        return this;
    }

    public CookingRecipeBuilder group(String group) {
        this.group = group;
        return this;
    }

    public CookingRecipeBuilder experience(float experience) {
        if (experience < 0.0F) {
            throw new IllegalArgumentException("experience must be >= 0: " + productPath);
        }
        this.experience = experience;
        return this;
    }

    public CookingRecipeBuilder cookingTime(int cookingTime) {
        if (cookingTime <= 0) {
            throw new IllegalArgumentException("cookingTime must be > 0: " + productPath);
        }
        this.cookingTime = cookingTime;
        return this;
    }

    public CookingRecipeBuilder input(Supplier<? extends ItemLike> item) {
        this.input = OiRecipeMint.itemSpec(Objects.requireNonNull(item, "input item"));
        return this;
    }

    public CookingRecipeBuilder input(TagKey<Item> tag) {
        this.input = OiRecipeMint.tagSpec(Objects.requireNonNull(tag, "input tag"));
        return this;
    }

    public CookingRecipeBuilder input(Ingredient ingredient) {
        this.input = OiRecipeMint.ingredientSpec(Objects.requireNonNull(ingredient, "input ingredient"));
        return this;
    }

    public CookingRecipeBuilder unlockedBy(Supplier<? extends ItemLike> item) {
        this.unlockItem = Objects.requireNonNull(item, "unlock item");
        this.unlockTag = null;
        return this;
    }

    public CookingRecipeBuilder unlockedBy(TagKey<Item> tag) {
        this.unlockTag = Objects.requireNonNull(tag, "unlock tag");
        this.unlockItem = null;
        return this;
    }

    /** OI-side production line tags (export to vanilla cooking ignores these). */
    public CookingRecipeBuilder productionLine(ProductionLine line) {
        productionLines.add(Objects.requireNonNull(line, "production line"));
        return this;
    }

    public CookingRecipeBuilder productionLines(ProductionLine... lines) {
        Objects.requireNonNull(lines, "production lines");
        for (ProductionLine line : lines) {
            productionLine(line);
        }
        return this;
    }

    public void save() {
        if (saved) {
            throw new IllegalStateException("Cooking recipe already saved: " + productPath);
        }
        if (input == null) {
            throw new IllegalStateException("Cooking recipe has no input: " + productPath);
        }
        if (resultCount != 1) {
            throw new IllegalStateException("Cooking result count > 1 is not supported for " + productPath);
        }
        saved = true;

        String foreignPath = VanillaFacingPaths.foreignPath(productPath, kindFolder);
        String oiName = VanillaFacingPaths.oiRecipeName(productPath, kindFolder);
        int time = cookingTime > 0 ? cookingTime : kind.defaultTime();
        OIItemInput itemInput = OiRecipeMint.toInput(input, 1);
        OiRecipeMint.mintItemOnly(
                recipeType, oiName, result, 1, List.of(itemInput), time, List.copyOf(productionLines));

        ExportHints hints = ExportHints.cooking(
                foreignPath,
                category,
                cookingCategory,
                group,
                OiRecipeMint.unlockOf(unlockItem, unlockTag),
                result,
                input,
                experience,
                time);
        recipeType.exportRecipe(oiName, null, hints);
    }
}
