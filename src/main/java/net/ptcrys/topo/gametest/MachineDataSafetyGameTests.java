package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.api.tick.NoopTickHandle;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.api.machine.data.DataInt;
import net.ptcrys.topo.api.machine.data.DataResourceKey;
import net.ptcrys.topo.api.machine.data.MachineDataScope;
import net.ptcrys.topo.api.machine.data.MachineDataSyncBatcher;
import net.ptcrys.topo.api.machine.data.SyncableFieldBuilder;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.slot.ItemResourceHandlerSlot;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;

public final class MachineDataSafetyGameTests {

    private static final String SUITE = "machine_data_safety";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final int TEST_COUNT = 14;

    private MachineDataSafetyGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("machine_data_safety"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "machine_data_persist_passive_storage_marks_chunk_unsaved",
                "Tests MachineDataDomain persistence scheduling through a real non-ticking item storage machine: " + "committed storage changes mark the chunk unsaved without relying on a MachineTicker.",
                MachineDataSafetyGameTests::machineDataPassiveStorageMarksChunkUnsaved);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_unsupported_persist_version",
                "Tests MachineDataDomain persistent schema safety: a saved machine_data version mismatch fails " + "closed instead of reading defaults.",
                MachineDataSafetyGameTests::machineDataRejectsUnsupportedPersistVersion);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_unknown_enum_persist_value",
                "Tests DataEnum persistent safety through a real macerator: an unknown enum value fails " + "closed instead of falling back to the first state.",
                MachineDataSafetyGameTests::machineDataRejectsUnknownEnumPersistValue);
        register(
                event,
                environment,
                index++,
                "machine_data_resource_key_missing_value_preserves_default",
                "Tests DataResourceKey persistent safety: an absent key in an existing saved scope keeps the " + "declared non-null default.",
                MachineDataSafetyGameTests::machineDataResourceKeyMissingValuePreservesDefault);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_trailing_sync_bytes",
                "Tests MachineDataDomain sync frame safety: a full snapshot with trailing bytes is rejected " + "instead of being partially accepted.",
                MachineDataSafetyGameTests::machineDataRejectsTrailingSyncBytes);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_late_field_registration",
                "Tests MachineDataDomain lifecycle safety: new scopes, fields, and sync slots cannot be " + "registered after runtime readiness.",
                MachineDataSafetyGameTests::machineDataRejectsLateFieldRegistration);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_to_server_fields_until_authorized",
                "Tests MachineDataDomain C2S safety: server-bound data fields are rejected until an explicit " + "authorization contract exists.",
                MachineDataSafetyGameTests::machineDataRejectsToServerFieldsUntilAuthorized);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_runtime_with_unconfigured_persist_policy",
                "Tests MachineDataDomain field policy safety: a field with an explicit sync policy but no " + "persist policy is rejected when the machine enters runtime.",
                MachineDataSafetyGameTests::machineDataRejectsRuntimeWithUnconfiguredPersistPolicy);
        register(
                event,
                environment,
                index++,
                "machine_data_rejects_runtime_with_unconfigured_sync_policy",
                "Tests MachineDataDomain field policy safety: a field with an explicit persist policy but no " + "client sync policy is rejected when the machine enters runtime.",
                MachineDataSafetyGameTests::machineDataRejectsRuntimeWithUnconfiguredSyncPolicy);
        register(
                event,
                environment,
                index++,
                "machine_data_client_slot_mirror_write_does_not_mark_persist_dirty",
                "Tests MachineDataDomain and LDLib2 slot safety: a client-side slot mirror write before " + "the machine-data baseline updates the local resource view without touching persist dirty.",
                MachineDataSafetyGameTests::machineDataClientSlotMirrorWriteDoesNotMarkPersistDirty);
        register(
                event,
                environment,
                index,
                "recipe_logic_missing_active_recipe_preserves_committed_work",
                "Tests RecipeLogic reload safety: a missing persisted active recipe pauses the machine " + "without clearing committed input, progress, or the recipe id.",
                MachineDataSafetyGameTests::recipeLogicMissingActiveRecipePreservesCommittedWork);
        register(
                event,
                environment,
                ++index,
                "machine_data_persist_real_chunk_save_rearms_epoch",
                "Tests the real ChunkDataEvent.Save acknowledgement path: a persisted mutation after a complete " + "chunk snapshot must open and mark a new dirty epoch.",
                MachineDataSafetyGameTests::machineDataRealChunkSaveRearmsPersistEpoch);
        register(
                event,
                environment,
                ++index,
                "machine_data_persist_mutation_after_snapshot_capture_is_remarked",
                "Tests persist generation safety: a mutation after SerializableChunkData capture but before its " + "ChunkDataEvent.Save acknowledgement must be marked for a later snapshot.",
                MachineDataSafetyGameTests::machineDataMutationAfterSnapshotCaptureIsRemarked);
        register(
                event,
                environment,
                ++index,
                "machine_data_persist_same_epoch_coalesces_chunk_dirty_marks",
                "Tests persist epoch coalescing and failed-snapshot recovery: repeated writes before a real " + "chunk-save acknowledgement do not touch an already-outstanding epoch, while an " + "explicit save backstop restores a dropped vanilla unsaved mark.",
                MachineDataSafetyGameTests::machineDataSameEpochCoalescesChunkDirtyMarks);
    }

    private static void machineDataPassiveStorageMarksChunkUnsaved(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        MachineBlockEntity machine = helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
        LevelChunk chunk = (LevelChunk) helper.getLevel().getChunk(helper.absolutePos(MACHINE_POS));
        chunk.tryMarkSaved();
        if (chunk.isUnsaved()) {
            helper.fail("Test setup failed: chunk should start from a saved state");
        }

        ResourceHandler<ItemResource> storage = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE_POS), Direction.UP);
        if (storage == null) {
            helper.fail("Item storage machine should expose item capability on UP side", MACHINE_POS);
        }
        insert(helper, storage, ItemResource.of(Items.IRON_INGOT), 1, "Passive item storage should accept an iron ingot");
        if (!chunk.isUnsaved()) {
            helper.fail("Committed storage change must mark the chunk immediately, before tick-post or unload saving");
        }
        // A command/test/copy snapshot is not a disk acknowledgement. It must not suppress the
        // already-outstanding chunk-dirty mark for this mutation.
        machine.saveWithFullMetadata(helper.getLevel().registryAccess());

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    if (!chunk.isUnsaved()) {
                        helper.fail("Committed storage change must mark the chunk unsaved even without a machine ticker");
                    }
                })
                .thenSucceed();
    }

    private static void machineDataRealChunkSaveRearmsPersistEpoch(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        LevelChunk chunk = chunk(helper);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    simulateRealChunkSnapshot(helper, chunk);
                    setEnergy(machine, 9_000);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk.isUnsaved()) {
                        helper.fail("First persisted mutation must mark the chunk unsaved");
                    }

                    simulateRealChunkSnapshot(helper, chunk);
                    if (chunk.isUnsaved()) {
                        helper.fail("Simulated real chunk snapshot should leave the chunk saved");
                    }

                    setEnergy(machine, 8_000);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk.isUnsaved()) {
                        helper.fail("A mutation after real ChunkDataEvent.Save must re-arm and mark a new epoch");
                    }
                })
                .thenSucceed();
    }

    private static void machineDataMutationAfterSnapshotCaptureIsRemarked(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        LevelChunk chunk = chunk(helper);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    simulateRealChunkSnapshot(helper, chunk);
                    setEnergy(machine, 9_000);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());

                    if (!chunk.tryMarkSaved()) {
                        helper.fail("Test setup expected the first persist epoch to leave the chunk unsaved");
                    }
                    SerializableChunkData captured = SerializableChunkData.copyOf(helper.getLevel(), chunk);
                    setEnergy(machine, 8_000);
                    NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, helper.getLevel(), captured));

                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk.isUnsaved()) {
                        helper.fail("Mutation after snapshot capture must be re-marked after acknowledgement");
                    }
                })
                .thenSucceed();
    }

    private static void machineDataSameEpochCoalescesChunkDirtyMarks(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        LevelChunk chunk = chunk(helper);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    simulateRealChunkSnapshot(helper, chunk);
                    setEnergy(machine, 9_000);
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk.tryMarkSaved()) {
                        helper.fail("Test setup expected the first persist epoch to mark the chunk unsaved");
                    }

                    // Deliberately clear only the vanilla flag, without the real save event. A
                    // second write remains in the already-outstanding epoch and must not re-mark.
                    setEnergy(machine, 8_000);
                    if (chunk.isUnsaved()) {
                        helper.fail("Repeated writes in one unacknowledged epoch must not touch the chunk again");
                    }

                    // This is the observable state left when vanilla clears the flag and snapshot
                    // capture throws before ChunkDataEvent.Save. The save backstop must recover it.
                    MachineDataSyncBatcher.flushResidualPersistForSave(helper.getLevel());
                    if (!chunk.isUnsaved()) {
                        helper.fail("A dropped vanilla unsaved mark must be restored for a later snapshot retry");
                    }
                })
                .thenSucceed();
    }

    private static void machineDataRejectsUnsupportedPersistVersion(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        insertIron(helper);

        helper.runAfterDelay(1, () -> {
            requireWorking(helper, machine).setProgressForGameTest(5);
            CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
            machineDataRoot(saved).putInt("version", 999_999);

            MachineBlockEntity loaded = Machines.createBlockEntity(machine.getBlockPos(), machine.getBlockState());
            assertIllegalState(
                    helper,
                    () -> loaded.loadWithComponents(TagValueInput.create(
                            ProblemReporter.DISCARDING,
                            helper.getLevel().registryAccess(),
                            saved)),
                    "Unsupported machine_data persist version",
                    "Persist version mismatch must fail before defaults can replace saved machine state");
            helper.succeed();
        });
    }

    private static void machineDataRejectsUnknownEnumPersistValue(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        insertIron(helper);

        helper.runAfterDelay(1, () -> {
            requireWorking(helper, machine);
            CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
            recipeLogicScope(saved).putString("state", "NO_SUCH_STATE");

            MachineBlockEntity loaded = Machines.createBlockEntity(machine.getBlockPos(), machine.getBlockState());
            assertIllegalState(
                    helper,
                    () -> loaded.loadWithComponents(TagValueInput.create(
                            ProblemReporter.DISCARDING,
                            helper.getLevel().registryAccess(),
                            saved)),
                    "Unknown enum value",
                    "Unknown persisted enum values must fail instead of becoming IDLE");
            helper.succeed();
        });
    }

    private static void machineDataResourceKeyMissingValuePreservesDefault(GameTestHelper helper) {
        ResourceKey<Recipe<?>> defaultRecipe = ResourceKey.create(Registries.RECIPE, IdHelper.oi("contract/default_recipe"));
        MachineBlockEntity savedMachine = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        savedMachine.data()
                .scope("contract")
                .resourceKey("recipe", Registries.RECIPE, defaultRecipe)
                .persisted()
                .syncNone()
                .done();
        savedMachine.setLevel(helper.getLevel());
        CompoundTag saved = savedMachine.saveWithFullMetadata(helper.getLevel().registryAccess());
        machineDataRoot(saved)
                .getCompoundOrEmpty("scopes")
                .getCompoundOrEmpty("contract")
                .remove("recipe");

        MachineBlockEntity loaded = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        DataResourceKey<Recipe<?>> loadedField = loaded.data()
                .scope("contract")
                .resourceKey("recipe", Registries.RECIPE, defaultRecipe)
                .persisted()
                .syncNone()
                .done();
        loaded.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(),
                saved));
        loaded.setLevel(helper.getLevel());
        if (!defaultRecipe.equals(loadedField.value())) {
            helper.fail("Missing resource key value should preserve default " + defaultRecipe + ", got " + loadedField.value());
        }
        helper.succeed();
    }

    private static void machineDataRejectsTrailingSyncBytes(GameTestHelper helper) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        DataInt value = machine.data().scope("contract_sync").intField("value", 0)
                .saveNone()
                .syncToClientAtEndOfDirtyTick()
                .done();
        machine.setLevel(helper.getLevel());
        value.set(9);

        byte[] valid = machine.data().lifecycleWriteUpdateTag(helper.getLevel().registryAccess());
        if (valid.length == 0) {
            helper.fail("Explicit test sync field should produce a full snapshot before corruption");
        }
        byte[] corrupt = Arrays.copyOf(valid, valid.length + 1);
        corrupt[corrupt.length - 1] = 0x55;

        MachineBlockEntity client = Machines.createBlockEntity(machine.getBlockPos(), machine.getBlockState());
        client.data().scope("contract_sync").intField("value", 0)
                .saveNone()
                .syncToClientAtEndOfDirtyTick()
                .done();
        client.setLevel(helper.getLevel());
        assertIllegalState(
                helper,
                () -> client.data().lifecycleReceiveClientBaseline(
                        corrupt,
                        helper.getLevel().registryAccess(),
                        ConnectionType.OTHER),
                "trailing",
                "Sync decoder must reject frames that leave unread bytes");
        helper.succeed();
    }

    private static void machineDataRejectsLateFieldRegistration(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        assertIllegalState(
                helper,
                () -> machine.data().scope("late_scope").intField("value", 1),
                "sealed",
                "Machine data must reject new scopes and fields after runtime readiness");

        MachineBlockEntity constructed = Machines.createBlockEntity(
                new BlockPos(2, 1, 1),
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        SyncableFieldBuilder<DataInt, Integer> lateField = constructed.data().scope("contract_test").intField("late_sync", 0)
                .saveNone()
                .syncNone();
        constructed.setLevel(helper.getLevel());
        assertIllegalState(
                helper,
                lateField::done,
                "sealed",
                "Machine data must reject field registration via done() after runtime readiness");
        helper.succeed();
    }

    private static void machineDataRejectsToServerFieldsUntilAuthorized(GameTestHelper helper) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        SyncableFieldBuilder<DataInt, Integer> clientWritable = machine.data().scope("contract_test").intField("clientWritable", 0)
                .saveNone();
        assertUnsupported(
                helper,
                clientWritable::syncToServer,
                "TO_SERVER",
                "Machine data must not expose client-to-server write fields before authorization exists");
        helper.succeed();
    }

    private static void machineDataRejectsRuntimeWithUnconfiguredPersistPolicy(GameTestHelper helper) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        assertIllegalState(
                helper,
                () -> machine.data().scope("contract_test").intField("value", 0)
                        .syncNone()
                        .done(),
                "persist strategy",
                "Machine data must reject a field whose builder declares no explicit persist policy");
        helper.succeed();
    }

    private static void machineDataRejectsRuntimeWithUnconfiguredSyncPolicy(GameTestHelper helper) {
        MachineBlockEntity machine = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        assertIllegalState(
                helper,
                () -> machine.data().scope("contract_test").intField("value", 0)
                        .saveNone()
                        .done(),
                "sync strategy",
                "Machine data must reject a field whose builder declares no explicit client sync policy");
        helper.succeed();
    }

    private static void machineDataClientSlotMirrorWriteDoesNotMarkPersistDirty(GameTestHelper helper) {
        MachineBlockEntity clientMachine = Machines.createBlockEntity(
                MACHINE_POS,
                BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        forcePhase(clientMachine, "CLIENT_WAITING");

        ItemResourcePort input = clientMachine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1);
        ItemResourceHandlerSlot slot = new ItemResourceHandlerSlot(input.handler(), 0);
        try {
            slot.set(new ItemStack(Items.IRON_INGOT, 3));
        } catch (IllegalStateException e) {
            helper.fail("LDLib2 client slot mirror writes must not touch machine-data persist dirty before baseline: " + e.getMessage());
        }
        int amount = input.handler().getAmountAsInt(0);
        if (amount != 3) {
            helper.fail("Client slot mirror should update the local resource view, expected 3 iron ingots, got " + amount);
        }
        RecipeLogic logic = clientMachine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        assertIllegalState(
                helper,
                logic::state,
                "phase=CLIENT_WAITING",
                "Direct business field reads must still crash before the client baseline is applied");
        helper.succeed();
    }

    private static void recipeLogicMissingActiveRecipePreservesCommittedWork(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        insertIron(helper);

        helper.runAfterDelay(1, () -> {
            RecipeLogic logic = requireWorking(helper, machine);
            logic.setProgressForGameTest(37);
            CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
            Identifier missingId = IdHelper.oi("macerator/missing_after_reload");
            recipeLogicScope(saved).putString("activeRecipeId", missingId.toString());

            BlockEntity loadedBlockEntity = BlockEntity.loadStatic(
                    machine.getBlockPos(),
                    machine.getBlockState(),
                    saved,
                    helper.getLevel().registryAccess());
            if (!(loadedBlockEntity instanceof MachineBlockEntity loaded)) {
                helper.fail("Mutated saved macerator should still load as MachineBlockEntity, got " + loadedBlockEntity);
                return;
            }
            loaded.setLevel(helper.getLevel());
            RecipeLogic loadedLogic = loaded.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
            loadedLogic.tick(helper.getLevel().getGameTime(), NoopTickHandle.INSTANCE);

            if (loadedLogic.state() != RecipeLogic.State.MISSING_ACTIVE_RECIPE) {
                helper.fail("Missing active recipe should pause in MISSING_ACTIVE_RECIPE, got " + loadedLogic.state());
            }
            if (loadedLogic.progress() != 37) {
                helper.fail("Missing active recipe must preserve progress 37, got " + loadedLogic.progress());
            }
            ResourceKey<Recipe<?>> activeId = loadedLogic.activeRecipeId();
            if (activeId == null || !activeId.identifier().equals(missingId)) {
                helper.fail("Missing active recipe must preserve the unresolved id, got " + activeId);
            }
            assertInputEmpty(helper, loaded,
                    "Missing active recipe must preserve already-committed input consumption instead of rolling to IDLE");
            helper.succeed();
        });
    }

    private static MachineBlockEntity placeMacerator(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        MachineBlockEntity machine = helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
        // 研磨配方按 tick 抽电;测试机起手满能,聚焦各自的本职断言。
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), 10_000);
        return machine;
    }

    private static LevelChunk chunk(GameTestHelper helper) {
        return (LevelChunk) helper.getLevel().getChunk(helper.absolutePos(MACHINE_POS));
    }

    private static void setEnergy(MachineBlockEntity machine, int amount) {
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), amount);
    }

    /** Mirrors ChunkMap.save's main-thread order without writing the GameTest level to disk. */
    private static void simulateRealChunkSnapshot(GameTestHelper helper, LevelChunk chunk) {
        chunk.tryMarkSaved();
        SerializableChunkData captured = SerializableChunkData.copyOf(helper.getLevel(), chunk);
        NeoForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, helper.getLevel(), captured));
    }

    private static void insertIron(GameTestHelper helper) {
        ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE_POS), Direction.UP);
        if (handler == null) {
            helper.fail("Expected macerator item input capability on UP side", MACHINE_POS);
        }
        insert(helper, handler, ironOreResource(), 1, "Macerator should accept one iron ore");
    }

    private static void insert(
                               GameTestHelper helper,
                               ResourceHandler<ItemResource> handler,
                               ItemResource resource,
                               int amount,
                               String message) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, transaction);
            transaction.commit();
            if (inserted != amount) {
                helper.fail(message + ", inserted " + inserted + " of " + amount);
            }
        }
    }

    private static RecipeLogic requireWorking(GameTestHelper helper, MachineBlockEntity machine) {
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        if (logic.state() != RecipeLogic.State.WORKING) {
            helper.fail("Macerator should be WORKING before safety assertion, got " + logic.state());
        }
        return logic;
    }

    private static void assertInputEmpty(GameTestHelper helper, MachineBlockEntity machine, String message) {
        ResourceHandler<ItemResource> handler = machine.machineComponents()
                .resources()
                .recipeSide()
                .handler(BuiltinTopoResourceIntegrations.ITEM.resourceType(),
                        net.ptcrys.topo.api.machine.resource.RecipeRole.INPUT);
        if (handler == null) {
            helper.fail("Expected loaded macerator to expose internal item input handler");
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(ironOreResource(), 1, transaction);
            if (extracted != 0) {
                helper.fail(message + ", extracted leftover input " + extracted);
            }
        }
    }

    private static ItemResource ironOreResource() {
        return ItemResource.of(MaterialHelper.requireItem(
                BuiltinTopoMaterials.IRON,
                BuiltinTopoMaterialForms.ORE));
    }

    private static CompoundTag machineDataRoot(CompoundTag saved) {
        return saved.getCompoundOrEmpty(MachineDataScope.SAVE_KEY);
    }

    private static CompoundTag recipeLogicScope(CompoundTag saved) {
        return machineDataRoot(saved)
                .getCompoundOrEmpty("scopes")
                .getCompoundOrEmpty(RecipeLogic.RECIPE_LOGIC_1.id().toString());
    }

    private static void assertIllegalState(
                                           GameTestHelper helper,
                                           Runnable action,
                                           String expectedMessagePart,
                                           String failureMessage) {
        try {
            action.run();
            helper.fail(failureMessage);
        } catch (IllegalStateException error) {
            if (!error.getMessage().contains(expectedMessagePart)) {
                helper.fail("Expected IllegalStateException containing '" + expectedMessagePart + "', got: " + error.getMessage());
            }
        }
    }

    private static void assertUnsupported(
                                          GameTestHelper helper,
                                          Runnable action,
                                          String expectedMessagePart,
                                          String failureMessage) {
        try {
            action.run();
            helper.fail(failureMessage);
        } catch (UnsupportedOperationException error) {
            if (!error.getMessage().contains(expectedMessagePart)) {
                helper.fail("Expected UnsupportedOperationException containing '" + expectedMessagePart + "', got: " + error.getMessage());
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void forcePhase(MachineBlockEntity machine, String phaseName) {
        try {
            Field phase = MachineDataScope.class.getDeclaredField("phase");
            phase.setAccessible(true);
            Class<? extends Enum> phaseType = (Class<? extends Enum>) phase.getType().asSubclass(Enum.class);
            phase.set(machine.data(), Enum.valueOf(phaseType, phaseName));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to force machine data phase for GameTest", e);
        }
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> function) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Machine data safety GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        testData(environment),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, function)));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
                                                                           Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE);
    }
}
