package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.function.Supplier;

/**
 * Common metal conversions: storage blocks, pinch dust packing, fine grinding, and vanilla cooking.
 *
 * <p>
 * Runs during RegisterEvent HIGHEST — must not resolve {@code BuiltInRegistries.ITEM} here;
 * use deferred suppliers and {@link MaterialHelper#itemPath}.
 */
public final class MetalFormProcessor extends MaterialPostProcessor {

    private static final int GRID_3X3_COUNT = 9;
    private static final int FINE_GRIND_TIME = 100;
    private static final long ENERGY_PER_TICK = 30;
    private static final float COOKING_EXPERIENCE = 0.7F;
    private static final int SMELTING_TIME = 200;
    private static final int BLASTING_TIME = 100;

    public MetalFormProcessor(Identifier id, RegistryCore core) {
        super(
                id,
                BuiltinOIMaterialForms.INGOT,
                BuiltinOIMaterialForms.DUST,
                BuiltinOIMaterialForms.BLOCK,
                BuiltinOIMaterialForms.NUGGET,
                BuiltinOIMaterialForms.TINY_DUST);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        registerCraftingAndCooking(material);
        registerIngotFineGrinding(material);
    }

    private static void registerCraftingAndCooking(Material material) {
        registerStorageBlockConversion(material);
        registerNineToOne(
                material,
                BuiltinOIMaterialForms.TINY_DUST,
                BuiltinOIMaterialForms.DUST,
                "shape/material/",
                "shapeless/material/");
        registerCooking(material);
    }

    private static void registerStorageBlockConversion(Material material) {
        if (MaterialHelper.isMinecraftNamespace(material, BuiltinOIMaterialForms.INGOT) && MaterialHelper.isMinecraftNamespace(material, BuiltinOIMaterialForms.BLOCK)) {
            return;
        }
        registerNineToOne(
                material,
                BuiltinOIMaterialForms.INGOT,
                BuiltinOIMaterialForms.BLOCK,
                "shape/material/",
                "shapeless/material/");
    }

    private static void registerNineToOne(
                                          Material material,
                                          MaterialForm unitForm,
                                          MaterialForm packedForm,
                                          String packedPrefix,
                                          String unitPrefix) {
        Supplier<Item> unit = MaterialHelper.materialItemSupplier(material, unitForm);
        Supplier<Item> packed = MaterialHelper.materialItemSupplier(material, packedForm);
        String unitPath = MaterialHelper.itemPath(material, unitForm);
        String packedPath = MaterialHelper.itemPath(material, packedForm);
        BuiltinOIRecipeTypes.CRAFTING_SHAPED
                .recipe(packedPrefix + packedPath + "_from_" + unitPath, packed)
                .pattern("XXX", "XXX", "XXX")
                .define('X', unit)
                .unlockedBy(unit)
                .save();
        BuiltinOIRecipeTypes.CRAFTING_SHAPELESS
                .recipe(unitPrefix + unitPath + "_from_" + packedPath, unit, GRID_3X3_COUNT)
                .requires(packed)
                .unlockedBy(packed)
                .save();
    }

    private static void registerCooking(Material material) {
        Supplier<Item> dust = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.DUST);
        Supplier<Item> ingot = MaterialHelper.materialItemSupplier(material, BuiltinOIMaterialForms.INGOT);
        String dustPath = MaterialHelper.itemPath(material, BuiltinOIMaterialForms.DUST);
        String ingotPath = MaterialHelper.itemPath(material, BuiltinOIMaterialForms.INGOT);
        BuiltinOIRecipeTypes.SMELTING
                .recipe("smelting/material/" + ingotPath + "_from_" + dustPath, ingot, dust)
                .experience(COOKING_EXPERIENCE)
                .cookingTime(SMELTING_TIME)
                .unlockedBy(dust)
                .save();
        BuiltinOIRecipeTypes.BLASTING
                .recipe("blasting/material/" + ingotPath + "_from_" + dustPath, ingot, dust)
                .experience(COOKING_EXPERIENCE)
                .cookingTime(BLASTING_TIME)
                .unlockedBy(dust)
                .save();
    }

    private void registerIngotFineGrinding(Material material) {
        ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
        BuiltinOIRecipeTypes.FINE_GRINDER.recipe(material.id().getPath() + "_dust_from_ingot")
                .input(items.in(material, BuiltinOIMaterialForms.INGOT, 1))
                .output(items.out(material, BuiltinOIMaterialForms.DUST, 1))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(FINE_GRIND_TIME)
                .productionLine(BuiltinOIProductionLines.FINE_GRINDING)
                .save();
    }
}
