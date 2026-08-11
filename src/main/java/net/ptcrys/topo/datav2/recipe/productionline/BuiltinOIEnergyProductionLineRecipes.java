package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.advancedEnergy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;

final class BuiltinOIEnergyProductionLineRecipes {

    private BuiltinOIEnergyProductionLineRecipes() {}

    static void init() {
        BuiltinOIRecipeTypes.COMBUSTION_GENERATOR.recipe("hydrogen_gas_to_energy")
                .productionLine(BuiltinOIProductionLines.ENERGY)
                .input(fluids().in(fluid(BuiltinOIMaterials.HYDROGEN, BuiltinOIMaterialForms.GAS), 100))
                .tickOutput(energy().out(20))
                .duration(20)
                .save();

        BuiltinOIRecipeTypes.ENERGY_COMPRESSOR.recipe("advanced_energy_from_energy")
                .productionLine(BuiltinOIProductionLines.ADVANCED_ENERGY)
                .tickInput(energy().in(10))
                .tickOutput(advancedEnergy().out(1))
                .duration(20)
                .save();
    }
}
