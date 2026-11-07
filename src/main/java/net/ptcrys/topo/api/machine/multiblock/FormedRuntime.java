package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.component.ServiceKey;
import net.ptcrys.topo.api.machine.component.ServiceMatch;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 控制器拥有的成型成员与资源聚合运行时。完整识别刷新成员或成员加载状态变化时递增 epoch，成员缓存按 epoch 失效；配方路由变化单独使路由缓存失效，避免原位替换后继续使用旧部件处理器。逐 tick
 * 使用平行数组比较版本，不创建逐成员临时集合。
 */
final class FormedRuntime {

    private static final long[] EMPTY_VERSIONS = new long[0];
    private static final boolean[] EMPTY_LOADED = new boolean[0];

    private final MachineBlockEntity controller;
    private final Map<MachineResourceType<?>, CapabilityHandlerCache> capabilityHandlers = new IdentityHashMap<>();
    /** 按 epoch 缓存成员服务匹配。 */
    private final Map<ServiceKey<?, ?>, ServiceCache> serviceCache = new IdentityHashMap<>();
    // Recognition is block-entity-free: it hands us member world positions, not live block entities.
    // Live MachineBlockEntity instances are re-resolved by position on the server thread, on demand.
    private List<BlockPos> memberPositions = List.of();
    /** 成员最近的资源内容版本，仅在已加载时有效。 */
    private long[] memberResourceVersions = EMPTY_VERSIONS;
    /** 成员最近的配方池路由版本。 */
    private long[] memberRecipeRoutingRevisions = EMPTY_VERSIONS;
    /** 上次轮询是否能解析为已加载机器。 */
    private boolean[] memberLoaded = EMPTY_LOADED;
    /** 缓存失效版本；成员刷新或加载状态变化时递增。 */
    private long memberEpoch;

