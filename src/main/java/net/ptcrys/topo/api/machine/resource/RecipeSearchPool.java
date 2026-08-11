package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;

/**
 * One recipe pool: pre-merged input/output handlers plus per-pool search scheduling (miss backoff,
 * wake-on-change). A recipe that hits this pool consumes and emits against the same id.
 */
public final class RecipeSearchPool {

    /** Cap search interval at 5s (20 tps). */
    public static final int MAX_BACKOFF_TICKS = 20 * 5;

    private final RecipeSearchPoolId id;
    /** Combined INPUT handlers by resource type (immutable map). */
    private final Map<MachineResourceType<?>, ResourceHandler<?>> inputs;
    /** Combined OUTPUT handlers by resource type (immutable map). */
    private final Map<MachineResourceType<?>, ResourceHandler<?>> outputs;
    /** When true, {@link #isEmpty()} is forced false (unit tests only). */
    private final boolean forceNonEmpty;
    /** Game time when this pool may be searched again; {@link Long#MIN_VALUE} means due now. */
    private long nextEligibleGameTime = Long.MIN_VALUE;
    /** Current backoff step in ticks after consecutive misses; 0 when idle/fresh. */
    private int backoffTicks;
    private @Nullable RememberedSearch recentSearch;
    private @Nullable RememberedSearch previousSearch;

    RecipeSearchPool(
                     RecipeSearchPoolId id,
                     Map<MachineResourceType<?>, ResourceHandler<?>> inputs,
                     Map<MachineResourceType<?>, ResourceHandler<?>> outputs) {
        this(id, inputs, outputs, false);
    }

    private RecipeSearchPool(
                             RecipeSearchPoolId id,
                             Map<MachineResourceType<?>, ResourceHandler<?>> inputs,
                             Map<MachineResourceType<?>, ResourceHandler<?>> outputs,
                             boolean forceNonEmpty) {
        this.id = Objects.requireNonNull(id, "id");
        this.inputs = Objects.requireNonNull(inputs, "inputs");
        this.outputs = Objects.requireNonNull(outputs, "outputs");
        this.forceNonEmpty = forceNonEmpty;
    }

    /** Test helper: non-empty pool for backoff/wake scheduling without a resource registry. */
    static RecipeSearchPool forTestingNonEmpty(RecipeSearchPoolId id) {
        return new RecipeSearchPool(id, Map.of(), Map.of(), true);
    }

    public RecipeSearchPoolId id() {
        return id;
    }

    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable ResourceHandler<R> inputHandler(MachineResourceType<R> type) {
        ResourceHandler<?> handler = inputs.get(type);
        return handler == null ? null : type.castHandler(handler);
    }

    @SuppressWarnings("unchecked")
    public <R extends Resource> @Nullable ResourceHandler<R> outputHandler(MachineResourceType<R> type) {
        ResourceHandler<?> handler = outputs.get(type);
        return handler == null ? null : type.castHandler(handler);
    }

    Map<MachineResourceType<?>, ResourceHandler<?>> inputs() {
        return inputs;
    }

    Map<MachineResourceType<?>, ResourceHandler<?>> outputs() {
        return outputs;
    }

    /**
     * Whether this pool should run a full recipe search at {@code gameTime}. Resource-empty pools
     * must still get one attempt because recipes may have no one-time inputs. A miss moves an empty
     * pool directly to the maximum backoff; {@link #wake()} makes it due immediately again.
     */
    public boolean shouldSearch(long gameTime) {
        return gameTime >= nextEligibleGameTime;
    }

    /** True when every input handler has no stored amount. Used to choose the miss backoff. */
    public boolean isEmpty() {
        if (forceNonEmpty) {
            return false;
        }
        for (ResourceHandler<?> handler : inputs.values()) {
            if (hasContent(handler)) {
                return false;
            }
        }
        return true;
    }

    /** Content arrived or structure changed — cancel backoff so the next poll may search immediately. */
    public void wake() {
        nextEligibleGameTime = Long.MIN_VALUE;
        backoffTicks = 0;
    }

    public void noteHit() {
        wake();
    }

    boolean hasRememberedSearches() {
        if (recentSearch != null && recentSearch.isStale()) {
            recentSearch = null;
        }
        if (previousSearch != null && previousSearch.isStale()) {
            previousSearch = null;
        }
        return recentSearch != null || previousSearch != null;
    }

