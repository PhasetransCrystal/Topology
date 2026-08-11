package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.resource.DirectSlotResourceAccess;
import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.mojang.logging.LogUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, read-only-derived insertion plan for one AE processing-pattern push.
 *
 * <p>
 * Inputs are aggregated before slots are assigned, so repeated keys share existing stacks and
 * all distinct resources compete for empty slots in one calculation. Topo's stack-backed buffers
 * commit by exact slot replacement. Opaque third-party handlers use a transaction only to commit
 * an already-complete plan; transactions are never used for capacity probing.
 */
final class AePatternInputPlan {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Non-uniform slot assignment is exponential and is kept behind this hard bound. */
    private static final int EXACT_EMPTY_SLOT_LIMIT = 10;
    private static final int MAX_INPUT_ENTRIES = 256;
    private static final int MAX_DISTINCT_RESOURCES = 128;
    private static final int MAX_HANDLER_SLOTS = 512;
    private static final ThreadLocal<Boolean> DIRECT_COMMIT_ACTIVE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final List<SlotChange<?>> changes;

    private AePatternInputPlan(List<SlotChange<?>> changes) {
        this.changes = changes;
    }

    static Collector collector() {
        return new Collector();
    }

    static @Nullable AePatternInputPlan create(
                                               List<Input> inputs,
                                               @Nullable ResourceHandler<ItemResource> itemHandler,
                                               @Nullable ResourceHandler<FluidResource> fluidHandler) {
        if (inputs.size() > MAX_INPUT_ENTRIES) {
            return null;
        }
        LinkedHashMap<ItemResource, Long> itemAmounts = new LinkedHashMap<>();
        LinkedHashMap<FluidResource, Long> fluidAmounts = new LinkedHashMap<>();
        for (Input input : inputs) {
            long amount = input.amount();
            if (amount <= 0L) {
                continue;
            }
            AEKey key = input.key();
            if (key instanceof AEItemKey itemKey) {
                if (!addAmount(itemAmounts, itemKey.toResource(), amount)) {
                    return null;
                }
            } else if (key instanceof AEFluidKey fluidKey) {
                if (!addAmount(fluidAmounts, fluidKey.toResource(), amount)) {
                    return null;
                }
            } else {
                return null;
            }
        }
        if (itemAmounts.size() > MAX_DISTINCT_RESOURCES || fluidAmounts.size() > MAX_DISTINCT_RESOURCES) {
            return null;
        }

        ArrayList<SlotChange<?>> changes = new ArrayList<>();
        if (!planHandler(itemHandler, itemAmounts, changes) || !planHandler(fluidHandler, fluidAmounts, changes)) {
            return null;
        }
        return new AePatternInputPlan(List.copyOf(changes));
    }

