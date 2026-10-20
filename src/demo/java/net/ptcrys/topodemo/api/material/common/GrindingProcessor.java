package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.api.recipe.common.ItemRecipeCapability;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** A reusable form-to-form process. Products supply all registration handles at declaration. */
public final class GrindingProcessor extends MaterialPostProcessor {

    private final MaterialForm input;
    private final MaterialForm output;
    private final TopoRecipeType<TopoRecipe> recipeType;
    private final ProductionLine line;
    private final ItemRecipeCapability items;

    public GrindingProcessor(Identifier id, MaterialForm input, MaterialForm output,
                             TopoRecipeType<TopoRecipe> recipeType, ProductionLine line, ItemRecipeCapability items) {
        super(id, input, output);
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.recipeType = Objects.requireNonNull(recipeType);
        this.line = Objects.requireNonNull(line);
        this.items = Objects.requireNonNull(items);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) return;
        recipeType.recipe(material.id().getNamespace() + "/" + material.id().getPath() + "_" + input.id().getPath() + "_to_" + output.id().getPath())
                .input(items.in(MaterialHelper.materialItemSupplier(material, input), 1))
                .output(items.out(MaterialHelper.materialItemSupplier(material, output), 1))
                .duration(40).productionLine(line).save();
    }
}
