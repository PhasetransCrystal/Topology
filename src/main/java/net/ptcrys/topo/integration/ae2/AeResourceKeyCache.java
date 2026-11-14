package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class AeResourceKeyCache<R extends Resource> implements AeResourceKeyResolver<R> {

    private final AeResourceKeyResolver<R> delegate;
    private final Map<R, @Nullable AEKey> keys = new HashMap<>();

    public AeResourceKeyCache(AeResourceKeyResolver<R> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    static <R extends Resource> AeResourceKeyResolver<R> cached(AeResourceKeyResolver<R> delegate) {
        return delegate instanceof AeResourceKeyCache<?> ? delegate : new AeResourceKeyCache<>(delegate);
    }

    @Override
    public @Nullable AEKey toKey(@Nullable R resource) {
        if (resource == null || resource.isEmpty()) {
            return null;
        }
        if (keys.containsKey(resource)) {
            return keys.get(resource);
        }
        AEKey key = delegate.toKey(resource);
        keys.put(resource, key);
        return key;
    }

    void markDirty() {
        keys.clear();
    }
}
