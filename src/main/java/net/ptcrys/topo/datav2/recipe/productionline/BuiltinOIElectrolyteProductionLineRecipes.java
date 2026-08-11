package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinOIElectrolyteProductionLineRecipes {

    private static final int SOLUTION_MB = 1_000;

    private BuiltinOIElectrolyteProductionLineRecipes() {}

    static void init() {
        // No free water: sulfuric acid solution already supplies aqueous volume. That keeps brine
        // (salt + water) and electrolyte (salt + acid) as disjoint kind sets for match/conflict scan.
        BuiltinOIRecipeTypes.MIXING_TANK.recipe("electrolyte_from_salt_sulfuric_acid")
                .productionLines(BuiltinOIProductionLines.electrolyteConsumers())
                .input(items().in(BuiltinOIMaterials.SALT, BuiltinOIMaterialForms.DUST, 1))
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.SULFURIC_ACID, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.ELECTROLYTE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();
    }
}
