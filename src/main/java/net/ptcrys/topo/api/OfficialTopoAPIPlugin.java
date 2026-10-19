package net.ptcrys.topo.api;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.machine.component.RecipeLogicMetadata;
import net.ptcrys.topo.api.machine.component.RecipeModifierDisplay;
import net.ptcrys.topo.api.machine.multiblock.MultiblockControllerMetadata;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleAttachment;

/** Official framework contributions. Uses the same typed points as dependent content plugins. */
public final class OfficialTopoAPIPlugin extends TopoPlugin {

    public static final OfficialTopoAPIPlugin INSTANCE = new OfficialTopoAPIPlugin();

    private OfficialTopoAPIPlugin() {
        super(Topology.REGISTRY);
    }

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        RecipeLogicMetadata.init();
        RecipeModifierDisplay.init();
        MultiblockControllerMetadata.init();
        PartRoleAttachment.init();
    }

    @Override
    public void registerLang(LangDomainRegistration lang) {
        TopoApiLang.init();
    }
}
