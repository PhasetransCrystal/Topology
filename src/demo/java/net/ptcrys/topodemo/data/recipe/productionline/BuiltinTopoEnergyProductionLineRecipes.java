package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 能量与高级能量生产线的固定配方。 */
public final class BuiltinTopoEnergyProductionLineRecipes {

    private BuiltinTopoEnergyProductionLineRecipes() {}

    public static void init() {
        BuiltinRecipeTypes.COMBUSTION_GENERATOR.recipe("hydrogen_gas_to_energy")
                .productionLine(BuiltinTopoProductionLines.ENERGY)
                .input(BuiltinTopoResourceIntegrations.FLUID.recipeCapability()
                        .in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.HYDROGEN, BuiltinMaterialForms.GAS), 100))
                .tickOutput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().out(20))
                .duration(20)
                .save();

        BuiltinRecipeTypes.ENERGY_COMPRESSOR.recipe("advanced_energy_from_energy")
                .productionLine(BuiltinTopoProductionLines.ADVANCED_ENERGY)
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(10))
                .tickOutput(BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability().out(1))
                .duration(20)
                .save();
    }
}
