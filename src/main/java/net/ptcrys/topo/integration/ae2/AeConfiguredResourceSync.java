package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;

import java.util.List;

/**
 * Syncs configured AE resources into a real local cache port.
 *
 * <p>
 * The hot path is structured in two passes to deliver a single-network-call-per-AEKey
 * worst case even when many slots share the same resource (the typical Drawing bus
 * workload). The previous design issued one {@code network.extract(SIMULATE)} +
 * {@code network.extract(MODULATE)} round-trip per slot, costing N × O(grid) AE2 inventory
 * walks; the redesigned path collapses that to O(unique-keys) walks.
 *
 * <h3>Pass 1 — single per-slot loop</h3>
 * <p>
 * Reads each slot's current resource and amount exactly once, attempts the rare
 * "stray content push" (each push opens its own short-lived transaction), then re-reads
 * only if the push moved data. Then records a pull intent into compact parallel arrays
 * without making any network call.
 *
 * <h3>Pass 2 — batched reserve / extract / distribute</h3>
 * <p>
 * Inside a single shared {@link Transaction}, reserve each intent's amount into local
 * via {@code local.insert}. Reservation failure short-circuits the slot before any
 * network call, preserving the invariant that <strong>a local handler that refuses
 * insertion does not cause the network to be debited</strong> — important for stocking
 * and capacity-limited handlers. After reservations are gathered, group them by AEKey
 * and issue one {@code network.extract(MODULATE)} per unique key. Distribute the
 * extracted pool back to intents in slot order, shrinking any over-reservation via
 * {@code local.extract} in the same shared transaction. Any unclaimed pool is refunded
 * back to the network before the transaction commits.
 *
 * <h3>Path B — two-stage off-ticker hand-off (added round 3)</h3>
 * <p>
 * The total wall-clock cost of {@code sync()} is dominated by Pass 2's
 * {@code Transaction.openRoot()} (~600 ns) plus the per-unique-key
 * {@code network.extractDirect}. To remove that cost from Topo's
 * {@code MachineTicker.runProfiledTick} accounting we split the entry point in two:
 * {@link #plan(ResourceHandler, List, AeResourceKeyResolver)} performs Pass 1 only and
 * returns a {@link PendingPlan} value object; {@link #execute(PendingPlan, ResourceHandler,
 * AeNetworkAccessor)} performs Pass 2 against that plan.
 *
 * <p>
 * The sync trait's per-40-tick {@code tick()} method therefore only runs {@code plan()}
 * (which makes <em>zero</em> network calls and opens <em>zero</em> transactions). The
 * pending plan is then handed off to AE2's own {@code IGridTickable.tickingRequest}
 * callback (next AE network tick, which is <strong>not</strong> profiled by Topo) where
 * Pass 2 actually moves resources. The work is not eliminated, only relocated to the
 * grid-tick side of the bookkeeping divide — server-tick total CPU is unchanged.
 *
 * <p>
 * The original {@link #sync(ResourceHandler, List, AeNetworkAccessor)} entry point is
 * preserved unchanged for unit tests and any caller that wants synchronous semantics
 * (e.g. tests that need the network mutation to be visible in the same call). It is now
 * implemented as {@code plan() → execute()}.
 */
public final class AeConfiguredResourceSync {

    private AeConfiguredResourceSync() {}

    public record Result(long pulled, long pushed, int skippedSlots) {}

    /**
     * Pass 1 output passed from the sync trait's tick to the deferred grid-tick execution.
     *
     * <p>
     * Holds the parallel-array form of decided pull intents plus the running counters from
     * the (already-completed) per-slot push phase. The arrays are sized exactly to
     * {@code intentCount} so {@code execute()} can iterate without bounds-check overhead.
     *
     * <p>
     * A {@link #empty()} value object is used when Pass 1 has nothing to do (offline grid,
     * no slots, every slot already satisfied) so callers can store a non-null pending field
     * and treat "empty plan" as "no work".
     */
    public static final class PendingPlan<R extends Resource> {

        private static final PendingPlan<?> EMPTY = new PendingPlan<>(0, new int[0], new AEKey[0],
                new Resource[0], new long[0], 0L, 0);

