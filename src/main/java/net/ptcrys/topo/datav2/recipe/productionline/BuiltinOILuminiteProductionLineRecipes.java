package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.advancedEnergy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.heat;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinOILuminiteProductionLineRecipes {

    private static final int SOLUTION_MB = 1_000;
    private static final int GAS_MB = 500;

    private BuiltinOILuminiteProductionLineRecipes() {}

    static void init() {
        // Duplicate the generic fine-grinding declarations under the same recipe names so the
        // recipe type merges only production-line metadata after all 12x consumer lines exist.
        BuiltinOIRecipeTypes.FINE_GRINDER.recipe("copper_dust_from_ingot")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(items().in(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.INGOT, 1))
                .output(items().out(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.DUST, 1))
                .tickInput(energy().in(30))
                .duration(100)
                .save();

        BuiltinOIRecipeTypes.FINE_GRINDER.recipe("nickel_dust_from_ingot")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(items().in(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.INGOT, 1))
                .output(items().out(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.DUST, 1))
                .tickInput(energy().in(30))
                .duration(100)
                .save();

        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe("copper_sulfate_solution_from_copper_dust")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(items().in(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.DUST, 2))
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.SULFURIC_ACID, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.COPPER_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.WASTE_GAS, BuiltinOIMaterialForms.GAS), GAS_MB))
                .tickInput(heat().in(10))
                .duration(160)
                .save();

        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe("nickel_sulfate_solution_from_nickel_dust")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(items().in(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.DUST, 1))
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.SULFURIC_ACID, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.NICKEL_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.WASTE_GAS, BuiltinOIMaterialForms.GAS), GAS_MB))
                .tickInput(heat().in(10))
                .duration(160)
                .save();

        BuiltinOIRecipeTypes.MIXING_TANK.recipe("copper_nickel_sulfate_solution")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.COPPER_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.NICKEL_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.COPPER_NICKEL_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();

        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe("copper_nickel_hydroxide_from_sulfate_solution")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.COPPER_NICKEL_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .input(items().in(BuiltinOIMaterials.SODIUM_HYDROXIDE, BuiltinOIMaterialForms.DUST, 1))
                .output(items().out(BuiltinOIMaterials.COPPER_NICKEL_HYDROXIDE,
                        BuiltinOIMaterialForms.DUST, 1))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.SODIUM_SULFATE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(160)
                .save();

        BuiltinOIRecipeTypes.ELECTRIC_FURNACE.recipe("copper_nickel_oxide_from_hydroxide")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(items().in(BuiltinOIMaterials.COPPER_NICKEL_HYDROXIDE, BuiltinOIMaterialForms.DUST, 1))
                .output(items().out(BuiltinOIMaterials.COPPER_NICKEL_OXIDE, BuiltinOIMaterialForms.DUST, 1))
                .output(fluids().out(fluid(BuiltinOIMaterials.STEAM, BuiltinOIMaterialForms.GAS), GAS_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();

        BuiltinOIRecipeTypes.EVAPORATOR.recipe("copper_nickel_oxide_solution_from_oxide")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(items().in(BuiltinOIMaterials.COPPER_NICKEL_OXIDE,
                        BuiltinOIMaterialForms.DUST, 1))
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.ELECTROLYTE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.COPPER_NICKEL_OXIDE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(heat().in(10))
                .duration(160)
                .save();

        BuiltinOIRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER.recipe("luminite_solution_from_copper_nickel_oxide_solution")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.COPPER_NICKEL_OXIDE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.LUMINITE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(advancedEnergy().in(2))
                .duration(200)
                .save();

        BuiltinOIRecipeTypes.ELECTROLYZER.recipe("luminite_gem_from_luminite_solution")
                .productionLines(BuiltinOIProductionLines.luminiteConsumers())
                .input(fluids().in(
                        fluid(BuiltinOIMaterials.LUMINITE, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .output(items().out(BuiltinOIMaterials.LUMINITE, BuiltinOIMaterialForms.GEM, 4))
                .output(fluids().out(
                        fluid(BuiltinOIMaterials.SPENT_CRYSTAL_LIQUOR, BuiltinOIMaterialForms.SOLUTION),
                        SOLUTION_MB))
                .tickInput(advancedEnergy().in(2))
                .duration(200)
                .save();
    }
}
