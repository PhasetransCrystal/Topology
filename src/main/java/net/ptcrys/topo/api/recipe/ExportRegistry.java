package net.ptcrys.topo.api.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.Recipe;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which foreign recipe ids were exported by which Topo recipe type (R1' anti-sync).
 *
 * <p>
 * Import skips a foreign holder only when {@link #isExportedBy} matches the <em>importing</em>
 * type — so {@code SMELTING} exports remain visible to {@code ELECTRIC_FURNACE}. Filled at product
 * {@code save()} on every process (client + server), not datagen-only.
 */
public final class ExportRegistry {

    private static final Map<ResourceKey<Recipe<?>>, Identifier> FOREIGN_TO_EXPORTING_TYPE = new ConcurrentHashMap<>();

    private ExportRegistry() {}

    public static void register(ResourceKey<Recipe<?>> foreignId, Identifier exportingTypeId) {
        Objects.requireNonNull(foreignId, "foreignId");
        Objects.requireNonNull(exportingTypeId, "exportingTypeId");
        Identifier previous = FOREIGN_TO_EXPORTING_TYPE.put(foreignId, exportingTypeId);
        if (previous != null && !previous.equals(exportingTypeId)) {
            throw new IllegalStateException(
                    "Foreign recipe " + foreignId.identifier() + " already exported by " + previous + ", cannot re-export from " + exportingTypeId);
        }
    }

    public static void register(Identifier foreignId, Identifier exportingTypeId) {
        register(ResourceKey.create(Registries.RECIPE, foreignId), exportingTypeId);
    }

    public static boolean isExportedBy(ResourceKey<Recipe<?>> foreignId, Identifier exportingTypeId) {
        Objects.requireNonNull(foreignId, "foreignId");
        Objects.requireNonNull(exportingTypeId, "exportingTypeId");
        return exportingTypeId.equals(FOREIGN_TO_EXPORTING_TYPE.get(foreignId));
    }

    public static boolean isExported(ResourceKey<Recipe<?>> foreignId) {
        return FOREIGN_TO_EXPORTING_TYPE.containsKey(Objects.requireNonNull(foreignId, "foreignId"));
    }

    public static @Nullable Identifier exportingTypeOf(ResourceKey<Recipe<?>> foreignId) {
        return FOREIGN_TO_EXPORTING_TYPE.get(Objects.requireNonNull(foreignId, "foreignId"));
    }

    /** Test / server-stop cleanup. Product code must not call between saves. */
    public static void clear() {
        FOREIGN_TO_EXPORTING_TYPE.clear();
    }

    /** Snapshot for tests. */
    public static Map<ResourceKey<Recipe<?>>, Identifier> snapshot() {
        return Collections.unmodifiableMap(FOREIGN_TO_EXPORTING_TYPE);
    }
}
