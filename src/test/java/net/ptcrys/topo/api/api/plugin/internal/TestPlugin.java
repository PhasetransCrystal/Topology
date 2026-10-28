package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

/** Fixtures use the same canonical domain creation as dependent plugins. */
class TestPlugin extends TopoPlugin {

    TestPlugin() {
        this(Topology.REGISTRY);
    }

    TestPlugin(RegistryCore core) {
        super(core);
    }
}
