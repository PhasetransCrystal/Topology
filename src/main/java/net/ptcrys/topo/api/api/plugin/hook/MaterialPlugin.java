package net.ptcrys.topo.api.api.plugin.hook;

import net.ptcrys.topo.api.api.builtin.MaterialContributionRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoContribution;

/** Named material contributions. Each hook visits all plugins before the module freezes its tables. */
public interface MaterialPlugin extends TopoContribution {

    default void registerMaterialFoundation(MaterialDomainRegistration material) {}

    default void registerMaterials(MaterialDomainRegistration material) {}

    /** Runs after every plugin's registerMaterials and before form matching/freeze. */
    default void registerMaterialContributions(MaterialContributionRegistration contributions) {}

    default void registerMaterialCreativeTabs(MaterialDomainRegistration material) {}

    default void registerMaterialFollowUps(MaterialDomainRegistration material) {}
}
