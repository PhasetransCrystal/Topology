package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.component.ServiceMatch;
import net.ptcrys.topo.apiv2.machine.component.render.MachineRenderComponent;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.common.component.ItemCountCounterRender;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.function.Consumer;

public final class MachineRenderComponentGameTests {

    private static final String SUITE = "machine_render_trait";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final int TEST_COUNT = 3;

    private MachineRenderComponentGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("machine_render_trait"), new TestEnvironmentDefinition.AllOf());
        register(
                event,
                environment,
                1,
                "machine_render_item_count_tracks_synced_storage_total",
                "Tests ItemCountCounter through a real item storage machine: committed item inserts recompute a " + "TO_CLIENT render field on the next tick and the update-tag baseline transfers that value to a " + "client copy.",
                MachineRenderComponentGameTests::machineRenderItemCountTracksSyncedStorageTotal);
        register(
                event,
                environment,
                2,
                "machine_render_capability_exposes_counter",
                "Tests MachineRenderComponent discovery: mounting ItemCountCounter exposes exactly one trait under the " + "render capability with the matching key, proving renderKey wires discovery without hand-written " + "capability bindings.",
                MachineRenderComponentGameTests::machineRenderCapabilityExposesCounter);
        register(
                event,
                environment,
                3,
                "machine_render_computed_field_rejects_direct_set",
                "Tests the computed DataField contract: ItemCountCounter's count is computed every tick from " + "storage, so a direct set() on it throws instead of overwriting the computed value.",
                MachineRenderComponentGameTests::machineRenderComputedFieldRejectsDirectSet);
    }

    private static void machineRenderItemCountTracksSyncedStorageTotal(GameTestHelper helper) {
        MachineBlockEntity machine = placeItemStorageMachine(helper);
        ItemCountCounterRender counter = machine.machineComponents().require(ItemCountCounterRender.KEY);
        assertCount(helper, counter, 0, "Fresh item storage render count should start empty");

        ResourceHandler<ItemResource> handler = requireItemCapability(helper);
        insert(helper, handler, ItemResource.of(Items.IRON_INGOT), 17);
        insert(helper, handler, ItemResource.of(Items.GOLD_INGOT), 5);

        // The counter recomputes on its next tick after the resource version changes, not synchronously on insert.
        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertCount(helper, counter, 22,
                            "Server render count should track the committed item total after one tick");

                    MachineBlockEntity client = Machines.createBlockEntity(machine.getBlockPos(), machine.getBlockState());
                    ItemCountCounterRender clientCounter = client.machineComponents().require(ItemCountCounterRender.KEY);
                    client.data().lifecycleReceiveClientBaseline(
                            machine.data().lifecycleWriteUpdateTag(helper.getLevel().registryAccess()),
                            helper.getLevel().registryAccess(),
                            ConnectionType.OTHER);
                    assertCount(helper, clientCounter, 22,
                            "Client render count should be sourced only from the synced DataInt baseline");
                })
                .thenSucceed();
    }

    private static void machineRenderCapabilityExposesCounter(GameTestHelper helper) {
        MachineBlockEntity machine = placeItemStorageMachine(helper);
        List<ServiceMatch<MachineRenderComponent<?>>> matches = machine.machineComponents().services(MachineRenderComponent.KEY, null);
        if (matches.size() != 1) {
            helper.fail("Item storage machine should expose exactly one render trait, got " + matches.size(), MACHINE_POS);
        }
        ServiceMatch<MachineRenderComponent<?>> match = matches.get(0);
        if (!match.key().equals(ItemCountCounterRender.KEY)) {
            helper.fail("Render capability should resolve to ItemCountCounter.KEY, got " + match.key(), MACHINE_POS);
        }
        if (!(match.value() instanceof ItemCountCounterRender)) {
            helper.fail("Render capability value should be the ItemCountCounter instance, got " + match.value(), MACHINE_POS);
        }
        helper.succeed();
    }

    private static void machineRenderComputedFieldRejectsDirectSet(GameTestHelper helper) {
        MachineBlockEntity machine = placeItemStorageMachine(helper);
        ItemCountCounterRender counter = machine.machineComponents().require(ItemCountCounterRender.KEY);
        boolean rejected = false;
        try {
            counter.count().set(5);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        if (!rejected) {
            helper.fail("Computed render field should reject a direct set()", MACHINE_POS);
        }
        helper.succeed();
    }

    private static MachineBlockEntity placeItemStorageMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinOIMachines.MACERATOR_T1.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static ResourceHandler<ItemResource> requireItemCapability(GameTestHelper helper) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(MACHINE_POS), Direction.UP);
        if (handler == null) {
            helper.fail("Expected item capability on item storage machine", MACHINE_POS);
        }
        return handler;
    }

    private static void insert(
                               GameTestHelper helper,
                               ResourceHandler<ItemResource> handler,
                               ItemResource resource,
                               int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, transaction);
            transaction.commit();
            if (inserted != amount) {
                helper.fail("Expected to insert " + amount + " " + resource + ", got " + inserted, MACHINE_POS);
            }
        }
    }

    private static void assertCount(
                                    GameTestHelper helper,
                                    ItemCountCounterRender counter,
                                    int expected,
                                    String message) {
        int actual = counter.count().value();
        if (actual != expected) {
            helper.fail(message + "; expected " + expected + ", got " + actual, MACHINE_POS);
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
            throw new IllegalArgumentException("Machine render trait GameTest index out of range: " + index);
        }
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