        final int intentCount;
        final int[] intentSlot;
        final AEKey[] intentKey;
        final R[] intentResource;
        final long[] intentDesired;
        /** Push side-effect total accumulated during Pass 1 (already committed via per-slot tx). */
        final long pushedTotal;
        /** Slots that pushed nothing despite having excess — already counted, propagate as-is. */
        final int skippedFromPush;

        private PendingPlan(int intentCount, int[] intentSlot, AEKey[] intentKey, R[] intentResource,
                            long[] intentDesired, long pushedTotal, int skippedFromPush) {
            this.intentCount = intentCount;
            this.intentSlot = intentSlot;
            this.intentKey = intentKey;
            this.intentResource = intentResource;
            this.intentDesired = intentDesired;
            this.pushedTotal = pushedTotal;
            this.skippedFromPush = skippedFromPush;
        }

        @SuppressWarnings("unchecked")
        public static <R extends Resource> PendingPlan<R> empty() {
            return (PendingPlan<R>) EMPTY;
        }

        /**
         * Builds a PendingPlan that <strong>references</strong> the caller's parallel arrays
         * (no defensive copy). The caller MUST guarantee the arrays are not mutated between
         * plan-build and {@link #execute}; in the sync-trait fast path this is enforced by the
         * server-tick / grid-tick serialisation (next fastPlan blocks while a plan is pending).
         *
         * <p>
         * Used by {@link AeResourceInputSync#fastPlan()} to avoid the per-tick allocation
         * of intent arrays (≥ 4 × {@code slots} ints/refs/longs on every Pass-1 build).
         */
        static <R extends Resource> PendingPlan<R> fromTraitArrays(
                                                                   int intentCount,
                                                                   int[] intentSlot,
                                                                   AEKey[] intentKey,
                                                                   R[] intentResource,
                                                                   long[] intentDesired,
                                                                   long pushedTotal,
                                                                   int skippedFromPush) {
            return new PendingPlan<>(intentCount, intentSlot, intentKey, intentResource,
                    intentDesired, pushedTotal, skippedFromPush);
        }

        public boolean isEmpty() {
            return intentCount == 0 && pushedTotal == 0L && skippedFromPush == 0;
        }

        /** True when there is no Pass-2 work to do (no pull intents). Push counters may still carry data. */
        public boolean hasNoIntents() {
            return intentCount == 0;
        }
    }

    public static <R extends Resource> Result sync(
                                                   ResourceHandler<R> local,
                                                   List<AeConfigSlot<R>> configSlots,
                                                   AeNetworkAccessor network) {
        return sync(local, configSlots, network, AeResourceKeyResolver.defaultResolver());
    }

    /**
     * Synchronous entry point — runs {@link #plan(ResourceHandler, List, AeResourceKeyResolver,
     * AeNetworkAccessor)} then {@link #execute(PendingPlan, ResourceHandler, AeNetworkAccessor)}
     * in the same call. Kept for unit tests and any caller that needs same-tick semantics.
     */
    public static <R extends Resource> Result sync(
                                                   ResourceHandler<R> local,
                                                   List<AeConfigSlot<R>> configSlots,
                                                   AeNetworkAccessor network,
                                                   AeResourceKeyResolver<R> keyResolver) {
        PendingPlan<R> plan = plan(local, configSlots, keyResolver, network);
        return execute(plan, local, network);
    }

