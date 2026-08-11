package net.ptcrys.topo.datav2.machine.common.component;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.MachineComponent;

/** Example plain trait consumed by the controller together with two buffer traits. */
public final class ExampleCatalyst extends MachineComponent {

    public static final ComponentKey<ExampleCatalyst> KEY = ComponentKey.oi("catalyst", ExampleCatalyst.class);

    private static final int PROCESS_VALUE = 5;

    private ExampleCatalyst(ComponentContext<ExampleCatalyst> context) {
        super(context);
    }

    public static ComponentMount<ExampleCatalyst> mount() {
        return KEY.mount(ExampleCatalyst::new);
    }

    public int processValue() {
        return PROCESS_VALUE;
    }
}
