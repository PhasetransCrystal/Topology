package net.ptcrys.topo.data.recipe.craft;

import net.ptcrys.registrylib.datagen.provider.RegistryLibRecipeProvider;
import net.ptcrys.topo.api.recipe.ExportHints;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.function.BiConsumer;

/** Shared emit helpers for {@link VanillaExportedRecipeAdapters} only. */
final class TopoCraftEmit {

    private TopoCraftEmit() {}

    static Ingredient resolveIngredient(
                                        ExportHints.IngredientSpec spec, RegistryLibRecipeProvider provider) {
        return switch (spec) {
            case ExportHints.IngredientSpec.ItemLikeSupplier s -> Ingredient.of(s.item().get());
            case ExportHints.IngredientSpec.Tag t -> Ingredient.of(
                    provider.registries().lookupOrThrow(Registries.ITEM).getOrThrow(t.tag()));
            case ExportHints.IngredientSpec.IngredientRaw raw -> raw.ingredient();
        };
    }

    static void applyUnlock(
                            BiConsumer<String, net.minecraft.advancements.Criterion<?>> unlockedBy,
                            RegistryLibRecipeProvider provider,
                            ExportHints hints,
                            ItemLike resultItem) {
        applyUnlock(unlockedBy, provider, hints, resultItem, null);
    }

    static void applyUnlock(
                            BiConsumer<String, net.minecraft.advancements.Criterion<?>> unlockedBy,
                            RegistryLibRecipeProvider provider,
                            ExportHints hints,
                            ItemLike resultItem,
                            ExportHints.@org.jspecify.annotations.Nullable CookingMeta cookingFallback) {
        ExportHints.Unlock unlock = hints.unlock();
        if (unlock instanceof ExportHints.Unlock.Item itemUnlock) {
            ItemLike unlockItem = itemUnlock.item().get();
            unlockedBy.accept(
                    "has_" + provider.safeName(unlockItem.asItem()), provider.has(unlockItem));
            return;
        }
        if (unlock instanceof ExportHints.Unlock.Tag tagUnlock) {
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag = tagUnlock.tag();
            unlockedBy.accept(
                    "has_" + tag.location().getPath().replace('/', '_'), provider.has(tag));
            return;
        }
        if (cookingFallback != null) {
            if (cookingFallback.input() instanceof ExportHints.IngredientSpec.ItemLikeSupplier s) {
                ItemLike inputItem = s.item().get();
                unlockedBy.accept(
                        "has_" + provider.safeName(inputItem.asItem()), provider.has(inputItem));
                return;
            }
            if (cookingFallback.input() instanceof ExportHints.IngredientSpec.Tag t) {
                unlockedBy.accept(
                        "has_" + t.tag().location().getPath().replace('/', '_'),
                        provider.has(t.tag()));
                return;
            }
        }
        unlockedBy.accept("has_" + provider.safeName(resultItem.asItem()), provider.has(resultItem));
    }
}
