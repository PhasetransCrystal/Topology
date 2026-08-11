package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

/**
 * GameTest-only buffer machines for pipe network tests: pure storage traits with the default
 * {@code PortAccess.storage()} (capability BOTH on every side), so pipes can extract
 * from and insert into them without recipe noise. Item tests use vanilla chests instead.
 *
 * <p>
 * Gated by {@link TopoScalarGameTestFixtures#enabled()}; registered in
 * {@code OfficialTopoPlugin#registerMachines} before the machine freeze.
 */
public final class TopoPipeGameTestFixtures {

    public static final int SCALAR_CAPACITY = 1_000_000;
    public static final int FLUID_CAPACITY_MB = 100_000;

    private static MachineDefinition energyBuffer;
    private static MachineDefinition advancedEnergyBuffer;
    private static MachineDefinition heatBuffer;
    private static MachineDefinition fluidTank;
    private static MachineDefinition itemBuffer;
    private static MachineDefinition itemSink;

    private TopoPipeGameTestFixtures() {}

    /** Call before {@code Machines.freeze()}, only when fixtures are enabled. */
    public static void initMachines() {
        if (energyBuffer != null) {
            return;
        }
        energyBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_pipe_energy_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Pipe Energy Buffer", "GameTest Pipe Energy Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.ENERGY_STORAGE,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        SCALAR_CAPACITY))
                .build();
        advancedEnergyBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_pipe_advanced_energy_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Pipe Advanced Energy Buffer", "GameTest Pipe Advanced Energy Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.ADVANCED_ENERGY_STORAGE,
                        BuiltinTopoResourceIntegrations.ADVANCED_ENERGY,
                        SCALAR_CAPACITY))
                .build();
        heatBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_pipe_heat_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Pipe Heat Buffer", "GameTest Pipe Heat Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.HEAT_STORAGE,
                        BuiltinTopoResourceIntegrations.HEAT,
                        SCALAR_CAPACITY))
                .build();
        fluidTank = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_pipe_fluid_tank")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Pipe Fluid Tank", "GameTest Pipe Fluid Tank")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(FluidResourcePort.storage(FluidResourcePort.FLUID_STORAGE, 1, FLUID_CAPACITY_MB))
                .build();
        itemBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_pipe_item_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Pipe Item Buffer", "GameTest Pipe Item Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.storage(ItemResourcePort.ITEM_INPUT_1, 9))
                .build();
        itemSink = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_pipe_item_sink")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Pipe Item Sink", "GameTest Pipe Item Sink")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                // input(): capability exposes an INSERT-only directional view — covers the pipe
                // fast lane's view-forwarding path (real consumer machines look like this).
                .component(ItemResourcePort.input(ItemResourcePort.ITEM_INPUT_1, 9))
                .build();
    }

    public static MachineDefinition energyBuffer() {
        return requireInitialized(energyBuffer, "energy buffer");
    }

    public static MachineDefinition advancedEnergyBuffer() {
        return requireInitialized(advancedEnergyBuffer, "advanced energy buffer");
    }

    public static MachineDefinition heatBuffer() {
        return requireInitialized(heatBuffer, "heat buffer");
    }

    public static MachineDefinition fluidTank() {
        return requireInitialized(fluidTank, "fluid tank");
    }

    public static MachineDefinition itemBuffer() {
        return requireInitialized(itemBuffer, "item buffer");
    }

    public static MachineDefinition itemSink() {
        return requireInitialized(itemSink, "item sink");
    }

    private static <T> T requireInitialized(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("Pipe gametest fixture " + what + " is not initialized; fixtures are " + "gated by neoforge.enabledGameTestNamespaces / -Doi.gametest.fixtures=true");
        }
        return value;
    }
}
