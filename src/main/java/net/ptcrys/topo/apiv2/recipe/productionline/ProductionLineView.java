package net.ptcrys.topo.apiv2.recipe.productionline;

import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;

import java.util.List;

public interface ProductionLineView {

    ProductionLine line();

    List<OIRecipe> recipes();

    List<OIRecipeType<?>> recipeTypes();

    List<MachineDefinition> machines();

    List<OIRecipe.InputEntry<?>> inputs();

    List<OIRecipe.OutputEntry<?>> outputs();

    List<OIRecipe.InputEntry<?>> tickInputs();

    List<OIRecipe.OutputEntry<?>> tickOutputs();
}
