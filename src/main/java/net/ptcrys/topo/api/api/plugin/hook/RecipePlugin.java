package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named recipe contributions. Each hook visits all plugins before the module freezes its tables. */
public interface RecipePlugin extends TopoContribution {

    default void registerRecipeFoundation(RecipeDomainRegistration recipe) {}

    default void registerRecipeTypes(RecipeDomainRegistration recipe) {}

    default void registerRecipes(RecipeDomainRegistration recipe) {}
}
