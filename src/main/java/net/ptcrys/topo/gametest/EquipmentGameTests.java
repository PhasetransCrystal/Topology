package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.MachineWorkControl.WorkMode;
import net.ptcrys.topo.api.machine.component.RecipeLogic;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topo.integration.jade.WrenchToolHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

/**
 * 测试 266-271:装备域交互链路。
 *
 * <p>
 * 266 扳手对机器是专精工具(挖速高于镐、isCorrectToolForDrops、mineBlock 耗 1 耐久);
 * 267 机器扳手专用采集(只入 mineable_with_wrench、不在 mineable/pickaxe——镐非正确工具无掉落,
 * 扳手才是;Jade WrenchToolHandler 对机器出扳手图标、对非机器空);268 扳手 shift 右键管道循环意图消耗 1 耐久;
 * 269 调控器右键切 HALTED——进度冻结、恢复后续跑完成,行为消耗 1 耐久;
 * 270 HALTED 经 machine_data 持久化跨 save/load;271 HALTED 下空闲机器有输入也不启动配方;
 * 296 扳手对管道也是专精工具(管道入 oi:mineable_with_wrench,挖速高于镐、可掉落、耗 1 耐久)。
 */
public final class EquipmentGameTests {

    private static final String SUITE = "equipment";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos PIPE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 7;

