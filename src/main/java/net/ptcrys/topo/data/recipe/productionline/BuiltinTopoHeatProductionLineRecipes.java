package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.data.recipe.special.BoilerRecipeType;

import net.minecraft.world.level.material.Fluids;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.heat;

final class BuiltinTopoHeatProductionLineRecipes {

    private static final int HYDROGEN_MB = 100;
    private static final int HYDROGEN_BURN_TICKS = 20;
    private static final int WATER_MB = HYDROGEN_BURN_TICKS / BoilerRecipeType.BURN_TICKS_PER_WATER_MB;
    private static final int ELECTRIC_DURATION = 20;

    private BuiltinTopoHeatProductionLineRecipes() {}

    static void init() {
        BuiltinTopoRecipeTypes.BOILER.recipe("hydrogen_gas_to_heat")
                .productionLine(BuiltinTopoProductionLines.HEAT)
                .input(fluids().in(fluid(BuiltinTopoMaterials.HYDROGEN, BuiltinTopoMaterialForms.GAS), HYDROGEN_MB))
                .input(fluids().in(Fluids.WATER, WATER_MB))
                .tickOutput(heat().out(BoilerRecipeType.HEAT_PER_TICK))
                .duration(HYDROGEN_BURN_TICKS)
                .save();

        BuiltinTopoRecipeTypes.RESISTIVE_HEATER.recipe("heat_from_energy")
                .productionLine(BuiltinTopoProductionLines.HEAT)
                .tickInput(energy().in(2))
                .tickOutput(heat().out(1))
                .duration(ELECTRIC_DURATION)
                .save();
    }
}
