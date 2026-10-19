package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.builtin.*;

import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Explicit registry for content plugins. Mods register one plugin instance from their {@code @Mod}
 * constructor; the host locks the list before the first content phase.
 *
 * <p>
 * Plugins have no ordering dependencies. Each lifecycle phase visits every plugin before the
 * engine freezes that phase's registries, so one plugin must not rely on another plugin's hook
 * having run first. A stable internal sort makes diagnostics and independent contributions
 * reproducible regardless of mod-constructor arrival order.
 */
public final class TopoPlugins {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final List<BoundPlugin> PLUGINS = new ArrayList<>();
    @Getter
    private static volatile boolean locked;

    private TopoPlugins() {}

    /** Registers a plugin instance. Registering the same instance twice is a no-op. */
    public static synchronized void register(TopoPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        for (BoundPlugin existing : PLUGINS) {
            if (existing.plugin() == plugin) return;
        }
        if (locked) {
            throw new IllegalStateException("TopoPlugins: cannot register after engine lock (bootstrap already ran)");
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
        for (BoundPlugin existing : PLUGINS) {
            if (existing.plugin().modId().equals(modId) && existing.plugin().registry() != plugin.registry()) {
                throw new IllegalStateException(
                        "TopoPlugins: modId '" + modId + "' already bound to a different RegistryCore");
            }
        }

        PLUGINS.add(new BoundPlugin(plugin));
        LOGGER.info("TopoPlugins: registered {} ({})", plugin.getClass().getName(), modId);
    }

    public static synchronized List<TopoPlugin> view() {
        List<TopoPlugin> plugins = new ArrayList<>(PLUGINS.size());
        for (BoundPlugin bound : PLUGINS) {
            plugins.add(bound.plugin());
        }
        return Collections.unmodifiableList(plugins);
    }

    static BoundPlugin findByModId(String modId) {
        for (BoundPlugin bound : PLUGINS) {
            if (bound.plugin().modId().equals(modId)) {
                return bound;
            }
        }
        return null;
    }

    static List<BoundPlugin> boundView() {
        return List.copyOf(PLUGINS);
    }

    static synchronized void lock() {
        if (locked) return;
        List<BoundPlugin> ordered = executionOrder(PLUGINS);
        PLUGINS.clear();
        PLUGINS.addAll(ordered);
        locked = true;
        LOGGER.info("TopoPlugins: locked {} plugins", PLUGINS.size());
    }

    static List<BoundPlugin> executionOrder(List<BoundPlugin> plugins) {
        return plugins.stream()
                .sorted(Comparator.comparing((BoundPlugin bound) -> bound.plugin().modId())
                        .thenComparing(bound -> bound.plugin().getClass().getName()))
                .toList();
    }

    static final class BoundPlugin {

        private final TopoPlugin plugin;

        BoundPlugin(TopoPlugin plugin) {
            this.plugin = Objects.requireNonNull(plugin, "plugin");
        }

        String label() {
            return plugin.getClass().getName() + " (" + plugin.modId() + ")";
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
