package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.machine.common.component.CreativeEnergyGenerator;
import net.ptcrys.topo.data.machine.common.component.resource.CreativeScalarResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

/**
 * GameTest-only creative energy generator / cell machines. Traits live in production code; the
 * machine definitions themselves are fixtures (not in {@code BuiltinTopoMachines}).
 *
 * <p>
 * Gated by {@link TopoScalarGameTestFixtures#enabled()}; must be registered before
 * {@code Machines.freeze()}.
 */
public final class TopoCreativeMachineGameTestFixtures {

    private static final int GENERATOR_BUFFER_CAPACITY = CreativeEnergyGenerator.MAX_RATE;

    private static MachineDefinition creativeGenerator;
    private static MachineDefinition creativeCell;

    private TopoCreativeMachineGameTestFixtures() {}

    public static boolean enabled() {
        return TopoScalarGameTestFixtures.enabled();
    }

    public static void initMachines() {
        if (creativeGenerator != null) {
            return;
        }
        creativeGenerator = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_creative_energy_generator")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Creative Energy Generator", "GameTest Creative Energy Generator")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.mount(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        GENERATOR_BUFFER_CAPACITY,
                        PortAccess.output()))
                .component(CreativeEnergyGenerator.mount(
                        CreativeEnergyGenerator.CREATIVE_GENERATOR,
                        ScalarResourcePort.ENERGY_OUTPUT_1))
                .build();
        creativeCell = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_creative_energy_cell")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Creative Energy Cell", "GameTest Creative Energy Cell")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(CreativeScalarResourcePort.mount(
                        CreativeScalarResourcePort.CREATIVE_ENERGY_STORAGE,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        PortAccess.storage()))
                .build();
    }

    public static MachineDefinition creativeGenerator() {
        return require(creativeGenerator, "creative energy generator");
    }

    public static MachineDefinition creativeCell() {
        return require(creativeCell, "creative energy cell");
    }

    private static MachineDefinition require(MachineDefinition definition, String name) {
        if (definition == null) {
            throw new IllegalStateException(
                    "GameTest " + name + " is not initialized; fixtures are gated by " + "neoforge.enabledGameTestNamespaces / -Doi.gametest.fixtures=true");
        }
        return definition;
    }
}
