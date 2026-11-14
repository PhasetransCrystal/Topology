package net.ptcrys.topo.api.recipe.search;

import net.ptcrys.topo.api.recipe.capability.RecipeCapability;

import net.minecraft.resources.Identifier;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stable int id table for one recipe search index.
 *
 * <p>
 * Each capability owns a separate primitive key namespace. Machine-fingerprint extraction and
 * index building therefore avoid allocating a temporary composite key object on the search path.
 */
public final class RecipeSearchKeyRegistry {

    private final Object2IntOpenHashMap<Object> unqualifiedIds = new Object2IntOpenHashMap<>();
    private final Map<Identifier, Object2IntOpenHashMap<Object>> capabilityIds = new HashMap<>();
    private final List<Object> keys = new ArrayList<>();

    public RecipeSearchKeyRegistry() {
        unqualifiedIds.defaultReturnValue(-1);
    }

    public int getOrAssign(Object key) {
        Objects.requireNonNull(key, "recipe search key");
        int existing = unqualifiedIds.getInt(key);
        if (existing >= 0) {
            return existing;
        }
        int assigned = keys.size();
        keys.add(key);
        unqualifiedIds.put(key, assigned);
        return assigned;
    }

    public int getOrAssign(RecipeCapability<?, ?> capability, Object key) {
        Objects.requireNonNull(capability, "recipe search capability");
        Objects.requireNonNull(key, "recipe search key");
        Object2IntOpenHashMap<Object> ids = capabilityIds.computeIfAbsent(capability.id(), ignored -> {
            Object2IntOpenHashMap<Object> created = new Object2IntOpenHashMap<>();
            created.defaultReturnValue(-1);
            return created;
        });
        int existing = ids.getInt(key);
        if (existing >= 0) {
            return existing;
        }
        int assigned = keys.size();
        keys.add(key);
        ids.put(key, assigned);
        return assigned;
    }

    public int idOf(@Nullable Object key) {
        return key == null ? -1 : unqualifiedIds.getInt(key);
    }

    public int idOf(RecipeCapability<?, ?> capability, @Nullable Object key) {
        if (key == null) {
            return -1;
        }
        Objects.requireNonNull(capability, "recipe search capability");
        Object2IntOpenHashMap<Object> ids = capabilityIds.get(capability.id());
        return ids == null ? -1 : ids.getInt(key);
    }

    public int size() {
        return keys.size();
    }
}
