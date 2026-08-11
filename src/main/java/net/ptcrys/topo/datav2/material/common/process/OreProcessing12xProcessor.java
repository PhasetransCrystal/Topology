package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;

import net.minecraft.resources.Identifier;

/** 12x ore processing: luminite-assisted resonant dust crystallization. */
public final class OreProcessing12xProcessor extends OreProcessingLineProcessor {

    public OreProcessing12xProcessor(Identifier id) {
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
                BuiltinOIMaterialForms.ACTIVATED_DUST,
                BuiltinOIMaterialForms.RESONANT_DUST,
                BuiltinOIMaterialForms.RARE_ELEMENT,
                BuiltinOIMaterialForms.INGOT);
    }

    @Override
    public void process(Material material) {
        if (shouldSkip(material)) {
            return;
        }
        ProductionLine line = BuiltinOIProductionLines.registerOreProcessing12x(material);
        registerOreToCrudeDust(material, line);
        registerCrudeDustWash(material, line);
        registerAcidLeach(material, line);
        registerNeutralize(material, line);
        registerElectrolyze(material, line);
        registerActivate(material, line);
        registerCrystallize(material, line);
        registerTwelveTimesSmelt(material, line);
    }
}
