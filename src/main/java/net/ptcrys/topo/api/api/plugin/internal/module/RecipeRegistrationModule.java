package net.ptcrys.topo.api.api.plugin.internal.module;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.hook.RecipePlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationContext;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationModule;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPlan;
import net.ptcrys.topo.api.api.plugin.internal.RegistrationPoint;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;
import net.ptcrys.topo.api.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import java.util.List;

/** Owns recipe registration steps and their freeze fences. */
public final class RecipeRegistrationModule implements RegistrationModule {

    static final RegistrationPoint<RecipePlugin, RecipeDomainRegistration> FOUNDATION = RegistrationPoint.of("topo:recipe/foundation", RecipePlugin.class, RecipeDomainRegistration.DOMAIN, RecipePlugin::registerRecipeFoundation);
    static final RegistrationPoint<RecipePlugin, RecipeDomainRegistration> TYPES = RegistrationPoint.of("topo:recipe/types", RecipePlugin.class, RecipeDomainRegistration.DOMAIN, RecipePlugin::registerRecipeTypes);
    static final RegistrationPoint<RecipePlugin, RecipeDomainRegistration> RECIPES = RegistrationPoint.of("topo:recipe/recipes", RecipePlugin.class, RecipeDomainRegistration.DOMAIN, RecipePlugin::registerRecipes);

    @Override
    public void define(RegistrationPlan.Builder plan, RegistrationContext context) {
        plan.point(Phase.CONTENT, RecipeRegistrationModule.FOUNDATION, List.of(), context, RecipeCapabilities::freeze);
        plan.point(Phase.CONTENT, RecipeRegistrationModule.TYPES, List.of(RecipeRegistrationModule.FOUNDATION.id()), context, TopoRecipeTypes::freeze);
        plan.point(Phase.DERIVED, RecipeRegistrationModule.RECIPES, List.of(RecipeRegistrationModule.TYPES.id()), context, ProductionLines::freeze);
    }
}
