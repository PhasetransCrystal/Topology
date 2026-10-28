package net.ptcrys.topo.api.api.plugin.internal;

import net.neoforged.bus.api.IEventBus;

/** Internal feature extension: declare dependencies, contributions and owned freeze fences. */
public interface RegistrationModule {

    void define(RegistrationPlan.Builder plan, RegistrationContext context);

    default void attach(IEventBus bus) {}
}
