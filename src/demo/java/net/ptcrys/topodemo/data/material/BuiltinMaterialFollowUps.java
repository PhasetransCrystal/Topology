package net.ptcrys.topodemo.data.material;

import net.ptcrys.registrylib.util.DataIngredient;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topo.helper.TagHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;

import net.minecraft.data.recipes.RecipeCategory;
import net.neoforged.neoforge.common.Tags;

import java.util.List;

/** Tags use declared form IDs; recipe callbacks resolve items after registry binding. */
public final class BuiltinMaterialFollowUps {

    public static void init() {
        for (var material : List.of(BuiltinMaterials.COPPER, BuiltinMaterials.TIN, BuiltinMaterials.BRONZE)) {
            var ingot = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.INGOT);
            var dust = MaterialHelper.materialItemSupplier(material, BuiltinMaterialForms.DUST);
            var ingotId = MaterialHelper.itemId(material, BuiltinMaterialForms.INGOT);
            var dustId = MaterialHelper.itemId(material, BuiltinMaterialForms.DUST);
            TopoDemo.REGISTRY.itemTags().addIds(Tags.Items.INGOTS, ingotId);
            TopoDemo.REGISTRY.itemTags().addIds(Tags.Items.DUSTS, dustId);
            TopoDemo.REGISTRY.itemTags().addIds(TagHelper.itemMaterial("ingots", material.id().getPath()), ingotId);
            TopoDemo.REGISTRY.itemTags().addIds(TagHelper.itemMaterial("dusts", material.id().getPath()), dustId);
            TopoDemo.REGISTRY.addRecipeData(provider -> provider.smelting(
                    DataIngredient.items(dust), RecipeCategory.MISC, ingot, 0.1f));
        }
    }

    private BuiltinMaterialFollowUps() {}
}
