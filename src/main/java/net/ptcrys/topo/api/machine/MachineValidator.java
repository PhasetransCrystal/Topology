package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.api.machine.component.RecipeLogicMetadata;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;

import java.util.List;

/** Freeze-time validation for machine declarations and their recipe/resource references. */
final class MachineValidator {

    private MachineValidator() {}

    static void validate(List<MachineDefinition> machines) {
        for (MachineDefinition machine : machines) {
            validateRecipeTypes(machine);
            validateResourcePorts(machine);
        }
    }

    private static void validateRecipeTypes(MachineDefinition machine) {
        for (RecipeLogicMetadata metadata : machine.recipeLogicMounts()) {
            for (TopoRecipeType<?> recipeType : metadata.recipeTypes()) {
                TopoRecipeType<?> registered;
                try {
                    registered = TopoRecipeTypes.require(recipeType.id());
                } catch (RuntimeException error) {
                    throw new IllegalStateException(
                            "machine " + machine.id() + " mounts unregistered recipe type " + recipeType.id(),
                            error);
                }
                if (registered != recipeType) {
                    throw new IllegalStateException(
                            "machine " + machine.id() + " mounts a foreign recipe type handle " + recipeType.id());
                }
                if (recipeType.isVanillaFacing()) {
                    throw new IllegalStateException(
                            "machine " + machine.id() + " cannot mount vanilla-facing recipe type " + recipeType.id());
                }
            }
        }
    }

    private static void validateResourcePorts(MachineDefinition machine) {
        for (ResourcePortMetadata port : machine.resourcePorts()) {
            MachineResourceType<?> resourceType = port.resourceType();
            MachineResourceType<?> registered;
            try {
                registered = MachineResourceTypes.require(resourceType.id());
            } catch (RuntimeException error) {
                throw new IllegalStateException(
                        "machine " + machine.id() + " declares port for unregistered resource type " + resourceType.id(),
                        error);
            }
            if (registered != resourceType) {
                throw new IllegalStateException(
                        "machine " + machine.id() + " declares a foreign resource type handle " + resourceType.id());
            }
        }
    }
}
