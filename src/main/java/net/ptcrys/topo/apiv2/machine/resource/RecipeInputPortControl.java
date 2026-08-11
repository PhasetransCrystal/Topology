package net.ptcrys.topo.apiv2.machine.resource;

import net.ptcrys.topo.apiv2.machine.component.MachineComponents;
import net.ptcrys.topo.apiv2.machine.component.ServiceKey;

import java.util.Objects;

/**
 * Optional machine service for components that replace normal isolatable input ports with their own
 * recipe handlers. Global resources and output ports are never affected.
 */
public interface RecipeInputPortControl {

    ServiceKey<RecipeInputPortControl, Void> KEY = ServiceKey.oi("recipe_input_port_control", RecipeInputPortControl.class, Void.class);

    /** Whether isolatable {@link ResourcePort} inputs must be omitted from recipe routing. */
    boolean suppressIsolatablePortInputs();

    /** Non-allocating after the first context-free service lookup. */
    static boolean suppressesInputs(MachineComponents components) {
        Objects.requireNonNull(components, "machine components");
        var controls = components.servicesCached(KEY);
        for (int index = 0; index < controls.size(); index++) {
            if (controls.get(index).value().suppressIsolatablePortInputs()) {
                return true;
            }
        }
        return false;
    }
}
