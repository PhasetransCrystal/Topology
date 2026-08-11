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
 * GameTest 专用的"被破坏行为"储存机器:每台只挂一个资源储存端口,无配方逻辑,容量按
 * 行为档位阈值预留余量,测试直接经 trait handler 预置存量后破坏方块并断言世界效果。
 *
 * <p>
 * 门控与 {@link TopoScalarGameTestFixtures} 同源;必须在 {@code Machines.freeze()} 前由
 * {@code OfficialTopoPlugin#registerMachines} 调用 {@link #initMachines()},且仅在夹具启用时。
 *
 * <p>
 * 固定容量(测试断言依赖,勿调):物品 2 槽;能量 10,000,000(覆盖耗散/电弧/爆燃三档);
 * 高级能量 100,000(密度 10 折算后覆盖电弧档);热量 200,000(覆盖余温与满档热闪);流体单罐 8,000mB。
 */
public final class TopoMachineDestroyedGameTestFixtures {

    public static final int ITEM_SLOTS = 2;
    public static final int ENERGY_CAPACITY = 10_000_000;
    public static final int ADVANCED_CAPACITY = 100_000;
    public static final int HEAT_CAPACITY = 200_000;
    public static final int FLUID_CAPACITY_MB = 8_000;

    private static MachineDefinition itemChest;
    private static MachineDefinition energyBuffer;
    private static MachineDefinition advancedEnergyBuffer;
    private static MachineDefinition heatBuffer;
    private static MachineDefinition fluidTank;

    private TopoMachineDestroyedGameTestFixtures() {}

    /** 在 {@code Machines.freeze()} 之前调用;夹具未启用时严禁调用。 */
    public static void initMachines() {
        if (itemChest != null) {
            return;
        }
        itemChest = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_destroyed_item_chest")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Destroyed Item Chest", "GameTest Destroyed Item Chest")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.storage(ItemResourcePort.ITEM_STORAGE, ITEM_SLOTS))
                .build();
        energyBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_destroyed_energy_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Destroyed Energy Buffer", "GameTest Destroyed Energy Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.ENERGY_STORAGE,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        ENERGY_CAPACITY))
                .build();
        advancedEnergyBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_destroyed_advanced_energy_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Destroyed Advanced Energy Buffer", "GameTest Destroyed Advanced Energy Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.ADVANCED_ENERGY_STORAGE,
                        BuiltinTopoResourceIntegrations.ADVANCED_ENERGY,
                        ADVANCED_CAPACITY))
                .build();
        heatBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_destroyed_heat_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Destroyed Heat Buffer", "GameTest Destroyed Heat Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.HEAT_STORAGE,
                        BuiltinTopoResourceIntegrations.HEAT,
                        HEAT_CAPACITY))
                .build();
        fluidTank = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_destroyed_fluid_tank")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Destroyed Fluid Tank", "GameTest Destroyed Fluid Tank")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(FluidResourcePort.storage(FluidResourcePort.FLUID_STORAGE, 1, FLUID_CAPACITY_MB))
                .build();
    }

    public static MachineDefinition itemChest() {
        return requireInitialized(itemChest, "item chest machine");
    }

    public static MachineDefinition energyBuffer() {
        return requireInitialized(energyBuffer, "energy buffer machine");
    }

    public static MachineDefinition advancedEnergyBuffer() {
        return requireInitialized(advancedEnergyBuffer, "advanced energy buffer machine");
    }

    public static MachineDefinition heatBuffer() {
        return requireInitialized(heatBuffer, "heat buffer machine");
    }

    public static MachineDefinition fluidTank() {
        return requireInitialized(fluidTank, "fluid tank machine");
    }

    private static <T> T requireInitialized(T value, String what) {
        if (value == null) {
            throw new IllegalStateException(
                    "Machine-destroyed gametest fixture " + what + " is not initialized; fixtures are gated by " + "neoforge.enabledGameTestNamespaces / -Doi.gametest.fixtures=true");
        }
        return value;
    }
}
