package net.ptcrys.topo.api.recipe.search;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;

import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Capability-aware search index for one Topo recipe type.
 *
 * <p>
 * Safe-search contract: this index is only a candidate generator. It may prove that a recipe is
 * worth checking, but it never proves that a recipe is the answer. Final selection is always the
 * first original holder whose exact start-input matcher accepts the current machine.
 */
public final class TopoRecipeSearchIndex<R extends TopoRecipe> {

    private static final int TINY_LINEAR_RECIPE_LIMIT = 64;
    private static final int FALLBACK_DOMINANCE_RATIO = 4;
    private static final ThreadLocal<SearchScratch> SCRATCH = ThreadLocal.withInitial(SearchScratch::new);

    private final RecipeSearchKeyRegistry keyRegistry;
    private final List<IndexedTopoRecipe<R>> recipesByOrdinal;
    private final RecipeHolder<R>[] holdersByOrdinal;
    private final Int2ObjectOpenHashMap<PostingList> postingsByLeadKey;
    private final int[] preciseCandidateOrdinals;
    private final int indexedRecipeCount;
    private final int preciseCandidateCount;
    private final int serverToken;
    private final boolean resourceVersionSearchCacheable;
    private final boolean lowSelectivityLinearPreferred;

    private TopoRecipeSearchIndex(
                                  RecipeSearchKeyRegistry keyRegistry,
                                  List<IndexedTopoRecipe<R>> recipesByOrdinal,
                                  RecipeHolder<R>[] holdersByOrdinal,
                                  Int2ObjectOpenHashMap<PostingList> postingsByLeadKey,
                                  int[] preciseCandidateOrdinals,
                                  int indexedRecipeCount,
                                  int preciseCandidateCount,
                                  int serverToken,
                                  boolean resourceVersionSearchCacheable,
                                  boolean lowSelectivityLinearPreferred) {
        this.keyRegistry = keyRegistry;
        this.recipesByOrdinal = List.copyOf(recipesByOrdinal);
        this.holdersByOrdinal = holdersByOrdinal;
        this.postingsByLeadKey = postingsByLeadKey;
        this.preciseCandidateOrdinals = preciseCandidateOrdinals;
        this.indexedRecipeCount = indexedRecipeCount;
        this.preciseCandidateCount = preciseCandidateCount;
        this.serverToken = serverToken;
        this.resourceVersionSearchCacheable = resourceVersionSearchCacheable;
        this.lowSelectivityLinearPreferred = lowSelectivityLinearPreferred;
    }

    public static <R extends TopoRecipe> TopoRecipeSearchIndex<R> build(Collection<RecipeHolder<R>> holders) {
        return build(holders, 0);
    }

