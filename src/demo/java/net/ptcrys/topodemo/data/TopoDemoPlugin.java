package net.ptcrys.topodemo.data;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.plugin.PluginRegistration;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.data.material.BuiltinDemoMaterials;

/** Declares this plugin's contributions; the host owns every lifecycle boundary. */
public final class TopoDemoPlugin extends TopoPlugin {

    public static final TopoDemoPlugin INSTANCE = new TopoDemoPlugin();

    private TopoDemoPlugin() {
        super(TopoDemo.REGISTRY);
    }

    @Override
    protected void configure(PluginRegistration registration) {
        registration.on(MaterialDomainRegistration.MATERIALS, material -> {
            BuiltinDemoMaterials.init();
        });
    }
}
