package net.ptcrys.topo.integration.ae2;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import com.google.common.primitives.Ints;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Virtual input storage backed by AE2. Extractions are modulated immediately and refunded on
 * transaction abort, so Topo recipe transactions remain rollback-safe even though AE2 storage is
 * not NeoForge-transactional. Insertion is always rejected; configured slots define the visible
 * window: {@code getAmountAsLong = min(network available, configured target − in-flight)}.
 */
public final class AeStockingResourceHandler<R extends Resource>
                                            extends SnapshotJournal<Integer>
                                            implements ResourceHandler<R> {

    private static final String PENDING_REFUNDS_TAG = "pendingRefunds";
    private static final String RESOURCE_TAG = "resource";
    private static final String AMOUNT_TAG = "amount";

    private final Class<R> resourceType;
    private final R emptyResource;
    private final ArrayList<AeConfigSlot<R>> slots;
    private final ArrayList<Extraction<R>> extractions = new ArrayList<>();
    private final HashMap<R, Long> inFlightTotals = new HashMap<>();
    private final ArrayList<PendingRefund<R>> pendingRefunds = new ArrayList<>();
    private final AeResourceKeyResolver<R> keyResolver;
    private final AeResourceKeyCache<R> keyCache;
    private Supplier<AeNetworkAccessor> networkSupplier;
    private @Nullable Runnable onChanged;
    private boolean configAmountsDirty = true;
    private long[] configuredPrefixBeforeSlot = new long[0];
    private long[] configuredTotalForSlot = new long[0];
    private AEKey[] configuredKeyForSlot = new AEKey[0];
    private Map<R, Long> configuredTotals = Map.of();
    private Map<R, AEKey> configuredKeys = Map.of();

    public AeStockingResourceHandler(
                                     Class<R> resourceType,
                                     R emptyResource,
                                     int slots,
                                     Supplier<AeNetworkAccessor> networkSupplier,
                                     @Nullable Runnable onChanged) {
        this(resourceType, emptyResource, AeConfigSlotSerializers.emptySlots(slots), networkSupplier, onChanged);
    }

    public AeStockingResourceHandler(
                                     Class<R> resourceType,
                                     R emptyResource,
                                     List<AeConfigSlot<R>> slots,
                                     Supplier<AeNetworkAccessor> networkSupplier,
                                     @Nullable Runnable onChanged) {
        this(resourceType, emptyResource, slots, networkSupplier, onChanged, AeResourceKeyResolver.defaultResolver());
    }

    public AeStockingResourceHandler(
                                     Class<R> resourceType,
                                     R emptyResource,
                                     List<AeConfigSlot<R>> slots,
                                     Supplier<AeNetworkAccessor> networkSupplier,
                                     @Nullable Runnable onChanged,
                                     AeResourceKeyResolver<R> keyResolver) {
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType");
        this.emptyResource = Objects.requireNonNull(emptyResource, "emptyResource");
        this.slots = new ArrayList<>(Objects.requireNonNull(slots, "slots"));
        this.networkSupplier = Objects.requireNonNull(networkSupplier, "networkSupplier");
        this.onChanged = onChanged;
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver");
        this.keyCache = new AeResourceKeyCache<>(this.keyResolver);
    }

    public void setNetworkSupplier(Supplier<AeNetworkAccessor> networkSupplier) {
        this.networkSupplier = Objects.requireNonNull(networkSupplier, "networkSupplier");
    }

    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    public void setConfigSlot(int slot, AeConfigSlot<R> config) {
        slots.set(slot, Objects.requireNonNull(config, "config"));
        configAmountsDirty = true;
        keyCache.markDirty();
        notifyChanged();
    }

    public List<AeConfigSlot<R>> configSlots() {
        return List.copyOf(slots);
    }

    public AeConfigSlot<R> configSlot(int slot) {
        return slots.get(slot);
    }

    @Override
    public int size() {
        return slots.size();
    }

    @Override
    public R getResource(int index) {
        if (index < 0 || index >= slots.size()) {
            return emptyResource;
        }
        return slots.get(index).resourceOr(emptyResource);
    }

    @Override
    public long getAmountAsLong(int index) {
        if (index < 0 || index >= slots.size()) {
            return 0L;
        }
        AeConfigSlot<R> slot = slots.get(index);
        if (!slot.configured()) {
            return 0L;
        }
        rebuildConfigAmountsIfNeeded();
        AEKey key = configuredKeyForSlot[index];
        if (key == null) {
            return 0L;
        }
        long available = network().available(key);
        long visibleTotal = Math.min(available, configuredCapAfterInFlight(slot.resource(), index));
        long alreadyVisible = configuredPrefixBeforeSlot(index);
        if (alreadyVisible >= visibleTotal) {
            return 0L;
        }
        return Math.min(visibleTotal - alreadyVisible, slot.targetAmount());
    }

    @Override
    public long getCapacityAsLong(int index, R resource) {
        if (index < 0 || index >= slots.size()) {
            return 0L;
        }
        AeConfigSlot<R> slot = slots.get(index);
        return slot.accepts(resource) ? slot.targetAmount() : 0L;
    }

    @Override
    public boolean isValid(int index, R resource) {
        if (index < 0 || index >= slots.size() || resource == null || resource.isEmpty()) {
            return false;
        }
        return slots.get(index).accepts(resource);
    }

    @Override
    public int insert(int index, R resource, int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return 0;
    }

    @Override
    public int extract(int index, R resource, int amount, TransactionContext transaction) {
        Objects.requireNonNull(transaction, "transaction");
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!isValid(index, resource)) {
            return 0;
        }
        long requested = Math.min(amount, getAmountAsLong(index));
        return Ints.saturatedCast(extractAll(resource, requested, transaction));
    }

    /** Live extractable window for {@code resource} across all configured slots. */
    public long extractableLong(R resource) {
        if (resource.isEmpty()) {
            return 0L;
        }
        long configuredCap = configuredCap(resource);
        if (configuredCap <= 0L) {
            return 0L;
        }
        long inFlight = inFlightAmount(resource);
        if (inFlight >= configuredCap) {
            return 0L;
        }
        AEKey key = configuredKey(resource);
        if (key == null) {
            return 0L;
        }
        return Math.min(network().available(key), configuredCap - inFlight);
    }

    private long extractAll(R resource, long amount, TransactionContext transaction) {
        flushPendingRefunds();
        long configuredCap = configuredCap(resource);
        if (configuredCap <= 0L) {
            return 0L;
        }
        long inFlight = inFlightAmount(resource);
        if (inFlight >= configuredCap) {
            return 0L;
        }
        AEKey key = configuredKey(resource);
        if (key == null) {
            return 0L;
        }
        long requested = Math.min(amount, configuredCap - inFlight);
        if (requested <= 0L) {
            return 0L;
        }
        AeNetworkAccessor network = network();
        if (!network.isOnline()) {
            return 0L;
        }
        requested = Math.min(requested, network.extract(key, requested, Actionable.SIMULATE));
        if (requested <= 0L) {
            return 0L;
        }

        updateSnapshots(transaction);
        long extracted = network.extract(key, requested, Actionable.MODULATE);
        if (extracted > 0L) {
            extractions.add(new Extraction<>(resource, key, extracted, network));
            addInFlight(resource, extracted);
            return extracted;
        }
        return 0L;
    }

    private AeNetworkAccessor network() {
        AeNetworkAccessor network = networkSupplier.get();
        return network == null ? AeNetworkAccessor.OFFLINE : network;
    }

    /**
     * Cheap drift probe for the owning trait's periodic recipe-cache invalidation: mixes the
     * cached network availability of every configured key. Stable while the network holds the
     * same counts; any drift (or online/offline flip) changes the value.
     */
    long availabilitySignature() {
        AeNetworkAccessor network = network();
        if (!network.isOnline()) {
            return 0L;
        }
        rebuildConfigAmountsIfNeeded();
        long signature = 1L;
        for (AEKey key : configuredKeys.values()) {
            signature = signature * 31L + network.availableCached(key);
        }
        return signature;
    }

    @Override
    protected Integer createSnapshot() {
        return extractions.size();
    }

    @Override
    protected void revertToSnapshot(Integer snapshot) {
        boolean rebuildInFlight = false;
        for (int i = extractions.size() - 1; i >= snapshot; i--) {
            Extraction<R> extraction = extractions.remove(i);
            rebuildInFlight |= !subtractInFlight(extraction.resource(), extraction.amount());
            refundOrQueue(extraction.resource(), extraction.key(), extraction.amount(), extraction.network());
        }
        if (rebuildInFlight) {
            rebuildInFlightTotals();
        }
        notifyChanged();
    }

    @Override
    protected void onRootCommit(Integer originalState) {
        if (originalState < extractions.size()) {
            boolean rebuildInFlight = false;
            for (int i = extractions.size() - 1; i >= originalState; i--) {
                Extraction<R> extraction = extractions.get(i);
                rebuildInFlight |= !subtractInFlight(extraction.resource(), extraction.amount());
            }
            extractions.subList(originalState, extractions.size()).clear();
            if (rebuildInFlight) {
                rebuildInFlightTotals();
            }
            notifyChanged();
        }
    }

    private void notifyChanged() {
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private long configuredCapAfterInFlight(R resource, int slot) {
        rebuildConfigAmountsIfNeeded();
        long configuredCap = configuredTotalForSlot[slot];
        long inFlight = inFlightAmount(resource);
        return inFlight >= configuredCap ? 0L : configuredCap - inFlight;
    }

    private long configuredCap(R resource) {
        rebuildConfigAmountsIfNeeded();
        return configuredTotals.getOrDefault(resource, 0L);
    }

    private long configuredPrefixBeforeSlot(int index) {
        rebuildConfigAmountsIfNeeded();
        return configuredPrefixBeforeSlot[index];
    }

    private void rebuildConfigAmountsIfNeeded() {
        if (!configAmountsDirty && configuredPrefixBeforeSlot.length == slots.size() && configuredTotalForSlot.length == slots.size() && configuredKeyForSlot.length == slots.size()) {
            return;
        }
        int size = slots.size();
        if (configuredPrefixBeforeSlot.length != size) {
            configuredPrefixBeforeSlot = new long[size];
        }
        if (configuredTotalForSlot.length != size) {
            configuredTotalForSlot = new long[size];
        }
        if (configuredKeyForSlot.length != size) {
            configuredKeyForSlot = new AEKey[size];
        }

        HashMap<R, Long> totals = new HashMap<>();
        HashMap<R, AEKey> keys = new HashMap<>();
        for (AeConfigSlot<R> slot : slots) {
            if (slot.configured()) {
                R resource = slot.resource();
                totals.merge(resource, slot.targetAmount(), AeStockingResourceHandler::saturatingAdd);
                AEKey key = keyCache.toKey(resource);
                if (key != null) {
                    keys.putIfAbsent(resource, key);
                }
            }
        }

        HashMap<R, Long> prefixes = new HashMap<>();
        for (int i = 0; i < size; i++) {
            AeConfigSlot<R> slot = slots.get(i);
            if (slot.configured()) {
                R resource = slot.resource();
                configuredPrefixBeforeSlot[i] = prefixes.getOrDefault(resource, 0L);
                configuredTotalForSlot[i] = totals.getOrDefault(resource, 0L);
                configuredKeyForSlot[i] = keys.get(resource);
                prefixes.merge(resource, slot.targetAmount(), AeStockingResourceHandler::saturatingAdd);
            } else {
                configuredPrefixBeforeSlot[i] = 0L;
                configuredTotalForSlot[i] = 0L;
                configuredKeyForSlot[i] = null;
            }
        }
        configuredTotals = Map.copyOf(totals);
        configuredKeys = Map.copyOf(keys);
        configAmountsDirty = false;
    }

    private @Nullable AEKey configuredKey(R resource) {
        rebuildConfigAmountsIfNeeded();
        return configuredKeys.get(resource);
    }

    private long inFlightAmount(R resource) {
        return inFlightTotals.getOrDefault(resource, 0L);
    }

    private void addInFlight(R resource, long amount) {
        inFlightTotals.merge(resource, amount, AeStockingResourceHandler::saturatingAdd);
    }

    private boolean subtractInFlight(R resource, long amount) {
        Long current = inFlightTotals.get(resource);
        if (current == null) {
            return false;
        }
        if (current == Long.MAX_VALUE) {
            return false;
        }
        long remaining = current - amount;
        if (remaining < 0L) {
            return false;
        }
        if (remaining > 0L) {
            inFlightTotals.put(resource, remaining);
        } else {
            inFlightTotals.remove(resource);
        }
        return true;
    }

    private void rebuildInFlightTotals() {
        inFlightTotals.clear();
        for (Extraction<R> extraction : extractions) {
            addInFlight(extraction.resource(), extraction.amount());
        }
    }

    private void refundOrQueue(R resource, AEKey key, long amount, AeNetworkAccessor preferredNetwork) {
        if (amount <= 0L) {
            return;
        }
        long inserted = preferredNetwork.insert(key, amount, Actionable.MODULATE);
        long remaining = amount - inserted;
        if (remaining > 0L) {
            pendingRefunds.add(new PendingRefund<>(resource, remaining));
        }
    }

    private void flushPendingRefunds() {
        if (pendingRefunds.isEmpty()) {
            return;
        }
        AeNetworkAccessor network = network();
        if (!network.isOnline()) {
            return;
        }
        boolean changed = false;
        for (int i = pendingRefunds.size() - 1; i >= 0; i--) {
            PendingRefund<R> refund = pendingRefunds.get(i);
            AEKey key = configuredKey(refund.resource());
            if (key == null) {
                key = keyResolver.toKey(refund.resource());
            }
            if (key == null) {
                continue;
            }
            long inserted = network.insert(key, refund.amount(), Actionable.MODULATE);
            long remaining = refund.amount() - inserted;
            if (remaining <= 0L) {
                pendingRefunds.remove(i);
                changed = true;
            } else if (remaining < refund.amount()) {
                pendingRefunds.set(i, new PendingRefund<>(refund.resource(), remaining));
                changed = true;
            }
        }
        if (changed) {
            notifyChanged();
        }
    }

    // ---- Test / diagnostic accessors ----
    // Public so GameTests can verify refund-queue and snapshot-journal invariants without
    // reflection. The *ForTest suffix signals "not for production callers".

    public long pendingRefundAmountForTest(R resource) {
        long amount = 0L;
        for (PendingRefund<R> refund : pendingRefunds) {
            if (refund.resource().equals(resource)) {
                amount = saturatingAdd(amount, refund.amount());
            }
        }
        return amount;
    }

    public void flushPendingRefundsForTest() {
        flushPendingRefunds();
    }

    /** Number of unresolved AE network extractions journalled in the current/last transaction tree. */
    public int activeExtractionCountForTest() {
        return extractions.size();
    }

    /** In-flight (open-tx) extracted total for one resource — O(1) via the inFlightTotals map. */
    public long inFlightAmountForTest(R resource) {
        return inFlightAmount(resource);
    }

    private static long saturatingAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0L ? Long.MAX_VALUE : r;
    }

    public void serialize(ValueOutput output) {
        AeConfigSlotSerializers.write(output, slots, resourceType);
        ValueOutput.ValueOutputList list = output.childrenList(PENDING_REFUNDS_TAG);
        for (PendingRefund<R> refund : pendingRefunds) {
            ValueOutput child = list.addChild();
            child.putLong(AMOUNT_TAG, refund.amount());
            AeConfigSlotSerializers.writeResource(child, RESOURCE_TAG, refund.resource(), resourceType);
        }
    }

    public void deserialize(ValueInput input) {
        AeConfigSlotSerializers.read(input, slots, resourceType);
        configAmountsDirty = true;
        keyCache.markDirty();
        pendingRefunds.clear();
        input.childrenList(PENDING_REFUNDS_TAG).ifPresent(list -> {
            for (ValueInput child : list) {
                long amount = child.getLongOr(AMOUNT_TAG, 0L);
                R resource = AeConfigSlotSerializers.readResource(child, RESOURCE_TAG, resourceType);
                if (amount > 0L && resource != null && !resource.isEmpty()) {
                    pendingRefunds.add(new PendingRefund<>(resource, amount));
                }
            }
        });
        notifyChanged();
    }

    private record Extraction<R extends Resource>(R resource, AEKey key, long amount, AeNetworkAccessor network) {}

    private record PendingRefund<R extends Resource>(R resource, long amount) {}
}
