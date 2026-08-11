package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.api.tick.TickHandle;
import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.MachineComponents;
import net.ptcrys.topo.apiv2.machine.data.DataValueIoField;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolSettings;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

/**
 * Direct ("stocking") input port: no local cache — the recipe side reads a virtual
 * {@link AeStockingResourceHandler} that transparently proxies the AE network under per-slot
 * configured targets. The port is recipe-side only; it deliberately exposes no automation block
 * capability, since piping a network proxy back out of the machine invites transfer loops.
 *
 * <p>
 * The trait also ticks a cheap availability-drift probe every 20 ticks: network stock can
 * change without this machine seeing any local mutation, and recipe logic caches failed searches
 * until the machine-wide resource version moves. The probe bumps that version when the cached
 * network counts of the configured keys drift (or the grid flips online/offline).
 */
public abstract class AeStockingPort<R extends Resource>
                                    extends MachineTicker
                                    implements AeConfiguredResource<R> {

    private final Class<R> resourceClass;
    private final MachineResourceType<R> machineResourceType;
    private final AeStockingResourceHandler<R> handler;
    private final DataValueIoField stockingData;
    private long lastAvailabilitySignature;

    protected AeStockingPort(
                             ComponentContext<? extends AeStockingPort<R>> context,
                             Class<R> resourceClass,
                             R emptyResource,
                             int slots,
                             MachineResourceType<R> machineResourceType) {
        super(context);
        this.resourceClass = resourceClass;
        this.machineResourceType = machineResourceType;
        this.handler = new AeStockingResourceHandler<>(
                resourceClass,
                emptyResource,
                slots,
                () -> AeNetworkAccessor.OFFLINE,
                this::onStockingChanged);
        this.stockingData = data().valueIoField(
                "ae_stocking",
                handler::serialize,
                handler::deserialize,
                () -> {})
                .persisted()
                .done();
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        AeGridNode grid = traits.require(AeGridNode.AE_GRID);
        handler.setNetworkSupplier(grid::network);
    }

    public AeStockingResourceHandler<R> handler() {
        return handler;
    }

    public void setConfigSlot(int slot, AeConfigSlot<R> config) {
        handler.setConfigSlot(slot, config);
    }

    @Override
    public Class<R> aeConfigResourceType() {
        return resourceClass;
    }

    @Override
    public int aeConfigSlotCount() {
        return handler.size();
    }

    @Override
    public AeConfigSlot<R> aeConfigSlot(int slot) {
        return handler.configSlot(slot);
    }

    @Override
    public void setAeConfigSlot(int slot, AeConfigSlot<R> config) {
        setConfigSlot(slot, config);
    }

    @Override
    public long aeStockedAmount(int slot) {
        return handler.getAmountAsLong(slot);
    }

    @Override
    protected <Q extends Resource> @Nullable ResourceHandler<Q> recipeResourceHandler(
                                                                                      MachineResourceType<Q> requestedType,
                                                                                      RecipeRole requestedIo) {
        if (requestedType != machineResourceType || requestedIo == RecipeRole.NONE || !RecipeRole.INPUT.allows(requestedIo)) {
            return null;
        }
        return requestedType.castHandler(handler);
    }

    @Override
    public RecipeSearchPoolId recipeSearchPoolId() {
        return RecipeSearchPoolSettings.resolve(
                machine().machineComponents(), RecipeSearchPoolId.DEFAULT);
    }

    @Override
    public boolean recipePoolScoped() {
        return !recipeSearchPoolId().isUniversal();
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        long signature = handler.availabilitySignature();
        if (signature != lastAvailabilitySignature) {
            lastAvailabilitySignature = signature;
            machine().machineComponents().noteResourceContentChanged();
        }
    }

    private void onStockingChanged() {
        // Configured-window or refund-queue mutations change both what recipes can match and what
        // must be persisted.
        machine().machineComponents().noteResourceContentChanged();
        stockingData.markDirty();
        data().markPersistedStateChanged();
    }
}
