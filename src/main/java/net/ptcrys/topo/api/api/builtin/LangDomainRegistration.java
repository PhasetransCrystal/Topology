package net.ptcrys.topo.api.api.builtin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Sole lang-domain registration entry for one {@link TopoPlugin}. Full i18n keys are always
 * {@code category.modId.path}; callers never embed the mod id in the path.
 */
public final class LangDomainRegistration {

    private final TopoPlugin plugin;
    private final RegistryCore registry;
    private final List<LangKey> keys = new ArrayList<>();

    private LangDomainRegistration(TopoPlugin plugin, RegistryCore registry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public static LangDomainRegistration of(TopoPlugin plugin) {
        return new LangDomainRegistration(plugin, plugin.registry());
    }

    public TopoPlugin plugin() {
        return plugin;
    }

    public RegistryCore registry() {
        return registry;
    }

    public String modId() {
        return plugin.modId();
    }

    /**
     * Registers {@code category.modId.path} → (en, cn). {@code category} has no dots; {@code path}
     * may contain dots for nesting (e.g. {@code recipe.state.idle}).
     */
    public LangKey key(String category, String path, String en, String cn) {
        requireCategory(category);
        PluginIds.requireBarePath(path);
        String fullKey = category + "." + modId() + "." + path;
        return register(fullKey, en, cn);
    }

    /**
     * Registers a resource-derived key via {@link Identifier#toLanguageKey(String)} after verifying
     * the id namespace matches this plugin (materials, forms, veins, …).
     */
    public LangKey resource(Identifier id, String category, String en, String cn) {
        Objects.requireNonNull(id, "id");
        // category is Identifier#toLanguageKey prefix (may contain dots, e.g. pipe.strategy).
        if (category == null || category.isBlank() || category.indexOf(':') >= 0) {
            throw new IllegalArgumentException("lang resource category invalid: " + category);
        }
        if (!modId().equals(id.getNamespace())) {
            throw new IllegalArgumentException(
                    "lang resource id namespace must match plugin modId: " + id + " vs " + modId());
        }
        return register(id.toLanguageKey(category), en, cn);
    }

    /**
     * Escape for non-standard key shapes required by foreign conventions (e.g. Jade config ids).
     * The full key must still include this plugin's modId.
     */
    public LangKey absolute(String fullKey, String en, String cn) {
        Objects.requireNonNull(fullKey, "fullKey");
        if (!fullKey.contains(modId())) {
            throw new IllegalArgumentException(
                    "absolute lang key must include plugin modId '" + modId() + "': " + fullKey);
        }
        return register(fullKey, en, cn);
    }

    /** Keys owned by this domain; datagen exports through this plugin's RegistryCore. */
    public List<LangKey> registeredKeys() {
        return List.copyOf(keys);
    }

    private LangKey register(String fullKey, String en, String cn) {
        LangKey key = LangRegistry.begin(fullKey, en, cn);
        keys.add(key);
        return key;
    }

    private static void requireCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("lang category must not be blank");
        }
        if (category.indexOf('.') >= 0 || category.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "lang category must be a single segment (no '.' or ':'): " + category);
        }
    }
}