    /**
     * Pass 1 only: decide which slots want what and how much, perform per-slot push, return a
     * {@link PendingPlan} for the caller to hand off to a later executor.
     *
     * <p>
     * <strong>Network-call profile:</strong> the push branch opens its own short-lived
     * transaction and may call {@code network.insert}; this is the rare excess-content path.
     * The pull-intent gathering itself makes zero network calls.
     *
     * <p>
     * {@code network} is used <em>only</em> by the push branch and the {@link
     * AeNetworkAccessor#isOnline()} short-circuit. The pull-intent path does not consult it.
     * A null/offline {@code network} therefore yields {@link PendingPlan#empty()}.
     */
    public static <R extends Resource> PendingPlan<R> plan(
                                                           ResourceHandler<R> local,
                                                           List<AeConfigSlot<R>> configSlots,
                                                           AeResourceKeyResolver<R> keyResolver,
                                                           AeNetworkAccessor network) {
        if (local == null || configSlots == null || network == null || keyResolver == null || !network.isOnline()) {
            return PendingPlan.empty();
        }

        AeResourceKeyResolver<R> cachedResolver = AeResourceKeyCache.cached(keyResolver);
        int slots = Math.min(local.size(), configSlots.size());

        long pushedTotal = 0L;
        int skippedFromPush = 0;
        int intentCount = 0;
        int[] intentSlot = new int[slots];
        AEKey[] intentKey = new AEKey[slots];
        @SuppressWarnings("unchecked")
        R[] intentResource = (R[]) new Resource[slots];
        long[] intentDesired = new long[slots];

        for (int slot = 0; slot < slots; slot++) {
            AeConfigSlot<R> config = configSlots.get(slot);
            R configResource = config.resource();
            long targetAmount = config.targetAmount();
            // configured() inlined: resource != null && !resource.isEmpty() && targetAmount > 0L
            boolean configured = configResource != null && !configResource.isEmpty() && targetAmount > 0L;

            R current = local.getResource(slot);
            long amount = local.getAmountAsLong(slot);

            // Single equals() between current and configResource — used to gate push, deficit
            // semantics, and the "stray content" branch. Previously AeConfigSlot's excess /
            // deficit / accepts each ran their own equals, costing ~3× for ItemResource which
            // has a relatively heavy components comparison.
            // "empty" mirrors AeConfigSlot.excess's three-way guard: null resource, empty
            // resource, or non-positive amount all count as empty.
            boolean currentEmpty = current == null || current.isEmpty() || amount <= 0L;
            boolean matches = configured && !currentEmpty && configResource.equals(current);

            // excess: only stray content (mismatched local) or over-target (configured & matching).
            long excess;
            if (currentEmpty) {
                excess = 0L;
            } else if (!configured || !matches) {
                // mismatched stray → push all of it
                excess = amount;
            } else {
                excess = Math.max(0L, amount - targetAmount);
            }

            if (excess > 0L) {
                long pushed = pushToNetwork(local, slot, current, excess, network, cachedResolver);
                if (pushed > 0L) {
                    pushedTotal = saturatingAdd(pushedTotal, pushed);
                } else {
                    skippedFromPush++;
                }
                // State after push has changed — re-read.
                current = local.getResource(slot);
                amount = local.getAmountAsLong(slot);
                currentEmpty = current == null || current.isEmpty() || amount <= 0L;
                matches = configured && !currentEmpty && configResource.equals(current);
            }

            // After push, only pull a configured resource into an empty or matching slot.
            if (!configured) continue;
            if (!currentEmpty && !matches) continue;
            // configured == true here, so configResource is non-null & non-empty.

            // deficit semantics: if matching, target - amount; if empty, target.
            long deficit = currentEmpty ? targetAmount : Math.max(0L, targetAmount - Math.max(0L, amount));
            if (deficit <= 0L) continue;

            AEKey key = cachedResolver.toKey(configResource);
            if (key == null) continue;

            long capacity = local.getCapacityAsLong(slot, configResource);
            long room = amount >= capacity ? 0L : capacity - Math.max(0L, amount);
            if (room <= 0L) continue;
            long toPull = Math.min(deficit, room);

            intentSlot[intentCount] = slot;
            intentKey[intentCount] = key;
            intentResource[intentCount] = configResource;
            intentDesired[intentCount] = toPull;
            intentCount++;
        }

        return new PendingPlan<>(intentCount, intentSlot, intentKey, intentResource, intentDesired,
                pushedTotal, skippedFromPush);
    }

    /**
     * Convenience overload — uses {@link AeResourceKeyResolver#defaultResolver()}.
     */
    public static <R extends Resource> PendingPlan<R> plan(
                                                           ResourceHandler<R> local,
                                                           List<AeConfigSlot<R>> configSlots,
                                                           AeNetworkAccessor network) {
        return plan(local, configSlots, AeResourceKeyResolver.defaultResolver(), network);
    }