    public static <R extends TopoRecipe> TopoRecipeSearchIndex<R> build(
                                                                        Collection<RecipeHolder<R>> holders,
                                                                        int serverToken) {
        RecipeSearchKeyRegistry registry = new RecipeSearchKeyRegistry();
        Set<RecipeCapability<?, ?>> extractableCapabilities = extractableCapabilities();
        List<IndexedTopoRecipe<R>> recipes = new ArrayList<>(holders.size());
        Int2LongOpenHashMap keyFrequency = new Int2LongOpenHashMap();
        keyFrequency.defaultReturnValue(0L);
        boolean resourceVersionSearchCacheable = true;

        int ordinal = 0;
        for (RecipeHolder<R> holder : holders) {
            IndexedTopoRecipe<R> recipe = IndexedTopoRecipe.of(ordinal++, holder, registry, extractableCapabilities);
            recipes.add(recipe);
            if (!holder.value().startInputsResourceVersionStable()) {
                resourceVersionSearchCacheable = false;
            }
            for (int keyId : recipe.sortedKeys) {
                keyFrequency.mergeLong(keyId, 1L, Long::sum);
            }
        }

        Int2ObjectOpenHashMap<PostingListBuilder> postingBuilders = new Int2ObjectOpenHashMap<>();
        IntArrayList preciseOrdinals = new IntArrayList();
        int indexedCount = 0;
        for (IndexedTopoRecipe<R> recipe : recipes) {
            if (recipe.isPreciseCandidate()) {
                preciseOrdinals.add(recipe.ordinal);
                continue;
            }
            recipe.selectLeadKey(keyFrequency);
            postingBuilders
                    .computeIfAbsent(recipe.leadKey(), PostingListBuilder::new)
                    .add(recipe.ordinal, recipe.leadKeyAmount());
            indexedCount++;
        }

        Int2ObjectOpenHashMap<PostingList> postings = new Int2ObjectOpenHashMap<>(postingBuilders.size());
        int largestPostingSize = 0;
        boolean amountDiscriminatingPostings = false;
        for (var entry : Int2ObjectMaps.fastIterable(postingBuilders)) {
            PostingList posting = entry.getValue().toPostingList();
            postings.put(entry.getIntKey(), posting);
            largestPostingSize = Math.max(largestPostingSize, posting.ordinals.length);
            if (posting.minimumLeadAmount > 1L || posting.maximumLeadAmount != posting.minimumLeadAmount) {
                amountDiscriminatingPostings = true;
            }
        }
        boolean lowSelectivityLinearPreferred = !amountDiscriminatingPostings && largestPostingSize > holders.size() / 3 && holders.size() > TINY_LINEAR_RECIPE_LIMIT;
        // Optimization: recipe types dominated by one low-selectivity key and no amount gate use
        // the old linear oracle immediately. Principle: if an index would activate a large
        // fraction of the type and cannot reject by quantity, fingerprint extraction is pure
        // overhead before the same exact matcher loop.

        return new TopoRecipeSearchIndex<>(
                registry,
                recipes,
                holdersByOrdinal(recipes),
                postings,
                preciseOrdinals.toIntArray(),
                indexedCount,
                preciseOrdinals.size(),
                serverToken,
                resourceVersionSearchCacheable,
                lowSelectivityLinearPreferred);
    }

    @SuppressWarnings("unchecked")
    private static <R extends TopoRecipe> RecipeHolder<R>[] holdersByOrdinal(List<IndexedTopoRecipe<R>> recipes) {
        RecipeHolder<R>[] holders = (RecipeHolder<R>[]) new RecipeHolder<?>[recipes.size()];
        for (int index = 0; index < recipes.size(); index++) {
            holders[index] = recipes.get(index).holder;
        }
        return holders;
    }

    public @Nullable RecipeHolder<R> findRecipe(MachineBlockEntity machine) {
        if (shouldUseLinearFastPath()) {
            // Optimization: use the old linear oracle for tiny, all-fallback, or
            // fallback-dominant recipe types.
            // Principle: when candidate generation cannot prune enough work to amortize machine
            // fingerprint extraction and merge planning, the safest algorithm is also fastest.
            return findLinearRecipe(machine);
        }

        SearchScratch scratch = SCRATCH.get();
        if (!scratch.acquire()) {
            // Optimization safety: re-entrant searches get an isolated scratch object.
            // Principle: ThreadLocal reuse removes hot-path allocations, but exact matchers must
            // never corrupt an outer search if they trigger another lookup in the future.
            return findRecipeWithScratch(machine, new SearchScratch());
        }
        try {
            return findRecipeWithScratch(machine, scratch);
        } finally {
            scratch.release();
        }
    }

    private boolean shouldUseLinearFastPath() {
        return indexedRecipeCount == 0 || lowSelectivityLinearPreferred || recipesByOrdinal.size() <= TINY_LINEAR_RECIPE_LIMIT || preciseCandidateCount * 4L >= recipesByOrdinal.size() || (preciseCandidateCount > 0 && preciseCandidateCount > (long) indexedRecipeCount * FALLBACK_DOMINANCE_RATIO);
    }

