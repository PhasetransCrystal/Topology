package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.CRUDE_DUST;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.DUST;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.INGOT;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms.ORE;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterials.IRON;

/** Real-machine coverage for the hard macerator/fine-grinder recipe boundary. */
public final class GrindingMachineSeparationGameTests {

    private static final String SUITE = "grinding_machine_separation";
    private static final int TEST_COUNT = 2;
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos MACERATOR_POS = new BlockPos(1, 1, 1);
    private static final BlockPos FINE_GRINDER_POS = new BlockPos(4, 1, 1);

    private GrindingMachineSeparationGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                IdHelper.oi(SUITE), new TestEnvironmentDefinition.AllOf());
        register(
                event,
                environment,
                1,
                "grinding_machines_process_only_their_assigned_inputs",
                "A macerator processes ore into crude dust while a fine grinder processes ingots into dust.",
                GrindingMachineSeparationGameTests::machinesProcessAssignedInputs);
        register(
                event,
                environment,
                2,
                "grinding_machines_reject_each_others_inputs",
                "Ingot remains idle in a macerator and ore remains idle in a fine grinder.",
                GrindingMachineSeparationGameTests::machinesRejectOtherInputs);
    }

    private static void machinesProcessAssignedInputs(GameTestHelper helper) {
        MachineBlockEntity macerator = placeAndCharge(helper, MACERATOR_POS, true);
        MachineBlockEntity fineGrinder = placeAndCharge(helper, FINE_GRINDER_POS, false);
        insert(helper, MACERATOR_POS, item(ORE), 1);
        insert(helper, FINE_GRINDER_POS, item(INGOT), 1);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertWorkingRecipe(
                            helper,
                            macerator,
                            "macerator/iron_crude_dust_from_ore",
                            "Macerator should select the ore crushing recipe");
                    assertWorkingRecipe(
                            helper,
                            fineGrinder,
                            "fine_grinder/iron_dust_from_ingot",
                            "Fine grinder should select the ingot fine-grinding recipe");
                    moveToLastTick(macerator);
                    moveToLastTick(fineGrinder);
                })
                .thenExecuteAfter(1, () -> {
                    assertOutput(helper, MACERATOR_POS, item(CRUDE_DUST), 2);
                    assertOutput(helper, FINE_GRINDER_POS, item(DUST), 1);
                })
                .thenSucceed();
    }

    private static void machinesRejectOtherInputs(GameTestHelper helper) {
        MachineBlockEntity macerator = placeAndCharge(helper, MACERATOR_POS, true);
        MachineBlockEntity fineGrinder = placeAndCharge(helper, FINE_GRINDER_POS, false);
        insert(helper, MACERATOR_POS, item(INGOT), 1);
        insert(helper, FINE_GRINDER_POS, item(ORE), 1);

        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    assertIdle(helper, macerator, "Macerator must not accept ingot fine-grinding recipes");
                    assertIdle(helper, fineGrinder, "Fine grinder must not accept ore crushing recipes");
                    assertStored(helper, MACERATOR_POS, item(INGOT), 1);
                    assertStored(helper, FINE_GRINDER_POS, item(ORE), 1);
                })
                .thenSucceed();
    }

    private static MachineBlockEntity placeAndCharge(
                                                     GameTestHelper helper,
                                                     BlockPos pos,
                                                     boolean macerator) {
        helper.setBlock(
                pos,
                (macerator ? BuiltinOIMachines.MACERATOR_T1 : BuiltinOIMachines.FINE_GRINDER_T1)
                        .registeredBlock()
                        .getDefaultState());
        MachineBlockEntity machine = helper.getBlockEntity(pos, MachineBlockEntity.class);
        ScalarResourcePort energy = machine.machineComponents().require(ScalarResourcePort.ENERGY_INPUT_1);
        energy.handler().set(0, energy.resource(), 10_000);
        return machine;
    }

    private static void assertWorkingRecipe(
                                            GameTestHelper helper,
                                            MachineBlockEntity machine,
                                            String recipePath,
                                            String message) {
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        if (logic.state() != RecipeLogic.State.WORKING || logic.activeRecipeId() == null || !logic.activeRecipeId().identifier().equals(IdHelper.oi(recipePath))) {
            helper.fail(message + "; state=" + logic.state() + ", recipe=" + logic.activeRecipeId());
        }
    }

    private static void moveToLastTick(MachineBlockEntity machine) {
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        logic.setProgressForGameTest(logic.maxProgress() - 1);
    }

    private static void assertIdle(GameTestHelper helper, MachineBlockEntity machine, String message) {
        RecipeLogic logic = machine.machineComponents().require(RecipeLogic.RECIPE_LOGIC_1);
        if (logic.state() != RecipeLogic.State.IDLE || logic.activeRecipeId() != null) {
            helper.fail(message + "; state=" + logic.state() + ", recipe=" + logic.activeRecipeId());
        }
    }

    private static void insert(GameTestHelper helper, BlockPos pos, ItemResource resource, int amount) {
        ResourceHandler<ItemResource> input = capability(helper, pos, Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = input.insert(resource, amount, transaction);
            if (inserted != amount) {
                helper.fail("Expected to insert " + amount + "x " + resource + ", got " + inserted, pos);
            }
            transaction.commit();
        }
    }

    private static void assertOutput(GameTestHelper helper, BlockPos pos, ItemResource resource, long expected) {
        long amount = amountOf(capability(helper, pos, Direction.DOWN), resource);
        if (amount != expected) {
            helper.fail("Expected output " + expected + "x " + resource + ", got " + amount, pos);
        }
    }

    private static void assertStored(GameTestHelper helper, BlockPos pos, ItemResource resource, long expected) {
        long amount = amountOf(capability(helper, pos, Direction.UP), resource);
        if (amount != expected) {
            helper.fail("Expected stored input " + expected + "x " + resource + ", got " + amount, pos);
        }
    }

    private static ResourceHandler<ItemResource> capability(
                                                            GameTestHelper helper,
                                                            BlockPos pos,
                                                            Direction side) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(
                Capabilities.Item.BLOCK, helper.absolutePos(pos), side);
        if (handler == null) {
            helper.fail("Expected item capability on " + side, pos);
        }
        return handler;
    }

    private static long amountOf(ResourceHandler<ItemResource> handler, ItemResource resource) {
        long amount = 0;
        for (int index = 0; index < handler.size(); index++) {
            if (handler.getResource(index).equals(resource)) {
                amount += handler.getAmountAsLong(index);
            }
        }
        return amount;
    }

    private static ItemResource item(net.ptcrys.topo.apiv2.material.form.MaterialForm form) {
        return ItemResource.of(MaterialHelper.requireItem(IRON, form));
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
