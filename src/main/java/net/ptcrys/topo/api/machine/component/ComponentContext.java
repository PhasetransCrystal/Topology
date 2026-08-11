package net.ptcrys.topo.api.machine.component;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.data.DataScope;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Construction-time context for a trait instance. A trait receives this in its constructor so its
 * identity and owner are complete immediately, without a later bind step.
 */
public final class ComponentContext<T extends MachineComponent> {

    private final ComponentKey<T> key;
    private final MachineBlockEntity machine;
    private final DataScope data;

    ComponentContext(ComponentKey<T> key, MachineBlockEntity machine, DataScope data) {
        this.key = Objects.requireNonNull(key, "trait key");
        this.machine = Objects.requireNonNull(machine, "machine");
        this.data = Objects.requireNonNull(data, "trait data scope");
    }

    public ComponentKey<T> key() {
        return key;
    }

    public Identifier id() {
        return key.id();
    }

    public MachineBlockEntity machine() {
        return machine;
    }

    public DataScope data() {
        return data;
    }
}
