package net.ptcrys.topo.apiv2.lang;

/**
 * Internal mint for fixed / template bilingual names. Domain strategies and form/equipment
 * builders call these after receiving bare strings from product tables.
 *
 * <p>
 * <b>Product tables must not call this type</b> and must not pass constructed
 * {@link FixedDisplayName} / {@link TemplateDisplayName} across API boundaries — only bare
 * {@code (en, cn)} at the unique product entry methods (code-style §3.13). Both languages are
 * required; neither may be blank.
 */
public final class DisplayNames {

    private DisplayNames() {}

    /** Complete labels (machine names, resource families, UI keys, …). */
    public static FixedDisplayName fixed(String en, String cn) {
        return new FixedDisplayName(requireText(en, "en"), requireText(cn, "cn"));
    }

    /** Material/part templates ({@code %s Ingot} / {@code %s锭}). */
    public static TemplateDisplayName template(String enPattern, String cnPattern) {
        return new TemplateDisplayName(requireText(enPattern, "enPattern"), requireText(cnPattern, "cnPattern"));
    }

    static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("display name " + what + " must not be blank");
        }
        return value;
    }
}
