package net.ptcrys.topodemo.data.material;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.resources.Identifier;

/** 为启用该处理器的金属注册锭到粉的精细研磨配方。 */
public final class FineGrindingProcessor extends MaterialPostProcessor {

    public FineGrindingProcessor(Identifier id) {
        super(id, BuiltinMaterialForms.INGOT, BuiltinMaterialForms.DUST);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) return;
        var items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
        BuiltinRecipeTypes.FINE_GRINDER.recipe(material.id().getPath() + "_dust_from_ingot")
                .input(items.in(MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.INGOT), 1))
                .output(items.out(MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST), 1))
                .tickInput(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().in(30))
                .duration(100)
                .productionLine(BuiltinTopoProductionLines.FINE_GRINDING)
                .save();
    }
}
