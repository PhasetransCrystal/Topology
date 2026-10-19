package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.TopoContribution;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

import java.util.List;
import java.util.Set;

/** One immutable plugin/owner snapshot for the entire startup, including datagen. */
public final class RegistrationContext {

    private final PluginSnapshot plugins;
    private final List<PluginRegistration> contributions;

    RegistrationContext(PluginSnapshot plugins) {
        this.plugins = plugins;
        contributions = plugins.plugins().stream().map(PluginRegistration::new).toList();
    }

    /** Reject an uninstalled domain before any content callback can run. */
    void validate(Set<RegistrationPoint<?, ?>> points) {
        Set<Class<?>> supported = new java.util.HashSet<>();
        for (RegistrationPoint<?, ?> point : points) supported.add(point.contributor());
        for (TopoPlugin plugin : plugins.plugins()) validateInterfaces(plugin.getClass(), supported, plugin.identity());
    }

    private static void validateInterfaces(Class<?> type, Set<Class<?>> supported, String plugin) {
        if (type == TopoPlugin.class) return;
        for (Class<?> contract : type.getInterfaces()) {
            if (contract != TopoContribution.class && TopoContribution.class.isAssignableFrom(contract) && !supported.contains(contract)) {
                throw new IllegalStateException("Plugin '" + plugin + "' uses an uninstalled contribution domain: " + contract.getName());
            }
            validateInterfaces(contract, supported, plugin);
        }
        if (type.getSuperclass() != null) validateInterfaces(type.getSuperclass(), supported, plugin);
    }

    void contribute(RegistrationPoint<?, ?> point) {
        for (PluginRegistration registration : contributions) registration.invoke(point);
    }

    public RegistryCore registry(String namespace) {
        return plugins.registry(namespace);
    }

    public List<TopoPlugin> plugins() {
        return plugins.plugins();
    }
}
