package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.OrientedMachineBlock;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.common.component.CreativeEnergyGenerator;
import net.ptcrys.topo.datav2.machine.common.component.resource.CreativeScalarResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

/**
 * GameTest-only creative energy generator / cell machines. Traits live in production code; the
 * machine definitions themselves are fixtures (not in {@code BuiltinOIMachines}).
 *
 * <p>
 * Gated by {@link OIScalarGameTestFixtures#enabled()}; must be registered before
 * {@code Machines.freeze()}.
 */
public final class OICreativeMachineGameTestFixtures {

    private static final int GENERATOR_BUFFER_CAPACITY = CreativeEnergyGenerator.MAX_RATE;

    private static MachineDefinition creativeGenerator;
    private static MachineDefinition creativeCell;

    private OICreativeMachineGameTestFixtures() {}

    public static boolean enabled() {
        return OIScalarGameTestFixtures.enabled();
    }

    public static void initMachines() {
        if (creativeGenerator != null) {
            return;
        }
        creativeGenerator = OfficialOIPlugin.INSTANCE.machine().machine("gametest_creative_energy_generator")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Creative Energy Generator", "GameTest Creative Energy Generator")
                .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.mount(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        GENERATOR_BUFFER_CAPACITY,
                        PortAccess.output()))
                .component(CreativeEnergyGenerator.mount(
                        CreativeEnergyGenerator.CREATIVE_GENERATOR,
                        ScalarResourcePort.ENERGY_OUTPUT_1))
                .build();
        creativeCell = OfficialOIPlugin.INSTANCE.machine().machine("gametest_creative_energy_cell")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Creative Energy Cell", "GameTest Creative Energy Cell")
                .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
                .component(CreativeScalarResourcePort.mount(
                        CreativeScalarResourcePort.CREATIVE_ENERGY_STORAGE,
                        BuiltinOIResourceIntegrations.ENERGY,
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
