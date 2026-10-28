package net.ptcrys.topo.api.api.lang;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;

import org.jetbrains.annotations.ApiStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lang-key table (query + freeze). Product / API writes only via
 * {@link LangDomainRegistration}.
 *
 * <p>
 * K is the full i18n key string (not a {@link net.minecraft.resources.Identifier}).
 */
public final class LangRegistry {

    private static final FreezableStrategyRegistry<String, LangKey, LangKey> REGISTRY = FreezableStrategyRegistry.create("lang keys");
    private static final Set<String> ENTRY_NAMES = new LinkedHashSet<>();

    private LangRegistry() {}

    /** Single write entry for {@link LangDomainRegistration}. */
    @ApiStatus.Internal
    public static LangKey begin(String key, String en, String cn) {
        requireText(key, "key");
        requireText(en, "en");
        requireText(cn, "cn");
        if (ENTRY_NAMES.contains(key)) {
            throw new IllegalStateException("Language key '" + key + "' is owned by a RegistryLib entry; configure its display name on the entry declaration");
        }
        LangKey handle = new LangKey(key, en, cn);
        return REGISTRY.register(key, handle, handle);
    }

    public static LangKey require(String key) {
        return REGISTRY.require(key);
    }

    public static List<LangKey> registered() {
        return REGISTRY.handlesView();
    }

    @ApiStatus.Internal
    public static void freeze() {
        REGISTRY.freeze();
        ENTRY_NAMES.clear();
    }

    /** Claims a generated entry name without exporting it through the independent LangKey pass. */
    public static void claimEntryName(String key) {
        requireText(key, "key");
        if (REGISTRY.isFrozen()) {
            throw new IllegalStateException("Cannot declare entry name '" + key + "' after language freeze");
        }
        if (REGISTRY.get(key) != null) {
            throw new IllegalStateException("Language key '" + key + "' is already owned by a Topo LangKey; configure its display name on the entry declaration");
        }
        if (!ENTRY_NAMES.add(key)) {
            throw new IllegalStateException("RegistryLib entry name '" + key + "' was declared twice");
        }
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("lang " + what + " must not be blank");
        }
    }
}