    private @Nullable RecipeHolder<R> findRecipeWithScratch(MachineBlockEntity machine, SearchScratch scratch) {
        scratch.begin();
        extractMachineMapInto(machine, scratch.fingerprint);

        if (!scratch.fingerprint.isEmpty()) {
            collectActivePostings(scratch);
        }

        if (scratch.activePostingCount == 0) {
            return findFirstPreciseCandidate(machine);
        }

        if (shouldUseLinearForHighFanout(scratch)) {
            // Optimization: high-fanout indexed plans fall back to the old linear oracle.
            // Principle: when the live fingerprint activates most of the recipe type, indexing no
            // longer prunes enough exact matcher calls to pay for planning overhead.
            return findLinearRecipe(machine);
        }

        if (scratch.activePostingCount == 1) {
            return findFirstStreamingCandidate(machine, scratch);
        }

        return findFirstMaterializedCandidate(machine, scratch);
    }

    private boolean shouldUseLinearForHighFanout(SearchScratch scratch) {
        int plannedRows = scratch.estimatedCandidateRows + preciseCandidateOrdinals.length;
        return plannedRows > recipesByOrdinal.size() / 3 && scratch.estimatedCandidateRows > 128;
    }

    private void collectActivePostings(SearchScratch scratch) {
        // Optimization: collect sorted posting streams instead of materialized candidates.
        // Principle: each fully indexed recipe is posted under exactly one lead key, and each
        // posting list is built in holder order, so query-time merging can preserve RecipeManager
        // order without allocating a combined candidate list or sorting it.
        for (var entry : Int2LongMaps.fastIterable(scratch.fingerprint)) {
            PostingList posting = postingsByLeadKey.get(entry.getIntKey());
            if (posting == null) {
                continue;
            }
            long availableAmount = entry.getLongValue();
            if (availableAmount < posting.minimumLeadAmount) {
                // Optimization: skip an entire posting list when even its cheapest recipe cannot
                // pass the lead-key amount gate. Principle: the lead amount is a necessary
                // lower-bound condition; failing every row in this list proves no candidate can
                // match, while other lead-key streams are still considered independently.
                continue;
            }
            scratch.addActivePosting(posting, availableAmount);
        }
    }

    private @Nullable RecipeHolder<R> findFirstPreciseCandidate(MachineBlockEntity machine) {
        for (int ordinal : preciseCandidateOrdinals) {
            IndexedTopoRecipe<R> recipe = recipesByOrdinal.get(ordinal);
            if (matchesStartInputs(recipe.holder.value(), machine)) {
                return recipe.holder;
            }
        }
        return null;
    }

    private @Nullable RecipeHolder<R> findFirstStreamingCandidate(MachineBlockEntity machine, SearchScratch scratch) {
        int preciseIndex = 0;
        while (true) {
            int preciseOrdinal = preciseIndex < preciseCandidateOrdinals.length ? preciseCandidateOrdinals[preciseIndex] : Integer.MAX_VALUE;
            int bestStream = scratch.findBestPostingStream();
            int indexedOrdinal = bestStream >= 0 ? scratch.currentOrdinal(bestStream) : Integer.MAX_VALUE;

            if (preciseOrdinal == Integer.MAX_VALUE && indexedOrdinal == Integer.MAX_VALUE) {
                return null;
            }

            if (preciseOrdinal < indexedOrdinal) {
                IndexedTopoRecipe<R> recipe = recipesByOrdinal.get(preciseOrdinal);
                preciseIndex++;
                if (matchesStartInputs(recipe.holder.value(), machine)) {
                    return recipe.holder;
                }
                continue;
            }

            IndexedTopoRecipe<R> recipe = recipesByOrdinal.get(indexedOrdinal);
            scratch.advance(bestStream);
            if (recipe.amountsSatisfied(scratch.fingerprint) && matchesStartInputs(recipe.holder.value(), machine)) {
                return recipe.holder;
            }
        }
    }

