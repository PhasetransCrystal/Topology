package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinTopoElectrolyteProductionLineRecipes {

    private static final int SOLUTION_MB = 1_000;

    private BuiltinTopoElectrolyteProductionLineRecipes() {}

    static void init() {
        // No free water: sulfuric acid solution already supplies aqueous volume. That keeps brine
        // (salt + water) and electrolyte (salt + acid) as disjoint kind sets for match/conflict scan.
        BuiltinTopoRecipeTypes.MIXING_TANK.recipe("electrolyte_from_salt_sulfuric_acid")
                .productionLines(BuiltinTopoProductionLines.electrolyteConsumers())
                .input(items().in(BuiltinTopoMaterials.SALT, BuiltinTopoMaterialForms.DUST, 1))
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.SULFURIC_ACID, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.ELECTROLYTE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();
    }
}
