package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;

final class BuiltinOIAirProductionLineRecipes {

    private BuiltinOIAirProductionLineRecipes() {}

    static void init() {
        BuiltinOIRecipeTypes.AIR_COLLECTOR.recipe("air_from_atmosphere")
                .productionLine(BuiltinOIProductionLines.AIR)
                .tickInput(energy().in(10))
                .output(fluids().out(fluid(BuiltinOIMaterials.AIR, BuiltinOIMaterialForms.GAS), 100))
                .duration(20)
                .save();
    }
}
