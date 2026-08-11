package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.data.machine.common.component.CreativeEnergyGenerator;
import net.ptcrys.topo.data.machine.common.component.resource.CreativeScalarResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

/**
 * 创造机器游戏内测试(全局编号 299-300):创造能源发电机(每 tick 把输出缓存补满到可设发电量、
 * 抽走后立即再生、rate 任意可设)与创造储能单元(容量 = Long.MAX_VALUE、默认空、真 long 累积、
 * 事务回滚)。
 */
public final class CreativeMachineGameTests {

    private static final String SUITE = "creative_machine";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 2;

    private CreativeMachineGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!TopoScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("creative_machine"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(event, environment, index++,
                "creative_generator_tops_output_to_settable_rate",
                "Tests the creative energy generator: each tick refills its energy output buffer to the " + "player-set rate, regenerates after extraction, stops at rate 0, and accepts an " + "arbitrary new rate.",
                CreativeMachineGameTests::creativeGeneratorTopsOutputToSettableRate);
        register(event, environment, index,
                "creative_cell_starts_empty_with_long_capacity",
                "Tests the creative energy cell: capacity is Long.MAX_VALUE, it starts empty, accumulates " + "past the int ceiling as a real long buffer, drains on extraction, and an aborted " + "transaction rolls the stored amount back.",
                CreativeMachineGameTests::creativeCellStartsEmptyWithLongCapacity);
    }

    /** 测试 299:创造发电机——每 tick 补满到 rate、抽空后再生、rate=0 不再生、可设任意 rate。 */
    private static void creativeGeneratorTopsOutputToSettableRate(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        MachineBlockEntity machine = placeMachine(helper, pos, TopoCreativeMachineGameTestFixtures.creativeGenerator());
        CreativeEnergyGenerator generator = machine.machineComponents().require(CreativeEnergyGenerator.CREATIVE_GENERATOR);
        ScalarResourcePort output = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
        generator.setRate(5000);

        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    if (output.storedAmount() != 5000) {
                        helper.fail("Generator must top its buffer to the rate 5000, found " + output.storedAmount());
                    }
                    drain(output);
                })
                .thenExecuteAfter(2, () -> {
                    if (output.storedAmount() != 5000) {
                        helper.fail("After draining, the generator must regenerate back to 5000, found " + output.storedAmount());
                    }
                    generator.setRate(0);
                    drain(output);
                })
                .thenExecuteAfter(3, () -> {
                    if (output.storedAmount() != 0) {
                        helper.fail("At rate 0 the generator must not regenerate, found " + output.storedAmount());
                    }
                    generator.setRate(123_456);
                })
                .thenExecuteAfter(3, () -> {
                    if (output.storedAmount() != 123_456) {
                        helper.fail("Generator must follow an arbitrary new rate 123456, found " + output.storedAmount());
                    }
                })
                .thenSucceed();
    }

    /** 测试 300:创造储能单元——容量 Long.MAX、默认空、累积过 int 上限、抽取减少、事务中止回滚。 */
    private static void creativeCellStartsEmptyWithLongCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        MachineBlockEntity machine = placeMachine(helper, pos, TopoCreativeMachineGameTestFixtures.creativeCell());
        CreativeScalarResourcePort cell = machine.machineComponents().require(CreativeScalarResourcePort.CREATIVE_ENERGY_STORAGE);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (cell.capacityAmount() != Long.MAX_VALUE) {
                        helper.fail("Creative cell capacity must be Long.MAX_VALUE, found " + cell.capacityAmount());
                        return;
                    }
                    if (cell.storedAmount() != 0L) {
                        helper.fail("Creative cell must start empty, found " + cell.storedAmount());
                        return;
                    }
                    ResourceHandler<ScalarResource> handler = cell.handler();
                    ScalarResource energy = cell.resource();
                    // Two full-int inserts must accumulate past the int ceiling — proving the long buffer.
                    insertCommitted(handler, energy, Integer.MAX_VALUE);
                    insertCommitted(handler, energy, Integer.MAX_VALUE);
                    long expected = 2L * Integer.MAX_VALUE;
                    if (cell.storedAmount() != expected) {
                        helper.fail("Two full-int inserts must accumulate to " + expected + " (real long buffer), found " + cell.storedAmount());
                        return;
                    }
                    // Extraction drains.
                    int extracted;
                    try (Transaction transaction = Transaction.openRoot()) {
                        extracted = handler.extract(0, energy, Integer.MAX_VALUE, transaction);
                        transaction.commit();
                    }
                    if (extracted != Integer.MAX_VALUE || cell.storedAmount() != (long) Integer.MAX_VALUE) {
                        helper.fail("Extraction must drain by " + Integer.MAX_VALUE + " to " + Integer.MAX_VALUE + ", got extracted=" + extracted + " stored=" + cell.storedAmount());
                        return;
                    }
                    // Aborted transaction must roll the stored amount back (SnapshotJournal correctness).
                    long before = cell.storedAmount();
                    try (Transaction transaction = Transaction.openRoot()) {
                        handler.insert(energy, 1_000_000, transaction);
                        // no commit -> abort on close
                    }
                    if (cell.storedAmount() != before) {
                        helper.fail("Aborted insert must roll back to " + before + ", found " + cell.storedAmount());
                    }
                })
                .thenSucceed();
    }

    private static void insertCommitted(ResourceHandler<ScalarResource> handler, ScalarResource energy, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            handler.insert(energy, amount, transaction);
            transaction.commit();
        }
    }

    private static void drain(ScalarResourcePort storage) {
        ResourceHandler<ScalarResource> handler = storage.handler();
        ScalarResource energy = storage.resource();
        try (Transaction transaction = Transaction.openRoot()) {
            handler.extract(0, energy, Integer.MAX_VALUE, transaction);
            transaction.commit();
        }
    }

    private static MachineBlockEntity placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(pos, MachineBlockEntity.class);
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Creative machine GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 200, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
