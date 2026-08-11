package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.resource.DirectResourceAccess;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolRouter;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePort;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Runtime trait set mounted on one {@link MachineBlockEntity}.
 *
 * <p>
 * The container keeps an immutable ordered list and a stable trait-key index. It is also the
 * sibling lookup surface handed to {@link MachineComponent#resolveDependencies(MachineComponents)}.
 */
public final class MachineComponents {

    private final List<MachineComponent> traits;
    private final List<MachineTicker> tickers;
    private final Map<Identifier, MachineComponent> byIdMap;
    private final Map<ServiceKey<?, ?>, List<MountedService<?, ?, ?>>> byServiceMap;
    /** Memo for {@link #servicesCached}; benign-race lazy fill, entries are immutable lists. */
    private final Map<ServiceKey<?, Void>, List<?>> contextFreeServiceCache = new ConcurrentHashMap<>();
    private final Map<MachineResourceType<?>, ResourceHandlerCache> resourceHandlerCache = new IdentityHashMap<>();
    private final Map<MachineResourceType<?>, RecipeDirectAccessCache> recipeDirectAccessCache = new IdentityHashMap<>();
    private final MachineResourceViews resourceViews = new MachineResourceViews(this);
    private long resourceContentVersion;
    /**
     * Structural revision of the recipe-pool routing table. Unlike {@link #resourceContentVersion},
     * ordinary inserts/extracts do not advance this clock; only changes that require a new table do.
     */
    private long recipeRoutingRevision;
    /**
     * Server-thread resource mutation batch used by one recipe step. Handler callbacks still fire
     * inline and mark their own persisted fields, but the machine-wide version/router/wake fan-out
     * is published once when the outer batch closes.
     */
    private int resourceMutationBatchDepth;
    private boolean resourceMutationPending;
    private @Nullable MachineResourceWake resourceMutationOrigin;
    /**
     * Selected pool for the current recipe attempt. Only used as a <em>router key</em> into
     * {@link #recipeSearchPoolRouter()}; handlers are never filtered on the fly.
     */
    private RecipeSearchPoolId activeRecipePool = RecipeSearchPoolId.DEFAULT;
    /** Lazily built pool→combined-handler table; cleared by {@link #invalidateRecipeHandlers()}. */
    private @Nullable RecipeSearchPoolRouter recipeSearchPoolRouter;

    /**
     * Internal capability token used by {@link MachineBlockEntity} to run framework lifecycle code.
     * Implementing this interface does not grant access; callers must hold the exact owner token.
     */
    public interface RuntimeAccess {}

    private MachineComponents(
                              List<MachineComponent> traits,
                              List<MachineTicker> tickers,
                              Map<Identifier, MachineComponent> byIdMap,
                              Map<ServiceKey<?, ?>, List<MountedService<?, ?, ?>>> byServiceMap) {
        this.traits = traits;
        this.tickers = tickers;
        this.byIdMap = byIdMap;
        this.byServiceMap = byServiceMap;
    }

    /**
     * Instantiate and mount the traits for {@code machine} in declared order. The builder rejects
     * duplicate keys at declaration time; this method also checks defensively for malformed callers.
     *
     * <p>
     * Dependencies are not resolved here. The block entity stores the returned container only after
     * calling {@link #resolveAll(RuntimeAccess)} with its framework runtime token.
     */
    public static MachineComponents createForRuntime(
                                                     RuntimeAccess access, MachineBlockEntity machine, List<ComponentMount<?>> mounts) {
        verifyRuntimeAccess(access);
        List<MachineComponent> list = new ArrayList<>(mounts.size());
        List<MachineTicker> tickers = new ArrayList<>();
        Map<Identifier, MachineComponent> byId = new LinkedHashMap<>();
        Map<ServiceKey<?, ?>, List<MountedService<?, ?, ?>>> byService = new LinkedHashMap<>();
        for (ComponentMount<?> mount : mounts) {
            int traitSlot = list.size();
            Identifier id = mount.key().id();
            if (byId.containsKey(id)) {
                throw new IllegalStateException(
                        "Duplicate trait key '" + id + "' on machine " + machine.getBlockPos());
            }
            MachineComponent trait = mount.create(machine);
            list.add(trait);
            if (trait instanceof MachineTicker ticker) {
                ticker.assignProfileSlot(traitSlot);
                tickers.add(ticker);
            }
            byId.put(id, trait);
            indexTrait(mount.key(), trait, byService);
        }
        return new MachineComponents(
                List.copyOf(list),
                List.copyOf(tickers),
                Collections.unmodifiableMap(byId),
                freezeLists(byService));
    }

    /** Run the single dependency resolve pass over every mounted trait. */
    public void resolveAll(RuntimeAccess access) {
        verifyRuntimeAccess(access);
        for (MachineComponent trait : traits) {
            trait.resolveDependencies(this);
        }
    }

    /** The mounted traits in declared order for framework lifecycle fanout. */
    public List<MachineComponent> allForRuntime(RuntimeAccess access) {
        verifyRuntimeAccess(access);
        return traits;
    }

    /** The mounted machine tickers in declared order for framework lifecycle fanout. */
    public List<MachineTicker> tickersForRuntime(RuntimeAccess access) {
        verifyRuntimeAccess(access);
        return tickers;
    }

    /** Look up a single trait by stable typed key. */
    public <T extends MachineComponent> Optional<T> optional(ComponentKey<T> key) {
        MachineComponent found = byIdMap.get(key.id());
        return found == null ? Optional.empty() : Optional.of(key.cast(found));
    }

    /** Query mounted trait API values declared for {@code capability} and present in {@code context}. */
    public <A, C> List<ServiceMatch<A>> services(
                                                 ServiceKey<A, C> capability,
                                                 @Nullable C context) {
        Objects.requireNonNull(capability, "trait capability");
        List<MountedService<?, ?, ?>> mounted = byServiceMap.get(capability);
        if (mounted == null) {
            return List.of();
        }
        List<ServiceMatch<A>> matches = new ArrayList<>();
        for (MountedService<?, ?, ?> candidate : mounted) {
            candidate.addIfPresent(capability, context, matches);
        }
        return List.copyOf(matches);
    }

    /**
     * Memoized {@link #services(ServiceKey, Object)} for <b>context-free</b> capabilities
     * ({@code Void} context, queried with {@code null}). The trait set and its capability bindings are
     * immutable after mount, and a {@code Void}-context provider has nothing to vary by, so the match
     * list is computed once and the per-frame/per-tick query paths (block-entity render extract,
     * work-view polling) stop allocating a list + match wrappers on every call.
     *
     * <p>
     * Contract: providers bound for a {@code Void}-context capability must be pure — always
     * present, always returning the same value object (live state belongs <em>inside</em> the returned
     * view, as {@code MachineWorkView.isRunning()} does).
     */
    @SuppressWarnings("unchecked")
    public <A> List<ServiceMatch<A>> servicesCached(ServiceKey<A, Void> capability) {
        List<?> cached = contextFreeServiceCache.get(Objects.requireNonNull(capability, "trait capability"));
        if (cached == null) {
            cached = services(capability, null);
            contextFreeServiceCache.put(capability, cached);
        }
        return (List<ServiceMatch<A>>) cached;
    }

    /**
     * Non-allocating capability visitor: tests mounted trait API values declared for
     * {@code capability} and present in {@code context} against {@code predicate}, in declared
     * mount order, until one matches. Per-tick query paths use this instead of
     * {@link #services(ServiceKey, Object)} so a query never allocates result lists;
     * list consumers keep using {@code services}.
     */
    public <A, C> boolean anyServiceMatches(
                                            ServiceKey<A, C> capability,
                                            @Nullable C context,
                                            Predicate<? super A> predicate) {
        Objects.requireNonNull(capability, "trait capability");
        Objects.requireNonNull(predicate, "trait capability predicate");
        List<MountedService<?, ?, ?>> mounted = byServiceMap.get(capability);
        if (mounted == null) {
            return false;
        }
        for (int index = 0; index < mounted.size(); index++) {
            A value = mounted.get(index).valueIfPresent(capability, context);
            if (value != null && predicate.test(value)) {
                return true;
            }
        }
        return false;
    }

    public MachineResourceViews resources() {
        return resourceViews;
    }

    public long resourceContentVersion() {
        return resourceContentVersion;
    }

    /**
     * Revision observed by formed controllers to detect pool assignment changes on their members.
     */
    public long recipeRoutingRevision() {
        return recipeRoutingRevision;
    }

    public void noteResourceContentChanged() {
        if (resourceMutationBatchDepth > 0) {
            resourceMutationPending = true;
            return;
        }
        publishResourceContentChanged(null);
    }

    /** Opens a re-entrant, allocation-free mutation batch for recipe-owned storage changes. */
    void beginResourceMutationBatch(MachineResourceWake origin) {
        Objects.requireNonNull(origin, "resource mutation origin");
        if (resourceMutationBatchDepth++ == 0) {
            resourceMutationOrigin = origin;
            resourceMutationPending = false;
        } else if (resourceMutationOrigin != origin) {
            // Nested work from another logic must not suppress either owner from the final wake.
            resourceMutationOrigin = null;
        }
    }

    /**
     * Closes one recipe mutation batch. Persist dirty marking is owned by each changed field and is
     * never suppressed; only the redundant machine-wide notification is coalesced.
     */
    void endResourceMutationBatch() {
        if (resourceMutationBatchDepth <= 0) {
            throw new IllegalStateException("Resource mutation batch underflow");
        }
        if (--resourceMutationBatchDepth != 0) {
            return;
        }
        boolean changed = resourceMutationPending;
        MachineResourceWake origin = resourceMutationOrigin;
        resourceMutationPending = false;
        resourceMutationOrigin = null;
        if (changed) {
            publishResourceContentChanged(origin);
        }
    }

    private void publishResourceContentChanged(@Nullable MachineResourceWake excludedWake) {
        // Optimization safety clock: recipe logic may cache a failed search only while this
        // machine-wide resource version is unchanged. Any item/fluid/etc. storage mutation bumps
        // the clock, so cached "no recipe" answers cannot survive new inputs or freed outputs.
        resourceContentVersion++;
        List<ServiceMatch<MachineResourceWake>> wakes = servicesCached(MachineResourceWake.KEY);
        boolean activeLogicOnly = excludedWake != null && wakes.size() == 1 && wakes.getFirst().value() == excludedWake;
        // Per-pool search backoff: content change must interrupt immediately (do not rebuild the
        // router — only clear schedules on an existing table).
        RecipeSearchPoolRouter router = recipeSearchPoolRouter;
        if (router != null && !activeLogicOnly) {
            router.wakeAll();
        }
        // Event wake fan-out: parked tickers that decayed their interval while waiting on this
        // very clock must hear about the bump immediately, or new inputs would sit unprocessed for
        // up to the backoff cap. Cached context-free list + indexed loop keeps this commit path
        // allocation free; providers must stay cheap when nothing is parked.
        if (activeLogicOnly) {
            return;
        }
        for (int index = 0; index < wakes.size(); index++) {
            MachineResourceWake wake = wakes.get(index).value();
            if (wake != excludedWake) {
                wake.onResourceContentChanged();
            }
        }
    }

    /** Currently selected pool key for capability lookups (never null). Pure router cursor. */
    public RecipeSearchPoolId activeRecipePool() {
        return activeRecipePool;
    }

    /**
     * Selects which pool the recipe capabilitys read via {@link #recipeResourceHandler}. Does not rebuild
     * or filter handlers — only chooses a key into {@link #recipeSearchPoolRouter()}.
     */
    public RecipePoolScope openRecipePool(RecipeSearchPoolId poolId) {
        Objects.requireNonNull(poolId, "pool id");
        RecipeSearchPoolId previous = activeRecipePool;
        activeRecipePool = poolId;
        return new RecipePoolScope(previous);
    }

    /**
     * Pool ids to poll — the only consumer-facing expansion of the old single combined input view.
     * Always contains at least one concrete pool; custom-only routing does not add a ghost
     * {@link RecipeSearchPoolId#DEFAULT}.
     */
    public List<RecipeSearchPoolId> recipeSearchPools() {
        return recipeSearchPoolRouter().poolIds();
    }

    /**
     * Production-side table: contributions grouped by pool, same-pool handlers merged. Consumers
     * only need {@link #recipeSearchPools()} plus the usual handler lookup for the selected pool.
     */
    public RecipeSearchPoolRouter recipeSearchPoolRouter() {
        RecipeSearchPoolRouter router = recipeSearchPoolRouter;
        if (router == null) {
            router = buildRecipeSearchPoolRouter();
            recipeSearchPoolRouter = router;
        }
        return router;
    }

    /** Fan-out used by multiblock members when aggregating contributions. */
    public void collectRecipeResourceHandlers(
                                              MachineResourceType<?> resourceType,
                                              RecipeRole recipeIo,
                                              Consumer<MachineComponent.RecipeResourceContribution<?>> out) {
        Objects.requireNonNull(resourceType, "resource type");
        Objects.requireNonNull(recipeIo, "recipe IO");
        Objects.requireNonNull(out, "out");
        for (int index = 0; index < traits.size(); index++) {
            collectTypedContributions(traits.get(index), resourceType, recipeIo, out);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void collectTypedContributions(
                                                  MachineComponent trait,
                                                  MachineResourceType resourceType,
                                                  RecipeRole recipeIo,
                                                  Consumer out) {
        trait.collectRecipeResourceHandlers(resourceType, recipeIo, out);
    }

    private RecipeSearchPoolRouter buildRecipeSearchPoolRouter() {
        RecipeSearchPoolRouter.Builder builder = RecipeSearchPoolRouter.builder();
        for (MachineResourceType<?> type : MachineResourceTypes.registered()) {
            collectRecipeResourceHandlers(type, RecipeRole.INPUT, contribution -> addInput(builder, type, contribution));
            collectRecipeResourceHandlers(
                    type, RecipeRole.OUTPUT, contribution -> addOutput(builder, type, contribution));
        }
        return builder.build();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addInput(
                                 RecipeSearchPoolRouter.Builder builder,
                                 MachineResourceType type,
                                 MachineComponent.RecipeResourceContribution contribution) {
        builder.addInput(type, contribution);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addOutput(
                                  RecipeSearchPoolRouter.Builder builder,
                                  MachineResourceType type,
                                  MachineComponent.RecipeResourceContribution contribution) {
        builder.addOutput(type, contribution);
    }

    /**
     * Drops the pool routing table and direct-access cache. Call when pool assignment or formed
     * membership changes.
     */
    public void invalidateRecipeHandlers() {
        recipeSearchPoolRouter = null;
        recipeDirectAccessCache.clear();
        recipeRoutingRevision++;
        noteResourceContentChanged();
    }

    /**
     * Drops the per-side combined capability handler cache for one resource type. Required whenever
     * a port's side-IO answer can change after mount — e.g. a runtime player reconfiguration — so
     * the next {@link #transferHandler} call rebuilds against the new policy. Callers that
     * change what neighbors observe must additionally fire {@code level.invalidateCapabilities(pos)}.
     */
    public void invalidateTransferHandlers(MachineResourceType<?> resourceType) {
        Objects.requireNonNull(resourceType, "resource type");
        resourceHandlerCache.remove(resourceType);
    }

    /** Restores the previous active recipe pool when closed. */
    public final class RecipePoolScope implements AutoCloseable {

        private final RecipeSearchPoolId previous;
        private boolean closed;

        private RecipePoolScope(RecipeSearchPoolId previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                activeRecipePool = previous;
            }
        }
    }

    <R extends Resource> @Nullable ResourceHandler<R> transferHandler(
                                                                      MachineResourceType<R> resourceType,
                                                                      @Nullable Direction side) {
        Objects.requireNonNull(resourceType, "resource type");
        ResourceHandlerCache cache = resourceHandlerCache.computeIfAbsent(resourceType, unused -> new ResourceHandlerCache());
        if (cache.contains(side)) {
            ResourceHandler<?> cached = cache.get(side);
            return cached == null ? null : resourceType.castHandler(cached);
        }

        ResourceHandler<R> handler = createCapabilityResourceHandler(resourceType, side);
        cache.put(side, handler);
        return handler;
    }

    private <R extends Resource> @Nullable ResourceHandler<R> createCapabilityResourceHandler(
                                                                                              MachineResourceType<R> resourceType,
                                                                                              @Nullable Direction side) {
        List<ResourceHandler<R>> handlers = new ArrayList<>();
        for (MachineComponent trait : traits) {
            ResourceHandler<R> handler = trait.transferHandler(resourceType, side);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return resourceType.combine(handlers);
    }

    /**
     * Combined recipe-side handler for the active pool key. Capabilities call this; the handler is a
     * pre-merged view from {@link RecipeSearchPoolRouter}, not a live filter over traits.
     */
    <R extends Resource> @Nullable ResourceHandler<R> recipeResourceHandler(
                                                                            MachineResourceType<R> resourceType,
                                                                            RecipeRole recipeIo) {
        return recipeSearchPoolRouter().handler(activeRecipePool, resourceType, recipeIo);
    }

    /**
     * Combined recipe-side handler for an explicit pool (router lookup only).
     */
    public <R extends Resource> @Nullable ResourceHandler<R> recipeResourceHandler(
                                                                                   MachineResourceType<R> resourceType,
                                                                                   RecipeRole recipeIo,
                                                                                   RecipeSearchPoolId poolId) {
        return recipeSearchPoolRouter().handler(poolId, resourceType, recipeIo);
    }

    /**
     * Recipe-side ports as direct-access views for the active pool's combined handler, or
     * {@code null} when the combined handler is not direct-capable.
     */
    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable List<DirectResourceAccess<R>> recipeDirectResourceAccess(
                                                                                                   MachineResourceType<R> resourceType,
                                                                                                   RecipeRole recipeIo) {
        return recipeDirectResourceAccess(resourceType, recipeIo, activeRecipePool);
    }

    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable List<DirectResourceAccess<R>> recipeDirectResourceAccess(
                                                                                                   MachineResourceType<R> resourceType,
                                                                                                   RecipeRole recipeIo,
                                                                                                   RecipeSearchPoolId poolId) {
        Objects.requireNonNull(resourceType, "resource type");
        Objects.requireNonNull(recipeIo, "recipe IO");
        Objects.requireNonNull(poolId, "pool id");
        if (recipeIo == RecipeRole.NONE) {
            return null;
        }
        RecipeDirectAccessCache cache = recipeDirectAccessCache.computeIfAbsent(resourceType, unused -> new RecipeDirectAccessCache());
        if (cache.contains(recipeIo, poolId)) {
            return (List<DirectResourceAccess<R>>) cache.get(recipeIo, poolId);
        }
        ResourceHandler<R> combined = recipeSearchPoolRouter().handler(poolId, resourceType, recipeIo);
        List<DirectResourceAccess<R>> access;
        if (combined == null) {
            access = List.of();
        } else if (combined instanceof DirectResourceAccess<?> direct && direct.directReady() && direct.directExactTransfers()) {
            access = List.of((DirectResourceAccess<R>) direct);
        } else {
            access = null;
        }
        cache.put(recipeIo, poolId, access);
        return access;
    }

    @SuppressWarnings("unchecked")
    <R extends Resource> List<ResourcePort<?, R>> resourcePorts(
                                                                MachineResourceType<R> resourceType) {
        Objects.requireNonNull(resourceType, "resource type");
        List<ResourcePort<?, R>> matches = new ArrayList<>();
        addResourcePorts(resourceType, null, false, matches);
        return List.copyOf(matches);
    }

    <R extends Resource> List<ResourcePort<?, R>> visibleResourcePorts(
                                                                       MachineResourceType<R> resourceType,
                                                                       RecipeRole recipeIo) {
        Objects.requireNonNull(resourceType, "resource type");
        Objects.requireNonNull(recipeIo, "recipe IO");
        if (recipeIo == RecipeRole.NONE) {
            return List.of();
        }
        List<ResourcePort<?, R>> matches = new ArrayList<>();
        addResourcePorts(resourceType, recipeIo, true, matches);
        return List.copyOf(matches);
    }

    @SuppressWarnings("unchecked")
    private <R extends Resource> void addResourcePorts(
                                                       MachineResourceType<R> resourceType,
                                                       @Nullable RecipeRole recipeIo,
                                                       boolean requireVisible,
                                                       List<ResourcePort<?, R>> matches) {
        for (MachineComponent trait : traits) {
            if (trait instanceof ResourcePort<?, ?> storage && storage.resourceType() == resourceType && (recipeIo == null || storage.recipeIo().allows(recipeIo)) && (!requireVisible || storage.playerSlotAccess().isVisible())) {
                matches.add((ResourcePort<?, R>) storage);
            }
        }
    }

    /** Require a sibling by stable typed key; throws precisely if absent or mistyped. */
    public <T extends MachineComponent> T require(ComponentKey<T> key) {
        MachineComponent found = byIdMap.get(key.id());
        if (found == null) {
            throw new IllegalStateException(
                    "Missing required trait '" + key.id() + "' of type " + key.type().getName());
        }
        return key.cast(found);
    }

    private static <T extends MachineComponent> void indexTrait(
                                                                ComponentKey<T> key,
                                                                MachineComponent trait,
                                                                Map<ServiceKey<?, ?>, List<MountedService<?, ?, ?>>> byService) {
        T typedTrait = key.cast(trait);
        for (ServiceBinding<T, ?, ?> binding : key.serviceBindings()) {
            indexService(key, typedTrait, binding, byService);
        }
    }

    private static <T extends MachineComponent, A, C> void indexService(
                                                                        ComponentKey<T> key,
                                                                        T trait,
                                                                        ServiceBinding<T, A, C> binding,
                                                                        Map<ServiceKey<?, ?>, List<MountedService<?, ?, ?>>> byService) {
        byService.computeIfAbsent(binding.service(), unused -> new ArrayList<>())
                .add(new MountedService<>(key, trait, binding));
    }

    private static <K, V> Map<K, List<V>> freezeLists(Map<K, List<V>> source) {
        Map<K, List<V>> frozen = new LinkedHashMap<>();
        for (Map.Entry<K, List<V>> entry : source.entrySet()) {
            frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozen);
    }

    private static void verifyRuntimeAccess(RuntimeAccess access) {
        if (!(access instanceof MachineBlockEntity.ComponentRuntimeAccess)) {
            throw new IllegalArgumentException("Machine trait runtime access is not authorized");
        }
    }

    private record MountedService<T extends MachineComponent, A, C>(
                                                                    ComponentKey<T> key,
                                                                    T trait,
                                                                    ServiceBinding<T, A, C> binding) {

        private MountedService {
            Objects.requireNonNull(key, "trait key");
            Objects.requireNonNull(trait, "trait");
            Objects.requireNonNull(binding, "trait capability binding");
        }

        private <Q, D> void addIfPresent(
                                         ServiceKey<Q, D> requested,
                                         @Nullable D context,
                                         List<ServiceMatch<Q>> matches) {
            Q value = valueIfPresent(requested, context);
            if (value != null) {
                matches.add(new ServiceMatch<>(key, value));
            }
        }

        private <Q, D> @Nullable Q valueIfPresent(ServiceKey<Q, D> requested, @Nullable D context) {
            if (!binding.service().equals(requested)) {
                return null;
            }
            return typedValueIfPresent(requested, context);
        }

        @SuppressWarnings("unchecked")
        private <Q, D> @Nullable Q typedValueIfPresent(ServiceKey<Q, D> requested, @Nullable D context) {
            ServiceBinding<T, Q, D> requestedBinding = (ServiceBinding<T, Q, D>) binding;
            Object provided = requestedBinding.provider().get(trait, context);
            return provided == null ? null : requested.cast(provided);
        }
    }

    private static final class ResourceHandlerCache {

        private final Map<Direction, @Nullable ResourceHandler<?>> sidedHandlers = new EnumMap<>(Direction.class);
        private @Nullable ResourceHandler<?> nullSideHandler;
        private boolean nullSideComputed;

        boolean contains(@Nullable Direction side) {
            return side == null ? nullSideComputed : sidedHandlers.containsKey(side);
        }

        @Nullable
        ResourceHandler<?> get(@Nullable Direction side) {
            return side == null ? nullSideHandler : sidedHandlers.get(side);
        }

        void put(@Nullable Direction side, @Nullable ResourceHandler<?> handler) {
            if (side == null) {
                nullSideHandler = handler;
                nullSideComputed = true;
            } else {
                sidedHandlers.put(side, handler);
            }
        }
    }

    /** {@code null} values are meaningful ("not direct-capable"), so presence is tracked separately. */
    private static final class RecipeDirectAccessCache {

        private final Map<RecipeHandlerCacheKey, @Nullable List<? extends DirectResourceAccess<?>>> access = new LinkedHashMap<>();

        boolean contains(RecipeRole recipeIo, RecipeSearchPoolId poolId) {
            return access.containsKey(new RecipeHandlerCacheKey(recipeIo, poolId));
        }

        @Nullable
        List<? extends DirectResourceAccess<?>> get(RecipeRole recipeIo, RecipeSearchPoolId poolId) {
            return access.get(new RecipeHandlerCacheKey(recipeIo, poolId));
        }

        void put(
                 RecipeRole recipeIo,
                 RecipeSearchPoolId poolId,
                 @Nullable List<? extends DirectResourceAccess<?>> value) {
            access.put(new RecipeHandlerCacheKey(recipeIo, poolId), value);
        }

        void clear() {
            access.clear();
        }
    }

    private record RecipeHandlerCacheKey(RecipeRole role, RecipeSearchPoolId poolId) {}
}
