package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named lang contributions. Each hook visits all plugins before the module freezes its tables. */
public interface LangPlugin extends TopoContribution {

    default void registerLang(LangDomainRegistration lang) {}
}
