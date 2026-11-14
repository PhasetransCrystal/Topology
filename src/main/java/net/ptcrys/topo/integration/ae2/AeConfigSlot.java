package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

/**
 * One ghost configuration slot for AE-backed machine parts.
 */
public record AeConfigSlot<R extends Resource>(@Nullable R resource, long targetAmount) {

    public AeConfigSlot {
        if (targetAmount < 0L) {
            throw new IllegalArgumentException("AE config target amount must be non-negative");
        }
    }

    public static <R extends Resource> AeConfigSlot<R> empty() {
        return new AeConfigSlot<>(null, 0L);
    }

    public static <R extends Resource> AeConfigSlot<R> of(R resource, long targetAmount) {
        if (resource == null) {
            throw new IllegalArgumentException("AE config resource must be non-null");
        }
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("AE config resource must be non-empty");
        }
        if (targetAmount <= 0L) {
            throw new IllegalArgumentException("AE config target amount must be > 0");
        }
        return new AeConfigSlot<>(resource, targetAmount);
    }

    public boolean configured() {
        return resource != null && !resource.isEmpty() && targetAmount > 0L;
    }

    public boolean accepts(@Nullable R other) {
        return configured() && resource.equals(other);
    }

    public long deficit(@Nullable R currentResource, long currentAmount) {
        if (!configured()) return 0L;
        if (!accepts(currentResource)) return targetAmount;
        return Math.max(0L, targetAmount - Math.max(0L, currentAmount));
    }

    public long excess(@Nullable R currentResource, long currentAmount) {
        if (currentResource == null || currentResource.isEmpty() || currentAmount <= 0L) return 0L;
        if (!configured() || !accepts(currentResource)) return currentAmount;
        return Math.max(0L, currentAmount - targetAmount);
    }

    public R resourceOr(R emptyResource) {
        return configured() ? resource : emptyResource;
    }
}
