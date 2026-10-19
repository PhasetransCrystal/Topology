package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;
import net.ptcrys.topo.api.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import java.util.List;

/** Owns recipe registration steps and their freeze fences. */
final class RecipeRegistrationModule implements RegistrationModule {

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, RecipeDomainRegistration.FOUNDATION, List.of(), context, RecipeCapabilities::freeze);
        plan.point(Phase.CONTENT, RecipeDomainRegistration.TYPES, List.of(RecipeDomainRegistration.FOUNDATION.id()), context, TopoRecipeTypes::freeze);
        plan.point(Phase.DERIVED, RecipeDomainRegistration.RECIPES, List.of(RecipeDomainRegistration.TYPES.id()), context, ProductionLines::freeze);
    }
}
