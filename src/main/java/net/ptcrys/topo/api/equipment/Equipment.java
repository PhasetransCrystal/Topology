package net.ptcrys.topo.api.equipment;

import net.minecraft.resources.Identifier;

/**
 * One finished-equipment kind (wrench, pickaxe, helmet, ...). Parts come from the material
 * domain's Form matrix; the finished item is registered once per material that declares the
 * stats payload the strategy requires. KHS citizen owned by {@link EquipmentRegistry}.
 */
public final class Equipment {

    private final Identifier id;
    private final EquipmentStrategy strategy;

    Equipment(Identifier id, EquipmentStrategy strategy) {
        this.id = id;
        this.strategy = strategy;
    }

    public Identifier id() {
        return id;
    }

    public EquipmentStrategy strategy() {
        return strategy;
    }
}
