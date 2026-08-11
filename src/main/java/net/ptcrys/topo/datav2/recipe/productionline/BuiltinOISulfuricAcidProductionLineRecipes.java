package net.ptcrys.topo.datav2.recipe.productionline;

import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;

import net.minecraft.world.level.material.Fluids;

import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.heat;
import static net.ptcrys.topo.datav2.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinOISulfuricAcidProductionLineRecipes {

    private static final int GAS_MB = 1_000;
    private static final int WATER_MB = 1_000;
    private static final int SOLUTION_MB = 1_000;

    private BuiltinOISulfuricAcidProductionLineRecipes() {}

    static void init() {
        BuiltinOIRecipeTypes.INDUSTRIAL_COMBUSTION_FURNACE.recipe("sulfur_dioxide_from_sulfur")
                .productionLines(BuiltinOIProductionLines.sulfuricAcidConsumers())
                .input(items().in(BuiltinOIMaterials.SULFUR, BuiltinOIMaterialForms.DUST, 1))
                .input(fluids().in(fluid(BuiltinOIMaterials.AIR, BuiltinOIMaterialForms.GAS), GAS_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.SULFUR_DIOXIDE, BuiltinOIMaterialForms.GAS), GAS_MB))
                .tickInput(heat().in(10))
                .duration(120)
                .save();

        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe("sulfur_trioxide_from_sulfur_dioxide")
                .productionLines(BuiltinOIProductionLines.sulfuricAcidConsumers())
                .input(fluids().in(fluid(BuiltinOIMaterials.SULFUR_DIOXIDE, BuiltinOIMaterialForms.GAS), GAS_MB))
                .input(fluids().in(fluid(BuiltinOIMaterials.AIR, BuiltinOIMaterialForms.GAS), GAS_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.SULFUR_TRIOXIDE, BuiltinOIMaterialForms.GAS), GAS_MB))
                .tickInput(energy().in(30))
                .duration(160)
                .save();

        BuiltinOIRecipeTypes.MIXING_TANK.recipe("sulfuric_acid_from_sulfur_trioxide")
                .productionLines(BuiltinOIProductionLines.sulfuricAcidConsumers())
                .input(fluids().in(fluid(BuiltinOIMaterials.SULFUR_TRIOXIDE, BuiltinOIMaterialForms.GAS), GAS_MB))
                .input(fluids().in(Fluids.WATER, WATER_MB))
                .output(fluids().out(fluid(BuiltinOIMaterials.SULFURIC_ACID, BuiltinOIMaterialForms.SOLUTION), SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();
    }
}
