package net.ptcrys.topo.api.api.util;

import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 原版物品栏访问；每次批量操作使用一个事务。 */
public class InventoryStorageAccess implements StorageAccess {

    private final ResourceHandler<ItemResource> inventory;

    public InventoryStorageAccess(ServerPlayer player) {
        this(PlayerInventoryWrapper.of(Objects.requireNonNull(player, "player")));
    }

    public InventoryStorageAccess(ResourceHandler<ItemResource> inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    @Override
    public long extract(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        if (!(resource instanceof ItemResource item) || amount == 0) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            long extracted = ResourceHandlerLongOps.extract(inventory, item, amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    @Override
    public long insert(Resource resource, long amount) {
        StorageResult.check(resource, amount);
        if (!(resource instanceof ItemResource item) || amount == 0) return 0;
        try (Transaction transaction = Transaction.openRoot()) {
            long inserted = ResourceHandlerLongOps.insert(inventory, item, amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    @Override
    public StorageResult extract(Map<? extends Resource, Long> request) {
        Map<Resource, Long> demand = StorageResult.requestCopy(request);
        Map<Resource, Long> fulfilled = new LinkedHashMap<>();
        try (Transaction transaction = Transaction.openRoot()) {
            for (var entry : demand.entrySet()) {
                if (!(entry.getKey() instanceof ItemResource item)) continue;
                long extracted = ResourceHandlerLongOps.extract(inventory, item, entry.getValue(), transaction);
                if (extracted > 0) fulfilled.put(item, extracted);
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
                if (!(entry.getKey() instanceof ItemResource item)) continue;
                long inserted = ResourceHandlerLongOps.insert(inventory, item, entry.getValue(), transaction);
                if (inserted > 0) fulfilled.put(item, inserted);
            }
            StorageResult result = StorageResult.of(demand, fulfilled);
            transaction.commit();
            return result;
        }
    }
}
