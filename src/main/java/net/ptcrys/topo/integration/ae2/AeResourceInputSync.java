package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.data.DataValueIoField;
import net.ptcrys.topo.api.machine.resource.ResourcePort;
import net.ptcrys.topo.api.tick.MachineTicker;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Per-machine periodic sync trait for AE-backed drawing input ports.
 *
 * <h3>Two-stage tick</h3>
 *
 * <p>
 * This trait runs every 40 game ticks via {@link MachineTicker}, but the work it triggers is
 * split into two halves to keep Topo's {@code runProfiledTick} accounting tight:
 *
 * <ol>
 * <li>{@link #tick(long, TickHandle)} (the Topo-profiled half) runs {@link #fastPlan()} only:
 * it reads slot state, makes intent decisions, and writes them into a parallel-array
 * {@link AeConfiguredResourceSync.PendingPlan}. It opens no transaction and makes no
 * network extract/insert call beyond the rare stray-content push branch.</li>
 * <li>{@link #processPending(AeNetworkAccessor)} (the AE2-attributed half) runs
 * {@link AeConfiguredResourceSync#execute}: shared transaction, local reservation, one
 * batched {@code extractDirect} per unique AEKey, distribution, refunds. It is invoked
 * from {@link AeGridNode#tickingRequest} on AE2's grid-tick path.</li>
 * </ol>
 *
 * <h3>Edge cases</h3>
 *
 * <p>
 * <strong>Grid offline between plan and execute:</strong> {@code execute} sees the offline
 * network and drops the pull intents; the next sync tick rebuilds a fresh plan, so nothing is
 * lost. <strong>Plan replacement before drain:</strong> a still-pending plan blocks new plan
 * builds so the reusable intent buffers are never overwritten under a pending snapshot.
 * <strong>Unload:</strong> {@link #onDetaching(TickHandle)} clears the pending plan so a
 * re-loading machine does not act on stale intents.
 */
public abstract class AeResourceInputSync<R extends Resource>
                                         extends MachineTicker
                                         implements AeConfiguredResource<R>, AeGridNode.PendingWorkProcessor {

    private final Class<R> resourceType;
    private final List<AeConfigSlot<R>> configSlots;
    private final ComponentKey<? extends ResourcePort<?, R>> portKey;
    private final DataValueIoField configData;
    /** Resolver cache for the rare push branch; the hot fastPlan path uses the slot arrays below. */
    private final AeResourceKeyCache<R> keyCache = new AeResourceKeyCache<>(AeResourceKeyResolver.defaultResolver());
    private @Nullable ResourcePort<?, R> port;
    private @Nullable AeGridNode grid;

    // ---- Per-slot pre-resolved snapshots for the hot fastPlan() path ----
    // Refreshed on setConfigSlot / load and read-only on the per-tick path: they eliminate the
    // per-slot record deref, AEKey resolve, and configured() check from the timed kernel.
    private final AEKey[] slotKeys;
    private final long[] slotTargetAmounts;
    private final R[] slotConfigResources;

    // ---- Reusable intent buffers for fastPlan() ----
    // Sized once at construction; PendingPlan.fromTraitArrays references them without copying.
    // The server-tick / grid-tick serialisation guarantees they are not overwritten while a
    // previous PendingPlan is still pending.
    private final int[] reusableIntentSlot;
    private final AEKey[] reusableIntentKey;
    private final R[] reusableIntentResource;
    private final long[] reusableIntentDesired;

    /**
     * Plan built by Pass 1 (tick), awaiting drain by Pass 2 (grid tick). {@code null} = nothing
     * pending. Accessed only on the server thread (both ticks serialise through it).
     */
    private AeConfiguredResourceSync.@Nullable PendingPlan<R> pendingPlan;

    @SuppressWarnings("unchecked")
    protected AeResourceInputSync(
                                  ComponentContext<? extends AeResourceInputSync<R>> context,
                                  Class<R> resourceType,
                                  int slots,
                                  ComponentKey<? extends ResourcePort<?, R>> portKey) {
        super(context);
        this.resourceType = resourceType;
        this.configSlots = AeConfigSlotSerializers.emptySlots(slots);
        this.portKey = portKey;
        this.slotKeys = new AEKey[slots];
        this.slotTargetAmounts = new long[slots];
        this.slotConfigResources = (R[]) new Resource[slots];
        this.reusableIntentSlot = new int[slots];
        this.reusableIntentKey = new AEKey[slots];
        this.reusableIntentResource = (R[]) new Resource[slots];
        this.reusableIntentDesired = new long[slots];
        this.configData = data().valueIoField(
                "ae_config",
                output -> AeConfigSlotSerializers.write(output, configSlots, this.resourceType),
                input -> AeConfigSlotSerializers.read(input, configSlots, this.resourceType),
                this::onConfigLoaded)
                .persisted()
                .done();
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        port = traits.require(portKey);
        AeGridNode gridTrait = traits.require(AeGridNode.AE_GRID);
        gridTrait.addPendingWorkProcessor(this);
        grid = gridTrait;
    }

    public void setConfigSlot(int slot, AeConfigSlot<R> config) {
        configSlots.set(slot, config);
        keyCache.markDirty();
        refreshSlotCache(slot);
        configData.markDirty();
        data().markPersistedStateChanged();
    }

    private void onConfigLoaded() {
        keyCache.markDirty();
        refreshAllSlotCache();
    }

    private void refreshSlotCache(int slot) {
        AeConfigSlot<R> config = configSlots.get(slot);
        R resource = config.resource();
        long target = config.targetAmount();
        boolean configured = resource != null && !resource.isEmpty() && target > 0L;
        if (configured) {
            slotKeys[slot] = AeResourceKeyResolver.<R>defaultResolver().toKey(resource);
            slotTargetAmounts[slot] = target;
            slotConfigResources[slot] = resource;
        } else {
            slotKeys[slot] = null;
            slotTargetAmounts[slot] = 0L;
            slotConfigResources[slot] = null;
        }
    }

    private void refreshAllSlotCache() {
        for (int slot = 0, n = configSlots.size(); slot < n; slot++) {
            refreshSlotCache(slot);
        }
    }

    public List<AeConfigSlot<R>> configSlots() {
        return List.copyOf(configSlots);
    }

    @Override
    public Class<R> aeConfigResourceType() {
        return resourceType;
    }

    @Override
    public int aeConfigSlotCount() {
        return configSlots.size();
    }

    @Override
    public AeConfigSlot<R> aeConfigSlot(int slot) {
        return configSlots.get(slot);
    }

    @Override
    public void setAeConfigSlot(int slot, AeConfigSlot<R> config) {
        setConfigSlot(slot, config);
    }

    @Override
    public long aeStockedAmount(int slot) {
        ResourcePort<?, R> localPort = port;
        return localPort == null ? 0L : localPort.handler().getAmountAsLong(slot);
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    /**
     * Pass 1 only — builds a {@link AeConfiguredResourceSync.PendingPlan} and stores it for the
     * grid-tick callback ({@link AeGridNode#tickingRequest}) to drain. If a previous plan
     * is still pending, this tick is skipped: overwriting the buffers would corrupt the
     * read-only snapshot the grid tick is about to drain; the next sync tick rebuilds.
     */
    @Override
    public void tick(long gameTime, TickHandle handle) {
        AeGridNode gridTrait = grid;
        if (port == null || gridTrait == null) {
            return;
        }
        if (pendingPlan != null) {
            return;
        }
        AeConfiguredResourceSync.PendingPlan<R> plan = fastPlan();
        // Push side-effects were already committed by fastPlan() (each push opens its own
        // short-lived transaction), so a plan with only push counters needs no grid-tick drain.
        if (plan.hasNoIntents()) {
            pendingPlan = null;
            return;
        }
        pendingPlan = plan;
        gridTrait.signalPendingWork();
    }

    /**
     * Hot path: builds a {@link AeConfiguredResourceSync.PendingPlan} from the pre-cached
     * per-slot snapshots and the reusable intent buffers — zero allocation on the no-intent
     * steady state, zero array allocation otherwise. Caller must hold the server-tick monopoly
     * and ensure no outstanding plan references the buffers (the {@code pendingPlan != null}
     * guard in {@link #tick}).
     */
    public AeConfiguredResourceSync.PendingPlan<R> fastPlan() {
        ResourcePort<?, R> localPort = port;
        AeGridNode gridTrait = grid;
        if (localPort == null || gridTrait == null) {
            return AeConfiguredResourceSync.PendingPlan.empty();
        }
        ResourceHandler<R> local = localPort.handler();
        AeNetworkAccessor network = gridTrait.network();
        if (!network.isOnline()) {
            return AeConfiguredResourceSync.PendingPlan.empty();
        }

        final int n = configSlots.size();
        // Defensive lower bound — the port handler size is fixed per machine, but stay safe.
        final int slots = Math.min(local.size(), n);

        final AEKey[] keys = slotKeys;
        final long[] targets = slotTargetAmounts;
        final R[] configResources = slotConfigResources;
        final int[] intentSlot = reusableIntentSlot;
        final AEKey[] intentKey = reusableIntentKey;
        final R[] intentResource = reusableIntentResource;
        final long[] intentDesired = reusableIntentDesired;

        long pushedTotal = 0L;
        int skippedFromPush = 0;
        int intentCount = 0;

        for (int slot = 0; slot < slots; slot++) {
            AEKey key = keys[slot];
            if (key == null) {
                continue;
            }
            long targetAmount = targets[slot];
            R configResource = configResources[slot];

            R current = local.getResource(slot);
            long amount = local.getAmountAsLong(slot);

            boolean currentEmpty = current == null || current.isEmpty() || amount <= 0L;
            boolean matches = !currentEmpty && configResource.equals(current);

            // excess: stray content (mismatched local) or over-target (matching).
            long excess;
            if (currentEmpty) {
                excess = 0L;
            } else if (!matches) {
                excess = amount;
            } else {
                excess = amount > targetAmount ? amount - targetAmount : 0L;
            }

            if (excess > 0L) {
                long pushed = AeConfiguredResourceSync.pushToNetwork(
                        local, slot, current, excess, network, keyCache);
                if (pushed > 0L) {
                    pushedTotal = AeConfiguredResourceSync.saturatingAdd(pushedTotal, pushed);
                } else {
                    skippedFromPush++;
                }
                current = local.getResource(slot);
                amount = local.getAmountAsLong(slot);
                currentEmpty = current == null || current.isEmpty() || amount <= 0L;
                matches = !currentEmpty && configResource.equals(current);
            }

            // Pull a configured resource into an empty or matching slot only.
            if (!currentEmpty && !matches) {
                continue;
            }

            long deficit = currentEmpty ? targetAmount : (targetAmount > amount ? targetAmount - (amount < 0L ? 0L : amount) : 0L);
            if (deficit <= 0L) {
                continue;
            }

            long capacity = local.getCapacityAsLong(slot, configResource);
            long effectiveAmount = amount < 0L ? 0L : amount;
            long room = effectiveAmount >= capacity ? 0L : capacity - effectiveAmount;
            if (room <= 0L) {
                continue;
            }
            long toPull = Math.min(deficit, room);

            intentSlot[intentCount] = slot;
            intentKey[intentCount] = key;
            intentResource[intentCount] = configResource;
            intentDesired[intentCount] = toPull;
            intentCount++;
        }

        if (intentCount == 0 && pushedTotal == 0L && skippedFromPush == 0) {
            return AeConfiguredResourceSync.PendingPlan.empty();
        }
        return AeConfiguredResourceSync.PendingPlan.fromTraitArrays(
                intentCount, intentSlot, intentKey, intentResource, intentDesired,
                pushedTotal, skippedFromPush);
    }

    /**
     * Pass 2 entry point invoked by {@link AeGridNode#tickingRequest} on AE2's grid tick.
     *
     * @return {@code true} if a plan was drained, keeping AE's tick rate urgent.
     */
    @Override
    public boolean processPending(AeNetworkAccessor network) {
        ResourcePort<?, R> localPort = port;
        if (localPort == null) {
            return false;
        }
        AeConfiguredResourceSync.PendingPlan<R> plan = pendingPlan;
        if (plan == null) {
            return false;
        }
        pendingPlan = null;
        AeConfiguredResourceSync.execute(plan, localPort.handler(), network);
        return true;
    }

    /** Test/diagnostic helper — whether a plan is waiting for the next grid tick. */
    boolean hasPendingPlan() {
        return pendingPlan != null;
    }

    @Override
    protected void onDetaching(TickHandle handle) {
        pendingPlan = null;
    }
}
