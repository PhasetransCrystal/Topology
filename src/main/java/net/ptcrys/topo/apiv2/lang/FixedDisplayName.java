package net.ptcrys.topo.apiv2.lang;

import java.util.Objects;

/**
 * Complete bilingual display text (no placeholders). Mint only via {@link DisplayNames#fixed} —
 * product tables pass bare {@code (en, cn)} strings at domain/strategy APIs, never this type.
 * Both {@link #en()} and {@link #cn()} are always non-blank.
 */
public final class FixedDisplayName {

    private final String en;
    private final String cn;

    FixedDisplayName(String en, String cn) {
        this.en = Objects.requireNonNull(en, "en");
        this.cn = Objects.requireNonNull(cn, "cn");
    }

    public String en() {
        return en;
    }

    public String cn() {
        return cn;
    }
}
