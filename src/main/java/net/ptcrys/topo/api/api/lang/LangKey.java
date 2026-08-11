package net.ptcrys.topo.api.api.lang;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Strong handle for a translation key (charter KHS citizen, H == S). Carries the i18n key plus its
 * English and Simplified-Chinese text; the single trusted source of the key string.
 *
 * <p>
 * At runtime {@link #getComponent} resolves to whatever locale the player has loaded
 * (en_us / zh_cn / zh_tw); the {@link #en()} / {@link #cn()} payloads exist so the datagen bridge
 * can emit all three locale files. Instances may only be minted by plugin
 * {@link LangDomainRegistration}.
 */
public final class LangKey {

    private final String key;
    private final String en;
    private final String cn;

    LangKey(String key, String en, String cn) {
        this.key = key;
        this.en = en;
        this.cn = cn;
    }

    public String key() {
        return key;
    }

    public String en() {
        return en;
    }

    public String cn() {
        return cn;
    }

    /**
     * A fresh mutable component (never cache/share a single instance: callers append to it).
     */
    public MutableComponent getComponent(Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * Resolves to a plain string for non-{@link Component} sinks (logs, string concatenation).
     */
    public String getString() {
        return getComponent().getString();
    }
}
