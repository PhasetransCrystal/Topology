package net.ptcrys.topo.api.api.util;

import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 物品容器向统一仓储暴露资源的能力绑定。扩展模组可注册自己的 Resource 和物品能力，
 * 容器替换、组件变化和剩余容器由能力实现通过同一个 ItemAccess 事务处理。
 */
public final class ItemStorageCapability<R extends Resource> {

    private static final List<ItemStorageCapability<?>> REGISTERED = new CopyOnWriteArrayList<>();

    static {
        register(FluidResource.class, Capabilities.Fluid.ITEM);
    }

    private final Class<R> resourceClass;
    private final ItemCapability<ResourceHandler<R>, ItemAccess> capability;

    private ItemStorageCapability(Class<R> resourceClass, ItemCapability<ResourceHandler<R>, ItemAccess> capability) {
        this.resourceClass = Objects.requireNonNull(resourceClass, "resource class");
        this.capability = Objects.requireNonNull(capability, "item storage capability");
    }

    /** 在模组初始化时注册，不绑定玩家、机器或某个物品实例。 */
    public static synchronized <R extends Resource> void register(
                                                                  Class<R> resourceClass, ItemCapability<ResourceHandler<R>, ItemAccess> capability) {
        for (var binding : REGISTERED) {
            if (binding.capability.equals(capability)) throw new IllegalArgumentException("Duplicate item storage capability");
        }
        REGISTERED.add(new ItemStorageCapability<>(resourceClass, capability));
    }

    static List<ItemStorageCapability<?>> applicable(Map<Resource, Long> request) {
        return REGISTERED.stream().filter(binding -> request.keySet().stream().anyMatch(binding.resourceClass::isInstance)).toList();
    }

    static List<ItemStorageCapability<?>> applicable(Resource resource) {
        return REGISTERED.stream().filter(binding -> binding.resourceClass.isInstance(resource)).toList();
    }

    long extract(ItemAccess access, Resource resource, long amount, TransactionContext transaction) {
        if (!resourceClass.isInstance(resource)) return 0;
        ResourceHandler<R> handler = access.getCapability(capability);
        return handler == null ? 0 : ResourceHandlerLongOps.extract(handler, resourceClass.cast(resource), amount, transaction);
    }

    long insert(ItemAccess access, Resource resource, long amount, TransactionContext transaction) {
        if (!resourceClass.isInstance(resource)) return 0;
        ResourceHandler<R> handler = access.getCapability(capability);
        return handler == null ? 0 : ResourceHandlerLongOps.insert(handler, resourceClass.cast(resource), amount, transaction);
    }

    void collectInsert(ItemAccess access, Map<Resource, Long> remaining, List<Candidate> candidates) {
        ResourceHandler<R> handler = access.getCapability(capability);
        if (handler == null) return;
        for (Resource resource : remaining.keySet()) {
            if (resourceClass.isInstance(resource)) {
                R typed = resourceClass.cast(resource);
                candidates.add(new Candidate(resource,
                        (amount, transaction) -> ResourceHandlerLongOps.insert(handler, typed, amount, transaction)));
            }
        }
    }

    void collectExtract(ItemAccess access, Map<Resource, Long> remaining, List<Candidate> candidates) {
        ResourceHandler<R> handler = access.getCapability(capability);
        if (handler == null) return;
        for (Resource resource : remaining.keySet()) {
            if (resourceClass.isInstance(resource)) {
                R typed = resourceClass.cast(resource);
                candidates.add(new Candidate(resource,
                        (amount, transaction) -> ResourceHandlerLongOps.extract(handler, typed, amount, transaction)));
            }
        }
    }

    record Candidate(Resource resource, Transfer transfer) {}

    @FunctionalInterface
    interface Transfer {

        long move(long amount, TransactionContext transaction);
    }
}
