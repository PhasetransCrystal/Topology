package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;

import net.minecraft.resources.Identifier;

/** 3x ore processing: crude dust washing and purified dust smelting. */
public final class OreProcessing3xProcessor extends OreProcessingLineProcessor {

    public OreProcessing3xProcessor(Identifier id) {
        super(
                id,
                BuiltinOIMaterialForms.ORE,
                BuiltinOIMaterialForms.DEEPSLATE_ORE,
                BuiltinOIMaterialForms.CRUDE_DUST,
                BuiltinOIMaterialForms.PURIFIED_DUST,
                BuiltinOIMaterialForms.TINY_DUST,
                BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinOIProductionLines.registerOreProcessing3x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerThreeTimesSmelt(material, line);
    }
}
