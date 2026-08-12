package net.ptcrys.topo.api.machine.component.render;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.MachineComponents;
import net.ptcrys.topo.api.machine.component.ServiceKey;
import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.client.machine.render.MachineRenderState;

/**
 * Base for traits that feed a block-entity renderer, generically paired with the
 * {@link MachineRenderState} that draws it.
 *
 * <p>
 * A render trait declares its display data as computed, client-synced data fields (see
 * {@code data().intField(...).computedEveryInternalTick(...)}); its {@link #createRenderState() render
 * state} binds those fields and draws them on the client. The base owns every concern that is not "what
 * data" and "how to draw":
 *
 * <ul>
 * <li><b>mounting</b>: it is a {@link MachineTicker}, so the owning machine ticks and therefore
 * refreshes and syncs its computed display fields every tick;</li>
 * <li><b>discovery</b>: {@link #renderKey(String, Class)} pre-binds {@link #CAPABILITY} so the renderer
 * finds the trait without the subclass writing any capability wiring.</li>
 * </ul>
 *
 * @param <S> the render state paired with this trait
 */
public abstract class MachineRenderComponent<S extends MachineRenderState<?>> extends MachineTicker {

    /** Capability the block-entity renderer queries to find render-contributing traits. */
    public static final ServiceKey<MachineRenderComponent<?>, Void> KEY = serviceKey();

    protected MachineRenderComponent(ComponentContext<? extends MachineRenderComponent<?>> context) {
        super(context);
    }

    /** Build a trait key with the render capability already bound, so subclasses never wire it by hand. */
    protected static <T extends MachineRenderComponent<?>> ComponentKey<T> renderKey(String path, Class<T> type) {
        return ComponentKey.id(path, type).service(KEY, (trait, unused) -> trait);
    }

    @Override
    public final void resolveDependencies(MachineComponents traits) {
        resolveRenderDependencies(traits);
    }

    /** Resolve sibling traits the computed fields read from. Override instead of {@code resolveDependencies}. */
    protected void resolveRenderDependencies(MachineComponents traits) {}

    /**
     * A render trait ticks only so the machine refreshes and syncs its computed display fields each
     * tick; the values themselves come from the fields' computers, so there is usually nothing to do
     * here. Subclasses may override for per-tick work that is not a computed field.
     */
    @Override
    public void tick(long gameTime, TickHandle handle) {}

    /**
     * Create the client render state that draws this trait, binding the synced fields it reads. This
     * pairs the trait with its renderer directly — no registry, holder, or registration step. Called by
     * the shared machine renderer on the client; never invoked server-side.
     */
    public abstract S createRenderState();

    // The capability key is type-erased; the generic self-type cannot flow through a Class literal, so
    // the wildcard cast is isolated here.
    private static ServiceKey<MachineRenderComponent<?>, Void> serviceKey() {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Class<MachineRenderComponent<?>> type = (Class) MachineRenderComponent.class;
        return ServiceKey.oi("machine_render", type, Void.class);
    }
}