    /**
     * Pass 2 only: reserve + batched network extract + distribute against a previously-built
     * {@link PendingPlan}.
     *
     * <p>
     * If the plan has no pull intents (only push counters), no transaction is opened.
     *
     * <p>
     * If the {@code network} has since gone offline between plan-build and execute (which
     * happens when the grid is unloaded between the sync trait's tick and AE2's grid tick),
     * the pull is dropped — push side-effects from the plan are still surfaced in the
     * returned {@link Result}.
     */
    public static <R extends Resource> Result execute(
                                                      PendingPlan<R> plan,
                                                      ResourceHandler<R> local,
                                                      AeNetworkAccessor network) {
        if (plan == null || plan.isEmpty()) {
            return new Result(0L, 0L, 0);
        }
        if (plan.hasNoIntents()) {
            return new Result(0L, plan.pushedTotal, plan.skippedFromPush);
        }
        if (local == null || network == null || !network.isOnline()) {
            // Grid disappeared between plan and execute. Surface push counters from Pass 1 — they
            // were committed in plan() — and discard the pull intents.
            return new Result(0L, plan.pushedTotal, plan.skippedFromPush + plan.intentCount);
        }

        final int intentCount = plan.intentCount;
        final int[] intentSlot = plan.intentSlot;
        final AEKey[] intentKey = plan.intentKey;
        final R[] intentResource = plan.intentResource;
        final long[] intentDesired = plan.intentDesired;

        long pulledTotal = 0L;
        int skippedSlots = plan.skippedFromPush;
        long[] intentReserved = new long[intentCount];
        AEKey[] uniqueKeys = new AEKey[intentCount];
        long[] uniqueReserved = new long[intentCount];
        long[] uniquePool = new long[intentCount];
        int uniqueCount = 0;

        try (Transaction tx = Transaction.openRoot()) {
            // Reserve per slot; drop slots whose local handler refuses outright.
            boolean anyReservation = false;
            for (int i = 0; i < intentCount; i++) {
                long reserved = ResourceHandlerLongOps.insert(
                        local, intentSlot[i], intentResource[i], intentDesired[i], tx);
                intentReserved[i] = reserved;
                if (reserved <= 0L) {
                    skippedSlots++;
                    continue;
                }
                anyReservation = true;

                int u = findUniqueKeyIndex(uniqueKeys, uniqueCount, intentKey[i]);
                if (u >= 0) {
                    uniqueReserved[u] = saturatingAdd(uniqueReserved[u], reserved);
                } else {
                    uniqueKeys[uniqueCount] = intentKey[i];
                    uniqueReserved[uniqueCount] = reserved;
                    uniqueCount++;
                }
            }

            if (!anyReservation) {
                // tx auto-rolls back unreservable inserts; no network calls were made.
                return new Result(0L, plan.pushedTotal, skippedSlots);
            }

            // One MODULATE call per unique AEKey — this is the bug fix's main payoff.
            // extractDirect collapses StorageHelper.poweredExtraction's 4-call sequence
            // (inv.extract SIM + energy SIM + energy MOD + inv.extract MOD) into 2-3 calls
            // by skipping the SIMULATE pre-walk; ~1.4 μs saved per unique key per sync.
            for (int u = 0; u < uniqueCount; u++) {
                long cached = network.availableCached(uniqueKeys[u]);
                if (cached <= 0L) {
                    uniquePool[u] = 0L;
                    continue;
                }
                long toExtract = Math.min(uniqueReserved[u], cached);
                uniquePool[u] = network.extractDirect(uniqueKeys[u], toExtract);
                if (uniquePool[u] < 0L || uniquePool[u] > toExtract) {
                    throw new IllegalStateException("AE network extracted " + uniquePool[u] + " for a request of " + toExtract + " " + uniqueKeys[u]);
                }
            }

            // Distribute pool to intents in slot order. If an intent's share is less than its
            // reservation, shrink the local reservation in the same transaction so net flow
            // balances. Skipped slots accounted toward skippedSlots only when they reserved
            // but got zero (excluded from the "no reservation" return-early branch above).
            for (int i = 0; i < intentCount; i++) {
                if (intentReserved[i] <= 0L) continue;
                int keyIdx = findUniqueKeyIndex(uniqueKeys, uniqueCount, intentKey[i]);
                long fromPool = Math.min(intentReserved[i], uniquePool[keyIdx]);
                if (fromPool < intentReserved[i]) {
                    long surplus = intentReserved[i] - fromPool;
                    long removed = ResourceHandlerLongOps.extract(
                            local, intentSlot[i], intentResource[i], surplus, tx);
                    if (removed < surplus) {
                        throw new IllegalStateException("Unable to shrink reservation in slot " + intentSlot[i] + " by " + surplus + "; removed only " + removed);
                    }
                }
                if (fromPool > 0L) {
                    uniquePool[keyIdx] -= fromPool;
                    pulledTotal = saturatingAdd(pulledTotal, fromPool);
                } else {
                    skippedSlots++;
                }
            }

            // Pool over-extraction: network gave us more than any slot can use. Refund the
            // surplus before commit so resources are conserved.
            for (int u = 0; u < uniqueCount; u++) {
                if (uniquePool[u] > 0L) {
                    long refunded = network.insert(uniqueKeys[u], uniquePool[u], Actionable.MODULATE);
                    if (refunded != uniquePool[u]) {
                        throw new IllegalStateException("Pool drift: unable to refund " + uniquePool[u] + " of " + uniqueKeys[u] + "; refunded only " + refunded);
                    }
                }
            }

            tx.commit();
        }

        return new Result(pulledTotal, plan.pushedTotal, skippedSlots);
    }

