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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shaped craft product builder: {@link #save()} mints an {@link OIRecipe} only, then asks the type
 * to {@link OIRecipeType#exportRecipe} via its bound adapter. Does not touch foreign serializers.
 */
public final class ShapedCraftingBuilder {

    private final RecipeDomainRegistration domain;
    private final OIRecipeType<OIRecipe> recipeType;
    private final String productPath;
    private final Supplier<? extends ItemLike> result;
    private final int resultCount;
    private final List<String> pattern = new ArrayList<>();
    private final Map<Character, ExportHints.IngredientSpec> defines = new LinkedHashMap<>();
    private RecipeCategory category = RecipeCategory.MISC;
    private Supplier<? extends ItemLike> unlockItem;
    private TagKey<Item> unlockTag;
    private String group;
    private boolean saved;

    ShapedCraftingBuilder(
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

    public ShapedCraftingBuilder category(RecipeCategory category) {
        this.category = Objects.requireNonNull(category, "category");
        return this;
    }

    public ShapedCraftingBuilder group(String group) {
        this.group = group;
        return this;
    }

    public ShapedCraftingBuilder pattern(String... rows) {
        Objects.requireNonNull(rows, "rows");
        if (rows.length == 0) {
            throw new IllegalArgumentException("pattern requires at least one row");
        }
        for (String row : rows) {
            pattern.add(Objects.requireNonNull(row, "pattern row"));
        }
        return this;
    }

    public ShapedCraftingBuilder define(char key, ItemLike item) {
        Objects.requireNonNull(item, "item");
        return putDefine(key, OiRecipeMint.itemSpec(item));
    }

    public ShapedCraftingBuilder define(char key, Supplier<? extends ItemLike> item) {
        Objects.requireNonNull(item, "item");
        return putDefine(key, OiRecipeMint.itemSpec(item));
    }

    public ShapedCraftingBuilder define(char key, TagKey<Item> tag) {
        Objects.requireNonNull(tag, "tag");
        return putDefine(key, OiRecipeMint.tagSpec(tag));
    }

    public ShapedCraftingBuilder define(char key, Ingredient ingredient) {
        Objects.requireNonNull(ingredient, "ingredient");
        return putDefine(key, OiRecipeMint.ingredientSpec(ingredient));
    }

    private ShapedCraftingBuilder putDefine(char key, ExportHints.IngredientSpec spec) {
        if (key == ' ') {
            throw new IllegalArgumentException("Space is reserved for empty slots");
        }
        if (defines.put(key, spec) != null) {
            throw new IllegalStateException("Duplicate define for key '" + key + "' in " + productPath);
        }
        return this;
    }

    public ShapedCraftingBuilder unlockedBy(ItemLike item) {
        Objects.requireNonNull(item, "unlock item");
        this.unlockItem = () -> item;
        this.unlockTag = null;
        return this;
    }

    public ShapedCraftingBuilder unlockedBy(Supplier<? extends ItemLike> item) {
        this.unlockItem = Objects.requireNonNull(item, "unlock item");
        this.unlockTag = null;
        return this;
    }

    public ShapedCraftingBuilder unlockedBy(TagKey<Item> tag) {
        this.unlockTag = Objects.requireNonNull(tag, "unlock tag");
        this.unlockItem = null;
        return this;
    }

    public void save() {
        if (saved) {
            throw new IllegalStateException("Crafting recipe already saved: " + productPath);
        }
        if (pattern.isEmpty()) {
            throw new IllegalStateException("Shaped recipe has no pattern: " + productPath);
        }
        if (defines.isEmpty()) {
            throw new IllegalStateException("Shaped recipe has no defines: " + productPath);
        }
        saved = true;

        String oiName = VanillaFacingPaths.craftingForeignPath(productPath);
        String foreignPath = oiName;
        List<OIItemInput> inputs = OiRecipeMint.flattenShaped(pattern, defines);
        OiRecipeMint.mintItemOnly(recipeType, oiName, result, resultCount, inputs, 1);

        List<ExportHints.ShapedKey> keys = new ArrayList<>(defines.size());
        for (Map.Entry<Character, ExportHints.IngredientSpec> e : defines.entrySet()) {
            keys.add(new ExportHints.ShapedKey(e.getKey(), e.getValue()));
        }
        ExportHints hints = ExportHints.shaped(
                foreignPath,
                category,
                group,
                OiRecipeMint.unlockOf(unlockItem, unlockTag),
                result,
                resultCount,
                List.copyOf(pattern),
                keys);
        recipeType.exportRecipe(oiName, null, hints);
    }
}
