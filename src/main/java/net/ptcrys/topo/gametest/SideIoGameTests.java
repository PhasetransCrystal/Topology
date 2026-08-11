package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.resource.AutomationIo;
import net.ptcrys.topo.api.machine.resource.PortAccess;
import net.ptcrys.topo.api.machine.ui.ComponentCollector;
import net.ptcrys.topo.api.machine.ui.PageCollector;
import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeSideVisual;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.material.BuiltinTopoProcessDies;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 运行时 per-side capability IO 配置的游戏内测试:声明默认、运行时改面、邻位缓存失效、声明包络
 * 子集拒绝、循环/重置/全禁、持久化往返、UI 卡片贡献、邻管连接刷新。除模具槽用例使用真实锻压机
 * 外,全部使用 {@link TopoSideIoGameTestFixtures} 的夹具机器(storage(UP)+configurable 物品口、
 * output(DOWN)+configurable 能量口)。
 */
public final class SideIoGameTests {

    private static final String SUITE = "side_io";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 9;
    private static final Direction[] HORIZONTALS = { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };

    private SideIoGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!TopoSideIoGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("side_io"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "side_io_default_matches_declared_policy",
                "Tests that a player-configurable port starts exactly at its declared policy: the storage item " + "port is BOTH on UP only, the output energy port EXTRACT on DOWN only, all other " + "sides and the null side expose no capability.",
                SideIoGameTests::sideIoDefaultMatchesDeclaredPolicy);
        register(
                event,
                environment,
                index++,
                "side_io_runtime_opened_side_accepts_io",
                "Tests setSideIo through the live capability path: NORTH starts absent, opening it as INSERT " + "exposes an insert-only handler (extract rejected), upgrading to BOTH allows extraction " + "- including the MachineComponents combined-handler cache invalidation.",
                SideIoGameTests::sideIoRuntimeOpenedSideAcceptsIo);
        register(
                event,
                environment,
                index++,
                "side_io_closed_side_invalidates_neighbor_cache",
                "Tests the level.invalidateCapabilities chain: a neighbor-style BlockCapabilityCache resolves " + "the UP item handler, closing UP to NONE at runtime makes the same cache re-resolve " + "to null instead of serving the stale handler.",
                SideIoGameTests::sideIoClosedSideInvalidatesNeighborCache);
        register(
                event,
                environment,
                index++,
                "side_io_rejects_modes_outside_declared_envelope",
                "Tests the declared-envelope subset rule: an EXTRACT-declared energy port rejects INSERT and " + "BOTH, accepts NONE/EXTRACT, and a non-configurable port rejects every runtime change.",
                SideIoGameTests::sideIoRejectsModesOutsideDeclaredEnvelope);
        register(
                event,
                environment,
                index++,
                "side_io_cycle_reset_and_disable_walk_the_envelope",
                "Tests cycleSideIo order (BOTH->NONE->INSERT->EXTRACT->BOTH on a storage port), resetSideIo " + "returning to the declared default, and disableAllSideIo closing every side.",
                SideIoGameTests::sideIoCycleResetAndDisableWalkTheEnvelope);
        register(
                event,
                environment,
                index++,
                "side_io_persists_across_serialization_roundtrip",
                "Tests that runtime side-IO survives a save/load roundtrip: a reshuffled packed value is " + "written by saveWithFullMetadata and read back identically on a fresh block entity.",
                SideIoGameTests::sideIoPersistsAcrossSerializationRoundtrip);
        register(
                event,
                environment,
                index++,
                "side_io_panel_contribution_follows_configurability",
                "Tests the UI contract: configurable ports contribute one side-IO card each (LEFT column), " + "non-configurable machines contribute none.",
                SideIoGameTests::sideIoPanelContributionFollowsConfigurability);
        register(
                event,
                environment,
                index++,
                "side_io_change_refreshes_neighbor_pipe_connection",
                "Tests the neighbor-block-update layer of the side-IO chain: an adjacent item pipe arm " + "connects when the facing machine side opens at runtime and retracts when it " + "closes again, without breaking and replacing the pipe.",
                SideIoGameTests::sideIoChangeRefreshesNeighborPipeConnection);
        register(
                event,
                environment,
                index,
                "side_io_die_slot_defaults_closed_and_opens_by_config",
                "Tests the forming press die slot policy: configurable with a BOTH envelope but defaulting " + "to every side closed - no capability anywhere (null side included) until the player " + "opens a face, then INSERT/EXTRACT move a die through that face and reset closes it.",
                SideIoGameTests::sideIoDieSlotDefaultsClosedAndOpensByConfig);
    }

    private static void sideIoDefaultMatchesDeclaredPolicy(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());

        ResourceHandler<ItemResource> up = requireItemCapability(helper, Direction.UP);
        insertItem(helper, up, 1, "UP must accept inserts on a BOTH default side");
        extractItem(helper, up, 1, "UP must allow extraction on a BOTH default side");
        for (Direction side : HORIZONTALS) {
            if (itemCapability(helper, side) != null) {
                helper.fail("Item capability must be absent on undeclared side " + side);
            }
        }
        if (itemCapability(helper, Direction.DOWN) != null) {
            helper.fail("Item capability must be absent on DOWN (declared UP only)");
        }
        if (itemCapability(helper, null) != null) {
            helper.fail("Item capability must be absent for the null side (declared side set)");
        }

        fillEnergy(helper, machine, 50);
        ResourceHandler<ScalarResource> down = requireEnergyCapability(helper, Direction.DOWN);
        extractEnergy(helper, down, 20, "DOWN must extract on an EXTRACT default side");
        try (Transaction transaction = Transaction.openRoot()) {
            if (down.insert(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(), 5, transaction) != 0) {
                helper.fail("EXTRACT-only DOWN energy capability must reject insertion");
            }
            transaction.commit();
        }
        if (energyCapability(helper, Direction.UP) != null) {
            helper.fail("Energy capability must be absent on UP (declared DOWN only)");
        }
        helper.succeed();
    }

    private static void sideIoRuntimeOpenedSideAcceptsIo(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_STORAGE);

        if (itemCapability(helper, Direction.NORTH) != null) {
            helper.fail("NORTH must start closed before the runtime change");
        }
        if (!storage.setSideIo(Direction.NORTH, AutomationIo.INSERT)) {
            helper.fail("Opening NORTH as INSERT inside a BOTH envelope must succeed");
        }
        ResourceHandler<ItemResource> north = requireItemCapability(helper, Direction.NORTH);
        insertItem(helper, north, 1, "Runtime-opened NORTH must accept inserts");
        try (Transaction transaction = Transaction.openRoot()) {
            if (north.extract(ItemResource.of(Items.COAL), 1, transaction) != 0) {
                helper.fail("INSERT-only NORTH must reject extraction");
            }
            transaction.commit();
        }
        if (!storage.setSideIo(Direction.NORTH, AutomationIo.BOTH)) {
            helper.fail("Upgrading NORTH to BOTH must succeed");
        }
        extractItem(helper, requireItemCapability(helper, Direction.NORTH), 1,
                "NORTH upgraded to BOTH must allow extraction");
        helper.succeed();
    }

    private static void sideIoClosedSideInvalidatesNeighborCache(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_STORAGE);
        BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> capability = itemBlockCapability(helper);
        BlockCapabilityCache<ResourceHandler<ItemResource>, @Nullable Direction> cache = BlockCapabilityCache.create(capability, helper.getLevel(), helper.absolutePos(MACHINE_POS), Direction.UP);

        if (cache.getCapability() == null) {
            helper.fail("Neighbor cache must resolve the UP item handler before the runtime change");
        }
        if (!storage.setSideIo(Direction.UP, AutomationIo.NONE)) {
            helper.fail("Closing UP to NONE must succeed");
        }
        if (cache.getCapability() != null) {
            helper.fail("Neighbor cache must re-resolve to null after UP was closed " + "(level.invalidateCapabilities chain is broken)");
        }
        helper.succeed();
    }

    private static void sideIoRejectsModesOutsideDeclaredEnvelope(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);

        if (!energy.sideIoConfigurable()) {
            helper.fail("The fixture energy port must report sideIoConfigurable()");
        }
        int before = energy.packedSideIo();
        if (energy.setSideIo(Direction.DOWN, AutomationIo.INSERT)) {
            helper.fail("INSERT must be rejected by an EXTRACT-declared envelope");
        }
        if (energy.setSideIo(Direction.DOWN, AutomationIo.BOTH)) {
            helper.fail("BOTH must be rejected by an EXTRACT-declared envelope");
        }
        if (energy.packedSideIo() != before) {
            helper.fail("Rejected changes must leave the packed side IO untouched");
        }
        if (!energy.setSideIo(Direction.DOWN, AutomationIo.NONE) || !energy.setSideIo(Direction.DOWN, AutomationIo.EXTRACT)) {
            helper.fail("NONE and EXTRACT must stay settable inside an EXTRACT envelope");
        }

        helper.setBlock(
                MACHINE_POS.above(1),
                TopoScalarGameTestFixtures.energyGenerator().registeredBlock().getDefaultState());
        MachineBlockEntity plain = helper.getBlockEntity(MACHINE_POS.above(1), MachineBlockEntity.class);
        ScalarResourcePort plainPort = plain.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
        if (plainPort.sideIoConfigurable()) {
            helper.fail("The scalar generator fixture port must stay non-configurable");
        }
        if (plainPort.setSideIo(Direction.DOWN, AutomationIo.NONE)) {
            helper.fail("A non-configurable port must reject every runtime side change");
        }
        helper.succeed();
    }

    private static void sideIoCycleResetAndDisableWalkTheEnvelope(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_STORAGE);
        int declaredDefault = PortAccess.withSideMode(0, Direction.UP, AutomationIo.BOTH);

        AutomationIo[] expectedCycle = {
                AutomationIo.NONE, AutomationIo.INSERT, AutomationIo.EXTRACT, AutomationIo.BOTH };
        for (AutomationIo expected : expectedCycle) {
            if (!storage.cycleSideIo(Direction.UP)) {
                helper.fail("Cycling a configurable side must report a change");
            }
            AutomationIo actual = PortAccess.sideModeOf(storage.packedSideIo(), Direction.UP);
            if (actual != expected) {
                helper.fail("Cycle from BOTH must walk NONE->INSERT->EXTRACT->BOTH, expected " + expected + " got " + actual);
            }
        }

        if (!storage.setSideIo(Direction.NORTH, AutomationIo.INSERT)) {
            helper.fail("Setup: opening NORTH must succeed before reset");
        }
        storage.resetSideIo();
        if (storage.packedSideIo() != declaredDefault) {
            helper.fail("resetSideIo must restore the declared default packed value, expected " + declaredDefault + " got " + storage.packedSideIo());
        }

        storage.disableAllSideIo();
        if (storage.packedSideIo() != 0) {
            helper.fail("disableAllSideIo must close every side");
        }
        if (itemCapability(helper, Direction.UP) != null) {
            helper.fail("UP item capability must be absent after disableAllSideIo");
        }
        helper.succeed();
    }

    private static void sideIoPersistsAcrossSerializationRoundtrip(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_STORAGE);
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);

        if (!storage.setSideIo(Direction.UP, AutomationIo.NONE) || !storage.setSideIo(Direction.NORTH, AutomationIo.INSERT) || !energy.setSideIo(Direction.DOWN, AutomationIo.NONE)) {
            helper.fail("Setup: the reshuffle before saving must succeed");
        }
        int expectedItems = storage.packedSideIo();
        int expectedEnergy = energy.packedSideIo();

        CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
        MachineBlockEntity loaded = Machines.createBlockEntity(machine.getBlockPos(), machine.getBlockState());
        loaded.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess(),
                saved));
        // Field reads stay guarded until the entity leaves BOOTSTRAP phase; attaching the
        // level mirrors the chunk-load path (same as MachineDataSafetyGameTests).
        loaded.setLevel(helper.getLevel());

        int loadedItems = loaded.machineComponents().require(ItemResourcePort.ITEM_STORAGE).packedSideIo();
        int loadedEnergy = loaded.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1).packedSideIo();
        if (loadedItems != expectedItems) {
            helper.fail("Item port side IO must survive the roundtrip, expected " + expectedItems + " got " + loadedItems);
        }
        if (loadedEnergy != expectedEnergy) {
            helper.fail("Energy port side IO must survive the roundtrip, expected " + expectedEnergy + " got " + loadedEnergy);
        }
        helper.succeed();
    }

    private static void sideIoPanelContributionFollowsConfigurability(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        long sideIoCards = collectSideIoCards(machine);
        if (sideIoCards != 2) {
            helper.fail("The fixture machine has two configurable ports and must contribute exactly two " + "side-IO cards, got " + sideIoCards);
        }

        helper.setBlock(
                MACHINE_POS.above(1),
                TopoScalarGameTestFixtures.energyGenerator().registeredBlock().getDefaultState());
        MachineBlockEntity plain = helper.getBlockEntity(MACHINE_POS.above(1), MachineBlockEntity.class);
        if (collectSideIoCards(plain) != 0) {
            helper.fail("A machine without configurable ports must contribute no side-IO cards");
        }
        helper.succeed();
    }

    /**
     * 测试 202:管道是纯方块,不持有 BlockCapabilityCache,只在 vanilla 邻居方块更新时重算连接
     * 角色——运行时改面必须发出该更新,否则邻管保持旧臂直到拆掉重放。
     */
    private static void sideIoChangeRefreshesNeighborPipeConnection(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, TopoSideIoGameTestFixtures.sideIoBuffer());
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_STORAGE);
        BlockPos pipePos = MACHINE_POS.north();
        helper.setBlock(pipePos, BuiltinTopoPipes.ITEM_PIPE_BASIC.registeredBlock().get().defaultBlockState());

        assertPipeVisual(helper, pipePos, Direction.SOUTH, PipeSideVisual.NONE,
                "the pipe must start disconnected while the machine NORTH side is closed");
        if (!storage.setSideIo(Direction.NORTH, AutomationIo.INSERT)) {
            helper.fail("Opening NORTH as INSERT inside a BOTH envelope must succeed");
        }
        assertPipeVisual(helper, pipePos, Direction.SOUTH, PipeSideVisual.PIPE,
                "opening the machine side must reconnect the standing pipe without replacing it");
        if (!storage.setSideIo(Direction.NORTH, AutomationIo.NONE)) {
            helper.fail("Closing NORTH back to NONE must succeed");
        }
        assertPipeVisual(helper, pipePos, Direction.SOUTH, PipeSideVisual.NONE,
                "closing the machine side must retract the standing pipe arm");
        helper.succeed();
    }

    /**
     * 测试 275:模具槽"默认全关、可配输入输出"——包络 BOTH 但声明默认 NONE(withDefaultAutomationIo),
     * 放置即六面无能力且 null side 关死(全六面声明面集);玩家开 INSERT/EXTRACT 后模具经该面
     * 进出,reset 回到全关而非包络默认。
     */
    private static void sideIoDieSlotDefaultsClosedAndOpensByConfig(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, BuiltinTopoMachines.COMPONENT_PROCESSOR_T1);
        ItemResourcePort die = machine.machineComponents().require(ItemResourcePort.ITEM_DIE_1);

        if (!die.sideIoConfigurable()) {
            helper.fail("The die slot must opt into player-configurable side IO");
        }
        if (die.packedSideIo() != 0) {
            helper.fail("The die slot must default to all sides closed, got packed " + die.packedSideIo());
        }
        for (Direction side : HORIZONTALS) {
            if (itemCapability(helper, side) != null) {
                helper.fail("Item capability must be absent on " + side + " while every horizontal-capable port is closed");
            }
        }
        if (itemCapability(helper, null) != null) {
            helper.fail("The null side must expose no capability (declared side set guards sideless access)");
        }

        ItemResource rejectedResource = ItemResource.of(Items.IRON_INGOT);
        ItemResource dieResource = ItemResource.of(BuiltinTopoProcessDies.TEMPLATE_PLATE.get());
        if (!die.setSideIo(Direction.NORTH, AutomationIo.INSERT)) {
            helper.fail("Opening die slot NORTH as INSERT inside a BOTH envelope must succeed");
        }
        ResourceHandler<ItemResource> north = requireItemCapability(helper, Direction.NORTH);
        try (Transaction transaction = Transaction.openRoot()) {
            if (north.insert(rejectedResource, 1, transaction) != 0) {
                helper.fail("Runtime-opened NORTH must reject non-die items");
            }
            if (north.insert(dieResource, 1, transaction) != 1) {
                helper.fail("Runtime-opened NORTH must accept the die insert");
            }
            transaction.commit();
        }
        if (die.handler().getAmountAsInt(0) != 1 || !dieResource.equals(die.handler().getResource(0))) {
            helper.fail("The die inserted through NORTH must land in the die slot");
        }

        if (!die.setSideIo(Direction.NORTH, AutomationIo.EXTRACT)) {
            helper.fail("Retargeting die slot NORTH to EXTRACT must succeed");
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (requireItemCapability(helper, Direction.NORTH).extract(dieResource, 1, transaction) != 1) {
                helper.fail("EXTRACT-opened NORTH must release the stored die");
            }
            transaction.commit();
        }

        if (!die.setSideIo(Direction.NORTH, AutomationIo.BOTH)) {
            helper.fail("The die slot envelope must admit BOTH");
        }
        die.resetSideIo();
        if (die.packedSideIo() != 0) {
            helper.fail("resetSideIo must restore the all-closed default, got packed " + die.packedSideIo());
        }
        if (itemCapability(helper, Direction.NORTH) != null) {
            helper.fail("NORTH must close again after the reset");
        }
        helper.succeed();
    }

    private static void assertPipeVisual(
                                         GameTestHelper helper,
                                         BlockPos pos,
                                         Direction side,
                                         PipeSideVisual expected,
                                         String message) {
        BlockState state = helper.getBlockState(pos);
        if (!(state.getBlock() instanceof PipeBlock)) {
            helper.fail("Expected a pipe block at " + pos);
        }
        PipeSideVisual actual = PipeBlock.visual(state, side);
        if (actual != expected) {
            helper.fail(message + " (expected " + side + " visual " + expected + ", found " + actual + ")");
        }
    }

    private static long collectSideIoCards(MachineBlockEntity machine) {
        PageCollector pages = new PageCollector();
        ComponentCollector components = new ComponentCollector();
        machine.collectMachineUi(pages, components);
        return components.entries().stream()
                .filter(entry -> entry.side() == ComponentCollector.Side.LEFT)
                .filter(entry -> entry.key().startsWith("topo_side_io_"))
                .count();
    }

    private static MachineBlockEntity place(GameTestHelper helper, MachineDefinition definition) {
        helper.setBlock(MACHINE_POS, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static void insertItem(
                                   GameTestHelper helper,
                                   ResourceHandler<ItemResource> handler,
                                   int count,
                                   String message) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(Items.COAL), count, transaction);
            if (inserted != count) {
                helper.fail(message + " (expected " + count + ", inserted " + inserted + ")");
            }
            transaction.commit();
        }
    }

    private static void extractItem(
                                    GameTestHelper helper,
                                    ResourceHandler<ItemResource> handler,
                                    int count,
                                    String message) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(ItemResource.of(Items.COAL), count, transaction);
            if (extracted != count) {
                helper.fail(message + " (expected " + count + ", extracted " + extracted + ")");
            }
            transaction.commit();
        }
    }

    private static void fillEnergy(GameTestHelper helper, MachineBlockEntity machine, int amount) {
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.ENERGY_OUTPUT_1);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = storage.handler().insert(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(), amount, transaction);
            if (inserted != amount) {
                helper.fail("Setup: expected to preload " + amount + " energy, inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void extractEnergy(
                                      GameTestHelper helper,
                                      ResourceHandler<ScalarResource> handler,
                                      int amount,
                                      String message) {
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability().resource(), amount, transaction);
            if (extracted != amount) {
                helper.fail(message + " (expected " + amount + ", extracted " + extracted + ")");
            }
            transaction.commit();
        }
    }

    private static BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> itemBlockCapability(
                                                                                                           GameTestHelper helper) {
        BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction> capability = BuiltinTopoResourceIntegrations.ITEM.resourceType().blockCapability();
        if (capability == null) {
            helper.fail("Item resource type has no block capability");
        }
        return capability;
    }

    private static @Nullable ResourceHandler<ItemResource> itemCapability(
                                                                          GameTestHelper helper,
                                                                          @Nullable Direction side) {
        return helper.getLevel().getCapability(itemBlockCapability(helper), helper.absolutePos(MACHINE_POS), side);
    }

    private static ResourceHandler<ItemResource> requireItemCapability(GameTestHelper helper, Direction side) {
        ResourceHandler<ItemResource> handler = itemCapability(helper, side);
        if (handler == null) {
            helper.fail("Expected item capability on side " + side, MACHINE_POS);
        }
        return handler;
    }

    private static @Nullable ResourceHandler<ScalarResource> energyCapability(
                                                                              GameTestHelper helper,
                                                                              @Nullable Direction side) {
        BlockCapability<ResourceHandler<ScalarResource>, @Nullable Direction> capability = BuiltinTopoResourceIntegrations.ENERGY.resourceType().blockCapability();
        if (capability == null) {
            helper.fail("Energy resource type has no block capability");
            return null;
        }
        return helper.getLevel().getCapability(capability, helper.absolutePos(MACHINE_POS), side);
    }

    private static ResourceHandler<ScalarResource> requireEnergyCapability(GameTestHelper helper, Direction side) {
        ResourceHandler<ScalarResource> handler = energyCapability(helper, side);
        if (handler == null) {
            helper.fail("Expected energy capability on side " + side, MACHINE_POS);
        }
        return handler;
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        register(event, environment, index, name, description, test, 120);
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test,
                                 int maxTicks) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Side IO GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, maxTicks, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
