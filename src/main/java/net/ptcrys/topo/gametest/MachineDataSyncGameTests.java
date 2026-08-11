package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.data.DataInt;
import net.ptcrys.topo.api.machine.data.MachineDataSyncBatcher;
import net.ptcrys.topo.api.machine.data.network.MachineDataBatchS2CPayload;
import net.ptcrys.topo.api.machine.ui.ComponentCollector;
import net.ptcrys.topo.api.machine.ui.PageCollector;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MachineDataSyncGameTests {

    private static final String SUITE = "machine_data_sync";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos FIRST_POS = new BlockPos(1, 1, 1);
    private static final BlockPos SECOND_POS = new BlockPos(3, 1, 1);
    private static final int TEST_COUNT = 5;

    private MachineDataSyncGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("machine_data_sync"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "machine_data_recipe_logic_fields_are_persist_only_and_emit_no_client_sync",
                "Tests RecipeLogic and SyncConfig none contract: running recipe fields persist on the " + "server, but do not produce machine-data client snapshots or deltas.",
                MachineDataSyncGameTests::machineDataRecipeLogicFieldsArePersistOnlyAndEmitNoClientSync);
        register(
                event,
                environment,
                index++,
                "machine_data_explicit_client_sync_field_batches_when_declared",
                "Tests MachineDataSyncBatcher with explicit test sync fields: one logical player receives one " + "batch payload containing two machine deltas.",
                MachineDataSyncGameTests::machineDataExplicitClientSyncFieldBatchesWhenDeclared);
        register(
                event,
                environment,
                index++,
                "machine_data_unready_field_access_crashes",
                "Tests MachineDataDomain explicit readiness contract: a constructed machine that has not loaded " + "or entered the world rejects business reads and writes instead of exposing defaults.",
                MachineDataSyncGameTests::machineDataUnreadyFieldAccessCrashes);
        register(
                event,
                environment,
                index++,
                "machine_data_unready_ui_collection_declares_bindings_without_field_reads",
                "Tests MachineDataDomain UI readiness contract: a client-like unready machine may declare " + "its LDLib2 UI structure, while direct business field reads still crash.",
                MachineDataSyncGameTests::machineDataUnreadyUiCollectionDeclaresBindingsWithoutFieldReads);
        register(
                event,
                environment,
                index,
                "machine_data_delta_before_baseline_crashes",
                "Tests MachineDataDomain client sync contract: an explicitly declared incremental delta applied " + "before the initial full baseline crashes instead of mutating default client fields.",
                MachineDataSyncGameTests::machineDataDeltaBeforeBaselineCrashes);
    }

    private static void machineDataRecipeLogicFieldsArePersistOnlyAndEmitNoClientSync(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper, FIRST_POS);
        insertIron(helper, FIRST_POS);

        helper.runAfterDelay(1, () -> {
            RecipeLogic logic = requireWorking(helper, machine);
            logic.setProgressForGameTest(11);

            assertNoClientSync(
                    helper,
                    machine,
                    "Recipe logic fields should be persist-only; UI values are LDLib2 menu bindings, not machine-data snapshots");

            SyncFieldMachine noneMachine = newExplicitNoneMachine(helper, SECOND_POS);
            noneMachine.field().set(7);
            assertNoClientSync(
                    helper,
                    noneMachine.machine(),
                    "syncToClientNone should make a changed field invisible to machine-data client sync");
            helper.succeed();
        });
    }

    private static void machineDataExplicitClientSyncFieldBatchesWhenDeclared(GameTestHelper helper) {
        SyncFieldMachine first = newExplicitSyncMachine(helper, FIRST_POS);
        SyncFieldMachine second = newExplicitSyncMachine(helper, SECOND_POS);
        SyncFieldMachine firstClient = syncedClientCopy(helper, first);
        SyncFieldMachine secondClient = syncedClientCopy(helper, second);

        first.field().set(11);
        second.field().set(22);

        Object player = new Object();
        List<MachineDataSyncBatcher.PlayerBatch<Object>> batches = MachineDataSyncBatcher.collectBatchesForTesting(
                List.of(first.machine().data(), second.machine().data()),
                ignored -> List.of(player),
                helper.getLevel().registryAccess());
        if (batches.size() != 1) {
            helper.fail("Expected one player batch for two dirty explicit sync machines, got " + batches.size());
        }
        MachineDataBatchS2CPayload payload = batches.getFirst().payload();
        if (payload.entries().size() != 2) {
            helper.fail("Expected one batch payload with two machine entries, got " + payload.entries().size());
        }

        applyPayload(helper, payload, Map.of(
                first.machine().getBlockPos().asLong(), firstClient.machine(),
                second.machine().getBlockPos().asLong(), secondClient.machine()));
        assertFieldValue(helper, firstClient, 11, "First client copy should receive explicit batched field delta");
        assertFieldValue(helper, secondClient, 22, "Second client copy should receive explicit batched field delta");

        List<MachineDataSyncBatcher.PlayerBatch<Object>> secondDrain = MachineDataSyncBatcher.collectBatchesForTesting(
                List.of(first.machine().data(), second.machine().data()),
                ignored -> List.of(player),
                helper.getLevel().registryAccess());
        if (!secondDrain.isEmpty()) {
            helper.fail("Batch drain should clear synced dirty fields, got " + secondDrain.size() + " extra batch");
        }
        helper.succeed();
    }

    private static void machineDataUnreadyFieldAccessCrashes(GameTestHelper helper) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                FIRST_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        DataInt contractField = machine.data().scope("contract_test").intField("value", 0)
                .saveNone()
                .syncNone()
                .done();
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);

        assertIllegalState(
                helper,
                logic::state,
                "phase=BOOTSTRAP",
                "Recipe logic should not expose default state before machine data is ready");
        assertIllegalState(
                helper,
                contractField::value,
                "phase=BOOTSTRAP",
                "Data field reads should crash before machine data is ready");
        assertIllegalState(
                helper,
                () -> contractField.set(1),
                "phase=BOOTSTRAP",
                "Data field writes should crash before machine data is ready");
        helper.succeed();
    }

    private static void machineDataUnreadyUiCollectionDeclaresBindingsWithoutFieldReads(GameTestHelper helper) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                FIRST_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);

        PageCollector pages = new PageCollector();
        ComponentCollector components = new ComponentCollector();
        try {
            machine.collectMachineUi(pages, components);
        } catch (IllegalStateException e) {
            helper.fail("Machine UI collection should be a structural binding declaration before readiness, got " + e.getMessage());
        }
        if (pages.entries().isEmpty()) {
            helper.fail("Unready macerator UI collection should still declare at least one page");
        }
        if (components.entries().isEmpty()) {
            helper.fail("Unready macerator UI collection should still declare bound status components");
        }
        assertIllegalState(
                helper,
                logic::state,
                "phase=BOOTSTRAP",
                "Direct field-backed recipe state reads must still crash before machine data is ready");
        helper.succeed();
    }

    private static void machineDataDeltaBeforeBaselineCrashes(GameTestHelper helper) {
        SyncFieldMachine server = newExplicitSyncMachine(helper, FIRST_POS);
        server.field().set(17);

        Object player = new Object();
        List<MachineDataSyncBatcher.PlayerBatch<Object>> batches = MachineDataSyncBatcher.collectBatchesForTesting(
                List.of(server.machine().data()),
                ignored -> List.of(player),
                helper.getLevel().registryAccess());
        if (batches.size() != 1 || batches.getFirst().payload().entries().size() != 1) {
            helper.fail("Expected exactly one explicit dirty machine delta before baseline crash assertion");
        }

        SyncFieldMachine clientWithoutBaseline = newExplicitSyncMachine(helper, FIRST_POS);
        MachineDataBatchS2CPayload.Entry entry = batches.getFirst().payload().entries().getFirst();
        assertIllegalState(
                helper,
                () -> clientWithoutBaseline.machine().data().lifecycleReceiveClientDelta(
                        entry.data(),
                        helper.getLevel().registryAccess(),
                        ConnectionType.NEOFORGE),
                "before initial client baseline",
                "Delta sync must not mutate a client copy before a full baseline is applied");
        assertFieldValue(helper, clientWithoutBaseline, 0,
                "Rejected pre-baseline delta must leave the client field at its declared default");
        helper.succeed();
    }

    private static MachineBlockEntity placeMacerator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        MachineBlockEntity machine = helper.getBlockEntity(pos, MachineBlockEntity.class);
        // 研磨配方按 tick 抽电;测试机起手满能,聚焦各自的本职断言。
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), 10_000);
        return machine;
    }

    private static SyncFieldMachine newExplicitSyncMachine(GameTestHelper helper, BlockPos pos) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                pos,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        DataInt field = machine.data().scope("contract_sync").intField("value", 0)
                .saveNone()
                .syncToClientAtEndOfDirtyTick()
                .done();
        machine.setLevel(helper.getLevel());
        return new SyncFieldMachine(machine, field);
    }

    private static SyncFieldMachine newExplicitNoneMachine(GameTestHelper helper, BlockPos pos) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                pos,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        DataInt field = machine.data().scope("contract_none").intField("value", 0)
                .saveNone()
                .syncNone()
                .done();
        machine.setLevel(helper.getLevel());
        return new SyncFieldMachine(machine, field);
    }

    private static SyncFieldMachine syncedClientCopy(GameTestHelper helper, SyncFieldMachine server) {
        MachineBlockEntity client = Machines.createBlockEntity(
                server.machine().getBlockPos(),
                server.machine().getBlockState());
        DataInt field = client.data().scope("contract_sync").intField("value", 0)
                .saveNone()
                .syncToClientAtEndOfDirtyTick()
                .done();
        client.setLevel(helper.getLevel());
        client.data().lifecycleReceiveClientBaseline(
                server.machine().data().lifecycleWriteUpdateTag(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess(),
                ConnectionType.OTHER);
        return new SyncFieldMachine(client, field);
    }

    private static void insertIron(GameTestHelper helper, BlockPos pos) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        if (handler == null) {
            helper.fail("Expected item input capability on macerator UP side", pos);
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ironOreResource(), 1, transaction);
            transaction.commit();
            if (inserted != 1) {
                helper.fail("Expected to insert one iron ore, got " + inserted, pos);
            }
        }
    }

    private static ItemResource ironOreResource() {
        return ItemResource.of(MaterialHelper.requireItem(
                BuiltinTopoMaterials.IRON,
                BuiltinTopoMaterialForms.ORE));
    }

    private static RecipeLogic requireWorking(GameTestHelper helper, MachineBlockEntity machine) {
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        if (logic.state() != RecipeLogic.State.WORKING) {
            helper.fail("Macerator should be WORKING before sync assertions, got " + logic.state());
        }
        return logic;
    }

    private static void assertNoClientSync(GameTestHelper helper, MachineBlockEntity machine, String message) {
        byte[] updateTagSync = machine.data().lifecycleWriteUpdateTag(helper.getLevel().registryAccess());
        if (updateTagSync.length != 0) {
            helper.fail(message + "; expected empty update-tag sync, got " + updateTagSync.length + " bytes");
        }
        Object player = new Object();
        List<MachineDataSyncBatcher.PlayerBatch<Object>> batches = MachineDataSyncBatcher.collectBatchesForTesting(
                List.of(machine.data()),
                ignored -> List.of(player),
                helper.getLevel().registryAccess());
        if (!batches.isEmpty()) {
            helper.fail(message + "; expected no client delta batches, got " + batches.size());
        }
    }

    private static void applyPayload(
                                     GameTestHelper helper,
                                     MachineDataBatchS2CPayload payload,
                                     Map<Long, MachineBlockEntity> machinesByPos) {
        Map<Long, MachineBlockEntity> mutable = new HashMap<>(machinesByPos);
        for (MachineDataBatchS2CPayload.Entry entry : payload.entries()) {
            MachineBlockEntity machine = mutable.remove(entry.pos());
            if (machine == null) {
                helper.fail("Batch payload contained unexpected machine position " + BlockPos.of(entry.pos()));
            }
            machine.data().lifecycleReceiveClientDelta(
                    entry.data(),
                    helper.getLevel().registryAccess(),
                    ConnectionType.NEOFORGE);
        }
        if (!mutable.isEmpty()) {
            helper.fail("Batch payload missed machine positions " + mutable.keySet());
        }
    }

    private static void assertFieldValue(
                                         GameTestHelper helper,
                                         SyncFieldMachine machine,
                                         int expected,
                                         String message) {
        int value = machine.field().value();
        if (value != expected) {
            helper.fail(message + "; expected value=" + expected + ", got " + value);
        }
    }

    private static void assertIllegalState(
                                           GameTestHelper helper,
                                           Runnable action,
                                           String expectedMessagePart,
                                           String message) {
        try {
            action.run();
        } catch (IllegalStateException e) {
            if (!e.getMessage().contains(expectedMessagePart)) {
                helper.fail(message + "; exception message should contain '" + expectedMessagePart + "', got " + e.getMessage());
            }
            return;
        }
        helper.fail(message + "; expected IllegalStateException");
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Machine data sync GameTest index out of range: " + index);
        }
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        testData(environment),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
                                                                           Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE);
    }

    private record SyncFieldMachine(MachineBlockEntity machine, DataInt field) {}
}
