package net.ptcrys.topo.apiv2.machine.component;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ServiceProvider<T extends MachineComponent, A, C> {

    @Nullable
    A get(T trait, @Nullable C context);
}
