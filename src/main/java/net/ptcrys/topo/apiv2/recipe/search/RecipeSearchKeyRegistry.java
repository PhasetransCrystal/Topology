package net.ptcrys.topo.apiv2.recipe.search;

import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability;

import net.minecraft.resources.Identifier;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Stable int id table for one recipe search index.
 *
 * <p>
 * Optimization: recipe keys are converted to dense primitive ids at index-build time.
 * Principle: the search hot path can use int-keyed maps and arrays instead of repeatedly hashing
 * rich objects such as item resources, fluid resources, or tag keys.
 */
public final class RecipeSearchKeyRegistry {

    private final Object2IntOpenHashMap<Object> ids = new Object2IntOpenHashMap<>();
    private final List<Object> keys = new ArrayList<>();

    public RecipeSearchKeyRegistry() {
        ids.defaultReturnValue(-1);
    }

    public int getOrAssign(Object key) {
        Objects.requireNonNull(key, "recipe search key");
        int existing = ids.getInt(key);
        if (existing >= 0) {
            return existing;
        }
        int assigned = keys.size();
        keys.add(key);
        ids.put(key, assigned);
        return assigned;
    }

    public int getOrAssign(RecipeCapability<?, ?> capability, Object key) {
        return getOrAssign(searchKey(capability, key));
    }

    public int idOf(@Nullable Object key) {
        return key == null ? -1 : ids.getInt(key);
    }

    public int idOf(RecipeCapability<?, ?> capability, @Nullable Object key) {
        return key == null ? -1 : ids.getInt(searchKey(capability, key));
    }

    public int size() {
        return keys.size();
    }

    private static SearchKey searchKey(RecipeCapability<?, ?> capability, Object key) {
        Objects.requireNonNull(capability, "recipe search capability");
        Objects.requireNonNull(key, "recipe search key");
        return new SearchKey(capability.id(), key);
    }

    private record SearchKey(Identifier capabilityId, Object value) {

        private SearchKey {
            Objects.requireNonNull(capabilityId, "recipe search capability id");
            Objects.requireNonNull(value, "recipe search key value");
        }
    }
}
