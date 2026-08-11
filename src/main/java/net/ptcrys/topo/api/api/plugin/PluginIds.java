package net.ptcrys.topo.api.api.plugin;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Path → {@link Identifier} under a plugin's fixed namespace. Callers never pass {@code modid:path};
 * the plugin owns namespace from construction.
 */
public final class PluginIds {

    private PluginIds() {}

    public static Identifier id(TopoPlugin plugin, String path) {
        Objects.requireNonNull(plugin, "plugin");
        requireBarePath(path);
        return Identifier.fromNamespaceAndPath(plugin.modId(), path);
    }

    /**
     * Rejects blank paths and any string that already contains a namespace separator.
     */
    public static void requireBarePath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("v2 registration path must not be blank");
        }
        if (path.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "v2 registration path must not include namespace (plugin owns namespace): " + path);
        }
    }
}
