package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.data.DataValueIoField;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridNodeService;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.util.AECableType;
import appeng.helpers.patternprovider.PatternContainer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * AE2 grid node lifecycle wrapper shared by every ME machine: one mounted instance owns the
 * {@link IManagedGridNode}, exposes it through {@link IInWorldGridNodeHost} (registered on the
 * shared machine block-entity type by {@link AeIntegration}), and persists its NBT through the
 * machine data domain.
 *
 * <h3>IGridTickable hand-off for sync traits</h3>
 *
 * <p>
 * This trait registers itself as an {@link IGridTickable} on the managed node. AE2's tick
 * manager invokes {@link #tickingRequest(IGridNode, int)} on its own grid-tick cadence; that
 * callback drains pending work from every registered {@link PendingWorkProcessor} (the drawing
 * input sync traits). Because AE2's grid tick is not counted by Topo's
 * {@code MachineTicker#runProfiledTick} accounting, the network MODULATE work is attributed to
 * AE2 rather than the sync trait's profile slot — the Topo-profiled sync tick only carries the
 * plan-build cost.
 */
public final class AeGridNode extends MachineComponent
                              implements IInWorldGridNodeHost, IActionHost, IGridTickable, PatternContainer {

    public static final ComponentKey<AeGridNode> AE_GRID = ComponentKey.id("ae_grid", AeGridNode.class);

    private static final String NODE_TAG = "node";
    private static final IGridNodeListener<AeGridNode> LISTENER = new IGridNodeListener<>() {

        @Override
        public void onSaveChanges(AeGridNode nodeOwner, IGridNode node) {
            nodeOwner.markNodeDataDirty();
        }

        @Override
        public void onStateChanged(AeGridNode nodeOwner, IGridNode node, State state) {
            nodeOwner.onGridStateChanged();
        }
    };

    /** Pass-2 hook drained from AE2's grid tick; see the class javadoc. */
    public interface PendingWorkProcessor {

        /** Returns {@code true} when pending work was drained, keeping the AE tick rate urgent. */
        boolean processPending(AeNetworkAccessor network);
    }

    private final double idlePowerUsage;
    private final List<ServiceRegistration<?>> services = new ArrayList<>();
    private final List<PendingWorkProcessor> pendingWorkProcessors = new ArrayList<>();
    private @Nullable IManagedGridNode managedNode;
    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final AeNetworkAccessor networkAccessor = AeNetworkAccessor.fromManagedNode(() -> managedNode, actionSource);
    private final DataValueIoField nodeData;
    private boolean nodeCreationPending;
    /**
     * Optional delegate for AE2's {@link PatternContainer} discovery. AE2's
     * {@code grid.getMachineClasses()} indexes nodes by their owner's runtime class — and the
     * owner of every Topo ME node is this trait. To make a sibling pattern provider visible to the
     * Pattern Access Terminal without one host class per ME variant, this trait permanently
     * implements {@link PatternContainer} and forwards the terminal methods to a delegate the
     * sibling trait registers during dependency resolution. With no delegate,
     * {@link #isVisibleInTerminal()} returns {@code false} so non-pattern-provider ME machines
     * stay out of the terminal listing.
     */
    private @Nullable PatternContainer patternDelegate;

    private AeGridNode(ComponentContext<AeGridNode> context, double idlePowerUsage) {
        super(context);
        this.idlePowerUsage = idlePowerUsage;
        // Register the IGridTickable service eagerly — it is propagated to the managed node in
        // ensureNode(). AE asserts that services are not added after the node is ready.
        services.add(new ServiceRegistration<>(IGridTickable.class, this));
        this.nodeData = data().valueIoField(
                "ae_node",
                this::serializeNode,
                this::deserializeNode,
                () -> {})
                .persisted()
                .done();
    }

    public static ComponentMount<AeGridNode> mount(double idlePowerUsage) {
        return AE_GRID.mount(context -> new AeGridNode(context, idlePowerUsage));
    }

    public <T extends IGridNodeService> AeGridNode addService(Class<T> serviceClass, T service) {
        services.add(new ServiceRegistration<>(serviceClass, service));
        IManagedGridNode node = managedNode;
        if (node != null) {
            if (node.isReady()) {
                throw new IllegalStateException("AE grid services must be registered before node creation");
            }
            node.addService(serviceClass, service);
        }
        return this;
    }

    public void addPendingWorkProcessor(PendingWorkProcessor processor) {
        pendingWorkProcessors.add(processor);
    }

    public AeNetworkAccessor network() {
        return networkAccessor;
    }

    public IActionSource actionSource() {
        return actionSource;
    }

    public void requestCraftingProviderUpdate() {
        IManagedGridNode node = managedNode;
        if (node != null) {
            ICraftingProvider.requestUpdate(node);
        }
    }

    /**
     * Sibling trait registration hook for AE2 Pattern Access Terminal integration. The pattern
     * provider trait calls this during dependency resolution so the AE2 terminal can find it
     * through this trait's {@link PatternContainer} surface. Passing {@code null} unregisters.
     */
    public void setPatternDelegate(@Nullable PatternContainer delegate) {
        this.patternDelegate = delegate;
    }

    // ---- PatternContainer ----

    /**
     * The AE2 grid this node is currently connected to, or {@code null} if the node has not been
     * created, was destroyed, or has not booted into a grid. Also serves as
     * {@link PatternContainer#getGrid()} — the same reference AE2 uses to detect that a pattern
     * container has lost its grid.
     */
    @Override
    public @Nullable IGrid getGrid() {
        IManagedGridNode node = managedNode;
        if (node == null) {
            return null;
        }
        IGridNode actual = node.getNode();
        return actual == null ? null : actual.getGrid();
    }

    @Override
    public boolean isVisibleInTerminal() {
        PatternContainer delegate = patternDelegate;
        return delegate != null && delegate.isVisibleInTerminal();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        PatternContainer delegate = patternDelegate;
        return delegate == null ? InternalInventory.empty() : delegate.getTerminalPatternInventory();
    }

    @Override
    public long getTerminalSortOrder() {
        PatternContainer delegate = patternDelegate;
        return delegate == null ? 0L : delegate.getTerminalSortOrder();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        PatternContainer delegate = patternDelegate;
        return delegate == null ? PatternContainerGroup.nothing() : delegate.getTerminalGroup();
    }

    // ---- lifecycle ----

    @Override
    public void onMachineLoad() {
        scheduleNodeCreation();
    }

    @Override
    public void onMachineUnload() {
        // MC 26.1 routes both block removal and chunk unload through BlockEntity#setRemoved, so
        // this single hook covers both teardown paths; the node NBT was persisted via nodeData.
        destroyNode();
    }

    // ---- IInWorldGridNodeHost / IActionHost ----

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        IManagedGridNode node = managedNode;
        return node == null ? null : node.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        IManagedGridNode node = managedNode;
        return node == null ? null : node.getNode();
    }

    // ---- IGridTickable ----

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 20, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        boolean anyDrained = false;
        for (PendingWorkProcessor processor : pendingWorkProcessors) {
            if (processor.processPending(networkAccessor)) {
                anyDrained = true;
            }
        }
        return anyDrained ? TickRateModulation.URGENT : TickRateModulation.SLEEP;
    }

    /**
     * Sync traits call this after Pass 1 stores a fresh plan, asking AE to wake this node if its
     * tick manager had put it to sleep.
     */
    void signalPendingWork() {
        IManagedGridNode managed = managedNode;
        if (managed == null) {
            return;
        }
        if (!managed.isOnline() || !managed.hasGridBooted()) {
            return;
        }
        IGridNode node = managed.getNode();
        if (node == null) {
            return;
        }
        try {
            node.getGrid().getTickManager().alertDevice(node);
        } catch (Throwable ignored) {
            // alertDevice can throw if we are not registered as tickable; ignore — we will tick
            // next AE cycle anyway.
        }
    }

    // ---- node lifecycle and persistence ----

    private void markNodeDataDirty() {
        nodeData.markDirty();
        data().markPersistedStateChanged();
    }

    private void onGridStateChanged() {
        // Online/offline flips change what network-backed ports can see, so the machine-wide
        // resource version must move or recipe logic keeps a stale failed-search cache.
        machine().machineComponents().noteResourceContentChanged();
    }

    private void serializeNode(ValueOutput output) {
        IManagedGridNode node = managedNode;
        if (node != null) {
            node.serialize(output);
        }
    }

    private void deserializeNode(ValueInput input) {
        ensureNode().deserialize(input);
    }

    private IManagedGridNode ensureNode() {
        IManagedGridNode node = managedNode;
        if (node == null) {
            node = GridHelper.createManagedNode(this, LISTENER)
                    .setInWorldNode(true)
                    .setExposedOnSides(EnumSet.allOf(Direction.class))
                    .setIdlePowerUsage(idlePowerUsage)
                    .setTagName(NODE_TAG);
            for (ServiceRegistration<?> service : services) {
                service.addTo(node);
            }
            managedNode = node;
        }
        return node;
    }

    private void scheduleNodeCreation() {
        if (nodeCreationPending) {
            return;
        }
        Level level = machine().getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        nodeCreationPending = true;
        GridHelper.onFirstTick(machine(), ignored -> {
            nodeCreationPending = false;
            if (machine().isRemoved()) {
                return;
            }
            Level currentLevel = machine().getLevel();
            if (currentLevel == null || currentLevel.isClientSide()) {
                return;
            }
            IManagedGridNode node = ensureNode();
            if (!node.isReady()) {
                node.create(currentLevel, machine().getBlockPos());
            }
        });
    }

    private void destroyNode() {
        nodeCreationPending = false;
        IManagedGridNode node = managedNode;
        if (node != null) {
            node.destroy();
            managedNode = null;
        }
    }

    private record ServiceRegistration<T extends IGridNodeService>(Class<T> serviceClass, T service) {

        void addTo(IManagedGridNode node) {
            node.addService(serviceClass, service);
        }
    }
}
