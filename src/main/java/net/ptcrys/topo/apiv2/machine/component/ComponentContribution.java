package net.ptcrys.topo.apiv2.machine.component;

/**
 * A staged machine-trait declaration that resolves to exactly one concrete {@link ComponentMount}.
 *
 * <p>
 * Contract: {@link #mount()} returns the underlying mount eagerly and without side effects, so
 * the machine builder can accept any staged wrapper (for example a part-capability-aware port
 * wrapper used without its capability) while depending only on this trait-layer abstraction.
 */
public interface ComponentContribution {

    ComponentMount<?> mount();
}
