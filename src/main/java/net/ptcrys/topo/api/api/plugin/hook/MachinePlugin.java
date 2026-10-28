package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named machine contributions. Each hook visits all plugins before the module freezes its tables. */
public interface MachinePlugin extends TopoContribution {

    default void registerMachineFoundation(MachineDomainRegistration machine) {}

    default void registerMachines(MachineDomainRegistration machine) {}

    default void registerMachineFollowUps(MachineDomainRegistration machine) {}
}
