package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.resource.LongResourceHandler;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Objects;

/**
 * Slot-based, long-amount buffer that holds up to {@code maxSlots} distinct (resource, count)
 * entries. The buffer is the storage half of the ME export hatch — recipe outputs land
 * here through the aggregate insert path; a push trait drains it into the AE network on a fixed
 * cadence and whatever the network refuses stays here.
 *
 * <p>
 * Semantics:
 * <ul>
 * <li>{@link #size()} is fixed at {@code maxSlots}. Empty slots report the empty resource and
 * amount 0.</li>
 * <li>Each distinct resource owns exactly one slot (saturating long count), enforced by the
 * overridden aggregate {@link #insert(Resource, int, TransactionContext)} — the default
 * per-slot loop would double-bucket a kind into a second slot.</li>
 * <li>Once all slots are claimed, an aggregate insert of a new kind returns 0 — no silent
 * eviction.</li>
 * <li>Mutations are journalled so an aborted recipe transaction rolls back; root commit
 * discards the journal.</li>
 * </ul>
 */
public final class AeKeyResourceBuffer<R extends Resource>
                                      extends SnapshotJournal<Integer>
                                      implements LongResourceHandler<R>, ValueIOSerializable {

    private static final String SLOTS_TAG = "slots";
    private static final String SLOT_INDEX_TAG = "i";
    private static final String SLOT_RESOURCE_TAG = "r";
    private static final String SLOT_AMOUNT_TAG = "a";

    private final Class<R> resourceType;
    private final R emptyResource;
    private final int maxSlots;
    private final Object[] resources;
    private final long[] counts;
    private final ArrayList<SlotChange<R>> journal = new ArrayList<>();
    private @Nullable Runnable onChanged;

    // Sparse index of currently non-empty slots: kindsInUse / nthNonEmptySlot answer in O(1)
    // instead of scanning the full slot array on the 40-tick push loop and UI paths.
    //
    // Invariants (maintained by insert / extract / revertToSnapshot / deserialize):
    // - nonEmptySlotIndices[0..kindsInUseCount) == { slot | !resources[slot].isEmpty() }
    // - For each slot s in that prefix, slotPositionInList[s] == its index in the prefix.
    // - For each slot s NOT in the prefix, slotPositionInList[s] == -1.
    // Removal is by swap-remove, so order is "stable until something empties".
    private final int[] nonEmptySlotIndices;
    private final int[] slotPositionInList;
    private int kindsInUseCount = 0;
    // Reused across revertToSnapshot calls; transactions are single-threaded, so reuse is safe.
    private final BitSet rollbackTouchedSlots;

    public AeKeyResourceBuffer(Class<R> resourceType, R emptyResource, int maxSlots) {
        if (maxSlots <= 0) {
            throw new IllegalArgumentException("maxSlots must be > 0, got " + maxSlots);
        }
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.emptyResource = Objects.requireNonNull(emptyResource, "emptyResource");
        this.maxSlots = maxSlots;
        this.resources = new Object[maxSlots];
        this.counts = new long[maxSlots];
        this.nonEmptySlotIndices = new int[maxSlots];
        this.slotPositionInList = new int[maxSlots];
        this.rollbackTouchedSlots = new BitSet(maxSlots);
        for (int i = 0; i < maxSlots; i++) {
            this.resources[i] = emptyResource;
            this.slotPositionInList[i] = -1;
        }
    }

    public Class<R> resourceType() {
        return resourceType;
    }

    @Override
    public int size() {
        return maxSlots;
    }

    @SuppressWarnings("unchecked")
    @Override
    public R getResource(int index) {
        if (index < 0 || index >= maxSlots) {
            return emptyResource;
        }
        return (R) resources[index];
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index < 0 || index >= maxSlots) {
            return 0L;
        }
        return counts[index];
    }

    @Override
    public long getCapacityAsLong(int index, R resource) {
        if (index < 0 || index >= maxSlots || resource == null || resource.isEmpty()) {
            return 0L;
        }
        R current = getResource(index);
        if (current.isEmpty() || current.equals(resource)) {
            return Long.MAX_VALUE;
        }
        return 0L;
    }

    @Override
    public boolean isValid(int index, R resource) {
        if (index < 0 || index >= maxSlots || resource == null || resource.isEmpty()) {
            return false;
        }
        R current = getResource(index);
        return current.isEmpty() || current.equals(resource);
    }

    @Override
    public int insert(int index, R resource, int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(insertLong(index, resource, amount, transaction));
    }

    @Override
    public int extract(int index, R resource, int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(extractLong(index, resource, amount, transaction));
    }

    /**
     * Aggregate insert override: routes through the one-slot-per-kind claim rule instead of the
     * default first-empty-slot loop, which would split one kind across slots.
     */
    @Override
    public int insert(R resource, int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(insertLong(resource, amount, transaction));
    }

    /** Aggregate extract override: at most one slot can hold {@code resource}; skip the scan. */
    @Override
    public int extract(R resource, int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return Math.toIntExact(extractLong(resource, amount, transaction));
    }

    @Override
    public long insertLong(R resource, long amount, TransactionContext tx) {
        checkLongTransfer(resource, amount, tx);
        if (amount == 0L) {
            return 0L;
        }
        int existingSlot = findExistingSlot(resource);
        if (existingSlot < 0 && kindsInUseCount >= maxSlots) {
            return 0L;
        }
        int targetSlot = existingSlot >= 0 ? existingSlot : findFirstEmptySlot();
        if (targetSlot < 0) {
            return 0L;
        }
        return insertLong(targetSlot, resource, amount, tx);
    }

    @Override
    public long extractLong(R resource, long amount, TransactionContext tx) {
        checkLongTransfer(resource, amount, tx);
        if (amount == 0L) {
            return 0L;
        }
        int slot = findExistingSlot(resource);
        if (slot < 0) {
            return 0L;
        }
        return extractLong(slot, resource, amount, tx);
    }

    public long extractableLong(R resource) {
        if (resource.isEmpty()) {
            return 0L;
        }
        int slot = findExistingSlot(resource);
        return slot < 0 ? 0L : counts[slot];
    }

    /** O(kindsInUse) lookup of the slot holding {@code resource}, or -1. */
    private int findExistingSlot(R resource) {
        for (int p = 0; p < kindsInUseCount; p++) {
            int slot = nonEmptySlotIndices[p];
            if (resource.equals(resources[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private int findFirstEmptySlot() {
        for (int i = 0; i < maxSlots; i++) {
            if (slotPositionInList[i] < 0) {
                return i;
            }
        }
        return -1;
    }

    /** Number of distinct resource kinds currently held. O(1). */
    public int kindsInUse() {
        return kindsInUseCount;
    }

    public int maxKinds() {
        return maxSlots;
    }

    /**
     * Slot index of the n-th currently non-empty slot, or -1 when out of range. O(1); order is
     * "stable until a slot empties" (extract uses swap-remove).
     */
    public int nthNonEmptySlot(int n) {
        if (n < 0 || n >= kindsInUseCount) {
            return -1;
        }
        return nonEmptySlotIndices[n];
    }

    /**
     * Per-tick AE push helper: push one non-empty slot's contents to the network and consume the
     * actually-accepted amount. The caller owns the surrounding transaction.
     */
    public long drainSlotIntoNetwork(
                                     int slotIndex,
                                     AeNetworkAccessor network,
                                     AeResourceKeyResolver<R> keyResolver,
                                     TransactionContext tx) {
        if (slotIndex < 0 || slotIndex >= maxSlots) {
            return 0L;
        }
        R resource = getResource(slotIndex);
        if (resource.isEmpty()) {
            return 0L;
        }
        long count = counts[slotIndex];
        if (count <= 0L) {
            return 0L;
        }
        AEKey key = keyResolver.toKey(resource);
        if (key == null) {
            return 0L;
        }

        // No insert(SIMULATE) pre-check: MEStorage.insert walks all mounted cells even in
        // SIMULATE mode, so the probe costs the same as the MODULATE we are about to do. The
        // network returns the actually-accepted amount and we consume exactly that.
        long committed = network.insert(key, count, Actionable.MODULATE);
        if (committed <= 0L) {
            return 0L;
        }
        if (committed > count) {
            compensateNetworkInsert(network, key, committed);
            throw new IllegalStateException("AE network inserted " + committed + " from a buffer request of " + count);
        }
        long extracted = extractLong(slotIndex, resource, committed, tx);
        if (extracted != committed) {
            compensateNetworkInsert(network, key, committed);
            throw new IllegalStateException("Unable to debit " + committed + " from AE output buffer slot " + slotIndex + "; extracted " + extracted);
        }
        return committed;
    }

    private static void compensateNetworkInsert(
                                                AeNetworkAccessor network,
                                                AEKey key,
                                                long amount) {
        long removed = network.extract(key, amount, Actionable.MODULATE);
        if (removed != amount) {
            throw new IllegalStateException("Unable to compensate an invalid AE network insert of " + amount + " " + key + "; removed only " + removed);
        }
    }

    @Override
    public long insertLong(int index, R resource, long amount, TransactionContext tx) {
        checkLongTransfer(resource, amount, tx);
        if (index < 0 || index >= maxSlots || amount == 0L) {
            return 0L;
        }
        R current = getResource(index);
        if (!current.isEmpty() && !current.equals(resource)) {
            return 0L;
        }

        long currentCount = counts[index];
        long newCount = saturatingAdd(currentCount, amount);
        long actuallyInserted = newCount - currentCount;
        if (actuallyInserted <= 0L) {
            return 0L;
        }

        updateSnapshots(tx);
        recordChange(index, current, currentCount);
        boolean wasEmpty = current.isEmpty();
        resources[index] = resource;
        counts[index] = newCount;
        if (wasEmpty) {
            trackSlotAsNonEmpty(index);
        }
        return actuallyInserted;
    }

    @Override
    public long extractLong(int index, R resource, long amount, TransactionContext tx) {
        checkLongTransfer(resource, amount, tx);
        if (index < 0 || index >= maxSlots || amount == 0L) {
            return 0L;
        }
        R current = getResource(index);
        if (!current.equals(resource)) {
            return 0L;
        }

        long currentCount = counts[index];
        long extracted = Math.min(amount, currentCount);
        if (extracted <= 0L) {
            return 0L;
        }

        updateSnapshots(tx);
        recordChange(index, current, currentCount);
        long remaining = currentCount - extracted;
        counts[index] = remaining;
        if (remaining == 0L) {
            resources[index] = emptyResource;
            untrackSlotAsNonEmpty(index);
        }
        return extracted;
    }

    private void trackSlotAsNonEmpty(int slot) {
        nonEmptySlotIndices[kindsInUseCount] = slot;
        slotPositionInList[slot] = kindsInUseCount;
        kindsInUseCount++;
    }

    private void untrackSlotAsNonEmpty(int slot) {
        int pos = slotPositionInList[slot];
        int lastPos = kindsInUseCount - 1;
        if (pos != lastPos) {
            int movedSlot = nonEmptySlotIndices[lastPos];
            nonEmptySlotIndices[pos] = movedSlot;
            slotPositionInList[movedSlot] = pos;
        }
        slotPositionInList[slot] = -1;
        kindsInUseCount--;
    }

    private void recordChange(int index, R prevResource, long prevCount) {
        journal.add(new SlotChange<>(index, prevResource, prevCount));
    }

    private static void checkLongTransfer(
                                          Resource resource,
                                          long amount,
                                          TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmpty(resource);
        if (amount < 0L) {
            throw new IllegalArgumentException("Transfer amount must be non-negative: " + amount);
        }
    }

    @Override
    protected Integer createSnapshot() {
        return journal.size();
    }

    @Override
    protected void revertToSnapshot(Integer snapshot) {
        // First pass: restore raw slot state from the journal slice; reconcile the sparse index
        // afterwards because a slot may have multiple mutations in the slice and only the final
        // restored state matters.
        rollbackTouchedSlots.clear();
        for (int i = journal.size() - 1; i >= snapshot; i--) {
            SlotChange<R> change = journal.get(i);
            rollbackTouchedSlots.set(change.slot());
            resources[change.slot()] = change.prevResource();
            counts[change.slot()] = change.prevCount();
        }
        journal.subList(snapshot, journal.size()).clear();
        for (int slot = rollbackTouchedSlots.nextSetBit(0); slot >= 0; slot = rollbackTouchedSlots.nextSetBit(slot + 1)) {
            boolean tracked = slotPositionInList[slot] >= 0;
            boolean shouldBeTracked = !getResource(slot).isEmpty();
            if (tracked && !shouldBeTracked) {
                untrackSlotAsNonEmpty(slot);
            } else if (!tracked && shouldBeTracked) {
                trackSlotAsNonEmpty(slot);
            }
        }
        notifyChanged();
    }

    @Override
    protected void onRootCommit(Integer originalState) {
        if (originalState < journal.size()) {
            journal.subList(originalState, journal.size()).clear();
            notifyChanged();
        }
    }

    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private void notifyChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    public void serialize(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList(SLOTS_TAG);
        for (int i = 0; i < maxSlots; i++) {
            R res = getResource(i);
            long amt = counts[i];
            if (res.isEmpty() || amt <= 0L) {
                continue;
            }
            ValueOutput child = list.addChild();
            child.putInt(SLOT_INDEX_TAG, i);
            child.putLong(SLOT_AMOUNT_TAG, amt);
            AeConfigSlotSerializers.writeResource(child, SLOT_RESOURCE_TAG, res, resourceType);
        }
    }

    public void deserialize(ValueInput input) {
        for (int i = 0; i < maxSlots; i++) {
            resources[i] = emptyResource;
            counts[i] = 0L;
            slotPositionInList[i] = -1;
        }
        kindsInUseCount = 0;
        journal.clear();
        input.childrenList(SLOTS_TAG).ifPresent(list -> {
            for (ValueInput child : list) {
                int index = child.getIntOr(SLOT_INDEX_TAG, -1);
                long amount = child.getLongOr(SLOT_AMOUNT_TAG, 0L);
                R resource = AeConfigSlotSerializers.readResource(child, SLOT_RESOURCE_TAG, resourceType);
                if (index < 0 || index >= maxSlots || amount <= 0L || resource == null || resource.isEmpty()) {
                    continue;
                }
                if (slotPositionInList[index] >= 0) {
                    continue;
                }
                resources[index] = resource;
                counts[index] = amount;
                trackSlotAsNonEmpty(index);
            }
        });
        notifyChanged();
    }

    private static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }

    private record SlotChange<R extends Resource>(int slot, R prevResource, long prevCount) {}
}
