package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;

import net.minecraft.world.level.material.Fluids;

import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.energy;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluid;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.fluids;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.heat;
import static net.ptcrys.topo.data.recipe.productionline.ProductionLineRecipeSupport.items;

final class BuiltinTopoSulfuricAcidProductionLineRecipes {

    private static final int GAS_MB = 1_000;
    private static final int WATER_MB = 1_000;
    private static final int SOLUTION_MB = 1_000;

    private BuiltinTopoSulfuricAcidProductionLineRecipes() {}

    static void init() {
        BuiltinTopoRecipeTypes.INDUSTRIAL_COMBUSTION_FURNACE.recipe("sulfur_dioxide_from_sulfur")
                .productionLines(BuiltinTopoProductionLines.sulfuricAcidConsumers())
                .input(items().in(BuiltinTopoMaterials.SULFUR, BuiltinTopoMaterialForms.DUST, 1))
                .input(fluids().in(fluid(BuiltinTopoMaterials.AIR, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.SULFUR_DIOXIDE, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .tickInput(heat().in(10))
                .duration(120)
                .save();

        BuiltinTopoRecipeTypes.CHEMICAL_REACTOR.recipe("sulfur_trioxide_from_sulfur_dioxide")
                .productionLines(BuiltinTopoProductionLines.sulfuricAcidConsumers())
                .input(fluids().in(fluid(BuiltinTopoMaterials.SULFUR_DIOXIDE, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .input(fluids().in(fluid(BuiltinTopoMaterials.AIR, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.SULFUR_TRIOXIDE, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .tickInput(energy().in(30))
                .duration(160)
                .save();

        BuiltinTopoRecipeTypes.MIXING_TANK.recipe("sulfuric_acid_from_sulfur_trioxide")
                .productionLines(BuiltinTopoProductionLines.sulfuricAcidConsumers())
                .input(fluids().in(fluid(BuiltinTopoMaterials.SULFUR_TRIOXIDE, BuiltinTopoMaterialForms.GAS), GAS_MB))
                .input(fluids().in(Fluids.WATER, WATER_MB))
                .output(fluids().out(fluid(BuiltinTopoMaterials.SULFURIC_ACID, BuiltinTopoMaterialForms.SOLUTION), SOLUTION_MB))
                .tickInput(energy().in(30))
                .duration(120)
                .save();
    }
}
