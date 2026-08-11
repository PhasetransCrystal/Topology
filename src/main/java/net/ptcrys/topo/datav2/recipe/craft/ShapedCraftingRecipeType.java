package net.ptcrys.topo.datav2.recipe.craft;

import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.datav2.OfficialOIPlugin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/**
 * Vanilla-facing shaped crafting as a real {@link OIRecipeType}. Product entry is
 * {@code type.recipe(path, result, …).save()} — same shape as machine types, typed for the grid.
 */
public final class ShapedCraftingRecipeType extends OIRecipeType<OIRecipe> {

    public ShapedCraftingRecipeType(Identifier id) {
        super(id, OIRecipe::new);
        vanillaFacing();
    }

    private static RecipeDomainRegistration official() {
        return OfficialOIPlugin.INSTANCE.recipe();
    }

    public ShapedCraftingBuilder recipe(String recipePath, Supplier<? extends ItemLike> result) {
        return recipe(official(), recipePath, result, 1);
    }

    public ShapedCraftingBuilder recipe(
                                        String recipePath, Supplier<? extends ItemLike> result, int count) {
        return recipe(official(), recipePath, result, count);
    }

    public ShapedCraftingBuilder recipe(
                                        String recipePath, Supplier<? extends ItemLike> result, Object... recipe) {
        return recipe(official(), recipePath, result, 1, recipe);
    }

    public ShapedCraftingBuilder recipe(
                                        String recipePath, Supplier<? extends ItemLike> result, int count, Object... recipe) {
        return recipe(official(), recipePath, result, count, recipe);
    }

    public ShapedCraftingBuilder recipe(
                                        RecipeDomainRegistration domain,
                                        String recipePath,
                                        Supplier<? extends ItemLike> result) {
        return recipe(domain, recipePath, result, 1);
    }

    public ShapedCraftingBuilder recipe(
                                        RecipeDomainRegistration domain,
                                        String recipePath,
                                        Supplier<? extends ItemLike> result,
                                        int count) {
        return new ShapedCraftingBuilder(domain, this, recipePath, result, count);
    }

    public ShapedCraftingBuilder recipe(
                                        RecipeDomainRegistration domain,
                                        String recipePath,
                                        Supplier<? extends ItemLike> result,
                                        int count,
                                        Object... recipe) {
        ShapedCraftingBuilder builder = recipe(domain, recipePath, result, count);
        VanillaCraftTokens.applyShaped(builder, recipe);
        return builder;
    }

    /**
     * Machine-style capability builder is not used for vanilla-facing craft types; use
     * {@link #recipe(String, Supplier)} overloads.
     */
    @Override
    public OIRecipe.Builder<OIRecipe> recipe(String recipeName) {
        throw new UnsupportedOperationException(
                id() + " is vanilla-facing; use recipe(path, result, …) instead of capability builder");
    }
}
