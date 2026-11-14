package net.ptcrys.topo.api.api.util;

import net.neoforged.neoforge.transfer.resource.Resource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按声明顺序转交剩余需求；完成后提前停止，不添加消耗、掉落或创造模式规则。 */
public final class CompositeStorageAccess implements StorageAccess {

    private final List<StorageAccess> accesses;

    public CompositeStorageAccess(List<? extends StorageAccess> accesses) {
        this.accesses = List.copyOf(accesses);
    }

    @Override
    public long extract(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        long remaining = amount;
        for (StorageAccess access : accesses) {
            if (remaining == 0) break;
            long moved;
            try {
                moved = access.extract(resource, remaining);
            } catch (RuntimeException failure) {
                throw new StorageAccessException("Storage extraction failed",
                        StorageResult.of(Map.of(resource, amount), Map.of(resource, amount - remaining)), failure);
            }
            if (moved < 0 || moved > remaining) throw new IllegalStateException("Invalid storage extract result");
            remaining -= moved;
        }
        return amount - remaining;
    }

    @Override
    public long insert(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        long remaining = amount;
        for (StorageAccess access : accesses) {
            if (remaining == 0) break;
            long moved;
            try {
                moved = access.insert(resource, remaining);
            } catch (RuntimeException failure) {
                throw new StorageAccessException("Storage insertion failed",
                        StorageResult.of(Map.of(resource, amount), Map.of(resource, amount - remaining)), failure);
            }
            if (moved < 0 || moved > remaining) throw new IllegalStateException("Invalid storage insert result");
            remaining -= moved;
        }
        return amount - remaining;
    }

    @Override
    public StorageResult extract(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> remaining = demand;
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        for (StorageAccess access : accesses) {
            if (remaining.isEmpty()) break;
            StorageResult result;
            try {
                result = access.extract(remaining);
            } catch (StorageAccessException failure) {
                failure.confirmed().fulfilled().forEach((resource, amount) -> fulfilled.merge(resource, amount, Math::addExact));
                throw new StorageAccessException("Storage provider failed", StorageResult.of(demand, fulfilled), failure);
            } catch (RuntimeException failure) {
                throw new StorageAccessException("Storage provider failed", StorageResult.of(demand, fulfilled), failure);
            }
            if (!StorageResult.of(remaining, result.fulfilled()).remaining().equals(result.remaining())) {
                throw new IllegalStateException("Storage result does not conserve the request");
            }
            result.fulfilled().forEach((resource, amount) -> fulfilled.merge(resource, amount, Math::addExact));
            remaining = result.remaining();
        }
        return StorageResult.of(demand, fulfilled);
    }

    @Override
    public StorageResult insert(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> remaining = demand;
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        for (StorageAccess access : accesses) {
            if (remaining.isEmpty()) break;
            StorageResult result;
            try {
                result = access.insert(remaining);
            } catch (StorageAccessException failure) {
                failure.confirmed().fulfilled().forEach((resource, amount) -> fulfilled.merge(resource, amount, Math::addExact));
                throw new StorageAccessException("Storage provider failed", StorageResult.of(demand, fulfilled), failure);
            } catch (RuntimeException failure) {
                throw new StorageAccessException("Storage provider failed", StorageResult.of(demand, fulfilled), failure);
            }
            if (!StorageResult.of(remaining, result.fulfilled()).remaining().equals(result.remaining())) {
                throw new IllegalStateException("Storage result does not conserve the request");
            }
            result.fulfilled().forEach((resource, amount) -> fulfilled.merge(resource, amount, Math::addExact));
            remaining = result.remaining();
        }
        return StorageResult.of(demand, fulfilled);
    }
}
