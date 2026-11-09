package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 空气收集生产线的配方。 */
public final class BuiltinTopoAirProductionLineRecipes {

    private BuiltinTopoAirProductionLineRecipes() {}

    public static void init() {
        BuiltinRecipeTypes.AIR_COLLECTOR.recipe("air_from_atmosphere")
                .productionLine(BuiltinTopoProductionLines.AIR)
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(10))
                .output(BuiltinTopoResourceIntegrations.FLUID.recipeCapability()
                        .out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.AIR, BuiltinMaterialForms.GAS), 100))
                .duration(20)
                .save();
    }
}
