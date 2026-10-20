package net.ptcrys.topodemo.data.recipe;

import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.api.recipe.BuiltinResources;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;

import java.util.List;

/** Concrete recipes run after all content modules; every not-yet-bound item remains deferred. */
public final class BuiltinRecipes {

    private BuiltinRecipes() {}

    public static void init() {
        var item = BuiltinResources.ITEM;
        for (var material : List.of(BuiltinMaterials.COPPER, BuiltinMaterials.TIN)) {
            for (var form : List.of(BuiltinMaterialForms.ORE, BuiltinMaterialForms.DEEPSLATE_ORE)) {
                BuiltinRecipeTypes.GRINDING.recipe(material.id() + "_" + form.id().getPath() + "_to_dust")
                        .input(item.in(MaterialHelper.materialItemSupplier(material, form), 1))
                        .output(item.out(MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST), 2))
                        .duration(80).productionLine(BuiltinRecipeTypes.METALLURGY).save();
            }
        }
        BuiltinRecipeTypes.ALLOYING.recipe("topodemo:bronze_alloy")
                .input(item.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.COPPER, BuiltinMaterialForms.DUST), 3))
                .input(item.in(MaterialHelper.materialItemSupplier(BuiltinMaterials.TIN, BuiltinMaterialForms.DUST), 1))
                .output(item.out(MaterialHelper.materialItemSupplier(BuiltinMaterials.BRONZE, BuiltinMaterialForms.INGOT), 4))
                .duration(100).productionLine(BuiltinRecipeTypes.METALLURGY).save();
    }
}
