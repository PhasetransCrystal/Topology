package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.builtin.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    private static boolean locked;

    private TopoPlugins() {}

    /**
     * Registers a plugin instance. Safe to call from any mod constructor (before engine lock).
     * Same instance twice is a no-op.
     */
    public static synchronized void register(TopoPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        if (locked) {
            throw new IllegalStateException(
                    "TopoPlugins: cannot register after engine lock (bootstrap already ran)");
        }
        String modId = Objects.requireNonNull(plugin.modId(), "plugin.modId()");
        Objects.requireNonNull(plugin.registry(), "plugin.registry()");
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
            if (existing.plugin() == plugin) {
                return;
            }
            if (existing.plugin().modId().equals(modId) && existing.plugin().registry() != plugin.registry()) {
                throw new IllegalStateException(
                        "TopoPlugins: modId '" + modId + "' already bound to a different RegistryCore");
            }
        }
        ORDER.add(new BoundPlugin(plugin));
        LOGGER.info("TopoPlugins: registered {} ({})", plugin.getClass().getName(), modId);
    }

    public static List<TopoPlugin> view() {
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
        return Collections.unmodifiableList(ORDER);
    }

    static synchronized void lock() {
        locked = true;
    }

    public static boolean isLocked() {
        return locked;
    }

    static final class BoundPlugin {

        private final TopoPlugin plugin;

        BoundPlugin(TopoPlugin plugin) {
            this.plugin = plugin;
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
