package net.ptcrys.topo.data.recipe.craft;

import net.ptcrys.topo.api.recipe.ExportHints;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;

import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Mint Topo recipes only (no foreign emit). Used by vanilla-facing builders. */
final class TopoRecipeMint {

    private TopoRecipeMint() {}

    static void mintItemOnly(
                             TopoRecipeType<TopoRecipe> type,
                             String oiRecipeName,
                             Supplier<? extends ItemLike> result,
                             int resultCount,
                             List<TopoItemInput> inputs,
                             int duration) {
        mintItemOnly(type, oiRecipeName, result, resultCount, inputs, duration, List.of());
    }

    static void mintItemOnly(
                             TopoRecipeType<TopoRecipe> type,
                             String oiRecipeName,
                             Supplier<? extends ItemLike> result,
                             int resultCount,
                             List<TopoItemInput> inputs,
                             int duration,
                             List<ProductionLine> productionLines) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(oiRecipeName, "oiRecipeName");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(productionLines, "productionLines");
        if (inputs.isEmpty()) {
            throw new IllegalStateException("Topo recipe requires at least one item input: " + oiRecipeName);
        }
        List<ProductionLine> lines = List.copyOf(productionLines);
        type.addRecipe(oiRecipeName, registries -> {
            ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
            ItemLike resultItem = result.get();
            TopoItemOutput output = TopoItemOutput.of(resultItem, resultCount);
            return type.createRecipe(
                    new TopoRecipe.InputEntry<?>[] { new TopoRecipe.InputEntry<>(items, List.copyOf(inputs)) },
                    new TopoRecipe.OutputEntry<?>[] { new TopoRecipe.OutputEntry<>(items, List.of(output)) },
                    TopoRecipe.EMPTY_INPUTS,
                    TopoRecipe.EMPTY_OUTPUTS,
                    duration,
                    lines);
        });
    }

    static TopoItemInput toInput(ExportHints.IngredientSpec spec, long count) {
        return switch (spec) {
            case ExportHints.IngredientSpec.ItemLikeSupplier s -> TopoItemInput.of(s.item(), count);
            case ExportHints.IngredientSpec.Tag t -> TopoItemInput.tag(t.tag(), count);
            case ExportHints.IngredientSpec.IngredientRaw raw -> TopoItemInput.fromIngredient(raw.ingredient(), count);
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

    static List<TopoItemInput> flattenShaped(
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
        List<TopoItemInput> inputs = new ArrayList<>();
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
