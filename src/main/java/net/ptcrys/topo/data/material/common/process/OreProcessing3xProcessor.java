package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.resources.Identifier;

/** 3x ore processing: crude dust washing and purified dust smelting. */
public final class OreProcessing3xProcessor extends OreProcessingLineProcessor {

    public OreProcessing3xProcessor(Identifier id) {
        super(
                id,
                BuiltinTopoMaterialForms.ORE,
                BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                BuiltinTopoMaterialForms.CRUDE_DUST,
                BuiltinTopoMaterialForms.PURIFIED_DUST,
                BuiltinTopoMaterialForms.TINY_DUST,
                BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinTopoProductionLines.registerOreProcessing3x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerThreeTimesSmelt(material, line);
    }
}
