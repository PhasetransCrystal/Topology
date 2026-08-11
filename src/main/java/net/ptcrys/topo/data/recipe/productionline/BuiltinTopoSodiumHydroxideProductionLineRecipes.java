package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import net.minecraft.world.level.material.Fluids;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.advancedEnergy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.heat;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinTopoSodiumHydroxideProductionLineRecipes {

    private static final int SOLUTION_MB = 1_000;
    private static final int GAS_MB = 500;

    private BuiltinTopoSodiumHydroxideProductionLineRecipes() {}

    static void init() {
        BuiltinTopoRecipeTypes.MIXING_TANK.recipe("brine_from_salt")
                .productionLines(BuiltinTopoProductionLines.sodiumHydroxideConsumers())
                .input(items().in(BuiltinTopoMaterials.SALT, BuiltinTopoMaterialForms.DUST, 1))
                .input(fluids().in(Fluids.WATER, SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.BRINE, BuiltinTopoMaterialForms.SOLUTION), SOLUTION_MB))
                .tickInput(energy().in(20))
                .duration(100)
                .save();

        BuiltinTopoRecipeTypes.ELECTROLYZER.recipe("sodium_hydroxide_solution_from_brine")
                .productionLines(BuiltinTopoProductionLines.sodiumHydroxideConsumers())
                .input(fluids().in(fluid(BuiltinTopoMaterials.BRINE, BuiltinTopoMaterialForms.SOLUTION), SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.CHLORINE, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.HYDROGEN, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.SODIUM_HYDROXIDE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(advancedEnergy().in(2))
                .duration(180)
                .save();

        BuiltinTopoRecipeTypes.EVAPORATOR.recipe("sodium_hydroxide_dust_from_solution")
                .productionLines(BuiltinTopoProductionLines.sodiumHydroxideConsumers())
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.SODIUM_HYDROXIDE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(items().out(BuiltinTopoMaterials.SODIUM_HYDROXIDE, BuiltinTopoMaterialForms.DUST, 1))
                .tickInput(heat().in(10))
                .duration(120)
                .save();
    }
}
