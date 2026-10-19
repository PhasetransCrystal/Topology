package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Constructor-time plugin collection. Arrival order never determines execution order. */
public final class TopoPlugins {

    private static final Map<String, TopoPlugin> PLUGINS = new TreeMap<>();
    private static final Map<String, RegistryCore> OWNERS = new HashMap<>();
    private static boolean locked;

    private TopoPlugins() {}

    /** Same instance is idempotent; a second instance of the same plugin class is a conflict. */
    public static synchronized void register(TopoPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        TopoPlugin existing = PLUGINS.get(plugin.identity());
        if (existing == plugin) return;
        if (locked) throw new IllegalStateException("Topo plugin registration is closed");
        if (existing != null) throw new IllegalStateException("Duplicate plugin identity: " + plugin.identity());
        RegistryCore owner = OWNERS.get(plugin.modId());
        if (owner != null && owner != plugin.registry()) {
            throw new IllegalStateException("Different RegistryCore instances for namespace '" + plugin.modId() + "'");
        }
        PLUGINS.put(plugin.identity(), plugin);
        OWNERS.putIfAbsent(plugin.modId(), plugin.registry());
    }

    public static synchronized List<TopoPlugin> view() {
        return List.copyOf(PLUGINS.values());
    }

    public static synchronized boolean isLocked() {
        return locked;
    }

    static synchronized Snapshot lock() {
        if (PLUGINS.isEmpty()) throw new IllegalStateException("No Topo plugins registered in mod constructors");
        locked = true;
        return new Snapshot(List.copyOf(PLUGINS.values()), Map.copyOf(OWNERS));
    }

    record Snapshot(List<TopoPlugin> plugins, Map<String, RegistryCore> owners) {

        Snapshot {
            plugins = plugins.stream().sorted(java.util.Comparator.comparing(TopoPlugin::identity)).toList();
            owners = Map.copyOf(owners);
        }

        RegistryCore registry(String namespace) {
            RegistryCore registry = owners.get(namespace);
            if (registry == null) throw new IllegalStateException("No Topo plugin owns namespace '" + namespace + "'");
            return registry;
        }
    }
}
