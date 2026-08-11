package net.ptcrys.topo.datav2.machine.common.component;

import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.MachineComponent;
import net.ptcrys.topo.apiv2.machine.component.MachineComponents;

/** Example controller that resolves three named sibling traits and reads their domain data. */
public final class ExampleController extends MachineComponent {

    public static final ComponentKey<ExampleController> KEY = ComponentKey.oi("controller", ExampleController.class);

    private ExampleController(ComponentContext<ExampleController> context) {
        super(context);
    }

    public static ComponentMount<ExampleController> mount() {
        return KEY.mount(ExampleController::new);
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        traits.require(ExampleBuffer.LEFT);
        traits.require(ExampleBuffer.RIGHT);
        traits.require(ExampleCatalyst.KEY);
    }

    public int totalProcessValueExample() {
        MachineComponents traits = machine().machineComponents();
        ExampleBuffer leftBuffer = traits.require(ExampleBuffer.LEFT);
        ExampleBuffer rightBuffer = traits.require(ExampleBuffer.RIGHT);
        ExampleCatalyst catalyst = traits.require(ExampleCatalyst.KEY);

        return leftBuffer.processValue() + rightBuffer.processValue() + catalyst.processValue();
    }

    public int totalProcessValueViaCapabilityExample() {
        return machine().machineComponents().services(ExampleBuffer.PROCESS_VALUE, null).stream()
                .mapToInt(match -> match.value().processValue())
                .sum();
    }
}
