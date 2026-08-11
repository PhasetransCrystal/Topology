package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.MachineComponent;
import net.ptcrys.topo.apiv2.machine.data.DataManualDirtyField;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolSettings;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

/**
 * ME push output port — holds up to {@code maxKinds} distinct resource kinds with saturating
 * long counts, fed by recipe outputs through the pool-scoped OUTPUT aggregation and drained
 * into the AE network by {@link AeResourceOutputPush}. AE is the only legitimate egress,
 * so the port exposes no automation block capability.
 *
 * <p>
 * Pool-scoped: emits only into the machine-level search-pool id (same config UI as input
 * hatches), so a recipe that runs on pool {@code P} only fills export buffers on {@code P}.
 */
public abstract class AeBufferPort<R extends Resource> extends MachineComponent {

    private final MachineResourceType<R> machineResourceType;
    private final AeKeyResourceBuffer<R> buffer;
    private final Runnable markBufferDataDirty;

    protected AeBufferPort(
                           ComponentContext<? extends AeBufferPort<R>> context,
                           Class<R> resourceClass,
                           R emptyResource,
                           int maxKinds,
                           MachineResourceType<R> machineResourceType) {
        super(context);
        this.machineResourceType = machineResourceType;
        this.buffer = new AeKeyResourceBuffer<>(resourceClass, emptyResource, maxKinds);
        DataManualDirtyField bufferData = machineResourceType.dataField(
                data(),
                "ae_buffer",
                buffer,
                () -> {})
                .persisted()
                .done();
        this.markBufferDataDirty = bufferData::markDirty;
        buffer.setOnChanged(this::onBufferChanged);
    }

    public AeKeyResourceBuffer<R> buffer() {
        return buffer;
    }

    @Override
    protected <Q extends Resource> @Nullable ResourceHandler<Q> recipeResourceHandler(
                                                                                      MachineResourceType<Q> requestedType,
                                                                                      RecipeRole requestedIo) {
        if (requestedType != machineResourceType || requestedIo == RecipeRole.NONE || !RecipeRole.OUTPUT.allows(requestedIo)) {
            return null;
        }
        return requestedType.castHandler(buffer);
    }

    @Override
    public boolean recipePoolScoped() {
        // UNIVERSAL export buffers join every pool's output view.
        return !recipeSearchPoolId().isUniversal();
    }

    @Override
    public RecipeSearchPoolId recipeSearchPoolId() {
        // Output-only machines default to UNIVERSAL when no config trait is present.
        return RecipeSearchPoolSettings.resolve(
                machine().machineComponents(), RecipeSearchPoolId.UNIVERSAL);
    }

    private void onBufferChanged() {
        // Buffer space freeing matters to WAITING_OUTPUT recipes, so the machine-wide resource
        // version moves with every buffer mutation, and the contents must persist.
        machine().machineComponents().noteResourceContentChanged();
        markBufferDataDirty.run();
        data().markPersistedStateChanged();
    }
}
