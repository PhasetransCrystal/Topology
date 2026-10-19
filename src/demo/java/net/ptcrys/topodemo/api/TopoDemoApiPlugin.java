package net.ptcrys.topodemo.api;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.plugin.PluginRegistration;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialForms;

/** Declares this plugin's contributions; the host owns every lifecycle boundary. */
public final class TopoDemoApiPlugin extends TopoPlugin {

    public static final TopoDemoApiPlugin INSTANCE = new TopoDemoApiPlugin();

    private TopoDemoApiPlugin() {
        super(TopoDemo.REGISTRY);
    }

    @Override
    protected void configure(PluginRegistration registration) {
        registration.on(MaterialDomainRegistration.FOUNDATION, material -> {
            BuiltinDemoMaterialDataTypes.init();
            BuiltinDemoMaterialForms.init();
        });
    }
}
