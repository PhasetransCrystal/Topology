package net.ptcrys.topo.api.machine.resource;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Identity of a recipe <em>search pool</em>: one logical line of inputs and isolatable outputs
 * searched / consumed / emitted together across resource types.
 *
 * <p>
 * {@link #DEFAULT} is one ordinary private line. {@link #UNIVERSAL} means <em>public /
 * shared across every pool</em>: those handlers join every concrete pool (public input usable by
 * all lines; public output that can accept from all lines — same idea as scalar energy). Custom
 * ids are six lowercase base-36 characters ({@code [0-9a-z]}).
 */
public final class RecipeSearchPoolId {

    /** Shared default pool used when a hatch/port does not opt into a custom line. */
    public static final RecipeSearchPoolId DEFAULT = new RecipeSearchPoolId("DEFAULT");

    /**
     * Public membership: not a polled pool of its own. UNIVERSAL ports join every concrete pool —
     * public inputs for all lines, public outputs that receive from all lines.
     */
    public static final RecipeSearchPoolId UNIVERSAL = new RecipeSearchPoolId("UNIVERSAL");

    private static final int CUSTOM_LENGTH = 6;
    private static final String CUSTOM_ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";

    private final String value;

    private RecipeSearchPoolId(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isDefault() {
        return this == DEFAULT || DEFAULT.value.equals(value);
    }

    public boolean isUniversal() {
        return this == UNIVERSAL || UNIVERSAL.value.equals(value);
    }

    /**
     * Parses a stored/UI string into a pool id. {@code null}, blank, or invalid tokens resolve to
     * {@link #DEFAULT}. {@code DEFAULT} / {@code UNIVERSAL} (any case) and valid six-char customs
     * keep their identity.
     */
    public static RecipeSearchPoolId parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase(DEFAULT.value)) {
            return DEFAULT;
        }
        if (trimmed.equalsIgnoreCase(UNIVERSAL.value)) {
            return UNIVERSAL;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (!isValidCustomToken(normalized)) {
            return DEFAULT;
        }
        return new RecipeSearchPoolId(normalized);
    }

    /**
     * Strict persisted-run parser. Unlike the configurable UI parser, malformed values are not
     * remapped to DEFAULT because doing so would move an active recipe onto another production
     * line after save corruption or an incompatible downgrade.
     */
    public static @Nullable RecipeSearchPoolId parseStrict(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase(DEFAULT.value)) {
            return DEFAULT;
        }
        if (trimmed.equalsIgnoreCase(UNIVERSAL.value)) {
            return UNIVERSAL;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        return isValidCustomToken(normalized) ? new RecipeSearchPoolId(normalized) : null;
    }

    /** {@code true} when {@code raw} is a well-formed custom six-char token (not reserved names). */
    public static boolean isValidCustomToken(@Nullable String raw) {
        if (raw == null || raw.length() != CUSTOM_LENGTH) {
            return false;
        }
        for (int i = 0; i < CUSTOM_LENGTH; i++) {
            char c = raw.charAt(i);
            if (c > 'z' || CUSTOM_ALPHABET.indexOf(c) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * UI validity: blank / {@code DEFAULT} / {@code UNIVERSAL} or a well-formed six-char custom
     * token. Invalid text still parses to {@link #DEFAULT} for routing, but the field border is red.
     */
    public static boolean isValidConfiguredToken(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String trimmed = raw.trim();
        if (trimmed.equalsIgnoreCase(DEFAULT.value) || trimmed.equalsIgnoreCase(UNIVERSAL.value)) {
            return true;
        }
        return isValidCustomToken(trimmed.toLowerCase(Locale.ROOT));
    }

    /** Random six-char custom id; caller should de-dupe in the local machine if needed. */
    public static RecipeSearchPoolId generateCustom() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        char[] chars = new char[CUSTOM_LENGTH];
        for (int i = 0; i < CUSTOM_LENGTH; i++) {
            chars[i] = CUSTOM_ALPHABET.charAt(random.nextInt(CUSTOM_ALPHABET.length()));
        }
        return new RecipeSearchPoolId(new String(chars));
    }

    /**
     * Generates a custom id not present in {@code used}. Falls back to a longer suffix only if the
     * local set is pathologically full (should not happen at hatch scale).
     */
    public static RecipeSearchPoolId generateUnique(java.util.Set<RecipeSearchPoolId> used) {
        Objects.requireNonNull(used, "used");
        for (int attempt = 0; attempt < 64; attempt++) {
            RecipeSearchPoolId id = generateCustom();
            if (!used.contains(id)) {
                return id;
            }
        }
        RecipeSearchPoolId id;
        do {
            id = generateCustom();
        } while (used.contains(id));
        return id;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RecipeSearchPoolId other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