    FormedRuntime(MachineBlockEntity controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    List<MachineBlockEntity> currentLoadedMembers() {
        Level level = controller.getLevel();
        if (level == null) {
            return List.of();
        }
        List<MachineBlockEntity> loaded = new ArrayList<>();
        for (BlockPos pos : memberPositions) {
            MachineBlockEntity member = resolveMember(level, pos);
            if (member != null) {
                loaded.add(member);
            }
        }
        return List.copyOf(loaded);
    }

    List<BlockPos> memberPositions() {
        return memberPositions;
    }

    void updateMembers(List<BlockPos> recognizedMembers) {
        Objects.requireNonNull(recognizedMembers, "recognized members");
        BlockPos controllerPos = controller.getBlockPos();
        Set<BlockPos> updated = new LinkedHashSet<>();
        for (BlockPos pos : recognizedMembers) {
            BlockPos immutable = pos.immutable();
            if (!immutable.equals(controllerPos)) {
                updated.add(immutable);
            }
        }
        List<BlockPos> updatedPositions = List.copyOf(updated);
        if (memberPositions.equals(updatedPositions)) {
            // 完整识别可能来自原位部件替换。位置与版本号相同，也不能继续持有旧实体的服务和处理器。
            if (!memberPositions.isEmpty()) {
                memberEpoch++;
                controller.machineComponents().invalidateRecipeHandlers();
            }
            pollMemberResourceVersions();
            return;
        }
        memberPositions = updatedPositions;
        resetMemberVersions();
        memberEpoch++;
        // Member set shapes the pool router; drop the cached table.
        controller.machineComponents().invalidateRecipeHandlers();
    }

    void clearMembers() {
        if (memberPositions.isEmpty()) {
            return;
        }
        memberPositions = List.of();
        memberResourceVersions = EMPTY_VERSIONS;
        memberRecipeRoutingRevisions = EMPTY_VERSIONS;
        memberLoaded = EMPTY_LOADED;
        memberEpoch++;
        controller.machineComponents().invalidateRecipeHandlers();
    }

    /** 无临时集合的成员轮询，检测内容版本、路由版本及加载状态变化。加载变化使成员缓存失效，加载或路由变化使配方路由缓存失效，只有内容变化时唤醒配方搜索。 */
    void pollMemberResourceVersions() {
        int count = memberPositions.size();
        if (count == 0) {
            return;
        }
        Level level = controller.getLevel();
        boolean contentChanged = false;
        boolean routingChanged = false;
        boolean loadStateFlipped = false;
        for (int i = 0; i < count; i++) {
            MachineBlockEntity member = level == null ? null : resolveMember(level, memberPositions.get(i));
            boolean loaded = member != null;
            if (loaded != memberLoaded[i]) {
                memberLoaded[i] = loaded;
                loadStateFlipped = true;
            }
            if (loaded) {
                var components = member.machineComponents();
                long contentVersion = components.resourceContentVersion();
                if (contentVersion != memberResourceVersions[i]) {
                    memberResourceVersions[i] = contentVersion;
                    contentChanged = true;
                }
                long routingRevision = components.recipeRoutingRevision();
                if (routingRevision != memberRecipeRoutingRevisions[i]) {
                    memberRecipeRoutingRevisions[i] = routingRevision;
                    routingChanged = true;
                }
            }
        }
        if (loadStateFlipped) {
            memberEpoch++;
        }
        if (routingChanged || loadStateFlipped) {
            controller.machineComponents().invalidateRecipeHandlers();
        } else if (contentChanged) {
            controller.machineComponents().noteResourceContentChanged();
        }
    }

    /** 按识别成员的确定顺序收集指定服务，跳过未加载成员，并按 epoch 缓存结果。 */
    @SuppressWarnings("unchecked")
    <A, C> List<ServiceMatch<A>> services(ServiceKey<A, C> service, @Nullable C context) {
        Objects.requireNonNull(service, "component service");
        ServiceCache cached = serviceCache.get(service);
        if (cached != null && cached.epoch() == memberEpoch && cached.context() == context) {
            return (List<ServiceMatch<A>>) cached.matches();
        }
        List<ServiceMatch<A>> matches = new ArrayList<>();
        for (MachineBlockEntity member : currentLoadedMembers()) {
            matches.addAll(member.machineComponents().services(service, context));
        }
        List<ServiceMatch<A>> result = List.copyOf(matches);
        serviceCache.put(service, new ServiceCache(memberEpoch, context, result));
        return result;
    }

    /** 一组服务匹配及其缓存版本。 */
    private record ServiceCache(long epoch, @Nullable Object context, List<?> matches) {}

    /** 将每个已加载成员的原始配方资源贡献给控制器池路由器；同池合并由路由器完成。 */
    <R extends Resource> void collectRecipeResourceHandlers(
                                                            MachineResourceType<R> resourceType,
                                                            RecipeRole recipeIo,
                                                            Consumer<MachineComponent.RecipeResourceContribution<R>> out) {
        Objects.requireNonNull(resourceType, "resource type");
        Objects.requireNonNull(recipeIo, "recipe IO");
        Objects.requireNonNull(out, "out");
        for (MachineBlockEntity member : currentLoadedMembers()) {
            member.machineComponents().collectRecipeResourceHandlers(resourceType, recipeIo, contribution -> {
                @SuppressWarnings("unchecked")
                MachineComponent.RecipeResourceContribution<R> typed = (MachineComponent.RecipeResourceContribution<R>) contribution;
                out.accept(typed);
            });
        }
    }

    @SuppressWarnings("unchecked")
    <R extends Resource> ResourceHandler<R> capabilityHandler(
                                                              MachineResourceType<R> resourceType,
                                                              @Nullable Direction side) {
        Objects.requireNonNull(resourceType, "resource type");
        CapabilityHandlerCache bySide = capabilityHandlers.computeIfAbsent(resourceType, unused -> new CapabilityHandlerCache());
        ResourceHandler<?> existing = bySide.get(side);
        if (existing != null) {
            return (ResourceHandler<R>) existing;
        }
        ResourceHandler<R> created = new DynamicResourceHandler<>(resourceType, member -> member.machineComponents()
                .resources()
                .transferSide()
                .handler(resourceType, side));
        bySide.put(side, created);
        return created;
    }

    private void resetMemberVersions() {
        int count = memberPositions.size();
        memberResourceVersions = count == 0 ? EMPTY_VERSIONS : new long[count];
        memberRecipeRoutingRevisions = count == 0 ? EMPTY_VERSIONS : new long[count];
        memberLoaded = count == 0 ? EMPTY_LOADED : new boolean[count];
        Level level = controller.getLevel();
        for (int i = 0; i < count; i++) {
            MachineBlockEntity member = level == null ? null : resolveMember(level, memberPositions.get(i));
            if (member != null) {
                memberLoaded[i] = true;
                var components = member.machineComponents();
                memberResourceVersions[i] = components.resourceContentVersion();
                memberRecipeRoutingRevisions[i] = components.recipeRoutingRevision();
            }
        }
    }

    /** 在调用方的世界线程按位置重新解析机器；区块未加载或不是机器时返回 null。 */
    private @Nullable MachineBlockEntity resolveMember(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return null;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MachineBlockEntity machine && !machine.isRemoved()) {
            return machine;
        }
        return null;
    }

