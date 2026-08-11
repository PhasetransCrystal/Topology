package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.advancedEnergy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;

final class BuiltinTopoEnergyProductionLineRecipes {

    private BuiltinTopoEnergyProductionLineRecipes() {}

    static void init() {
        BuiltinTopoRecipeTypes.COMBUSTION_GENERATOR.recipe("hydrogen_gas_to_energy")
                .productionLine(BuiltinTopoProductionLines.ENERGY)
                .input(fluids().in(fluid(BuiltinTopoMaterials.HYDROGEN, BuiltinTopoMaterialForms.GAS), 100))
                .tickOutput(energy().out(20))
                .duration(20)
                .save();

        BuiltinTopoRecipeTypes.ENERGY_COMPRESSOR.recipe("advanced_energy_from_energy")
                .productionLine(BuiltinTopoProductionLines.ADVANCED_ENERGY)
                .tickInput(energy().in(10))
                .tickOutput(advancedEnergy().out(1))
                .duration(20)
                .save();
    }
}
