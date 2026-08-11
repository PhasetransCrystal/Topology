package net.ptcrys.topo.datav2.recipe.craft;

import net.ptcrys.topo.apiv2.recipe.ExportHints;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.content.OIItemInput;
import net.ptcrys.topo.apiv2.recipe.content.OIItemOutput;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;

import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Mint OI recipes only (no foreign emit). Used by vanilla-facing builders. */
final class OiRecipeMint {

    private OiRecipeMint() {}

    static void mintItemOnly(
                             OIRecipeType<OIRecipe> type,
                             String oiRecipeName,
                             Supplier<? extends ItemLike> result,
                             int resultCount,
                             List<OIItemInput> inputs,
                             int duration) {
        mintItemOnly(type, oiRecipeName, result, resultCount, inputs, duration, List.of());
    }

    static void mintItemOnly(
                             OIRecipeType<OIRecipe> type,
                             String oiRecipeName,
                             Supplier<? extends ItemLike> result,
                             int resultCount,
                             List<OIItemInput> inputs,
                             int duration,
                             List<ProductionLine> productionLines) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(oiRecipeName, "oiRecipeName");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(productionLines, "productionLines");
        if (inputs.isEmpty()) {
            throw new IllegalStateException("OI recipe requires at least one item input: " + oiRecipeName);
        }
        List<ProductionLine> lines = List.copyOf(productionLines);
        type.addRecipe(oiRecipeName, registries -> {
            ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
            ItemLike resultItem = result.get();
            OIItemOutput output = OIItemOutput.of(resultItem, resultCount);
            return type.createRecipe(
                    new OIRecipe.InputEntry<?>[] { new OIRecipe.InputEntry<>(items, List.copyOf(inputs)) },
                    new OIRecipe.OutputEntry<?>[] { new OIRecipe.OutputEntry<>(items, List.of(output)) },
                    OIRecipe.EMPTY_INPUTS,
                    OIRecipe.EMPTY_OUTPUTS,
                    duration,
                    lines);
        });
    }

    static OIItemInput toInput(ExportHints.IngredientSpec spec, long count) {
        return switch (spec) {
            case ExportHints.IngredientSpec.ItemLikeSupplier s -> OIItemInput.of(s.item(), count);
            case ExportHints.IngredientSpec.Tag t -> OIItemInput.tag(t.tag(), count);
            case ExportHints.IngredientSpec.IngredientRaw raw -> OIItemInput.fromIngredient(raw.ingredient(), count);
        };
    }

    static ExportHints.IngredientSpec itemSpec(ItemLike item) {
        return new ExportHints.IngredientSpec.ItemLikeSupplier(() -> item);
    }

    static ExportHints.IngredientSpec itemSpec(Supplier<? extends ItemLike> item) {
        return new ExportHints.IngredientSpec.ItemLikeSupplier(item);
    }

    static ExportHints.IngredientSpec tagSpec(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        return new ExportHints.IngredientSpec.Tag(tag);
    }

    static ExportHints.IngredientSpec ingredientSpec(net.minecraft.world.item.crafting.Ingredient ingredient) {
        return new ExportHints.IngredientSpec.IngredientRaw(ingredient);
    }

    static List<OIItemInput> flattenShaped(
                                           List<String> pattern, Map<Character, ExportHints.IngredientSpec> keys) {
        Map<Character, Integer> counts = new LinkedHashMap<>();
        for (String row : pattern) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c == ' ') {
                    continue;
                }
                counts.merge(c, 1, Integer::sum);
            }
        }
        List<OIItemInput> inputs = new ArrayList<>();
        for (Map.Entry<Character, Integer> e : counts.entrySet()) {
            ExportHints.IngredientSpec spec = keys.get(e.getKey());
            if (spec == null) {
                throw new IllegalStateException("Pattern key '" + e.getKey() + "' has no define");
            }
            inputs.add(toInput(spec, e.getValue()));
        }
        return inputs;
    }

    static ExportHints.@org.jspecify.annotations.Nullable Unlock unlockOf(
                                                                          @org.jspecify.annotations.Nullable Supplier<? extends ItemLike> unlockItem,
                                                                          net.minecraft.tags.@org.jspecify.annotations.Nullable TagKey<net.minecraft.world.item.Item> unlockTag) {
        if (unlockItem != null) {
            return new ExportHints.Unlock.Item(unlockItem);
        }
        if (unlockTag != null) {
            return new ExportHints.Unlock.Tag(unlockTag);
        }
        return null;
    }
}
