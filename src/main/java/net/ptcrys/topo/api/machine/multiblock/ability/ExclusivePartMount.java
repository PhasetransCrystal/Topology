package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.component.ComponentContext;
import net.ptcrys.topo.api.machine.component.ComponentContribution;
import net.ptcrys.topo.api.machine.component.ComponentKey;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponent;

/**
 * Marks a machine definition as an <b>exclusive part</b>.
 *
 * <p>
 * This is a sibling component contribution, not a wrapper around a port mount: add it next to the
 * part mount, and the marker can be combined with any part type (item, fluid, energy, …):
 *
 * <pre>{@code
 * .component(ItemPort.mount(ItemPort.INPUT, 3, PortAccess.input()).role(INPUT))
 * .component(ExclusivePartMount.of())
 * }</pre>
 *
 * <p>
 * The marker mounts a no-op {@link MachineComponent} carrying {@link ExclusivePartAttachment}; the
 * member's {@code MachineDefinition} metadata then reports the part as exclusive. Classification
 * (structure cell vs. machine member) is still decided by the part mount itself.
 */
public final class ExclusivePartMount implements ComponentContribution {

    private static final ComponentKey<Marker> KEY = ComponentKey.of(
            OfficialTopoAPIPlugin.INSTANCE.machine().id("exclusive_part"), Marker.class);

    private ExclusivePartMount() {}

    /** The marker contribution to add next to a part mount. */
    public static ExclusivePartMount of() {
        return new ExclusivePartMount();
    }

    @Override
    public ComponentMount<?> mount() {
        return KEY.mount(Marker::new).withMetadata(ExclusivePartAttachment.INSTANCE);
    }

    /** No-op runtime marker; the exclusivity lives in the mount metadata. */
    private static final class Marker extends MachineComponent {

        private Marker(ComponentContext<Marker> context) {
            super(context);
        }
    }
}
