package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIProcessDies;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;

import java.util.function.Supplier;

/** Wire-tier conversions for the wire mill: ingot draw to 1x, then adjacent 2:1 bundling. */
public final class WireTierFormProcessor extends MaterialPostProcessor {

    private static final int DEFAULT_DURATION = 100;
    private static final long ENERGY_PER_TICK = 30;

    public WireTierFormProcessor(Identifier id) {
        super(
                id,
                BuiltinOIMaterialForms.INGOT,
                BuiltinOIMaterialForms.WIRE_1X,
                BuiltinOIMaterialForms.WIRE_2X,
                BuiltinOIMaterialForms.WIRE_4X,
                BuiltinOIMaterialForms.WIRE_8X,
                BuiltinOIMaterialForms.WIRE_16X);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        registerDraw(material);
        registerBundle(material, BuiltinOIMaterialForms.WIRE_1X, BuiltinOIMaterialForms.WIRE_2X);
        registerBundle(material, BuiltinOIMaterialForms.WIRE_2X, BuiltinOIMaterialForms.WIRE_4X);
        registerBundle(material, BuiltinOIMaterialForms.WIRE_4X, BuiltinOIMaterialForms.WIRE_8X);
        registerBundle(material, BuiltinOIMaterialForms.WIRE_8X, BuiltinOIMaterialForms.WIRE_16X);
    }

    private static void registerDraw(Material material) {
        register(
                material,
                BuiltinOIMaterialForms.INGOT,
                1,
                BuiltinOIMaterialForms.WIRE_1X,
                2,
                BuiltinOIProcessDies.TEMPLATE_WIRE::get);
    }

    private static void registerBundle(Material material, MaterialForm inputForm, MaterialForm outputForm) {
        register(material, inputForm, 2, outputForm, 1, null);
    }

    private static void register(
                                 Material material,
                                 MaterialForm inputForm,
                                 int inputCount,
                                 MaterialForm outputForm,
                                 int outputCount,
                                 Supplier<? extends ItemLike> template) {
        ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
        var recipe = BuiltinOIRecipeTypes.WIRE_MILL.recipe(recipeId(material, outputForm, inputForm))
                .input(items.in(material, inputForm, inputCount));
        if (template != null) {
            recipe.input(items.unconsumedInput(template));
        }
        recipe.output(items.out(material, outputForm, outputCount))
                .tickInput(energy.in(ENERGY_PER_TICK))
                .duration(DEFAULT_DURATION)
                .productionLine(BuiltinOIProductionLines.WIRE_TIER)
                .save();
    }

    private static String recipeId(Material material, MaterialForm outputForm, MaterialForm inputForm) {
        String materialPath = material.id().getPath();
        return itemName(materialPath, outputForm) + "_from_" + itemName(materialPath, inputForm);
    }

    private static String itemName(String materialPath, MaterialForm form) {
        return String.format(form.strategy().registryPath(), materialPath);
    }
}
