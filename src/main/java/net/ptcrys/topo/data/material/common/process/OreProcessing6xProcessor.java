package net.ptcrys.topo.data.material.common.process;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;

import net.minecraft.resources.Identifier;

/** 6x ore processing: neutralized slurry electrolysis and high-purity dust smelting. */
public final class OreProcessing6xProcessor extends OreProcessingLineProcessor {

    public OreProcessing6xProcessor(Identifier id) {
        super(
                id,
                BuiltinTopoMaterialForms.ORE,
                BuiltinTopoMaterialForms.DEEPSLATE_ORE,
                BuiltinTopoMaterialForms.CRUDE_DUST,
                BuiltinTopoMaterialForms.PURIFIED_DUST,
                BuiltinTopoMaterialForms.TINY_DUST,
                BuiltinTopoMaterialForms.SLURRY,
                BuiltinTopoMaterialForms.NEUTRALIZED_SLURRY,
                BuiltinTopoMaterialForms.HIGH_PURITY_DUST,
                BuiltinTopoMaterialForms.RARE_ELEMENT,
                BuiltinTopoMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinTopoProductionLines.registerOreProcessing6x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerAcidLeach(material, line);
        registerNeutralize(material, line);
        registerElectrolyze(material, line);
        registerSixTimesSmelt(material, line);
    }
}
