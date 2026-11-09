package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.world.level.material.Fluids;

/** 旧版热量生产线中的固定配方。 */
public final class BuiltinTopoHeatProductionLineRecipes {

    private BuiltinTopoHeatProductionLineRecipes() {}

    public static void init() {
        BuiltinRecipeTypes.BOILER.recipe("water_to_heat")
                .productionLine(BuiltinTopoProductionLines.HEAT)
                .input(BuiltinTopoResourceIntegrations.FLUID.recipeCapability().in(Fluids.WATER, 1))
                .tickOutput(BuiltinTopoResourceIntegrations.HEAT.recipeCapability().out(20))
                .duration(10)
                .save();

        BuiltinRecipeTypes.RESISTIVE_HEATER.recipe("heat_from_energy")
                .productionLine(BuiltinTopoProductionLines.HEAT)
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(2))
                .tickOutput(BuiltinTopoResourceIntegrations.HEAT.recipeCapability().out(1))
                .duration(20)
                .save();
    }
}