    private static int findUniqueKeyIndex(AEKey[] uniqueKeys, int uniqueCount, AEKey key) {
        for (int u = 0; u < uniqueCount; u++) {
            if (uniqueKeys[u].equals(key)) return u;
        }
        return -1;
    }

    static <R extends Resource> long pushToNetwork(
                                                   ResourceHandler<R> local,
                                                   int slot,
                                                   R resource,
                                                   long requested,
                                                   AeNetworkAccessor network,
                                                   AeResourceKeyResolver<R> keyResolver) {
        if (resource == null || resource.isEmpty() || requested <= 0L) return 0L;
        AEKey key = keyResolver.toKey(resource);
        if (key == null) return 0L;

        // No `insert(SIMULATE)` pre-check: AE2's `MEStorage.insert` walks all mounted cells
        // even for SIMULATE, so the probe is just as expensive as the real MODULATE we're
        // about to do anyway. Attempt MODULATE directly with the full `requested` amount —
        // if the network rejects part or all of it, the refund block restores the local
        // reservation. The cost of a wasted Transaction.openRoot when the network is full is
        // ~120 ns; the saved O(grid) SIMULATE call is multiple μs.
        long extracted;
        long inserted;
        try (Transaction tx = Transaction.openRoot()) {
            extracted = ResourceHandlerLongOps.extract(local, slot, resource, requested, tx);
            if (extracted <= 0L) return 0L;
            inserted = network.insert(key, extracted, Actionable.MODULATE);
            if (inserted < 0L || inserted > extracted) {
                if (inserted > 0L) {
                    long compensated = network.extract(key, inserted, Actionable.MODULATE);
                    if (compensated != inserted) {
                        throw new IllegalStateException("Invalid AE insert result " + inserted + " and compensation removed only " + compensated);
                    }
                }
                throw new IllegalStateException("AE network inserted " + inserted + " for a request of " + extracted);
            }
            long refund = extracted - inserted;
            if (refund > 0L) {
                long restored = ResourceHandlerLongOps.insert(local, slot, resource, refund, tx);
                if (restored < refund) {
                    long compensated = inserted <= 0L ? 0L : network.extract(key, inserted, Actionable.MODULATE);
                    if (compensated != inserted) {
                        throw new IllegalStateException("Unable to restore local ME cache refund " + refund + " or compensate inserted AE amount " + inserted);
                    }
                    return 0L;
                }
            }
            tx.commit();
        }
        return inserted;
    }

    static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }
}
