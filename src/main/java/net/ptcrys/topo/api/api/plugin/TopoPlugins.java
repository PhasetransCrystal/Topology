package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.plugin.internal.PluginCollection;

import net.neoforged.bus.api.IEventBus;

import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/** Constructor-time plugin collection. Arrival order never determines execution order. */
public final class TopoPlugins {

    private static final PluginCollection PLUGINS = new PluginCollection();

    private TopoPlugins() {}

    /** Attach the single Topo host during its mod constructor. */
    @ApiStatus.Internal
    public static void attachHost(IEventBus bus) {
        PLUGINS.attachHost(bus);
    }

    /** Same instance is idempotent; a second instance of the same plugin class is a conflict. */
    public static void register(TopoPlugin plugin) {
        PLUGINS.register(plugin);
    }

    public static List<TopoPlugin> view() {
        return PLUGINS.view();
    }

    public static boolean isLocked() {
        return PLUGINS.isLocked();
    }
}
