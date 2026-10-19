package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.*;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Explicit plugin registry (GTCEu {@code AddonFinder.add} style). Official and third-party use the
 * <strong>same</strong> path: call {@link #register(TopoPlugin)} from the mod {@code @Mod}
 * constructor before the first {@code RegisterEvent}.
 *
 * <p>
 * No SPI / annotation scan. Runtime and datagen share this list via {@link TopoPluginEngine#prepare()}.
 * Multiple plugins may share one {@code modId} only when they share the same {@link
 * net.ptcrys.registrylib.RegistryCore}.
 */
public final class TopoPlugins {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final List<BoundPlugin> ORDER = new ArrayList<>();
    @Getter
    private static volatile boolean locked;

    private TopoPlugins() {}

    /**
     * Registers a plugin instance. Safe to call from any mod constructor (before engine lock).
     * Same instance twice is a no-op.
     */
    public static synchronized void register(TopoPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (BoundPlugin existing : ORDER) {
            if (existing.plugin() == plugin) return;
        }
        register(plugin instanceof OfficialTopoAPIPlugin ? "topo:api" : plugin.getClass().getName(), plugin);
    }

    /** Optional scheduling metadata belongs to registration, not to the content plugin contract. */
    public static synchronized void register(String id, TopoPlugin plugin, String... after) {
        BoundPlugin registration = new BoundPlugin(id, plugin, Set.of(after));
        for (BoundPlugin existing : ORDER) {
            if (existing.plugin() == plugin) {
                if (!existing.id().equals(id) || !existing.after().equals(registration.after())) {
                    throw new IllegalStateException("TopoPlugins: same instance registered with different scheduling metadata");
                }
                return;
            }
        }
        if (locked) {
            throw new IllegalStateException(
                    "TopoPlugins: cannot register after engine lock (bootstrap already ran)");
        }
        String modId = Objects.requireNonNull(plugin.modId(), "plugin.modId()");
        Objects.requireNonNull(plugin.registry(), "plugin.registry()");
        if (!modId.equals(plugin.registry().getModid())) {
            throw new IllegalStateException("TopoPlugins: RegistryCore namespace must match plugin.modId() for '" + modId + "'");
        }
        Objects.requireNonNull(plugin.material(), "plugin.material()");
        Objects.requireNonNull(plugin.equipment(), "plugin.equipment()");
        Objects.requireNonNull(plugin.recipe(), "plugin.recipe()");
        Objects.requireNonNull(plugin.machine(), "plugin.machine()");
        Objects.requireNonNull(plugin.ore(), "plugin.ore()");
        Objects.requireNonNull(plugin.lang(), "plugin.lang()");
        if (!modId.equals(plugin.material().modId()) || !modId.equals(plugin.equipment().modId()) || !modId.equals(plugin.recipe().modId()) || !modId.equals(plugin.machine().modId()) || !modId.equals(plugin.ore().modId()) || !modId.equals(plugin.lang().modId())) {
            throw new IllegalStateException(
                    "TopoPlugins: domain API modId must match plugin.modId() for '" + modId + "'");
        }
        if (plugin.material().registry() != plugin.registry() || plugin.equipment().registry() != plugin.registry() || plugin.recipe().registry() != plugin.registry() || plugin.machine().registry() != plugin.registry() || plugin.ore().registry() != plugin.registry() || plugin.lang().registry() != plugin.registry()) {
            throw new IllegalStateException(
                    "TopoPlugins: domain API RegistryCore must be plugin.registry() for '" + modId + "'");
        }
        for (BoundPlugin existing : ORDER) {
            if (existing.id().equals(id)) {
                throw new IllegalStateException("TopoPlugins: duplicate pluginId '" + id + "'");
            }
            if (existing.plugin().modId().equals(modId) && existing.plugin().registry() != plugin.registry()) {
                throw new IllegalStateException(
                        "TopoPlugins: modId '" + modId + "' already bound to a different RegistryCore");
            }
        }
        ORDER.add(registration);
        LOGGER.info("TopoPlugins: registered {} ({})", plugin.getClass().getName(), modId);
    }

    public static synchronized List<TopoPlugin> view() {
        List<TopoPlugin> plugins = new ArrayList<>(ORDER.size());
        for (BoundPlugin bound : ORDER) {
            plugins.add(bound.plugin());
        }
        return Collections.unmodifiableList(plugins);
    }

    static BoundPlugin findByModId(String modId) {
        for (BoundPlugin bound : ORDER) {
            if (bound.plugin().modId().equals(modId)) {
                return bound;
            }
        }
        return null;
    }

    static List<BoundPlugin> boundView() {
        return List.copyOf(ORDER);
    }

    static synchronized void lock() {
        if (locked) {
            return;
        }
        List<BoundPlugin> ordered = order(boundView());
        ORDER.clear();
        ORDER.addAll(ordered);
        locked = true;
        LOGGER.info("TopoPlugins: locked execution order {}", ordered.stream().map(BoundPlugin::id).toList());
    }

    /** Stable topological order; the official API always precedes content plugins when present. */
    static List<BoundPlugin> order(List<BoundPlugin> plugins) {
        Map<String, BoundPlugin> pending = new TreeMap<>();
        String officialId = null;
        for (BoundPlugin plugin : plugins) {
            if (pending.putIfAbsent(plugin.id(), plugin) != null) {
                throw new IllegalStateException("TopoPlugins: duplicate pluginId '" + plugin.id() + "'");
            }
            if (plugin.plugin() instanceof OfficialTopoAPIPlugin) {
                officialId = plugin.id();
            }
        }
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (BoundPlugin plugin : plugins) {
            Set<String> required = new java.util.LinkedHashSet<>(plugin.after());
            if (officialId != null && !officialId.equals(plugin.id())) {
                required.add(officialId);
            }
            for (String id : required) {
                if (!pending.containsKey(id)) {
                    throw new IllegalStateException("TopoPlugins: '" + plugin.id() + "' requires missing plugin '" + id + "'");
                }
            }
            dependencies.put(plugin.id(), required);
        }
        List<BoundPlugin> result = new ArrayList<>();
        while (!pending.isEmpty()) {
            String next = null;
            for (String id : pending.keySet()) {
                if (dependencies.get(id).isEmpty()) {
                    next = id;
                    break;
                }
            }
            if (next == null) {
                throw new IllegalStateException("TopoPlugins: cyclic plugin dependencies " + pending.keySet());
            }
            result.add(pending.remove(next));
            for (Set<String> required : dependencies.values()) {
                required.remove(next);
            }
        }
        return List.copyOf(result);
    }

    static final class BoundPlugin {

        private final String id;
        private final Set<String> after;
        private final TopoPlugin plugin;

        BoundPlugin(String id, TopoPlugin plugin, Set<String> after) {
            this.id = Objects.requireNonNull(id, "plugin id");
            if (id.isBlank()) throw new IllegalArgumentException("TopoPlugins: plugin id must not be blank");
            this.plugin = Objects.requireNonNull(plugin, "plugin");
            this.after = Set.copyOf(after);
        }

        String id() {
            return id;
        }

        Set<String> after() {
            return after;
        }

        TopoPlugin plugin() {
            return plugin;
        }

        MaterialDomainRegistration material() {
            return plugin.material();
        }

        EquipmentDomainRegistration equipment() {
            return plugin.equipment();
        }

        RecipeDomainRegistration recipe() {
            return plugin.recipe();
        }

        MachineDomainRegistration machine() {
            return plugin.machine();
        }

        OreDomainRegistration ore() {
            return plugin.ore();
        }

        LangDomainRegistration lang() {
            return plugin.lang();
        }
    }
}
