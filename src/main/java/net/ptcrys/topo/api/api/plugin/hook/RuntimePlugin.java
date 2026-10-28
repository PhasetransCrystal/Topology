package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.RuntimeDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named runtime contributions. Each hook visits all plugins before the module freezes its tables. */
public interface RuntimePlugin extends TopoContribution {

    default void registerRuntime(RuntimeDomainRegistration runtime) {}
}
