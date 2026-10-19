package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** One immutable plugin/owner snapshot for the entire startup, including datagen. */
final class RegistrationContext {

    private final TopoPlugins.Snapshot plugins;
    private List<PluginRegistration> contributions;

    RegistrationContext(TopoPlugins.Snapshot plugins) {
        this.plugins = plugins;
    }

    void configure(Set<RegistrationPoint<?>> points) {
        List<PluginRegistration> result = new ArrayList<>();
        for (TopoPlugin plugin : plugins.plugins()) {
            PluginRegistration registration = new PluginRegistration(plugin, points);
            try {
                plugin.configure(registration);
            } catch (RuntimeException | Error error) {
                throw new IllegalStateException("Plugin '" + plugin.identity() + "' failed to configure", error);
            } finally {
                registration.seal();
            }
            result.add(registration);
        }
        contributions = List.copyOf(result);
    }

    void contribute(RegistrationPoint<?> point) {
        for (PluginRegistration registration : contributions) registration.invoke(point);
    }

    RegistryCore registry(String namespace) {
        return plugins.registry(namespace);
    }

    List<TopoPlugin> plugins() {
        return plugins.plugins();
    }
}
