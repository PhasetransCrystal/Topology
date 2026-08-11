package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.special.BoilerRecipeType;

import net.minecraft.world.level.material.Fluids;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.heat;

final class BuiltinOIHeatProductionLineRecipes {

    private static final int HYDROGEN_MB = 100;
    private static final int HYDROGEN_BURN_TICKS = 20;
    private static final int WATER_MB = HYDROGEN_BURN_TICKS / BoilerRecipeType.BURN_TICKS_PER_WATER_MB;
    private static final int ELECTRIC_DURATION = 20;

    private BuiltinOIHeatProductionLineRecipes() {}

    static void init() {
        BuiltinOIRecipeTypes.BOILER.recipe("hydrogen_gas_to_heat")
                .productionLine(BuiltinOIProductionLines.HEAT)
                .input(fluids().in(fluid(BuiltinOIMaterials.HYDROGEN, BuiltinOIMaterialForms.GAS), HYDROGEN_MB))
                .input(fluids().in(Fluids.WATER, WATER_MB))
                .tickOutput(heat().out(BoilerRecipeType.HEAT_PER_TICK))
                .duration(HYDROGEN_BURN_TICKS)
                .save();

        BuiltinOIRecipeTypes.RESISTIVE_HEATER.recipe("heat_from_energy")
                .productionLine(BuiltinOIProductionLines.HEAT)
                .tickInput(energy().in(2))
                .tickOutput(heat().out(1))
                .duration(ELECTRIC_DURATION)
                .save();
    }
}
