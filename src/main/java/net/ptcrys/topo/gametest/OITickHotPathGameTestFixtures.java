package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.api.tick.TickHandle;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.OrientedMachineBlock;
import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * GameTest 夹具:机器 tick 热路径行为(落盘节流、框架 after-tick 去重、标量直通道多口分配)。
 * 门控与注册窗口同 {@link OIScalarGameTestFixtures};本夹具复用标量夹具的发电配方
 * (煤 → 5 能量/t × 20t),只新增机器形态。
 *
 * <p>
 * 固定数值(测试断言依赖,勿调):
 * <ul>
 * <li>双口发电机:能量输出口 1 容量 {@value #DUAL_PORT_FIRST_CAPACITY},
 * 能量存储口容量 {@value #DUAL_PORT_SECOND_CAPACITY};5 能量/t 在第 2 个工作 tick 必然跨口;</li>
 * <li>双 ticker 机器:两个空转 sync ticker + 一个每内部 tick 重算的计数字段。</li>
 * </ul>
 */
public final class OITickHotPathGameTestFixtures {

    public static final int DUAL_PORT_FIRST_CAPACITY = 7;
    public static final int DUAL_PORT_SECOND_CAPACITY = 100;

    private static final AtomicInteger RECOMPUTE_COUNT = new AtomicInteger();

    private static MachineDefinition dualPortGenerator;
    private static MachineDefinition dualTickerMachine;

    private OITickHotPathGameTestFixtures() {}

    /** 在 {@code Machines.freeze()} 之前调用,仅当 {@link OIScalarGameTestFixtures#enabled()}。 */
    public static void initMachines() {
        if (dualPortGenerator != null) {
            return;
        }
        dualPortGenerator = OfficialOIPlugin.INSTANCE.machine().machine("gametest_dual_port_energy_generator")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Dual Port Energy Generator", "GameTest Dual Port Energy Generator")
                .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
                .component(ItemResourcePort.input(ItemResourcePort.ITEM_INPUT_1, 1))
                .component(ScalarResourcePort.output(
                        ScalarResourcePort.ENERGY_OUTPUT_1,
                        BuiltinOIResourceIntegrations.ENERGY,
                        DUAL_PORT_FIRST_CAPACITY))
                .component(ScalarResourcePort.storage(
                        ScalarResourcePort.ENERGY_STORAGE,
                        BuiltinOIResourceIntegrations.ENERGY,
                        DUAL_PORT_SECOND_CAPACITY))
                .component(RecipeLogic.mount(
                        RecipeLogic.RECIPE_LOGIC_1, OIScalarGameTestFixtures.energyGeneratorType()))
                .build();
        dualTickerMachine = OfficialOIPlugin.INSTANCE.machine().machine("gametest_dual_ticker_machine")
                .block(OrientedMachineBlock::new)
                .blockEntity(MachineBlockEntity::new)
                .displayName("GameTest Dual Ticker Machine", "GameTest Dual Ticker Machine")
                .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
                .component(FixtureTicker.TICKER_A.mount(context -> new FixtureTicker(context, true)))
                .component(FixtureTicker.TICKER_B.mount(context -> new FixtureTicker(context, false)))
                .build();
    }

    public static MachineDefinition dualPortGenerator() {
        return requireInitialized(dualPortGenerator, "dual port energy generator machine");
    }

    public static MachineDefinition dualTickerMachine() {
        return requireInitialized(dualTickerMachine, "dual ticker machine");
    }

    /** 全局重算计数:每次计算字段重算 +1。测试取差值,不重置。 */
    public static int recomputeCount() {
        return RECOMPUTE_COUNT.get();
    }

    private static <T> T requireInitialized(T value, String what) {
        if (value == null) {
            throw new IllegalStateException(
                    "Tick hot path gametest fixture " + what + " is not initialized; fixtures are gated by " + "neoforge.enabledGameTestNamespaces / -Doi.gametest.fixtures=true");
        }
        return value;
    }

    /**
     * 空转 sync ticker(interval=1)。挂 A、B 两份验证"框架 after-tick 工作每机器每 tick 只跑一次";
     * 仅 A 注册计算字段,避免重算计数被字段份数放大。
     */
    static final class FixtureTicker extends MachineTicker {

        static final ComponentKey<FixtureTicker> TICKER_A = ComponentKey.oi("gametest_hot_path_ticker_a", FixtureTicker.class);
        static final ComponentKey<FixtureTicker> TICKER_B = ComponentKey.oi("gametest_hot_path_ticker_b", FixtureTicker.class);

        FixtureTicker(ComponentContext<FixtureTicker> context, boolean countsRecomputes) {
            super(context);
            if (countsRecomputes) {
                data().intField("recompute_count", 0)
                        .saveNone()
                        .syncNone()
                        .computedEveryInternalTick(RECOMPUTE_COUNT::incrementAndGet)
                        .done();
            }
        }

        @Override
        public void tick(long gameTime, TickHandle handle) {}
    }
}
