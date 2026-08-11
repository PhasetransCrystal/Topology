package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;

final class BuiltinTopoAirProductionLineRecipes {

    private BuiltinTopoAirProductionLineRecipes() {}

    static void init() {
        BuiltinTopoRecipeTypes.AIR_COLLECTOR.recipe("air_from_atmosphere")
                .productionLine(BuiltinTopoProductionLines.AIR)
                .tickInput(energy().in(10))
                .output(fluids().out(fluid(BuiltinTopoMaterials.AIR, BuiltinTopoMaterialForms.GAS), 100))
                .duration(20)
                .save();
    }
}
