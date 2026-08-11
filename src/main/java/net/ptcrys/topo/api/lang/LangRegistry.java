package net.ptcrys.topo.api.lang;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import java.util.List;

/**
 * Lang-key table (query + freeze). Product / API writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.LangDomainRegistration}.
 *
 * <p>
 * K is the full i18n key string (not a {@link net.minecraft.resources.Identifier}).
 */
public final class LangRegistry {

    private static final FreezableStrategyRegistry<String, LangKey, LangKey> REGISTRY = FreezableStrategyRegistry.create("lang keys");

    private LangRegistry() {}

    /** Single write entry for {@link net.ptcrys.topo.apiv2.plugin.LangDomainRegistration}. */
    public static LangKey begin(String key, String en, String cn) {
        requireText(key, "key");
        requireText(en, "en");
        requireText(cn, "cn");
        LangKey handle = new LangKey(key, en, cn);
        return REGISTRY.register(key, handle, handle);
    }

    public static LangKey require(String key) {
        return REGISTRY.require(key);
    }

    public static List<LangKey> registered() {
        return REGISTRY.handlesView();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    private static void requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("lang " + what + " must not be blank");
        }
    }
}
