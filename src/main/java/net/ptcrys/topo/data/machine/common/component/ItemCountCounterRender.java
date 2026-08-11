package net.ptcrys.topo.data.machine.common.component;

import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.component.render.MachineRenderComponent;
import net.ptcrys.topo.api.machine.data.DataInt;
import net.ptcrys.topo.client.machine.render.item.ItemCountRenderState;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Reference render trait: shows the total item count of an item-storage machine above the block.
 *
 * <p>
 * This is the canonical example of contributing a block-entity renderer. The whole trait is "what to
 * display": a computed, client-synced field that recomputes the storage total every tick, plus the
 * storage it reads. Mounting and discovery live in {@link MachineRenderComponent}; the matching draw code is
 * {@code ItemCountRenderState}, which binds {@link #count()} directly.
 */
public final class ItemCountCounterRender extends MachineRenderComponent<ItemCountRenderState> {

    public static final ComponentKey<ItemCountCounterRender> KEY = renderKey("item_count_counter", ItemCountCounterRender.class);

    private final ComponentKey<ItemResourcePort> storageKey;
    private final DataInt count = data().intField("count", 0)
            .computedEveryInternalTick(() -> requireStorage().computeItemTotal())
            .saveNone()
            .syncToClientAtEndOfDirtyTick()
            .done();
    private @Nullable ItemResourcePort storage;

    private ItemCountCounterRender(ComponentContext<ItemCountCounterRender> context, ComponentKey<ItemResourcePort> storageKey) {
        super(context);
        this.storageKey = Objects.requireNonNull(storageKey, "storage key");
    }

    public static ComponentMount<ItemCountCounterRender> mount(ComponentKey<ItemCountCounterRender> key, ComponentKey<ItemResourcePort> storageKey) {
        return key.mount(context -> new ItemCountCounterRender(context, storageKey));
    }

    @Override
    protected void resolveRenderDependencies(MachineComponents traits) {
        storage = traits.require(storageKey);
    }

    @Override
    public ItemCountRenderState createRenderState() {
        return new ItemCountRenderState(this);
    }

    /** The synced display value the client render state reads. */
    public DataInt count() {
        return count;
    }

    private ItemResourcePort requireStorage() {
        ItemResourcePort current = storage;
        if (current == null) {
            throw new IllegalStateException("Item count counter has not resolved storage " + storageKey);
        }
        return current;
    }
}
