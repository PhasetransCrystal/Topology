package net.ptcrys.topodemo.data.recipe;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

public final class BuiltinRecipeTypes {

    private static final net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration RECIPE = TopoDemoPlugin.INSTANCE.recipe();
    public static final TopoRecipeType<TopoRecipe> GRINDING = RECIPE.recipeType("grinding").displayName("Grinding", "粉碎");
    public static final TopoRecipeType<TopoRecipe> ALLOYING = RECIPE.recipeType("alloying").displayName("Alloying", "合金混合");
    public static final ProductionLine METALLURGY = RECIPE.productionLine("metallurgy");

    private BuiltinRecipeTypes() {}

    public static void init() {}
}
