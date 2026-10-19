package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Immutable namespace ownership and deterministic plugin order for one startup. */
public record PluginSnapshot(List<TopoPlugin> plugins, Map<String, RegistryCore> owners) {

    public PluginSnapshot {
        plugins = plugins.stream().sorted(Comparator.comparing(TopoPlugin::identity)).toList();
        owners = Map.copyOf(owners);
    }

    public RegistryCore registry(String namespace) {
        RegistryCore registry = owners.get(namespace);
        if (registry == null) throw new IllegalStateException("No Topo plugin owns namespace '" + namespace + "'");
        return registry;
    }
}