    private @Nullable RecipeHolder<R> findFirstMaterializedCandidate(MachineBlockEntity machine, SearchScratch scratch) {
        scratch.materializeIndexedCandidates();
        sortIndexedCandidates(scratch);

        int preciseIndex = 0;
        int indexedIndex = 0;
        int indexedSize = scratch.indexedCandidates.size();
        while (preciseIndex < preciseCandidateOrdinals.length || indexedIndex < indexedSize) {
            int preciseOrdinal = preciseIndex < preciseCandidateOrdinals.length ? preciseCandidateOrdinals[preciseIndex] : Integer.MAX_VALUE;
            int indexedOrdinal = indexedIndex < indexedSize ? scratch.indexedCandidates.getInt(indexedIndex) : Integer.MAX_VALUE;

            if (preciseOrdinal < indexedOrdinal) {
                IndexedTopoRecipe<R> recipe = recipesByOrdinal.get(preciseOrdinal);
                preciseIndex++;
                if (matchesStartInputs(recipe.holder.value(), machine)) {
                    return recipe.holder;
                }
                continue;
            }

            IndexedTopoRecipe<R> recipe = recipesByOrdinal.get(indexedOrdinal);
            indexedIndex++;
            if (recipe.amountsSatisfied(scratch.fingerprint) && matchesStartInputs(recipe.holder.value(), machine)) {
                return recipe.holder;
            }
        }
        return null;
    }

    private void sortIndexedCandidates(SearchScratch scratch) {
        if (scratch.indexedCandidates.size() < 2) {
            return;
        }
        // Optimization: materialized multi-stream plans sort only indexed candidates.
        // Principle: fallback ordinals are already in holder order, so sorting the smaller indexed
        // side gives a cheap two-way merge without marking every recipe in the type.
        IntArrays.quickSort(scratch.indexedCandidates.elements(), 0, scratch.indexedCandidates.size());
    }

