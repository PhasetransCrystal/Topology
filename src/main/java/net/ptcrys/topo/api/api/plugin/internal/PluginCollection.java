package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

import net.neoforged.bus.api.IEventBus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Constructor-time collection, sealed into one canonical snapshot on the first registry event. */
public final class PluginCollection {

    private final Map<String, TopoPlugin> plugins = new TreeMap<>();
    private final Map<String, RegistryCore> owners = new HashMap<>();
    private PluginSnapshot snapshot;

    public void attachHost(IEventBus bus) {
        TopoPluginEngine.register(bus, this::freeze);
    }

    public synchronized void register(TopoPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        String identity = plugin.identity();
        TopoPlugin existing = plugins.get(identity);
        if (existing == plugin) return;
        if (snapshot != null) throw new IllegalStateException("Topo plugin registration is closed");
        if (existing != null) throw new IllegalStateException("Duplicate plugin identity: " + identity);
        RegistryCore owner = owners.get(plugin.modId());
        if (owner != null && owner != plugin.registry()) {
            throw new IllegalStateException("Different RegistryCore instances for namespace '" + plugin.modId() + "'");
        }
        plugins.put(identity, plugin);
        owners.putIfAbsent(plugin.modId(), plugin.registry());
    }

    public synchronized List<TopoPlugin> view() {
        return snapshot != null ? snapshot.plugins() : List.copyOf(plugins.values());
    }

    public synchronized boolean isLocked() {
        return snapshot != null;
    }

    synchronized PluginSnapshot freeze() {
        if (snapshot == null) {
            if (plugins.isEmpty()) throw new IllegalStateException("No Topo plugins registered in mod constructors");
            snapshot = new PluginSnapshot(List.copyOf(plugins.values()), owners);
        }
        return snapshot;
    }
}
