package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.OrientedMachineBlock;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;

import net.minecraft.core.Direction;

/**
 * GameTest 专用的运行时 side-IO 配置夹具机器。一台缓存机挂两个可配置端口,覆盖两种声明包络:
 *
 * <ul>
 * <li>物品口:storage(UP) + 可配置 —— BOTH 包络,玩家可在 NONE/INSERT/EXTRACT/BOTH 间循环;</li>
 * <li>能量口:output(DOWN) + 可配置 —— EXTRACT 包络,玩家只能在 NONE/EXTRACT 间循环,
 * 用于子集越界拒绝的断言。</li>
 * </ul>
 *
 * <p>
 * 门控与 {@link TopoScalarGameTestFixtures#enabled()} 同源;注册须在 {@code Machines.freeze()} 之前
 * (由 {@code OfficialTopoPlugin#registerMachines} 调用)。
 */
public final class TopoSideIoGameTestFixtures {

    public static final int ENERGY_CAPACITY = 100;

    private static MachineDefinition sideIoBuffer;

    private TopoSideIoGameTestFixtures() {}

    public static boolean enabled() {
        return TopoScalarGameTestFixtures.enabled();
    }

    /** 在 {@code Machines.freeze()} 之前调用。 */
    public static void initMachines() {
        if (sideIoBuffer != null) {
            return;
        }
        sideIoBuffer = OfficialTopoPlugin.INSTANCE.machine().machine("gametest_side_io_buffer")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Side IO Buffer", "GameTest Side IO Buffer")
                .creativeTab(BuiltinTopoCreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.mount(
                        ItemResourcePort.ITEM_STORAGE,
                        1,
                        PortAccess.storage(Direction.UP).withPlayerConfigurableSides()))
                .component(ScalarResourcePort.mount(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinTopoResourceIntegrations.ENERGY,
                        ENERGY_CAPACITY,
                        PortAccess.output(Direction.DOWN).withPlayerConfigurableSides()))
                .build();
    }

    public static MachineDefinition sideIoBuffer() {
        if (sideIoBuffer == null) {
            throw new IllegalStateException(
                    "Side-IO gametest fixture machine is not initialized; fixtures are gated by " + "neoforge.enabledGameTestNamespaces / -Doi.gametest.fixtures=true");
        }
        return sideIoBuffer;
    }
}
