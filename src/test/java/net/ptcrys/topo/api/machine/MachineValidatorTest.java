package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.component.RecipeLogicMetadata;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MachineValidatorTest {

    @Test
    void rejectsRecipeTypesThatAreNotRegistered() {
        var key = ComponentKey.of(Identifier.parse("topo_test:machine_probe"), MachineComponent.class);
        var metadata = new RecipeLogicMetadata(List.of(new ProbeType(Identifier.parse("topo_test:probe_type"))));
        ComponentMount<MachineComponent> mount = key.mount(context -> new MachineComponent(context) {}, metadata);
        MachineDefinition definition = definition(mount);

        var error = assertThrows(IllegalStateException.class, () -> MachineValidator.validate(List.of(definition)));
        assertTrue(error.getMessage().contains("unregistered recipe type"));
    }

    @Test
    void derivedIndexesAreStableAfterConstruction() {
        MachineDefinition definition = definition();
        assertSame(definition.resourcePorts(), definition.resourcePorts());
        assertSame(definition.recipeLogicMounts(), definition.recipeLogicMounts());
        assertSame(definition.recipeTypes(), definition.recipeTypes());
        assertTrue(definition.resourcePorts().isEmpty());
        assertTrue(definition.recipeTypes().isEmpty());
        assertDoesNotThrow(() -> MachineValidator.validate(List.of(definition)));
    }

    private static MachineDefinition definition(ComponentMount<?>... mounts) {
        return new MachineDefinition(
                Identifier.parse("topo_test:probe_machine"),
                Topology.REGISTRY,
                MachineBlock::new,
                MachineBlockEntity::new,
                "Probe",
                "探针",
                null,
                null,
                List.of(mounts));
    }

    private static final class ProbeType extends TopoRecipeType<TopoRecipe> {

        private ProbeType(Identifier id) {
            super(id, TopoRecipe::new);
        }
    }
}