    /** Exact-checks at most two successful historical candidates for one recipe type. */
    @Nullable
    RecipeHolder<? extends TopoRecipe> rememberedSearch(
                                                        MachineBlockEntity machine, TopoRecipeType<?> recipeType) {
        RememberedSearch recent = recentSearch;
        if (recent != null && recent.isFor(recipeType)) {
            if (recipeType.matchesRememberedRecipe(machine, recent.holder())) {
                return recent.holder();
            }
            if (recent.noteProbeFailure()) {
                recentSearch = null;
            }
        }
        RememberedSearch previous = previousSearch;
        if (previous != null && previous.isFor(recipeType)) {
            if (recipeType.matchesRememberedRecipe(machine, previous.holder())) {
                return previous.holder();
            }
            if (previous.noteProbeFailure()) {
                previousSearch = null;
            }
        }
        return null;
    }

    /** Learns only recipes that passed every start gate and committed their start inputs. */
    void rememberSuccessfulStart(
                                 TopoRecipeType<?> recipeType, RecipeHolder<? extends TopoRecipe> holder, long searchRevision) {
        RememberedSearch recent = recentSearch;
        if (recent != null && recent.references(recipeType, holder)) {
            recent.refresh(recipeType, holder, searchRevision);
            return;
        }
        RememberedSearch previous = previousSearch;
        if (previous != null && previous.references(recipeType, holder)) {
            previous.refresh(recipeType, holder, searchRevision);
            previousSearch = recent;
            recentSearch = previous;
            return;
        }
        RememberedSearch target = recent != null && previous != null ? previous : new RememberedSearch();
        target.refresh(recipeType, holder, searchRevision);
        previousSearch = recent != null ? recent : previous;
        recentSearch = target;
    }

    void noteRememberedStartFailure(TopoRecipeType<?> recipeType, RecipeHolder<? extends TopoRecipe> holder) {
        RememberedSearch recent = recentSearch;
        if (recent != null && recent.references(recipeType, holder) && recent.noteProbeFailure()) {
            recentSearch = null;
            return;
        }
        RememberedSearch previous = previousSearch;
        if (previous != null && previous.references(recipeType, holder) && previous.noteProbeFailure()) {
            previousSearch = null;
        }
    }

    /**
     * No matching recipe in this pool. Empty pools jump directly to the five-second cap; pools
     * containing inputs use exponential backoff 1,2,4,… up to the same cap.
     */
    public void noteMiss(long gameTime) {
        if (isEmpty()) {
            backoffTicks = MAX_BACKOFF_TICKS;
        } else if (backoffTicks <= 0) {
            backoffTicks = 1;
        } else {
            int next = backoffTicks << 1;
            backoffTicks = Math.min(MAX_BACKOFF_TICKS, next <= 0 ? MAX_BACKOFF_TICKS : next);
        }
        nextEligibleGameTime = gameTime + backoffTicks;
    }

    private static boolean hasContent(ResourceHandler<?> handler) {
        int size = handler.size();
        for (int i = 0; i < size; i++) {
            if (handler.getAmountAsLong(i) > 0L) {
                return true;
            }
        }
        return false;
    }

    private static final class RememberedSearch {

        private @Nullable TopoRecipeType<?> hitType;
        private @Nullable RecipeHolder<? extends TopoRecipe> holder;
        private long searchRevision;
        private int probeFailures;

        boolean references(TopoRecipeType<?> type, RecipeHolder<? extends TopoRecipe> candidate) {
            return hitType == type && holder != null && holder.id().equals(candidate.id());
        }

        RecipeHolder<? extends TopoRecipe> holder() {
            return Objects.requireNonNull(holder, "remembered recipe holder");
        }

        boolean isFor(TopoRecipeType<?> type) {
            return hitType == type;
        }

        boolean isStale() {
            return hitType != null && searchRevision != hitType.searchRevision();
        }

        void refresh(TopoRecipeType<?> type, RecipeHolder<? extends TopoRecipe> candidate, long revision) {
            hitType = type;
            holder = candidate;
            searchRevision = revision;
            probeFailures = 0;
        }

        boolean noteProbeFailure() {
            return ++probeFailures >= 2;
        }
    }
}
