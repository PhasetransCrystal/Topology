package net.ptcrys.topodemo.data.recipe.productionline;

import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** 食盐与硫酸溶液混合制备电解液。 */
public final class BuiltinTopoElectrolyteProductionLineRecipes {

    private BuiltinTopoElectrolyteProductionLineRecipes() {}

    public static void init() {
        var fluids = BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
        BuiltinRecipeTypes.MIXING_TANK.recipe("electrolyte_from_salt_sulfuric_acid")
                .productionLines(BuiltinTopoProductionLines.electrolyteConsumers())
                .input(BuiltinTopoResourceIntegrations.ITEM.recipeCapability()
                        .in(MaterialHelper.materialItemSupplier(BuiltinMaterials.SALT, BuiltinMaterialForms.DUST), 1))
                .input(fluids.in(MaterialHelper.materialFluidSupplier(BuiltinMaterials.SULFURIC_ACID, BuiltinMaterialForms.SOLUTION), 1_000))
                .output(fluids.out(MaterialHelper.materialFluidSupplier(BuiltinMaterials.ELECTROLYTE, BuiltinMaterialForms.SOLUTION), 1_000))
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(30))
                .duration(120)
                .save();
    }
}
