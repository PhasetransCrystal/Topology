package net.ptcrys.topo.apiv2.equipment;

import net.ptcrys.topo.apiv2.machine.MachineItemBehavior;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.MaterialContentContext;

import org.jspecify.annotations.Nullable;

/**
 * The equipment socket: decides which materials get this equipment and registers the per-material
 * item (plus its crafting recipe and tags). Driven once per (equipment, material) pair by the
 * equipment bootstrap after the material registry froze.
 */
public interface EquipmentStrategy {

    /** Declaration-driven: true when {@code material} declares the stats payload this kind needs. */
    boolean appliesTo(Material material);

    void register(MaterialContentContext context, Equipment self);

    /**
     * The machine interaction this kind's items perform, or null for none. The runtime binder
     * registers it into the machine domain's {@code MachineItemBehaviors} once items are bound.
     */
    default @Nullable MachineItemBehavior machineBehavior() {
        return null;
    }
}