    private interface MemberHandlerFactory<R extends Resource> {

        @Nullable
        ResourceHandler<R> handler(MachineBlockEntity member);
    }

    /** 按资源类型及访问面聚合成员处理器，委托按成员 epoch 缓存。 */
    private final class DynamicResourceHandler<R extends Resource> implements ResourceHandler<R> {

        private final MachineResourceType<R> resourceType;
        private final MemberHandlerFactory<R> factory;
        /** 当前委托的缓存版本；-1 表示尚未解析。 */
        private long resolvedEpoch = -1;
        private @Nullable ResourceHandler<R> resolvedDelegate;

        private DynamicResourceHandler(
                                       MachineResourceType<R> resourceType,
                                       MemberHandlerFactory<R> factory) {
            this.resourceType = Objects.requireNonNull(resourceType, "resource type");
            this.factory = Objects.requireNonNull(factory, "member handler factory");
        }

        @Override
        public int size() {
            ResourceHandler<R> delegate = delegate();
            return delegate == null ? 0 : delegate.size();
        }

        @Override
        public R getResource(int index) {
            return requireDelegate().getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return requireDelegate().getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, R resource) {
            return requireDelegate().getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, R resource) {
            ResourceHandler<R> delegate = delegate();
            return delegate != null && delegate.isValid(index, resource);
        }

        @Override
        public int insert(int index, R resource, int amount, TransactionContext transaction) {
            return requireDelegate().insert(index, resource, amount, transaction);
        }

        @Override
        public int insert(R resource, int amount, TransactionContext transaction) {
            ResourceHandler<R> delegate = delegate();
            return delegate == null ? 0 : delegate.insert(resource, amount, transaction);
        }

        @Override
        public int extract(int index, R resource, int amount, TransactionContext transaction) {
            return requireDelegate().extract(index, resource, amount, transaction);
        }

        @Override
        public int extract(R resource, int amount, TransactionContext transaction) {
            ResourceHandler<R> delegate = delegate();
            return delegate == null ? 0 : delegate.extract(resource, amount, transaction);
        }

        private ResourceHandler<R> requireDelegate() {
            ResourceHandler<R> delegate = delegate();
            if (delegate == null) {
                throw new IndexOutOfBoundsException("No formed " + resourceType.id() + " resource indices");
            }
            return delegate;
        }

        private @Nullable ResourceHandler<R> delegate() {
            if (resolvedEpoch != memberEpoch) {
                resolvedDelegate = resolveDelegate();
                resolvedEpoch = memberEpoch;
            }
            return resolvedDelegate;
        }

        private @Nullable ResourceHandler<R> resolveDelegate() {
            List<ResourceHandler<R>> handlers = new ArrayList<>();
            for (MachineBlockEntity member : currentLoadedMembers()) {
                ResourceHandler<R> handler = factory.handler(member);
                if (handler != null) {
                    handlers.add(handler);
                }
            }
            return resourceType.combine(handlers);
        }
    }

    private static final class CapabilityHandlerCache {

        private final Map<Direction, ResourceHandler<?>> sidedHandlers = new EnumMap<>(Direction.class);
        private @Nullable ResourceHandler<?> nullSideHandler;

        @Nullable
        ResourceHandler<?> get(@Nullable Direction side) {
            return side == null ? nullSideHandler : sidedHandlers.get(side);
        }

        void put(@Nullable Direction side, ResourceHandler<?> handler) {
            if (side == null) {
                nullSideHandler = handler;
            } else {
                sidedHandlers.put(side, handler);
            }
        }
    }
}