    boolean commit() {
        if (DIRECT_COMMIT_ACTIVE.get()) {
            return false;
        }
        boolean direct = true;
        for (SlotChange<?> change : changes) {
            if (!change.isStillCurrent()) {
                return false;
            }
            direct &= change.canCommitDirectly();
        }
        if (changes.isEmpty()) {
            return true;
        }
        if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
            return false;
        }
        if (direct) {
            DIRECT_COMMIT_ACTIVE.set(Boolean.TRUE);
            try {
                commitDirectly();
                return true;
            } finally {
                DIRECT_COMMIT_ACTIVE.set(Boolean.FALSE);
            }
        }
        return commitOpaqueHandlers();
    }

    private void commitDirectly() {
        int applied = 0;
        try {
            for (SlotChange<?> change : changes) {
                applied++;
                change.applyDirectly();
            }
        } catch (RuntimeException | Error failure) {
            for (int index = applied - 1; index >= 0; index--) {
                try {
                    changes.get(index).restoreDirectly();
                } catch (RuntimeException | Error rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        }
    }

    private boolean commitOpaqueHandlers() {
        // Feasibility and slot assignment are complete. This transaction is solely the atomic
        // commit boundary required by handlers that do not expose direct stack replacement.
        try (Transaction transaction = Transaction.openRoot()) {
            for (SlotChange<?> change : changes) {
                if (!change.insert(transaction)) {
                    return false;
                }
            }
            try {
                transaction.commit();
            } catch (RuntimeException notificationFailure) {
                // NeoForge runs root-commit callbacks only after every journal has discarded its
                // rollback snapshot. The resource state is committed at this point; reporting a
                // failed push would let AE retry and duplicate the inputs. Publish the staged
                // counters and surface the callback failure through the log instead.
                LOGGER.error(
                        "AE pattern inputs committed, but a handler root-commit notification failed",
                        notificationFailure);
            }
            return true;
        }
    }

    private static <R extends Resource> boolean addAmount(Map<R, Long> amounts, R resource, long amount) {
        if (resource.isEmpty()) {
            return false;
        }
        long previous = amounts.getOrDefault(resource, 0L);
        if (Long.MAX_VALUE - previous < amount) {
            return false;
        }
        amounts.put(resource, previous + amount);
        return true;
    }

    private static <R extends Resource> boolean planHandler(
                                                            @Nullable ResourceHandler<R> handler,
                                                            LinkedHashMap<R, Long> amounts,
                                                            List<SlotChange<?>> changes) {
        if (amounts.isEmpty()) {
            return true;
        }
        if (handler == null) {
            return false;
        }

        int size = handler.size();
        if (size < 0 || size > MAX_HANDLER_SLOTS) {
            return false;
        }
        Object[] originalResources = new Object[size];
        Object[] plannedResources = new Object[size];
        long[] originalAmounts = new long[size];
        long[] plannedAmounts = new long[size];
        for (int slot = 0; slot < size; slot++) {
            R resource = Objects.requireNonNull(handler.getResource(slot), "handler resource");
            long amount = handler.getAmountAsLong(slot);
            if (amount < 0L || (resource.isEmpty() && amount != 0L)) {
                return false;
            }
            originalResources[slot] = resource;
            plannedResources[slot] = resource;
            originalAmounts[slot] = amount;
            plannedAmounts[slot] = amount;
        }

        LinkedHashMap<R, Long> remaining = new LinkedHashMap<>(amounts);
        for (Map.Entry<R, Long> entry : remaining.entrySet()) {
            entry.setValue(fillMatchingSlots(
                    handler,
                    entry.getKey(),
                    entry.getValue(),
                    plannedResources,
                    plannedAmounts));
        }
        if (!assignEmptySlots(handler, remaining, plannedResources, plannedAmounts)) {
            return false;
        }

        for (int slot = 0; slot < size; slot++) {
            R before = resourceAt(originalResources, slot);
            R after = resourceAt(plannedResources, slot);
            if (originalAmounts[slot] != plannedAmounts[slot] || !before.equals(after)) {
                changes.add(new SlotChange<>(
                        handler,
                        slot,
                        before,
                        originalAmounts[slot],
                        after,
                        plannedAmounts[slot]));
            }
        }
        return true;
    }

    private static <R extends Resource> long fillMatchingSlots(
                                                               ResourceHandler<R> handler,
                                                               R resource,
                                                               long needed,
                                                               Object[] plannedResources,
                                                               long[] plannedAmounts) {
        for (int slot = 0; slot < plannedResources.length && needed > 0L; slot++) {
            if (resource.equals(resourceAt(plannedResources, slot))) {
                needed = fillSlot(handler, slot, resource, needed, plannedResources, plannedAmounts);
            }
        }
        return needed;
    }

    private static <R extends Resource> boolean assignEmptySlots(
                                                                 ResourceHandler<R> handler,
                                                                 LinkedHashMap<R, Long> remaining,
                                                                 Object[] plannedResources,
                                                                 long[] plannedAmounts) {
        ArrayList<Demand<R>> demands = new ArrayList<>();
        int emptySlots = 0;
        for (Object resource : plannedResources) {
            if (((Resource) resource).isEmpty()) {
                emptySlots++;
            }
        }
        for (Map.Entry<R, Long> entry : remaining.entrySet()) {
            if (entry.getValue() <= 0L) {
                continue;
            }
            Demand<R> demand = createDemand(
                    handler, entry.getKey(), entry.getValue(), plannedResources);
            if (demand == null) {
                return false;
            }
            demands.add(demand);
        }
        if (demands.isEmpty()) {
            return true;
        }
        if (emptySlots < demands.size()) {
            return false;
        }
        demands.sort((left, right) -> {
            int candidates = Integer.compare(left.slots().length, right.slots().length);
            return candidates != 0 ? candidates : Long.compare(left.spareCapacity(), right.spareCapacity());
        });
        if (demands.size() == 1) {
            return assignSingleDemand(demands.getFirst(), plannedResources, plannedAmounts);
        }
        if (hasUniformCapacityPerResource(demands)) {
            return assignWithMatching(demands, plannedResources, plannedAmounts);
        }
        if (emptySlots > EXACT_EMPTY_SLOT_LIMIT) {
            return false;
        }
        int[] slotBits = new int[plannedResources.length];
        Arrays.fill(slotBits, -1);
        int bit = 0;
        for (int slot = 0; slot < plannedResources.length; slot++) {
            if (resourceAt(plannedResources, slot).isEmpty()) {
                slotBits[slot] = bit++;
            }
        }
        boolean[][] failed = new boolean[demands.size()][1 << emptySlots];
        return assignExactly(demands, 0, plannedResources, plannedAmounts, slotBits, failed);
    }

    private static <R extends Resource> boolean assignSingleDemand(
                                                                   Demand<R> demand,
                                                                   Object[] plannedResources,
                                                                   long[] plannedAmounts) {
        long remaining = demand.amount();
        for (int index = 0; index < demand.slots().length && remaining > 0L; index++) {
            int slot = demand.slots()[index];
            long inserted = Math.min(remaining, demand.capacities()[index]);
            plannedResources[slot] = demand.resource();
            plannedAmounts[slot] = inserted;
            remaining -= inserted;
        }
        return remaining == 0L;
    }

    private static <R extends Resource> @Nullable Demand<R> createDemand(
                                                                         ResourceHandler<R> handler,
                                                                         R resource,
                                                                         long amount,
                                                                         Object[] plannedResources) {
        int[] slots = new int[plannedResources.length];
        long[] capacities = new long[plannedResources.length];
        int count = 0;
        long totalCapacity = 0L;
        for (int slot = 0; slot < plannedResources.length; slot++) {
            if (!resourceAt(plannedResources, slot).isEmpty() || !handler.isValid(slot, resource)) {
                continue;
            }
            long capacity = Math.max(0L, handler.getCapacityAsLong(slot, resource));
            if (capacity > 0L) {
                slots[count] = slot;
                capacities[count] = capacity;
                count++;
                totalCapacity = saturatingAdd(totalCapacity, capacity);
            }
        }
        if (totalCapacity < amount) {
            return null;
        }
        return new Demand<>(
                resource,
                amount,
                Arrays.copyOf(slots, count),
                Arrays.copyOf(capacities, count),
                totalCapacity - amount);
    }

    private static <R extends Resource> boolean assignExactly(
                                                              List<Demand<R>> demands,
                                                              int demandIndex,
                                                              Object[] plannedResources,
                                                              long[] plannedAmounts,
                                                              int[] slotBits,
                                                              boolean[][] failed) {
        if (demandIndex == demands.size()) {
            return true;
        }
        int usedMask = usedSlotMask(plannedResources, slotBits);
        if (failed[demandIndex][usedMask]) {
            return false;
        }
        boolean assigned = assignCandidateCombination(
                demands,
                demandIndex,
                0,
                demands.get(demandIndex).amount(),
                plannedResources,
                plannedAmounts,
                slotBits,
                failed);
        if (!assigned) {
            failed[demandIndex][usedMask] = true;
        }
        return assigned;
    }

    private static <R extends Resource> boolean assignCandidateCombination(
                                                                           List<Demand<R>> demands,
                                                                           int demandIndex,
                                                                           int candidateIndex,
                                                                           long needed,
                                                                           Object[] plannedResources,
                                                                           long[] plannedAmounts,
                                                                           int[] slotBits,
                                                                           boolean[][] failed) {
        if (needed == 0L) {
            return assignExactly(
                    demands,
                    demandIndex + 1,
                    plannedResources,
                    plannedAmounts,
                    slotBits,
                    failed);
        }
        Demand<R> demand = demands.get(demandIndex);
        if (candidateIndex == demand.slots().length || availableCapacity(demand, candidateIndex, plannedResources) < needed) {
            return false;
        }

        int slot = demand.slots()[candidateIndex];
        if (resourceAt(plannedResources, slot).isEmpty()) {
            R emptyResource = resourceAt(plannedResources, slot);
            long emptyAmount = plannedAmounts[slot];
            long inserted = Math.min(needed, demand.capacities()[candidateIndex]);
            plannedResources[slot] = demand.resource();
            plannedAmounts[slot] = inserted;
            if (assignCandidateCombination(
                    demands,
                    demandIndex,
                    candidateIndex + 1,
                    needed - inserted,
                    plannedResources,
                    plannedAmounts,
                    slotBits,
                    failed)) {
                return true;
            }
            plannedResources[slot] = emptyResource;
            plannedAmounts[slot] = emptyAmount;
        }
        return assignCandidateCombination(
                demands,
                demandIndex,
                candidateIndex + 1,
                needed,
                plannedResources,
                plannedAmounts,
                slotBits,
                failed);
    }

    private static int usedSlotMask(Object[] plannedResources, int[] slotBits) {
        int mask = 0;
        for (int slot = 0; slot < slotBits.length; slot++) {
            int bit = slotBits[slot];
            if (bit >= 0 && !((Resource) plannedResources[slot]).isEmpty()) {
                mask |= 1 << bit;
            }
        }
        return mask;
    }

    private static <R extends Resource> long availableCapacity(
                                                               Demand<R> demand,
                                                               int fromCandidate,
                                                               Object[] plannedResources) {
        long capacity = 0L;
        for (int index = fromCandidate; index < demand.slots().length; index++) {
            if (resourceAt(plannedResources, demand.slots()[index]).isEmpty()) {
                capacity = saturatingAdd(capacity, demand.capacities()[index]);
            }
        }
        return capacity;
    }

    private static boolean hasUniformCapacityPerResource(List<? extends Demand<?>> demands) {
        for (Demand<?> demand : demands) {
            long capacity = demand.capacities()[0];
            for (int index = 1; index < demand.capacities().length; index++) {
                if (demand.capacities()[index] != capacity) {
                    return false;
                }
            }
        }
        return true;
    }

    private static <R extends Resource> boolean assignWithMatching(
                                                                   List<Demand<R>> demands,
                                                                   Object[] plannedResources,
                                                                   long[] plannedAmounts) {
        int totalUnits = 0;
        for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
            Demand<R> demand = demands.get(demandIndex);
            long capacity = demand.capacities()[0];
            long units = (demand.amount() - 1L) / capacity + 1L;
            if (units > demand.slots().length || units > MAX_HANDLER_SLOTS - totalUnits) {
                return false;
            }
            totalUnits += (int) units;
        }
        int[] unitDemands = new int[totalUnits];
        int nextUnit = 0;
        for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
            Demand<R> demand = demands.get(demandIndex);
            int units = (int) ((demand.amount() - 1L) / demand.capacities()[0] + 1L);
            Arrays.fill(unitDemands, nextUnit, nextUnit + units, demandIndex);
            nextUnit += units;
        }

        int[] slotOwner = new int[plannedResources.length];
        int[] unitSlot = new int[unitDemands.length];
        boolean[] visitedSlots = new boolean[plannedResources.length];
        Arrays.fill(slotOwner, -1);
        Arrays.fill(unitSlot, -1);
        for (int unit = 0; unit < unitDemands.length; unit++) {
            Arrays.fill(visitedSlots, false);
            if (!augmentMatching(
                    unit,
                    unitDemands,
                    demands,
                    slotOwner,
                    unitSlot,
                    visitedSlots)) {
                return false;
            }
        }

        long[] remaining = new long[demands.size()];
        for (int index = 0; index < demands.size(); index++) {
            remaining[index] = demands.get(index).amount();
        }
        for (int unit = 0; unit < unitDemands.length; unit++) {
            int demandIndex = unitDemands[unit];
            Demand<R> demand = demands.get(demandIndex);
            int slot = unitSlot[unit];
            long inserted = Math.min(remaining[demandIndex], demand.capacities()[0]);
            plannedResources[slot] = demand.resource();
            plannedAmounts[slot] = inserted;
            remaining[demandIndex] -= inserted;
        }
        return true;
    }

    private static <R extends Resource> boolean augmentMatching(
                                                                int unit,
                                                                int[] unitDemands,
                                                                List<Demand<R>> demands,
                                                                int[] slotOwner,
                                                                int[] unitSlot,
                                                                boolean[] visitedSlots) {
        Demand<R> demand = demands.get(unitDemands[unit]);
        for (int slot : demand.slots()) {
            if (visitedSlots[slot]) {
                continue;
            }
            visitedSlots[slot] = true;
            int previousOwner = slotOwner[slot];
            if (previousOwner < 0 || augmentMatching(
                    previousOwner,
                    unitDemands,
                    demands,
                    slotOwner,
                    unitSlot,
                    visitedSlots)) {
                slotOwner[slot] = unit;
                unitSlot[unit] = slot;
                return true;
            }
        }
        return false;
    }

    private static <R extends Resource> long fillSlot(
                                                      ResourceHandler<R> handler,
                                                      int slot,
                                                      R resource,
                                                      long needed,
                                                      Object[] plannedResources,
                                                      long[] plannedAmounts) {
        if (!handler.isValid(slot, resource)) {
            return needed;
        }
        long current = plannedAmounts[slot];
        long capacity = handler.getCapacityAsLong(slot, resource);
        if (capacity <= current) {
            return needed;
        }
        long inserted = Math.min(needed, capacity - current);
        plannedResources[slot] = resource;
        plannedAmounts[slot] = current + inserted;
        return needed - inserted;
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    @SuppressWarnings("unchecked")
    private static <R extends Resource> R resourceAt(Object[] resources, int slot) {
        return (R) resources[slot];
    }

    record Input(AEKey key, long amount) {

        Input {
            Objects.requireNonNull(key, "input key");
        }
    }

    static final class Collector {

        private final ArrayList<Input> inputs = new ArrayList<>();
        private boolean overflow;

        void add(AEKey key, long amount) {
            if (amount <= 0L || overflow) {
                return;
            }
            if (inputs.size() == MAX_INPUT_ENTRIES) {
                overflow = true;
                return;
            }
            inputs.add(new Input(key, amount));
        }

        @Nullable
        AePatternInputPlan plan(
                                @Nullable ResourceHandler<ItemResource> itemHandler,
                                @Nullable ResourceHandler<FluidResource> fluidHandler) {
            return overflow ? null : create(inputs, itemHandler, fluidHandler);
        }
    }

    private record Demand<R extends Resource>(
                                              R resource,
                                              long amount,
                                              int[] slots,
                                              long[] capacities,
                                              long spareCapacity) {}

    private static final class SlotChange<R extends Resource> {

        private final ResourceHandler<R> handler;
        private final int slot;
        private final R before;
        private final long beforeAmount;
        private final R after;
        private final long afterAmount;

        private SlotChange(
                           ResourceHandler<R> handler,
                           int slot,
                           R before,
                           long beforeAmount,
                           R after,
                           long afterAmount) {
            this.handler = handler;
            this.slot = slot;
            this.before = before;
            this.beforeAmount = beforeAmount;
            this.after = after;
            this.afterAmount = afterAmount;
        }

        private boolean isStillCurrent() {
            return slot < handler.size() && before.equals(handler.getResource(slot)) && beforeAmount == handler.getAmountAsLong(slot);
        }

        private boolean canCommitDirectly() {
            if (!(handler instanceof DirectSlotResourceAccess<?> direct)) {
                return false;
            }
            return direct.supportsDirectSetAmount(beforeAmount) && direct.supportsDirectSetAmount(afterAmount);
        }

        private void applyDirectly() {
            directHandler().directSet(slot, after, afterAmount);
        }

        private void restoreDirectly() {
            directHandler().directSet(slot, before, beforeAmount);
        }

        private boolean insert(TransactionContext transaction) {
            long requested = afterAmount - beforeAmount;
            return ResourceHandlerLongOps.insert(handler, slot, after, requested, transaction) == requested;
        }

        @SuppressWarnings("unchecked")
        private DirectSlotResourceAccess<R> directHandler() {
            return (DirectSlotResourceAccess<R>) handler;
        }
    }
}