    private @Nullable RecipeHolder<R> findLinearRecipe(MachineBlockEntity machine) {
        for (RecipeHolder<R> holder : holdersByOrdinal) {
            if (matchesStartInputs(holder.value(), machine)) {
                return holder;
            }
        }
        return null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void extractMachineMapInto(MachineBlockEntity machine, Int2LongMap into) {
        RecipeCapability<?, ?>[] indexing = RecipeCapabilities.indexingArrayForSearch();
        if (indexing.length == 0 && !RecipeCapabilities.isFrozen()) {
            for (RecipeCapability capability : RecipeCapabilities.registered()) {
                if (capability.contributesIndexKeys()) {
                    capability.extractMachineKeys(machine, keyRegistry, into);
                }
            }
            return;
        }
        // Optimization: iterate the frozen indexing-capability array.
        // Principle: only capabilities that can emit search keys scan machine handlers, so scalar
        // or UI-only lanes never add virtual dispatch and branch work to the recipe hot path.
        for (RecipeCapability capability : indexing) {
            if (capability == null) {
                continue;
            }
            capability.extractMachineKeys(machine, keyRegistry, into);
        }
    }

    private static boolean matchesStartInputs(TopoRecipe recipe, MachineBlockEntity machine) {
        // Optimization: call TopoRecipe's start-input matcher directly instead of allocating the
        // vanilla RecipeInput adapter. Principle: this preserves the old TopoRecipe#matches
        // contract exactly; tick IO and output gates remain in RecipeLogic.
        return recipe.matchInputs(machine);
    }

    private static Set<RecipeCapability<?, ?>> extractableCapabilities() {
        Set<RecipeCapability<?, ?>> capabilities = Collections.newSetFromMap(new IdentityHashMap<>());
        RecipeCapability<?, ?>[] indexing = RecipeCapabilities.indexingArrayForSearch();
        if (indexing.length == 0 && !RecipeCapabilities.isFrozen()) {
            for (RecipeCapability<?, ?> capability : RecipeCapabilities.registered()) {
                if (capability.contributesIndexKeys()) {
                    capabilities.add(capability);
                }
            }
            return capabilities;
        }
        Collections.addAll(capabilities, indexing);
        capabilities.remove(null);
        return capabilities;
    }

    public int indexedRecipeCount() {
        return indexedRecipeCount;
    }

    public int fallbackRecipeCount() {
        return preciseCandidateCount;
    }

    public int keyCount() {
        return keyRegistry.size();
    }

    public int serverToken() {
        return serverToken;
    }

    public boolean resourceVersionSearchCacheable() {
        return resourceVersionSearchCacheable;
    }

    private static final class PostingList {

        private final int[] ordinals;
        private final long[] leadAmounts;
        private final long minimumLeadAmount;
        private final long maximumLeadAmount;

        private PostingList(int[] ordinals, long[] leadAmounts, long minimumLeadAmount, long maximumLeadAmount) {
            this.ordinals = ordinals;
            this.leadAmounts = leadAmounts;
            this.minimumLeadAmount = minimumLeadAmount;
            this.maximumLeadAmount = maximumLeadAmount;
        }
    }

    private static final class PostingListBuilder {

        private final IntArrayList ordinals = new IntArrayList();
        private final LongArrayList leadAmounts = new LongArrayList();
        private long minimumLeadAmount = Long.MAX_VALUE;
        private long maximumLeadAmount;

        private PostingListBuilder(int unusedKey) {}

        void add(int ordinal, long leadAmount) {
            ordinals.add(ordinal);
            leadAmounts.add(leadAmount);
            minimumLeadAmount = Math.min(minimumLeadAmount, leadAmount);
            maximumLeadAmount = Math.max(maximumLeadAmount, leadAmount);
        }

        PostingList toPostingList() {
            return new PostingList(ordinals.toIntArray(), leadAmounts.toLongArray(), minimumLeadAmount, maximumLeadAmount);
        }
    }

    private static final class SearchScratch {

        private final Int2LongOpenHashMap fingerprint = new Int2LongOpenHashMap();
        private final IntArrayList indexedCandidates = new IntArrayList();
        private PostingList[] activePostings = new PostingList[8];
        private long[] activeAmounts = new long[8];
        private int[] activePositions = new int[8];
        private int activePostingCount;
        private int estimatedCandidateRows;
        private boolean inUse;

        private SearchScratch() {
            fingerprint.defaultReturnValue(0L);
        }

        boolean acquire() {
            if (inUse) {
                return false;
            }
            inUse = true;
            return true;
        }

        void begin() {
            fingerprint.clear();
            indexedCandidates.clear();
            activePostingCount = 0;
            estimatedCandidateRows = 0;
        }

        void addActivePosting(PostingList posting, long availableAmount) {
            ensureActiveCapacity(activePostingCount + 1);
            activePostings[activePostingCount] = posting;
            activeAmounts[activePostingCount] = availableAmount;
            activePositions[activePostingCount] = 0;
            activePostingCount++;
            estimatedCandidateRows += posting.ordinals.length;
        }

        void materializeIndexedCandidates() {
            indexedCandidates.clear();
            for (int stream = 0; stream < activePostingCount; stream++) {
                PostingList posting = activePostings[stream];
                long availableAmount = activeAmounts[stream];
                if (availableAmount >= posting.maximumLeadAmount) {
                    indexedCandidates.addElements(indexedCandidates.size(), posting.ordinals);
                    continue;
                }
                for (int index = 0; index < posting.ordinals.length; index++) {
                    if (availableAmount >= posting.leadAmounts[index]) {
                        indexedCandidates.add(posting.ordinals[index]);
                    }
                }
            }
        }

        int findBestPostingStream() {
            int bestStream = -1;
            int bestOrdinal = Integer.MAX_VALUE;
            for (int stream = 0; stream < activePostingCount; stream++) {
                int ordinal = currentOrdinal(stream);
                if (ordinal < bestOrdinal) {
                    bestOrdinal = ordinal;
                    bestStream = stream;
                }
            }
            return bestStream;
        }

        int currentOrdinal(int stream) {
            PostingList posting = activePostings[stream];
            int position = activePositions[stream];
            while (position < posting.ordinals.length && activeAmounts[stream] < posting.leadAmounts[position]) {
                position++;
            }
            activePositions[stream] = position;
            return position < posting.ordinals.length ? posting.ordinals[position] : Integer.MAX_VALUE;
        }

        void advance(int stream) {
            activePositions[stream]++;
        }

        void release() {
            inUse = false;
        }

        private void ensureActiveCapacity(int requiredSize) {
            if (activePostings.length >= requiredSize) {
                return;
            }
            int newSize = Math.max(requiredSize, activePostings.length * 2);
            activePostings = java.util.Arrays.copyOf(activePostings, newSize);
            activeAmounts = java.util.Arrays.copyOf(activeAmounts, newSize);
            activePositions = java.util.Arrays.copyOf(activePositions, newSize);
        }
    }
}
