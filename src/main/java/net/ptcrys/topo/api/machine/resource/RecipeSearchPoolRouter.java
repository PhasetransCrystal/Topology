package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Production-side pool container: contributions are grouped and same-pool handlers combined; each
 * {@link RecipeSearchPool} then owns search scheduling (miss backoff and wake-on-change).
 *
 * <p>
 * Inputs and isolatable outputs are pool-scoped: a recipe that hits pool {@code P} consumes from
 * {@code P} and emits into {@code P}. Unscoped handlers (e.g. scalar energy) join every pool.
 *
 * <p>
 * Consumers obtain a non-empty list of concrete pools and either poll via {@link #search} or
 * select a pool for lane lookups. {@link RecipeSearchPoolId#DEFAULT} is present only when selected
 * by a scoped contribution, or as the fallback for a machine containing global resources only.
 */
public final class RecipeSearchPoolRouter {

    private final List<RecipeSearchPool> pools;
    private final List<RecipeSearchPoolId> poolIds;
    private final Map<RecipeSearchPoolId, RecipeSearchPool> poolsById;
    /**
     * Public/global-only view for active recipes persisted before UNIVERSAL became membership
     * rather than a concrete pool. It is deliberately absent from {@link #pools} and
     * {@link #poolsById}, so new searches can never select it.
     */
    private final RecipeSearchPool legacyUniversalView;
    /** Index where the next polling pass starts; server-tick confined with the rest of recipe logic. */
    private int nextSearchStartIndex;

    private RecipeSearchPoolRouter(
                                   List<RecipeSearchPool> pools,
                                   RecipeSearchPool legacyUniversalView) {
        this.pools = pools;
        this.legacyUniversalView = Objects.requireNonNull(
                legacyUniversalView, "legacy universal view");
        List<RecipeSearchPoolId> ids = new ArrayList<>(pools.size());
        Map<RecipeSearchPoolId, RecipeSearchPool> byId = new LinkedHashMap<>();
        for (RecipeSearchPool pool : pools) {
            ids.add(pool.id());
            byId.put(pool.id(), pool);
        }
        this.poolIds = List.copyOf(ids);
        this.poolsById = Collections.unmodifiableMap(byId);
    }

    /** Stable pool list for polling. Always contains at least one concrete pool after build. */
    public List<RecipeSearchPool> pools() {
        return pools;
    }

    /** Cached immutable id view; this method is allocation-free on the idle recipe-search path. */
    public List<RecipeSearchPoolId> poolIds() {
        return poolIds;
    }

    public @Nullable RecipeSearchPool pool(RecipeSearchPoolId id) {
        return poolsById.get(id);
    }

    /** Cancel backoff on every pool (resource content or structure change). */
    public void wakeAll() {
        for (int i = 0; i < pools.size(); i++) {
            pools.get(i).wake();
        }
    }

    /**
     * Poll pools that are due and run full recipe search under each until one hits. Resource-empty
     * pools still get an attempt for recipes without one-time inputs; an empty miss moves directly
     * to the five-second backoff cap.
     */
    @SuppressWarnings("unchecked")
    public @Nullable SearchHit search(
                                      MachineBlockEntity machine,
                                      List<? extends TopoRecipeType<?>> recipeTypes,
                                      long gameTime,
                                      PoolActivator activator) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(recipeTypes, "recipe types");
        Objects.requireNonNull(activator, "activator");
        int poolCount = pools.size();
        int startIndex = nextSearchStartIndex < poolCount ? nextSearchStartIndex : 0;
        for (int offset = 0; offset < poolCount; offset++) {
            int poolIndex = startIndex + offset;
            if (poolIndex >= poolCount) {
                poolIndex -= poolCount;
            }
            RecipeSearchPool pool = pools.get(poolIndex);
            if (!pool.shouldSearch(gameTime)) {
                continue;
            }
            try (AutoCloseable scope = activator.activate(pool.id())) {
                SearchHit hit = pool.hasRememberedSearches() ? searchPoolWithHistory(machine, recipeTypes, pool) : searchPoolTraditional(machine, recipeTypes, pool);
                if (hit != null) {
                    pool.noteHit();
                    nextSearchStartIndex = nextIndex(poolIndex, poolCount);
                    return hit;
                }
            } catch (Exception e) {
                throw e instanceof RuntimeException re ? re : new RuntimeException(e);
            }
            pool.noteMiss(gameTime);
        }
        nextSearchStartIndex = nextIndex(startIndex, poolCount);
        return null;
    }

    /** Records only a candidate whose complete RecipeLogic start sequence committed successfully. */
    public void rememberSuccessfulStart(SearchHit hit) {
        Objects.requireNonNull(hit, "search hit");
        hit.requireCurrentRevision();
        RecipeSearchPool pool = Objects.requireNonNull(
                poolsById.get(hit.poolId()), () -> "search hit references missing pool " + hit.poolId());
        pool.rememberSuccessfulStart(hit.recipeType(), hit.holder(), hit.searchRevision());
    }

    /** Historical candidates that fail a later start gate are demoted and eventually evicted. */
    public void noteRememberedStartFailure(SearchHit hit) {
        Objects.requireNonNull(hit, "search hit");
        if (hit.source() != SearchSource.HISTORY) {
            return;
        }
        hit.requireCurrentRevision();
        RecipeSearchPool pool = Objects.requireNonNull(
                poolsById.get(hit.poolId()), () -> "search hit references missing pool " + hit.poolId());
        pool.noteRememberedStartFailure(hit.recipeType(), hit.holder());
    }

    /** Re-runs the original type/holder order in one pool after a historical start gate fails. */
    public @Nullable SearchHit researchPoolWithoutHistory(
                                                          MachineBlockEntity machine,
                                                          List<? extends TopoRecipeType<?>> recipeTypes,
                                                          RecipeSearchPoolId poolId,
                                                          PoolActivator activator) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(recipeTypes, "recipe types");
        Objects.requireNonNull(poolId, "pool id");
        Objects.requireNonNull(activator, "activator");
        RecipeSearchPool pool = Objects.requireNonNull(
                poolsById.get(poolId), () -> "cannot research missing pool " + poolId);
        try (AutoCloseable scope = activator.activate(pool.id())) {
            return searchPoolTraditional(machine, recipeTypes, pool);
        } catch (Exception e) {
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    private static @Nullable SearchHit searchPoolWithHistory(
                                                             MachineBlockEntity machine,
                                                             List<? extends TopoRecipeType<?>> recipeTypes,
                                                             RecipeSearchPool pool) {
        for (int index = 0; index < recipeTypes.size(); index++) {
            TopoRecipeType<?> type = recipeTypes.get(index);
            RecipeHolder<? extends TopoRecipe> remembered = pool.rememberedSearch(machine, type);
            if (remembered != null) {
                return new SearchHit(
                        remembered, pool.id(), type, SearchSource.HISTORY, type.searchRevision());
            }
            RecipeHolder<?> found = type.findRecipe(machine);
            if (found != null) {
                return searchHit(found, pool, type);
            }
        }
        return null;
    }

    private static @Nullable SearchHit searchPoolTraditional(
                                                             MachineBlockEntity machine,
                                                             List<? extends TopoRecipeType<?>> recipeTypes,
                                                             RecipeSearchPool pool) {
        for (int index = 0; index < recipeTypes.size(); index++) {
            TopoRecipeType<?> type = recipeTypes.get(index);
            RecipeHolder<?> found = type.findRecipe(machine);
            if (found != null) {
                return searchHit(found, pool, type);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static SearchHit searchHit(RecipeHolder<?> holder, RecipeSearchPool pool, TopoRecipeType<?> type) {
        return new SearchHit(
                (RecipeHolder<? extends TopoRecipe>) holder,
                pool.id(),
                type,
                SearchSource.TRADITIONAL,
                type.searchRevision());
    }

    private static int nextIndex(int index, int size) {
        int next = index + 1;
        return next < size ? next : 0;
    }

    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable ResourceHandler<R> handler(
                                                                     RecipeSearchPoolId poolId,
                                                                     MachineResourceType<R> resourceType,
                                                                     RecipeRole recipeIo) {
        Objects.requireNonNull(poolId, "pool id");
        Objects.requireNonNull(resourceType, "resource type");
        Objects.requireNonNull(recipeIo, "recipe IO");
        if (recipeIo == RecipeRole.NONE) {
            return null;
        }
        // Old saves may contain an in-flight run whose selected pool was UNIVERSAL. Preserve
        // exactly that old execution view (public/global handlers only) without making UNIVERSAL
        // pollable again. Missing DEFAULT/custom ids remain null and therefore fail closed.
        RecipeSearchPool pool = poolId.isUniversal() ? legacyUniversalView : poolsById.get(poolId);
        if (pool == null) {
            return null;
        }
        if (recipeIo == RecipeRole.OUTPUT) {
            return pool.outputHandler(resourceType);
        }
        return pool.inputHandler(resourceType);
    }

    /** Opens the active-pool scope for lane lookups during a search attempt. */
    @FunctionalInterface
    public interface PoolActivator {

        AutoCloseable activate(RecipeSearchPoolId poolId);
    }

    public enum SearchSource {
        HISTORY,
        TRADITIONAL
    }

    public record SearchHit(
                            RecipeHolder<? extends TopoRecipe> holder,
                            RecipeSearchPoolId poolId,
                            TopoRecipeType<?> recipeType,
                            SearchSource source,
                            long searchRevision) {

        public SearchHit {
            Objects.requireNonNull(holder, "holder");
            Objects.requireNonNull(poolId, "pool id");
            Objects.requireNonNull(recipeType, "recipe type");
            Objects.requireNonNull(source, "search source");
            if (holder.value().recipeType() != recipeType) {
                throw new IllegalArgumentException("search hit holder does not belong to its recipe type");
            }
        }

        public void requireCurrentRevision() {
            long current = recipeType.searchRevision();
            if (searchRevision != current) {
                throw new IllegalStateException("Recipe search hit crossed a revision boundary for " + recipeType.id() + ": captured=" + searchRevision + ", current=" + current);
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<MachineResourceType<?>, Map<RecipeSearchPoolId, List<ResourceHandler<?>>>> scopedInputs = new IdentityHashMap<>();
        private final Map<MachineResourceType<?>, List<ResourceHandler<?>>> unscopedInputs = new IdentityHashMap<>();
        private final Map<MachineResourceType<?>, Map<RecipeSearchPoolId, List<ResourceHandler<?>>>> scopedOutputs = new IdentityHashMap<>();
        private final Map<MachineResourceType<?>, List<ResourceHandler<?>>> unscopedOutputs = new IdentityHashMap<>();
        private final Set<RecipeSearchPoolId> seenPools = new LinkedHashSet<>();

        public <R extends Resource> Builder addInput(
                                                     MachineResourceType<R> type,
                                                     MachineComponent.RecipeResourceContribution<R> contribution) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(contribution, "contribution");
            if (!contribution.poolScoped() || contribution.poolId().isUniversal()) {
                // UNIVERSAL is membership in every concrete pool, never a concrete pool itself.
                unscopedInputs.computeIfAbsent(type, unused -> new ArrayList<>()).add(contribution.handler());
                return this;
            }
            seenPools.add(contribution.poolId());
            scopedInputs
                    .computeIfAbsent(type, unused -> new LinkedHashMap<>())
                    .computeIfAbsent(contribution.poolId(), unused -> new ArrayList<>())
                    .add(contribution.handler());
            return this;
        }

        public <R extends Resource> Builder addOutput(
                                                      MachineResourceType<R> type,
                                                      MachineComponent.RecipeResourceContribution<R> contribution) {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(contribution, "contribution");
            if (!contribution.poolScoped() || contribution.poolId().isUniversal()) {
                // Global outputs (e.g. scalar energy) join every pool's emit view.
                unscopedOutputs.computeIfAbsent(type, unused -> new ArrayList<>()).add(contribution.handler());
                return this;
            }
            seenPools.add(contribution.poolId());
            scopedOutputs
                    .computeIfAbsent(type, unused -> new LinkedHashMap<>())
                    .computeIfAbsent(contribution.poolId(), unused -> new ArrayList<>())
                    .add(contribution.handler());
            return this;
        }

        public RecipeSearchPoolRouter build() {
            List<RecipeSearchPoolId> poolOrder = orderPools(seenPools);

            Set<MachineResourceType<?>> allInputTypes = new LinkedHashSet<>();
            allInputTypes.addAll(scopedInputs.keySet());
            allInputTypes.addAll(unscopedInputs.keySet());

            Set<MachineResourceType<?>> allOutputTypes = new LinkedHashSet<>();
            allOutputTypes.addAll(scopedOutputs.keySet());
            allOutputTypes.addAll(unscopedOutputs.keySet());

            List<RecipeSearchPool> builtPools = new ArrayList<>(poolOrder.size());
            for (RecipeSearchPoolId poolId : poolOrder) {
                Map<MachineResourceType<?>, ResourceHandler<?>> inputByType = combineRole(poolId, allInputTypes, scopedInputs, unscopedInputs);
                Map<MachineResourceType<?>, ResourceHandler<?>> outputByType = combineRole(poolId, allOutputTypes, scopedOutputs, unscopedOutputs);
                builtPools.add(new RecipeSearchPool(
                        poolId,
                        inputByType.isEmpty() ? Map.of() : Map.copyOf(inputByType),
                        outputByType.isEmpty() ? Map.of() : Map.copyOf(outputByType)));
            }

            Map<MachineResourceType<?>, ResourceHandler<?>> universalInputs = combineUnscoped(unscopedInputs);
            Map<MachineResourceType<?>, ResourceHandler<?>> universalOutputs = combineUnscoped(unscopedOutputs);
            RecipeSearchPool legacyUniversalView = new RecipeSearchPool(
                    RecipeSearchPoolId.UNIVERSAL,
                    universalInputs.isEmpty() ? Map.of() : Map.copyOf(universalInputs),
                    universalOutputs.isEmpty() ? Map.of() : Map.copyOf(universalOutputs));
            return new RecipeSearchPoolRouter(List.copyOf(builtPools), legacyUniversalView);
        }

        private static Map<MachineResourceType<?>, ResourceHandler<?>> combineUnscoped(
                                                                                       Map<MachineResourceType<?>, List<ResourceHandler<?>>> unscoped) {
            Map<MachineResourceType<?>, ResourceHandler<?>> byType = new IdentityHashMap<>();
            for (Map.Entry<MachineResourceType<?>, List<ResourceHandler<?>>> entry : unscoped.entrySet()) {
                ResourceHandler<?> combined = combine(entry.getKey(), entry.getValue());
                if (combined != null) {
                    byType.put(entry.getKey(), combined);
                }
            }
            return byType;
        }

        private static Map<MachineResourceType<?>, ResourceHandler<?>> combineRole(
                                                                                   RecipeSearchPoolId poolId,
                                                                                   Set<MachineResourceType<?>> types,
                                                                                   Map<MachineResourceType<?>, Map<RecipeSearchPoolId, List<ResourceHandler<?>>>> scoped,
                                                                                   Map<MachineResourceType<?>, List<ResourceHandler<?>>> unscoped) {
            Map<MachineResourceType<?>, ResourceHandler<?>> byType = new IdentityHashMap<>();
            for (MachineResourceType<?> type : types) {
                List<ResourceHandler<?>> handlers = new ArrayList<>();
                Map<RecipeSearchPoolId, List<ResourceHandler<?>>> byPool = scoped.get(type);
                if (byPool != null) {
                    List<ResourceHandler<?>> scopedList = byPool.get(poolId);
                    if (scopedList != null) {
                        handlers.addAll(scopedList);
                    }
                }
                List<ResourceHandler<?>> unscopedList = unscoped.get(type);
                if (unscopedList != null) {
                    handlers.addAll(unscopedList);
                }
                ResourceHandler<?> combined = combine(type, handlers);
                if (combined != null) {
                    byType.put(type, combined);
                }
            }
            return byType;
        }

        private static List<RecipeSearchPoolId> orderPools(Set<RecipeSearchPoolId> seen) {
            LinkedHashSet<RecipeSearchPoolId> ordered = new LinkedHashSet<>();
            // Keep DEFAULT first only when a real scoped contribution selected it. A custom-only
            // controller must not gain a ghost DEFAULT pool that can consume UNIVERSAL inputs.
            if (seen.contains(RecipeSearchPoolId.DEFAULT)) {
                ordered.add(RecipeSearchPoolId.DEFAULT);
            }
            for (RecipeSearchPoolId id : seen) {
                if (!id.isUniversal()) {
                    ordered.add(id);
                }
            }
            // With only global resources there is no scoped id to name the execution view; DEFAULT
            // remains the single-machine fallback.
            if (ordered.isEmpty()) {
                ordered.add(RecipeSearchPoolId.DEFAULT);
            }
            return List.copyOf(ordered);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private static @Nullable ResourceHandler<?> combine(
                                                            MachineResourceType type, List<ResourceHandler<?>> handlers) {
            if (handlers.isEmpty()) {
                return null;
            }
            return type.combine(handlers);
        }
    }
}
