package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.advancedEnergy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.heat;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinTopoLuminiteProductionLineRecipes {

    private static final int SOLUTION_MB = 1_000;
    private static final int GAS_MB = 500;

    private BuiltinTopoLuminiteProductionLineRecipes() {}

    static void init() {
        // Duplicate the generic fine-grinding declarations under the same recipe names so the
        // recipe type merges only production-line metadata after all 12x consumer lines exist.
        BuiltinTopoRecipeTypes.FINE_GRINDER.recipe("copper_dust_from_ingot")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items().in(BuiltinTopoMaterials.COPPER, BuiltinTopoMaterialForms.INGOT, 1))
                .output(items().out(BuiltinTopoMaterials.COPPER, BuiltinTopoMaterialForms.DUST, 1))
                .tickInput(energy().in(30))
                .duration(100)
                .save();

        BuiltinTopoRecipeTypes.FINE_GRINDER.recipe("nickel_dust_from_ingot")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items().in(BuiltinTopoMaterials.NICKEL, BuiltinTopoMaterialForms.INGOT, 1))
                .output(items().out(BuiltinTopoMaterials.NICKEL, BuiltinTopoMaterialForms.DUST, 1))
                .tickInput(energy().in(30))
                .duration(100)
                .save();

        BuiltinTopoRecipeTypes.CHEMICAL_REACTOR.recipe("copper_sulfate_solution_from_copper_dust")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items().in(BuiltinTopoMaterials.COPPER, BuiltinTopoMaterialForms.DUST, 2))
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.SULFURIC_ACID, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.COPPER_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.WASTE_GAS, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .tickInput(heat().in(10))
                .duration(160)
                .save();

        BuiltinTopoRecipeTypes.CHEMICAL_REACTOR.recipe("nickel_sulfate_solution_from_nickel_dust")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items().in(BuiltinTopoMaterials.NICKEL, BuiltinTopoMaterialForms.DUST, 1))
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.SULFURIC_ACID, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.NICKEL_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.WASTE_GAS, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .tickInput(heat().in(10))
                .duration(160)
                .save();

        BuiltinTopoRecipeTypes.MIXING_TANK.recipe("copper_nickel_sulfate_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.COPPER_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.NICKEL_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.COPPER_NICKEL_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();

        BuiltinTopoRecipeTypes.CHEMICAL_REACTOR.recipe("copper_nickel_hydroxide_from_sulfate_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.COPPER_NICKEL_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .input(items().in(BuiltinTopoMaterials.SODIUM_HYDROXIDE, BuiltinTopoMaterialForms.DUST, 1))
                .output(items().out(BuiltinTopoMaterials.COPPER_NICKEL_HYDROXIDE,
                        BuiltinTopoMaterialForms.DUST, 1))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.SODIUM_SULFATE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(160)
                .save();

        BuiltinTopoRecipeTypes.ELECTRIC_FURNACE.recipe("copper_nickel_oxide_from_hydroxide")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items().in(BuiltinTopoMaterials.COPPER_NICKEL_HYDROXIDE, BuiltinTopoMaterialForms.DUST, 1))
                .output(items().out(BuiltinTopoMaterials.COPPER_NICKEL_OXIDE, BuiltinTopoMaterialForms.DUST, 1))
                .output(fluids().out(fluid(BuiltinTopoMaterials.STEAM, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();

        BuiltinTopoRecipeTypes.EVAPORATOR.recipe("copper_nickel_oxide_solution_from_oxide")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(items().in(BuiltinTopoMaterials.COPPER_NICKEL_OXIDE,
                        BuiltinTopoMaterialForms.DUST, 1))
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.ELECTROLYTE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.COPPER_NICKEL_OXIDE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(heat().in(10))
                .duration(160)
                .save();

        BuiltinTopoRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER.recipe("luminite_solution_from_copper_nickel_oxide_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.COPPER_NICKEL_OXIDE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.LUMINITE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(advancedEnergy().in(2))
                .duration(200)
                .save();

        BuiltinTopoRecipeTypes.ELECTROLYZER.recipe("luminite_gem_from_luminite_solution")
                .productionLines(BuiltinTopoProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinTopoMaterials.LUMINITE, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(items().out(BuiltinTopoMaterials.LUMINITE, BuiltinTopoMaterialForms.GEM, 4))
                .output(fluids().out(
                        fluid(BuiltinTopoMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinTopoMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(advancedEnergy().in(2))
                .duration(200)
                .save();
    }
}
