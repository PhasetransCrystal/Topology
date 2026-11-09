package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.api.util.StorageAccess;
import net.ptcrys.topo.api.api.util.StorageAccessException;
import net.ptcrys.topo.api.api.util.StorageResult;

import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** 按需求访问 AE 的泛型键仓储，不枚举网络，也不缓存库存。 */
public final class AeStorageAccess implements StorageAccess {

    private final Supplier<? extends AeNetworkAccessor> network;

    public AeStorageAccess(Supplier<? extends AeNetworkAccessor> network) {
        this.network = Objects.requireNonNull(network, "network");
    }

    @Override
    public long extract(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        if (amount == 0) return 0;
        AEKey key = AeKeyResourceAdapter.toKey(resource);
        if (key == null) return 0;
        AeNetworkAccessor access = network.get();
        if (access == null) return 0;
        long actual = access.extract(key, amount, Actionable.MODULATE);
        if (actual < 0 || actual > amount) throw new IllegalStateException("Invalid AE extract result");
        return actual;
    }

    @Override
    public long insert(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        if (amount == 0) return 0;
        AEKey key = AeKeyResourceAdapter.toKey(resource);
        if (key == null) return 0;
        AeNetworkAccessor access = network.get();
        if (access == null) return 0;
        long actual = access.insert(key, amount, Actionable.MODULATE);
        if (actual < 0 || actual > amount) throw new IllegalStateException("Invalid AE insert result");
        return actual;
    }

    @Override
    public StorageResult extract(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        if (demand.isEmpty()) return StorageResult.of(demand, fulfilled);
        try {
            AeNetworkAccessor access = null;
            for (var entry : demand.entrySet()) {
                AEKey key = AeKeyResourceAdapter.toKey(entry.getKey());
                if (key == null) continue;
                if (access == null) {
                    access = network.get();
                    if (access == null) break;
                }
                long actual = access.extract(key, entry.getValue(), Actionable.MODULATE);
                if (actual < 0 || actual > entry.getValue()) throw new IllegalStateException("Invalid AE transfer result");
                if (actual > 0) fulfilled.put(entry.getKey(), actual);
            }
        } catch (RuntimeException failure) {
            throw new StorageAccessException("AE storage extraction failed", StorageResult.of(demand, fulfilled), failure);
        }
        return StorageResult.of(demand, fulfilled);
    }

    @Override
    public StorageResult insert(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        if (demand.isEmpty()) return StorageResult.of(demand, fulfilled);
        try {
            AeNetworkAccessor access = null;
            for (var entry : demand.entrySet()) {
                AEKey key = AeKeyResourceAdapter.toKey(entry.getKey());
                if (key == null) continue;
                if (access == null) {
                    access = network.get();
                    if (access == null) break;
                }
                long actual = access.insert(key, entry.getValue(), Actionable.MODULATE);
                if (actual < 0 || actual > entry.getValue()) throw new IllegalStateException("Invalid AE transfer result");
                if (actual > 0) fulfilled.put(entry.getKey(), actual);
            }
        } catch (RuntimeException failure) {
            throw new StorageAccessException("AE storage insertion failed", StorageResult.of(demand, fulfilled), failure);
        }
        return StorageResult.of(demand, fulfilled);
    }
}
