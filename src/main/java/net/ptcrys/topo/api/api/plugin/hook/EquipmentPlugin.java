package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.EquipmentDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named equipment contributions. Each hook visits all plugins before the module freezes its tables. */
public interface EquipmentPlugin extends TopoContribution {

    default void registerEquipmentKinds(EquipmentDomainRegistration equipment) {}
}
