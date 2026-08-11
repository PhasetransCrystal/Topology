package net.ptcrys.topo.data.recipe.productionline;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

final class ProductionLineRecipeSupport {

    private ProductionLineRecipeSupport() {}

    static ItemRecipeCapability items() {
        return BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
    }

    static FluidRecipeCapability fluids() {
        return BuiltinTopoResourceIntegrations.FLUID.recipeCapability();
    }

    static ScalarRecipeCapability energy() {
        return BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
    }

    static ScalarRecipeCapability advancedEnergy() {
        return BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
    }

    static ScalarRecipeCapability heat() {
        return BuiltinTopoResourceIntegrations.HEAT.recipeCapability();
    }

    static Supplier<Fluid> fluid(Material material, MaterialForm form) {
        return MaterialHelper.materialFluidSupplier(material, form);
    }
}
