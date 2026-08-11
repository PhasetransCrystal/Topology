package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;

import net.minecraft.resources.Identifier;

/** 6x ore processing: neutralized slurry electrolysis and high-purity dust smelting. */
public final class OreProcessing6xProcessor extends OreProcessingLineProcessor {

    public OreProcessing6xProcessor(Identifier id) {
        super(
                id,
                BuiltinOIMaterialForms.ORE,
                BuiltinOIMaterialForms.DEEPSLATE_ORE,
                BuiltinOIMaterialForms.CRUDE_DUST,
                BuiltinOIMaterialForms.PURIFIED_DUST,
                BuiltinOIMaterialForms.TINY_DUST,
                BuiltinOIMaterialForms.SLURRY,
                BuiltinOIMaterialForms.NEUTRALIZED_SLURRY,
                BuiltinOIMaterialForms.HIGH_PURITY_DUST,
                BuiltinOIMaterialForms.RARE_ELEMENT,
                BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinOIProductionLines.registerOreProcessing6x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerAcidLeach(material, line);
        registerNeutralize(material, line);
        registerElectrolyze(material, line);
        registerSixTimesSmelt(material, line);
    }
}
