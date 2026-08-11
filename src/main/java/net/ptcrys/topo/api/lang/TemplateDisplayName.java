package net.ptcrys.topo.api.lang;

import java.util.Objects;

/**
 * Bilingual name template; single {@code %s} is replaced by a {@link DisplayNameSource}'s name.
 * Mint only via {@link DisplayNames#template} — product tables never construct or pass this type.
 */
public final class TemplateDisplayName {

    private final String enPattern;
    private final String cnPattern;

    TemplateDisplayName(String enPattern, String cnPattern) {
        this.enPattern = Objects.requireNonNull(enPattern, "enPattern");
        this.cnPattern = Objects.requireNonNull(cnPattern, "cnPattern");
        requireSinglePlaceholder(enPattern, "enPattern");
        requireSinglePlaceholder(cnPattern, "cnPattern");
    }

    public String enPattern() {
        return enPattern;
    }

    public String cnPattern() {
        return cnPattern;
    }

    public FixedDisplayName resolve(DisplayNameSource subject) {
        Objects.requireNonNull(subject, "subject");
        String en = String.format(enPattern, subject.displayNameEn());
        String cn = String.format(cnPattern, subject.displayNameCn());
        return DisplayNames.fixed(en, cn);
    }

    private static void requireSinglePlaceholder(String pattern, String label) {
        int first = pattern.indexOf("%s");
        if (first < 0) {
            throw new IllegalArgumentException(label + " must contain exactly one %s: " + pattern);
        }
        if (pattern.indexOf("%s", first + 2) >= 0) {
            throw new IllegalArgumentException(label + " must contain exactly one %s: " + pattern);
        }
    }
}
