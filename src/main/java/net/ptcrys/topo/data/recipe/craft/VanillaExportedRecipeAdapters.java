package net.ptcrys.topo.data.recipe.craft;

import net.ptcrys.registrylib.datagen.ProviderType;
import net.ptcrys.registrylib.datagen.provider.RegistryLibRecipeProvider;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.recipe.ExportHints;
import net.ptcrys.topo.api.recipe.ExportedRecipeAdapter;
import net.ptcrys.topo.api.recipe.TopoRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.SimpleCookingRecipeBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;

import java.util.Objects;

/**
 * Write projection: already-minted {@link TopoRecipe} + {@link ExportHints} → foreign recipe JSON
 * and unlock advancements. Product builders must not call RegistryLib emit directly.
 */
public final class VanillaExportedRecipeAdapters {

    private VanillaExportedRecipeAdapters() {}

    public static ExportedRecipeAdapter<TopoRecipe> shaped(RecipeDomainRegistration domain) {
        Objects.requireNonNull(domain, "domain");
        return (recipeId, recipe, hints) -> {
            ExportHints.ShapedLayout layout = hints.shaped().orElse(null);
            if (layout == null) {
                return null;
            }
            Identifier foreignId = domain.id(hints.foreignPath());
            domain.registry()
                    .addDataGenerator(ProviderType.RECIPE, provider -> emitShaped(provider, domain, hints, layout));
            return foreignId;
        };
    }

    public static ExportedRecipeAdapter<TopoRecipe> shapeless(RecipeDomainRegistration domain) {
        Objects.requireNonNull(domain, "domain");
        return (recipeId, recipe, hints) -> {
            ExportHints.ShapelessLayout layout = hints.shapeless().orElse(null);
            if (layout == null) {
                return null;
            }
            Identifier foreignId = domain.id(hints.foreignPath());
            domain.registry()
                    .addDataGenerator(
                            ProviderType.RECIPE, provider -> emitShapeless(provider, domain, hints, layout));
            return foreignId;
        };
    }

    public static ExportedRecipeAdapter<TopoRecipe> cooking(
                                                            RecipeDomainRegistration domain, CookingRecipeBuilder.Kind kind) {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(kind, "kind");
        return (recipeId, recipe, hints) -> {
            ExportHints.CookingMeta meta = hints.cooking().orElse(null);
            if (meta == null || hints.resultCount() != 1) {
                return null;
            }
            Identifier foreignId = domain.id(hints.foreignPath());
            domain.registry()
                    .addDataGenerator(
                            ProviderType.RECIPE, provider -> emitCooking(provider, domain, hints, meta, kind));
            return foreignId;
        };
    }

    private static void emitShaped(
                                   RegistryLibRecipeProvider provider,
                                   RecipeDomainRegistration domain,
                                   ExportHints hints,
                                   ExportHints.ShapedLayout layout) {
        ItemLike resultItem = hints.result().get();
        ShapedRecipeBuilder builder = provider.shaped(hints.recipeCategory(), resultItem, hints.resultCount());
        for (String row : layout.pattern()) {
            builder.pattern(row);
        }
        for (ExportHints.ShapedKey key : layout.keys()) {
            builder.define(key.key(), TopoCraftEmit.resolveIngredient(key.ingredient(), provider));
        }
        if (hints.group() != null) {
            builder.group(hints.group());
        }
        TopoCraftEmit.applyUnlock(builder::unlockedBy, provider, hints, resultItem);
        builder.save(provider, ResourceKey.create(Registries.RECIPE, domain.id(hints.foreignPath())));
    }

    private static void emitShapeless(
                                      RegistryLibRecipeProvider provider,
                                      RecipeDomainRegistration domain,
                                      ExportHints hints,
                                      ExportHints.ShapelessLayout layout) {
        ItemLike resultItem = hints.result().get();
        ShapelessRecipeBuilder builder = provider.shapeless(hints.recipeCategory(), resultItem, hints.resultCount());
        for (ExportHints.CountedIngredient req : layout.ingredients()) {
            Ingredient ingredient = TopoCraftEmit.resolveIngredient(req.ingredient(), provider);
            builder.requires(ingredient, req.count());
        }
        if (hints.group() != null) {
            builder.group(hints.group());
        }
        TopoCraftEmit.applyUnlock(builder::unlockedBy, provider, hints, resultItem);
        builder.save(provider, ResourceKey.create(Registries.RECIPE, domain.id(hints.foreignPath())));
    }

    private static void emitCooking(
                                    RegistryLibRecipeProvider provider,
                                    RecipeDomainRegistration domain,
                                    ExportHints hints,
                                    ExportHints.CookingMeta meta,
                                    CookingRecipeBuilder.Kind kind) {
        ItemLike resultItem = hints.result().get();
        Ingredient ingredient = TopoCraftEmit.resolveIngredient(meta.input(), provider);
        SimpleCookingRecipeBuilder builder = switch (kind) {
            case SMELTING -> SimpleCookingRecipeBuilder.smelting(
                    ingredient,
                    hints.recipeCategory(),
                    meta.cookingCategory(),
                    resultItem,
                    meta.experience(),
                    meta.cookingTime());
            case BLASTING -> SimpleCookingRecipeBuilder.blasting(
                    ingredient,
                    hints.recipeCategory(),
                    meta.cookingCategory(),
                    resultItem,
                    meta.experience(),
                    meta.cookingTime());
            case SMOKING -> SimpleCookingRecipeBuilder.smoking(
                    ingredient, hints.recipeCategory(), resultItem, meta.experience(), meta.cookingTime());
            case CAMPFIRE -> SimpleCookingRecipeBuilder.campfireCooking(
                    ingredient, hints.recipeCategory(), resultItem, meta.experience(), meta.cookingTime());
        };
        if (hints.group() != null) {
            builder.group(hints.group());
        }
        TopoCraftEmit.applyUnlock(builder::unlockedBy, provider, hints, resultItem, meta);
        builder.save(provider, ResourceKey.create(Registries.RECIPE, domain.id(hints.foreignPath())));
    }
}