    private EquipmentGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("equipment"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "wrench_is_machine_mining_specialist",
                "Tests the wrench tool component against the machine block's mineable tags: " + "wrench out-speeds the pickaxe on machines, is a correct tool for drops, " + "and mining charges one durability.",
                EquipmentGameTests::wrenchIsMachineMiningSpecialist);
        register(
                event,
                environment,
                index++,
                "machine_is_wrench_only_harvest",
                "Tests machines are wrench-only harvest: machines join oi:mineable_with_wrench but NOT " + "mineable/pickaxe, so the pickaxe is not a correct tool for drops while the " + "wrench is, and Jade's WrenchToolHandler shows a wrench for machines but " + "nothing for non-wrench blocks.",
                EquipmentGameTests::machineIsWrenchOnlyHarvest);
        register(
                event,
                environment,
                index++,
                "wrench_pipe_intent_cycle_costs_durability",
                "Tests PipeBlock's wrench interaction: a sneaking player cycling a pipe side intent " + "with the wrench charges exactly one durability.",
                EquipmentGameTests::wrenchPipeIntentCycleCostsDurability);
        register(
                event,
                environment,
                index++,
                "regulator_halts_and_resumes_working_recipe",
                "Tests RegulatorBehavior + MachineWorkControl on a working macerator: halting freezes " + "progress across ticks, the regulator loses one durability, and resuming " + "completes the recipe.",
                EquipmentGameTests::regulatorHaltsAndResumesWorkingRecipe);
        register(
                event,
                environment,
                index++,
                "halted_work_mode_persists_across_save_load",
                "Tests workMode persistence through machine_data: a halted machine saved and " + "reloaded stays HALTED with its recipe state held.",
                EquipmentGameTests::haltedWorkModePersistsAcrossSaveLoad);
        register(
                event,
                environment,
                index++,
                "halted_idle_machine_does_not_start_recipes",
                "Tests the HALTED tick guard on an idle machine: inputs sit unconsumed and the " + "state machine never leaves IDLE while halted.",
                EquipmentGameTests::haltedIdleMachineDoesNotStartRecipes);
        register(
                event,
                environment,
                index,
                "wrench_dismantles_pipes_fast",
                "Tests the wrench tool component against a pipe block's mineable tags: pipes join " + "oi:mineable_with_wrench, so the wrench is a correct tool for drops, " + "out-speeds the pickaxe, and mining charges one durability.",
                EquipmentGameTests::wrenchDismantlesPipesFast);
    }

    private static void wrenchIsMachineMiningSpecialist(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        BlockState machineState = machine.getBlockState();
        if (!machineState.is(MachineBlock.MINEABLE_WITH_WRENCH)) {
            helper.fail("Machine blocks must join oi:mineable_with_wrench", MACHINE_POS);
        }
        ItemStack wrench = equipmentStack("iron_wrench");
        ItemStack pickaxe = equipmentStack("iron_pickaxe");

        if (!wrench.isCorrectToolForDrops(machineState)) {
            helper.fail("Wrench should be a correct tool for machine drops");
        }
        float wrenchSpeed = wrench.getDestroySpeed(machineState);
        float pickaxeSpeed = pickaxe.getDestroySpeed(machineState);
        if (wrenchSpeed <= pickaxeSpeed) {
            helper.fail("Wrench should out-speed the pickaxe on machines, got wrench=" + wrenchSpeed + " vs pickaxe=" + pickaxeSpeed);
        }

        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        wrench.mineBlock(helper.getLevel(), machineState, helper.absolutePos(MACHINE_POS), miner);
        if (wrench.getDamageValue() != 1) {
            helper.fail("Mining a machine should charge exactly one wrench durability, got " + wrench.getDamageValue());
        }
        helper.succeed();
    }

    private static void machineIsWrenchOnlyHarvest(GameTestHelper helper) {
        MachineBlockEntity machine = placeMacerator(helper);
        BlockState machineState = machine.getBlockState();
        if (machineState.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            helper.fail("Machines must be wrench-only: stay out of mineable/pickaxe", MACHINE_POS);
        }
        if (!machineState.is(MachineBlock.MINEABLE_WITH_WRENCH)) {
            helper.fail("Machines must join oi:mineable_with_wrench", MACHINE_POS);
        }

        ItemStack wrench = equipmentStack("iron_wrench");
        ItemStack pickaxe = equipmentStack("iron_pickaxe");
        if (pickaxe.isCorrectToolForDrops(machineState)) {
            helper.fail("Pickaxe must NOT be a correct tool for a wrench-only machine");
        }
        if (!wrench.isCorrectToolForDrops(machineState)) {
            helper.fail("Wrench must be the correct tool for machine drops");
        }
        if (wrench.getDestroySpeed(machineState) <= pickaxe.getDestroySpeed(machineState)) {
            helper.fail("Wrench should out-speed the pickaxe on a wrench-only machine");
        }

        // Jade harvest icon: WrenchToolHandler resolves a wrench for machines and nothing for a
        // plain non-wrench block.
        ItemStack icon = WrenchToolHandler.INSTANCE.test(
                machineState, helper.getLevel(), helper.absolutePos(MACHINE_POS));
        if (icon.isEmpty() || !icon.is(equipmentStack("iron_wrench").getItem())) {
            helper.fail("Jade WrenchToolHandler must show the wrench icon for a machine, got " + icon);
        }
        ItemStack stoneIcon = WrenchToolHandler.INSTANCE.test(
                Blocks.STONE.defaultBlockState(), helper.getLevel(), helper.absolutePos(MACHINE_POS));
        if (!stoneIcon.isEmpty()) {
            helper.fail("Jade WrenchToolHandler must not claim a non-wrench block, got " + stoneIcon);
        }
        helper.succeed();
    }

    private static void wrenchPipeIntentCycleCostsDurability(GameTestHelper helper) {
        BlockPos pipePos = new BlockPos(1, 1, 1);
        helper.setBlock(pipePos, BuiltinTopoPipes.ITEM_PIPE_BASIC.registeredBlock().getDefaultState());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = equipmentStack("iron_wrench");
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        player.setShiftKeyDown(true);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    BlockPos absolute = helper.absolutePos(pipePos);
                    BlockState state = helper.getLevel().getBlockState(absolute);
                    BlockHitResult hit = new BlockHitResult(
                            Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
                    state.useItemOn(wrench, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                    if (wrench.getDamageValue() != 1) {
                        helper.fail("Cycling a pipe side intent should charge exactly one wrench durability, got " + wrench.getDamageValue());
                    }
                })
                .thenSucceed();
    }

    // ---- 296 ----
    private static void wrenchDismantlesPipesFast(GameTestHelper helper) {
        helper.setBlock(PIPE_POS, BuiltinTopoPipes.ITEM_PIPE_BASIC.registeredBlock().getDefaultState());
        BlockState pipeState = helper.getLevel().getBlockState(helper.absolutePos(PIPE_POS));
        if (!pipeState.is(MachineBlock.MINEABLE_WITH_WRENCH)) {
            helper.fail("Pipe blocks must join oi:mineable_with_wrench", PIPE_POS);
        }
        ItemStack wrench = equipmentStack("iron_wrench");
        ItemStack pickaxe = equipmentStack("iron_pickaxe");

        if (!wrench.isCorrectToolForDrops(pipeState)) {
            helper.fail("Wrench should be a correct tool for pipe drops");
        }
        float wrenchSpeed = wrench.getDestroySpeed(pipeState);
        float pickaxeSpeed = pickaxe.getDestroySpeed(pipeState);
        if (wrenchSpeed <= pickaxeSpeed) {
            helper.fail("Wrench should out-speed the pickaxe on pipes, got wrench=" + wrenchSpeed + " vs pickaxe=" + pickaxeSpeed);
        }

        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        wrench.mineBlock(helper.getLevel(), pipeState, helper.absolutePos(PIPE_POS), miner);
        if (wrench.getDamageValue() != 1) {
            helper.fail("Mining a pipe should charge exactly one wrench durability, got " + wrench.getDamageValue());
        }
        helper.succeed();
    }

    private static void regulatorHaltsAndResumesWorkingRecipe(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        insertOne(helper, top, ironOreResource());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack regulator = equipmentStack("iron_regulator");
        player.setItemInHand(InteractionHand.MAIN_HAND, regulator);
        int[] frozenProgress = new int[1];

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should be WORKING before the regulator halt, got " + logic.state());
                    }
                    useRegulatorOnMachine(helper, player, regulator);
                    if (logic.workMode() != WorkMode.HALTED) {
                        helper.fail("Regulator click should set workMode HALTED, got " + logic.workMode());
                    }
                    if (regulator.getDamageValue() != 1) {
                        helper.fail("Toggling should charge exactly one regulator durability, got " + regulator.getDamageValue());
                    }
                    if (logic.isRunning()) {
                        helper.fail("HALTED must read as not running (work views drive the active " + "blockstate, UI and Jade), but isRunning() stayed true");
                    }
                    frozenProgress[0] = logic.progress();
                })
                .thenExecuteAfter(3, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.progress() != frozenProgress[0]) {
                        helper.fail("HALTED must freeze recipe progress, expected " + frozenProgress[0] + " got " + logic.progress());
                    }
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("HALTED must hold the held state untouched, got " + logic.state());
                    }
                    assertActiveBlockstate(helper, false,
                            "Halted machine must stop rendering active within a tick");
                    useRegulatorOnMachine(helper, player, regulator);
                    if (logic.workMode() != WorkMode.RUNNING) {
                        helper.fail("Second regulator click should resume RUNNING, got " + logic.workMode());
                    }
                    if (!logic.isRunning()) {
                        helper.fail("Resumed WORKING recipe must read as running again");
                    }
                    logic.setProgressForGameTest(logic.maxProgress() - 1);
                })
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.IDLE) {
                        helper.fail("Resumed recipe should run to completion, got " + logic.state());
                    }
                })
                .thenSucceed();
    }

    private static void haltedWorkModePersistsAcrossSaveLoad(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        insertOne(helper, top, ironOreResource());

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Macerator should be WORKING before halt+save, got " + logic.state());
                    }
                    logic.toggleWorkMode();

                    CompoundTag saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
                    BlockEntity loadedBlockEntity = BlockEntity.loadStatic(
                            machine.getBlockPos(),
                            machine.getBlockState(),
                            saved,
                            helper.getLevel().registryAccess());
                    if (!(loadedBlockEntity instanceof MachineBlockEntity loaded)) {
                        helper.fail("Saved macerator should load as MachineBlockEntity, got " + loadedBlockEntity);
                        return;
                    }
                    loaded.setLevel(helper.getLevel());
                    RecipeLogic loadedLogic = loaded.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
                    if (loadedLogic.workMode() != WorkMode.HALTED) {
                        helper.fail("Loaded macerator should stay HALTED, got " + loadedLogic.workMode());
                    }
                    if (loadedLogic.state() != RecipeLogic.State.WORKING) {
                        helper.fail("Loaded macerator should hold the WORKING state under halt, got " + loadedLogic.state());
                    }
                })
                .thenSucceed();
    }

    private static void haltedIdleMachineDoesNotStartRecipes(GameTestHelper helper) {
        MachineBlockEntity machine = chargeEnergy(placeMacerator(helper));
        logic(helper).toggleWorkMode();
        ResourceHandler<ItemResource> top = requireCapability(helper, Direction.UP);
        insertOne(helper, top, ironOreResource());

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    RecipeLogic logic = logic(helper);
                    if (logic.state() != RecipeLogic.State.IDLE) {
                        helper.fail("HALTED idle machine must not start recipes, got " + logic.state());
                    }
                    if (amountOf(requireCapability(helper, Direction.UP), ironOreResource()) != 1) {
                        helper.fail("HALTED idle machine must not consume inputs");
                    }
                })
                .thenSucceed();
    }

    // ---- fixtures -----------------------------------------------------------------------------

    private static MachineBlockEntity placeMacerator(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinTopoMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static ItemResource ironOreResource() {
        return ItemResource.of(MaterialHelper.requireItem(
                BuiltinTopoMaterials.IRON,
                BuiltinTopoMaterialForms.ORE));
    }

    private static MachineBlockEntity chargeEnergy(MachineBlockEntity machine) {
        ScalarResourcePort storage = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        storage.handler().set(0, storage.resource(), 10_000);
        return machine;
    }

    private static RecipeLogic logic(GameTestHelper helper) {
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class)
                .machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
    }

    private static void assertActiveBlockstate(GameTestHelper helper, boolean expected, String message) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(MACHINE_POS));
        for (var property : state.getProperties()) {
            if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty booleanProperty && booleanProperty.getName().equals("active")) {
                boolean active = state.getValue(booleanProperty);
                if (active != expected) {
                    helper.fail(message + "; expected active=" + expected + ", got " + active);
                }
                return;
            }
        }
        helper.fail("Macerator blockstate should expose boolean property 'active'", MACHINE_POS);
    }

    private static void useRegulatorOnMachine(GameTestHelper helper, Player player, ItemStack regulator) {
        BlockPos absolute = helper.absolutePos(MACHINE_POS);
        BlockState state = helper.getLevel().getBlockState(absolute);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.NORTH, absolute, false);
        state.useItemOn(regulator, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static ItemStack equipmentStack(String path) {
        Item item = BuiltInRegistries.ITEM.getValue(IdHelper.oi(path));
        if (item == Items.AIR) {
            throw new IllegalStateException("Equipment item not registered: " + path);
        }
        return new ItemStack(item);
    }

    private static ResourceHandler<ItemResource> requireCapability(GameTestHelper helper, Direction side) {
        ResourceHandler<ItemResource> handler = helper.getLevel()
                .getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE_POS), side);
        if (handler == null) {
            helper.fail("Expected item capability on macerator side " + side, MACHINE_POS);
        }
        return handler;
    }

    private static void insertOne(GameTestHelper helper, ResourceHandler<ItemResource> handler,
                                  ItemResource resource) {
        int inserted;
        try (Transaction transaction = Transaction.openRoot()) {
            inserted = handler.insert(resource, 1, transaction);
            transaction.commit();
        }
        if (inserted != 1) {
            helper.fail("Fixture insert should accept one item, got " + inserted);
        }
    }

    private static long amountOf(ResourceHandler<ItemResource> handler, ItemResource resource) {
        long amount = 0;
        for (int slot = 0; slot < handler.size(); slot++) {
            if (handler.getResource(slot).equals(resource)) {
                amount += handler.getAmountAsInt(slot);
            }
        }
        return amount;
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Equipment GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
