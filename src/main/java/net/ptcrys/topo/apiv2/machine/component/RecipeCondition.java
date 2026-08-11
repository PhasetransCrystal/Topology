package net.ptcrys.topo.apiv2.machine.component;

/** Generic pre-search condition hook for recipe logic traits. */
@FunctionalInterface
public interface RecipeCondition {

    ServiceKey<RecipeCondition, RecipeLogic> KEY = ServiceKey.oi("recipe_condition", RecipeCondition.class, RecipeLogic.class);

    boolean allowsRecipeSearch();
}
