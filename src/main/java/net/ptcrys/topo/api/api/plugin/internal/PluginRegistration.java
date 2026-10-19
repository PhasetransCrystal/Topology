package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.api.plugin.TopoPlugin;

/** Captures one immutable plugin identity for internal dispatch and failure attribution. */
final class PluginRegistration {

    private final TopoPlugin plugin;

    PluginRegistration(TopoPlugin plugin) {
        this.plugin = plugin;
    }

    void invoke(RegistrationPoint<?, ?> point) {
        try {
            point.invoke(plugin);
        } catch (RuntimeException | Error error) {
            throw new IllegalStateException("Plugin '" + plugin.identity() + "' failed at '" + point + "'", error);
        }
    }
}
