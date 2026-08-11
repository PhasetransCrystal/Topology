package net.ptcrys.topo.api.recipe.search;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;

import net.minecraft.world.item.crafting.RecipeHolder;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;

import java.util.Arrays;
import java.util.Set;

final class IndexedTopoRecipe<R extends TopoRecipe> {

    private static final int NO_LEAD_KEY = -1;

    final int ordinal;
    final RecipeHolder<R> holder;
    final int[] sortedKeys;
    private final long[] sortedKeyAmounts;
    private final boolean preciseCandidate;
    private int leadKey = NO_LEAD_KEY;
    private long leadKeyAmount;

    private IndexedTopoRecipe(
                              int ordinal,
                              RecipeHolder<R> holder,
                              int[] sortedKeys,
                              long[] sortedKeyAmounts,
                              boolean preciseCandidate) {
        this.ordinal = ordinal;
        this.holder = holder;
        this.sortedKeys = sortedKeys;
        this.sortedKeyAmounts = sortedKeyAmounts;
        this.preciseCandidate = preciseCandidate;
    }

    static <R extends TopoRecipe> IndexedTopoRecipe<R> of(
                                                          int ordinal,
                                                          RecipeHolder<R> holder,
                                                          RecipeSearchKeyRegistry registry,
                                                          Set<RecipeCapability<?, ?>> extractableCapabilities) {
        Int2LongOpenHashMap amounts = new Int2LongOpenHashMap();
        amounts.defaultReturnValue(0L);
        Int2LongOpenHashMap reservations = new Int2LongOpenHashMap();
        reservations.defaultReturnValue(0L);

        // Optimization: index only the start-input phase used by TopoRecipeType#findRecipe.
        // Principle: tick IO is checked by RecipeLogic after search; folding tick inputs
        // into the lookup fingerprint would make the optimization stricter than the old linear
        // Recipe#matches path and could hide recipes before the state machine can decide.
        addEntries(holder.value().inputs(), registry, amounts, reservations, extractableCapabilities);
        for (var entry : reservations.int2LongEntrySet()) {
            amounts.mergeLong(entry.getIntKey(), entry.getLongValue(), IndexedTopoRecipe::saturatedAdd);
        }

        int[] keys = amounts.keySet().toIntArray();
        Arrays.sort(keys);
        long[] keyAmounts = new long[keys.length];
        for (int i = 0; i < keys.length; i++) {
            keyAmounts[i] = amounts.get(keys[i]);
        }
        return new IndexedTopoRecipe<>(ordinal, holder, keys, keyAmounts, keys.length == 0);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addEntries(
                                   TopoRecipe.InputEntry<?>[] entries,
                                   RecipeSearchKeyRegistry registry,
                                   Int2LongOpenHashMap additiveAmounts,
                                   Int2LongOpenHashMap reservationAmounts,
                                   Set<RecipeCapability<?, ?>> extractableCapabilities) {
        for (TopoRecipe.InputEntry<?> entry : entries) {
            RecipeCapability capability = entry.capability();
            boolean capabilityExtractable = extractableCapabilities.contains(capability);
            for (Object content : entry.contents()) {
                var keys = capability.indexKeys(content);
                if (keys.isEmpty() || !capabilityExtractable) {
                    // Optimization downgrade: ignore only this weak projection.
                    // Principle: any remaining indexed key is still a necessary condition and can
                    // safely prune candidates; recipes with no indexed keys at all become the
                    // always-precise fallback capability after this pass.
                    continue;
                }
                long amount = capability.inputAmount(content);
                RecipeCapability.InputIndexAmountMode amountMode = capability.inputIndexAmountMode(content);
                for (Object key : keys) {
                    int keyId = registry.getOrAssign(capability, key);
                    if (amountMode == RecipeCapability.InputIndexAmountMode.MAX_RESERVATION) {
                        reservationAmounts.mergeLong(keyId, amount, Math::max);
                    } else {
                        additiveAmounts.mergeLong(keyId, amount, IndexedTopoRecipe::saturatedAdd);
                    }
                }
            }
        }
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    boolean isPreciseCandidate() {
        return preciseCandidate;
    }

    boolean hasIndexedKeys() {
        return sortedKeys.length > 0;
    }

    int leadKey() {
        return leadKey;
    }

    long leadKeyAmount() {
        return leadKeyAmount;
    }

    boolean leadAmountSatisfied(long availableAmount) {
        return availableAmount >= leadKeyAmount;
    }

    void selectLeadKey(Int2LongMap keyFrequency) {
        if (sortedKeys.length == 0) {
            leadKey = NO_LEAD_KEY;
            leadKeyAmount = 0L;
            return;
        }
        int selected = sortedKeys[0];
        long selectedAmount = sortedKeyAmounts[0];
        long selectedFrequency = keyFrequency.get(selected);
        for (int i = 1; i < sortedKeys.length; i++) {
            int keyId = sortedKeys[i];
            long frequency = keyFrequency.get(keyId);
            long amount = sortedKeyAmounts[i];
            if (frequency < selectedFrequency || (frequency == selectedFrequency && amount > selectedAmount)) {
                selected = keyId;
                selectedAmount = amount;
                selectedFrequency = frequency;
            }
        }
        // Optimization: choose the rarest required key as this recipe's posting key, and prefer
        // the largest required amount when rarity ties.
        // Principle: if a recipe is fully indexable, the machine must contain every required key;
        // posting it under a rare/high-amount key minimizes fanout and lets amount gates reject
        // shared-key candidates before precise matching while preserving safety.
        leadKey = selected;
        // Optimization: retain the lead key's lower-bound amount for posting-stage rejection.
        // Principle: failing this one necessary amount proves the recipe cannot match, while the
        // later full amount check still verifies every key before exact matching.
        leadKeyAmount = selectedAmount;
    }

    boolean amountsSatisfied(Int2LongMap machineMap) {
        int keyCount = sortedKeys.length;
        if (keyCount == 0) {
            return true;
        }
        // Optimization: single-key recipes use a straight indexed compare.
        // Principle: most machine recipes have one dominant input, so avoid iterator overhead in
        // the hottest positive path.
        if (keyCount == 1) {
            return machineMap.get(sortedKeys[0]) >= sortedKeyAmounts[0];
        }
        for (int i = 0; i < keyCount; i++) {
            if (machineMap.get(sortedKeys[i]) < sortedKeyAmounts[i]) {
                return false;
            }
        }
        return true;
    }
}
