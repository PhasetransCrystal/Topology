package net.ptcrys.topo.data.recipe.craft;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.data.OfficialTopoPlugin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/**
 * Vanilla-facing shapeless crafting as a real {@link TopoRecipeType}.
 */
public final class ShapelessCraftingRecipeType extends TopoRecipeType<TopoRecipe> {

    public ShapelessCraftingRecipeType(Identifier id) {
        super(id, TopoRecipe::new);
        vanillaFacing();
    }

    private static RecipeDomainRegistration official() {
        return OfficialTopoPlugin.INSTANCE.recipe();
    }

    public ShapelessCraftingBuilder recipe(String recipePath, Supplier<? extends ItemLike> result) {
        return recipe(official(), recipePath, result, 1);
    }

    public ShapelessCraftingBuilder recipe(
                                           String recipePath, Supplier<? extends ItemLike> result, int count) {
        return recipe(official(), recipePath, result, count);
    }

    public ShapelessCraftingBuilder recipe(
                                           String recipePath, Supplier<? extends ItemLike> result, Object... ingredients) {
        return recipe(official(), recipePath, result, 1, ingredients);
    }

    public ShapelessCraftingBuilder recipe(
                                           String recipePath, Supplier<? extends ItemLike> result, int count, Object... ingredients) {
        return recipe(official(), recipePath, result, count, ingredients);
    }

    public ShapelessCraftingBuilder recipe(
                                           RecipeDomainRegistration domain,
                                           String recipePath,
                                           Supplier<? extends ItemLike> result) {
        return recipe(domain, recipePath, result, 1);
    }

    public ShapelessCraftingBuilder recipe(
                                           RecipeDomainRegistration domain,
                                           String recipePath,
                                           Supplier<? extends ItemLike> result,
                                           int count) {
        return new ShapelessCraftingBuilder(domain, this, recipePath, result, count);
    }

    public ShapelessCraftingBuilder recipe(
                                           RecipeDomainRegistration domain,
                                           String recipePath,
                                           Supplier<? extends ItemLike> result,
                                           int count,
                                           Object... ingredients) {
        ShapelessCraftingBuilder builder = recipe(domain, recipePath, result, count);
        VanillaCraftTokens.applyShapeless(builder, ingredients);
        return builder;
    }

    @Override
    public TopoRecipe.Builder<TopoRecipe> recipe(String recipeName) {
        throw new UnsupportedOperationException(
                id() + " is vanilla-facing; use recipe(path, result, …) instead of capability builder");
    }
}
