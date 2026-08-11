package net.ptcrys.topo.api.recipe.productionline;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;

import java.util.List;

public interface ProductionLineView {

    ProductionLine line();

    List<TopoRecipe> recipes();

    List<TopoRecipeType<?>> recipeTypes();

    List<MachineDefinition> machines();

    List<TopoRecipe.InputEntry<?>> inputs();

    List<TopoRecipe.OutputEntry<?>> outputs();

    List<TopoRecipe.InputEntry<?>> tickInputs();

    List<TopoRecipe.OutputEntry<?>> tickOutputs();
}
