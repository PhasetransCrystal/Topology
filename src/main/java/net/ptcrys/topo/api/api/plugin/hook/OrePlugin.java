package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named ore contributions. Each hook visits all plugins before the module freezes its tables. */
public interface OrePlugin extends TopoContribution {

    default void registerOreFoundation(OreDomainRegistration ore) {}

    default void registerOreDisplays(OreDomainRegistration ore) {}

    default void registerOreVeins(OreDomainRegistration ore) {}

    default void registerOreDatagen(OreDomainRegistration ore) {}
}
