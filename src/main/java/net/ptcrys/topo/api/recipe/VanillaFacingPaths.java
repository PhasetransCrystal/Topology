package net.ptcrys.topo.api.recipe;

import java.util.Locale;
import java.util.Objects;

/**
 * Path helpers for vanilla-facing Topo types: Topo recipe names must not double the foreign kind folder.
 */
public final class VanillaFacingPaths {

    public static final String SHAPE = "shape";
    public static final String SHAPELESS = "shapeless";
    public static final String MANUAL = "manual";
    public static final String SMELTING = "smelting";
    public static final String BLASTING = "blasting";
    public static final String SMOKING = "smoking";
    /** Campfire foreign folder matches vanilla serializer id segment. */
    public static final String CAMPFIRE_COOKING = "campfire_cooking";

    private VanillaFacingPaths() {}

    /**
     * Strips a leading {@code kindFolder/} if present so Topo {@code recipeName} stays
     * {@code material/...} under type {@code topo_smelting}.
     */
    public static String stripKindFolder(String productPath, String kindFolder) {
        Objects.requireNonNull(productPath, "productPath");
        Objects.requireNonNull(kindFolder, "kindFolder");
        String prefix = kindFolder + "/";
        if (productPath.startsWith(prefix)) {
            return productPath.substring(prefix.length());
        }
        // Also accept accidental dual-prefix from old call sites.
        String dual = kindFolder + "/" + kindFolder + "/";
        if (productPath.startsWith(dual)) {
            return productPath.substring(dual.length());
        }
        return productPath;
    }

    /** Foreign datapack path: always {@code kindFolder/<stripped>}. */
    public static String foreignPath(String productPath, String kindFolder) {
        String stripped = stripKindFolder(productPath, kindFolder);
        if (stripped.isBlank()) {
            throw new IllegalArgumentException("empty path after strip for kind " + kindFolder + ": " + productPath);
        }
        return kindFolder + "/" + stripped;
    }

    /** Topo recipe name under the vanilla-facing type (no kind folder). */
    public static String oiRecipeName(String productPath, String kindFolder) {
        String stripped = stripKindFolder(productPath, kindFolder);
        if (stripped.isBlank()) {
            throw new IllegalArgumentException("empty Topo recipe name for kind " + kindFolder + ": " + productPath);
        }
        return stripped;
    }

    /** Crafting shaped/shapeless keep historical prefixes {@code shape/} {@code shapeless/} {@code manual/}. */
    public static String craftingForeignPath(String productPath) {
        Objects.requireNonNull(productPath, "productPath");
        if (productPath.isBlank()) {
            throw new IllegalArgumentException("productPath blank");
        }
        // Already namespaced under shape/shapeless/manual — keep as foreign path and Topo name.
        return productPath;
    }

    public static String requireLowerPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (!lower.equals(path)) {
            throw new IllegalArgumentException("recipe path must be lowercase: " + path);
        }
        return path;
    }
}
