package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;

/** Fixtures use the same canonical domain creation as dependent plugins. */
class TestPlugin extends TopoPlugin {

    TestPlugin() {
        this(Topology.REGISTRY);
    }

    TestPlugin(RegistryCore core) {
        super(core);
    }

    @Override
    protected void configure(PluginRegistration registration) {}
}
