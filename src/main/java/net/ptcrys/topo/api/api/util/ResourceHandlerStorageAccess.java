package net.ptcrys.topo.api.api.util;

import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 将资源处理器适配为仓储访问，使用处理器的整体存取接口。 */
public class ResourceHandlerStorageAccess<R extends Resource> implements StorageAccess {

    private final ResourceHandler<R> inventory;
    private final Class<R> resourceClass;

    public ResourceHandlerStorageAccess(Class<R> resourceClass, ResourceHandler<R> inventory) {
        this.resourceClass = Objects.requireNonNull(resourceClass, "resource class");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public long extract(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        if (!resourceClass.isInstance(resource) || amount == 0) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            long result = ResourceHandlerLongOps.extract(inventory, resourceClass.cast(resource), amount, transaction);
            transaction.commit();
            return result;
        }
    }

    @Override
    public long insert(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        if (!resourceClass.isInstance(resource) || amount == 0) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            long result = ResourceHandlerLongOps.insert(inventory, resourceClass.cast(resource), amount, transaction);
            transaction.commit();
            return result;
        }
    }

    @Override
    public StorageResult extract(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        try (Transaction transaction = Transaction.openRoot()) {
            for (var entry : demand.entrySet()) {
                if (!resourceClass.isInstance(entry.getKey())) continue;
                R resource = resourceClass.cast(entry.getKey());
                long extracted = ResourceHandlerLongOps.extract(inventory, resource, entry.getValue(), transaction);
                if (extracted > 0) fulfilled.put(resource, extracted);
            }
            StorageResult result = StorageResult.of(demand, fulfilled);
            transaction.commit();
            return result;
        }
    }

    @Override
    public StorageResult insert(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        try (Transaction transaction = Transaction.openRoot()) {
            for (var entry : demand.entrySet()) {
                if (!resourceClass.isInstance(entry.getKey())) continue;
                R resource = resourceClass.cast(entry.getKey());
                long inserted = ResourceHandlerLongOps.insert(inventory, resource, entry.getValue(), transaction);
                if (inserted > 0) fulfilled.put(resource, inserted);
            }
            StorageResult result = StorageResult.of(demand, fulfilled);
            transaction.commit();
            return result;
        }
    }
}
