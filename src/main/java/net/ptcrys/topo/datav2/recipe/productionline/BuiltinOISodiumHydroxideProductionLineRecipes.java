package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;

import net.minecraft.world.level.material.Fluids;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.advancedEnergy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.heat;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinOISodiumHydroxideProductionLineRecipes {

    private static final int SOLUTION_MB = 1_000;
    private static final int GAS_MB = 500;

    private BuiltinOISodiumHydroxideProductionLineRecipes() {}

    static void init() {
        BuiltinOIRecipeTypes.MIXING_TANK.recipe("brine_from_salt")
                .productionLines(BuiltinOIProductionLines.sodiumHydroxideConsumers())
                .input(items().in(BuiltinOIMaterials.SALT, BuiltinOIMaterialForms.DUST, 1))
                .input(fluids().in(Fluids.WATER, SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.BRINE, BuiltinOIMaterialForms.SOLUTION), SOLUTION_MB))
                .tickInput(energy().in(20))
                .duration(100)
                .save();

        BuiltinOIRecipeTypes.ELECTROLYZER.recipe("sodium_hydroxide_solution_from_brine")
                .productionLines(BuiltinOIProductionLines.sodiumHydroxideConsumers())
                .input(fluids().in(fluid(BuiltinOIMaterials.BRINE, BuiltinOIMaterialForms.SOLUTION), SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.CHLORINE, BuiltinOIMaterialForms.GAS), GAS_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.HYDROGEN, BuiltinOIMaterialForms.GAS), GAS_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.SODIUM_HYDROXIDE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(advancedEnergy().in(2))
                .duration(180)
                .save();

        BuiltinOIRecipeTypes.EVAPORATOR.recipe("sodium_hydroxide_dust_from_solution")
                .productionLines(BuiltinOIProductionLines.sodiumHydroxideConsumers())
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.SODIUM_HYDROXIDE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(items().out(BuiltinOIMaterials.SODIUM_HYDROXIDE, BuiltinOIMaterialForms.DUST, 1))
                .tickInput(heat().in(10))
                .duration(120)
                .save();
    }
}
