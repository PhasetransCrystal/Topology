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
 * Controller-owned formed member runtime and dynamic resource aggregation surface.
 *
 * <p>
 * Steady-state contract: the member set carries an epoch counter, bumped whenever the member list
 * changes ({@link #updateMembers}/{@link #clearMembers}) or {@link #pollMemberResourceVersions}
 * detects a member chunk load/unload flip. Each {@link DynamicResourceHandler} memoizes its resolved
 * delegate against that epoch, so an unchanged epoch costs zero re-resolution. The per-tick version
 * poll itself compares {@code long[]}/{@code boolean[]} parallel arrays in place and allocates
 * nothing.
 */
final class FormedRuntime {

    private static final long[] EMPTY_VERSIONS = new long[0];
    private static final boolean[] EMPTY_LOADED = new boolean[0];

    private final MachineBlockEntity controller;
    private final Map<MachineResourceType<?>, CapabilityHandlerCache> capabilityHandlers = new IdentityHashMap<>();
    /** Per-capability memoized role matches; entries self-invalidate via the member epoch. */
    private final Map<ServiceKey<?, ?>, ServiceCache> serviceCache = new IdentityHashMap<>();
    // Recognition is block-entity-free: it hands us member world positions, not live block entities.
    // Live MachineBlockEntity instances are re-resolved by position on the server thread, on demand.
    private List<BlockPos> memberPositions = List.of();
    /** Parallel to {@link #memberPositions}: last polled resource content version (valid while loaded). */
    private long[] memberResourceVersions = EMPTY_VERSIONS;
    /** Parallel to {@link #memberPositions}: last polled recipe-pool routing revision. */
    private long[] memberRecipeRoutingRevisions = EMPTY_VERSIONS;
    /** Parallel to {@link #memberPositions}: whether the member resolved to a live machine last poll. */
    private boolean[] memberLoaded = EMPTY_LOADED;
    /** Bumped on member-set changes and member load/unload flips; delegate memoization keys on it. */
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

    /**
     * Zero-allocation per-tick poll: compares each loaded member's resource content and recipe
     * routing revisions in place and detects member load/unload flips. Load flips rebuild the
     * controller's pool router and bump the member epoch; content-only changes merely wake searches.
     */
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

    /**
     * Capability matches mounted on the currently loaded members, in deterministic member order (the
     * recognized member order, controller excluded). Memoized against the member epoch per
     * (capability, context) pair — a 1-tick recipe machine queries this on every start, so an
     * unchanged member set must not re-resolve members or re-collect matches each time.
     */
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

    /** One memoized {@link #services} result; invalidated by epoch mismatch, never cleared explicitly. */
    private record ServiceCache(long epoch, @Nullable Object context, List<?> matches) {}

    /**
     * Forwards each loaded member's recipe contributions into the controller's pool router builder.
     * Pool merging happens only at router build time — handlers stay pool-unaware.
     */
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

    /**
     * Re-resolves the live member block entity at {@code pos} on the calling (server) thread, or
     * {@code null} if its chunk is not loaded or it is no longer a live machine. Recognition never
     * holds a block entity, so this is where positions become live members again.
     */
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

    /**
     * Aggregating capability handler for one (resource type, side). The combined member delegate is
     * memoized against {@link #memberEpoch}: while the member set and member load states are
     * unchanged, repeated queries reuse the resolved delegate without touching the world.
     */
    private final class DynamicResourceHandler<R extends Resource> implements ResourceHandler<R> {

        private final MachineResourceType<R> resourceType;
        private final MemberHandlerFactory<R> factory;
        /** Epoch the memoized delegate was resolved against; {@code -1} means never resolved. */
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
