package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.machine.resource.RecipeSearchPool;
import net.ptcrys.topo.api.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.api.machine.resource.RecipeSearchPoolRouter;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.data.machine.BuiltinTopoMachines;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Minecraft-level guards for recipe search-pool identity, membership, and empty-pool scheduling. */
public final class RecipeSearchPoolGameTests {

    private static final String SUITE = "recipe_search_pool";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final int TEST_COUNT = 3;
    private static final long LONG_AMOUNT = (long) Integer.MAX_VALUE + 123L;
    private static final MachineResourceType<ItemResource> ITEM_TYPE = BuiltinTopoResourceIntegrations.ITEM.resourceType();

    private RecipeSearchPoolGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("recipe_search_pool"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "recipe_search_pool_default_custom_and_universal_membership",
                "DEFAULT and six-character lines stay private while UNIVERSAL input/output joins every line.",
                RecipeSearchPoolGameTests::defaultCustomAndUniversalMembership);
        register(
                event,
                environment,
                index++,
                "recipe_search_pool_finds_recipe_without_start_inputs",
                "A resource-empty DEFAULT pool must still search recipes with no one-time inputs.",
                RecipeSearchPoolGameTests::findsRecipeWithoutStartInputs);
        register(
                event,
                environment,
                index,
                "recipe_search_pool_empty_miss_uses_bounded_backoff",
                "A resource-empty pool with no matching recipe searches once, then waits at the bounded backoff.",
                RecipeSearchPoolGameTests::emptyMissUsesBoundedBackoff);
    }

    private static void defaultCustomAndUniversalMembership(GameTestHelper helper) {
        RecipeSearchPoolId first = RecipeSearchPoolId.parse("a1b2c3");
        RecipeSearchPoolId second = RecipeSearchPoolId.parse("d4e5f6");
        RecipeSearchPoolRouter router = RecipeSearchPoolRouter.builder()
                .addInput(ITEM_TYPE, contribution(first, Items.GOLD_INGOT))
                .addInput(ITEM_TYPE, contribution(RecipeSearchPoolId.UNIVERSAL, Items.REDSTONE, LONG_AMOUNT))
                .addInput(ITEM_TYPE, contribution(RecipeSearchPoolId.DEFAULT, Items.IRON_INGOT))
                .addInput(ITEM_TYPE, contribution(second, Items.COPPER_INGOT))
                .addOutput(ITEM_TYPE, contribution(first, Items.EMERALD))
                .addOutput(ITEM_TYPE, contribution(RecipeSearchPoolId.UNIVERSAL, Items.COAL, LONG_AMOUNT))
                .addOutput(ITEM_TYPE, contribution(RecipeSearchPoolId.DEFAULT, Items.DIAMOND))
                .addOutput(ITEM_TYPE, contribution(second, Items.LAPIS_LAZULI))
                .build();

        List<RecipeSearchPoolId> expectedIds = List.of(RecipeSearchPoolId.DEFAULT, first, second);
        if (!router.poolIds().equals(expectedIds)) {
            helper.fail("Expected concrete pool order " + expectedIds + ", got " + router.poolIds());
        }
        if (router.pool(RecipeSearchPoolId.UNIVERSAL) != null) {
            helper.fail("UNIVERSAL is membership, not a concrete pollable pool");
        }

        assertItems(helper, router, RecipeSearchPoolId.DEFAULT, RecipeRole.INPUT, Items.IRON_INGOT, Items.REDSTONE);
        assertItems(helper, router, first, RecipeRole.INPUT, Items.GOLD_INGOT, Items.REDSTONE);
        assertItems(helper, router, second, RecipeRole.INPUT, Items.COPPER_INGOT, Items.REDSTONE);
        assertItems(helper, router, RecipeSearchPoolId.DEFAULT, RecipeRole.OUTPUT, Items.DIAMOND, Items.COAL);
        assertItems(helper, router, first, RecipeRole.OUTPUT, Items.EMERALD, Items.COAL);
        assertItems(helper, router, second, RecipeRole.OUTPUT, Items.LAPIS_LAZULI, Items.COAL);
        assertItems(helper, router, RecipeSearchPoolId.UNIVERSAL, RecipeRole.INPUT, Items.REDSTONE);
        assertItems(helper, router, RecipeSearchPoolId.UNIVERSAL, RecipeRole.OUTPUT, Items.COAL);
        assertAmount(helper, router, first, RecipeRole.INPUT, Items.REDSTONE, LONG_AMOUNT);
        assertAmount(helper, router, second, RecipeRole.OUTPUT, Items.COAL, LONG_AMOUNT);
        helper.succeed();
    }

    private static void findsRecipeWithoutStartInputs(GameTestHelper helper) {
        MachineBlockEntity machine = placeMachine(helper);
        RecipeSearchPoolRouter router = RecipeSearchPoolRouter.builder().build();
        AtomicReference<RecipeSearchPoolId> active = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        StubRecipeType recipeType = new StubRecipeType(active, attempts, RecipeSearchPoolId.DEFAULT::equals);

        RecipeSearchPoolRouter.SearchHit hit = router.search(
                machine, List.of(recipeType), 0L, poolActivator(active));
        if (hit == null || !hit.poolId().isDefault()) {
            helper.fail("Resource-empty DEFAULT pool did not find its no-start-input recipe: " + hit);
        }
        if (attempts.get() != 1 || active.get() != null) {
            helper.fail("Expected one scoped search with a closed pool scope; attempts=" + attempts.get() + ", active=" + active.get());
        }
        helper.succeed();
    }

    private static void emptyMissUsesBoundedBackoff(GameTestHelper helper) {
        MachineBlockEntity machine = placeMachine(helper);
        RecipeSearchPoolRouter router = RecipeSearchPoolRouter.builder().build();
        AtomicReference<RecipeSearchPoolId> active = new AtomicReference<>();
        AtomicInteger attempts = new AtomicInteger();
        StubRecipeType recipeType = new StubRecipeType(active, attempts, unused -> false);

        assertNoHit(helper, router, machine, recipeType, active, 0L);
        assertNoHit(helper, router, machine, recipeType, active, 1L);
        assertNoHit(helper, router, machine, recipeType, active, RecipeSearchPool.MAX_BACKOFF_TICKS - 1L);
        if (attempts.get() != 1) {
            helper.fail("Empty miss should suppress searches until the cap expires; attempts=" + attempts.get());
        }
        assertNoHit(helper, router, machine, recipeType, active, RecipeSearchPool.MAX_BACKOFF_TICKS);
        if (attempts.get() != 2) {
            helper.fail("Empty pool should become due at the bounded backoff; attempts=" + attempts.get());
        }
        helper.succeed();
    }

    private static MachineBlockEntity placeMachine(GameTestHelper helper) {
        helper.setBlock(MACHINE_POS, BuiltinTopoMachines.CHEMICAL_REACTOR_T2.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static RecipeSearchPoolRouter.PoolActivator poolActivator(
                                                                      AtomicReference<RecipeSearchPoolId> active) {
        return poolId -> {
            RecipeSearchPoolId previous = active.getAndSet(poolId);
            return () -> active.set(previous);
        };
    }

    private static void assertNoHit(
                                    GameTestHelper helper,
                                    RecipeSearchPoolRouter router,
                                    MachineBlockEntity machine,
                                    TopoRecipeType<?> recipeType,
                                    AtomicReference<RecipeSearchPoolId> active,
                                    long gameTime) {
        RecipeSearchPoolRouter.SearchHit hit = router.search(machine, List.of(recipeType), gameTime, poolActivator(active));
        if (hit != null || active.get() != null) {
            helper.fail("Expected a scoped search miss at t=" + gameTime + ", hit=" + hit + ", active=" + active.get());
        }
    }

    private static MachineComponent.RecipeResourceContribution<ItemResource> contribution(
                                                                                          RecipeSearchPoolId poolId, Item item) {
        return contribution(poolId, item, 1L);
    }

    private static MachineComponent.RecipeResourceContribution<ItemResource> contribution(
                                                                                          RecipeSearchPoolId poolId, Item item, long amount) {
        return new MachineComponent.RecipeResourceContribution<>(
                poolId, true, new FixedItemHandler(ItemResource.of(item), amount));
    }

    private static void assertAmount(
                                     GameTestHelper helper,
                                     RecipeSearchPoolRouter router,
                                     RecipeSearchPoolId poolId,
                                     RecipeRole role,
                                     Item item,
                                     long expected) {
        ResourceHandler<ItemResource> handler = router.handler(poolId, ITEM_TYPE, role);
        if (handler == null) {
            helper.fail("Missing " + role + " item handler for pool " + poolId);
            return;
        }
        ItemResource expectedResource = ItemResource.of(item);
        long actual = 0L;
        for (int slot = 0; slot < handler.size(); slot++) {
            if (expectedResource.equals(handler.getResource(slot))) {
                actual += handler.getAmountAsLong(slot);
            }
        }
        if (actual != expected) {
            helper.fail("Pool " + poolId + " " + role + " expected " + expected + " " + item + ", got " + actual);
        }
    }

    private static void assertItems(
                                    GameTestHelper helper,
                                    RecipeSearchPoolRouter router,
                                    RecipeSearchPoolId poolId,
                                    RecipeRole role,
                                    Item... expectedItems) {
        ResourceHandler<ItemResource> handler = router.handler(poolId, ITEM_TYPE, role);
        if (handler == null) {
            helper.fail("Missing " + role + " item handler for pool " + poolId);
            return;
        }
        Set<ItemResource> actual = new HashSet<>();
        for (int slot = 0; slot < handler.size(); slot++) {
            ItemResource resource = handler.getResource(slot);
            if (!resource.isEmpty() && handler.getAmountAsLong(slot) > 0L) {
                actual.add(resource);
            }
        }
        Set<ItemResource> expected = new HashSet<>();
        for (Item item : expectedItems) {
            expected.add(ItemResource.of(item));
        }
        if (!actual.equals(expected)) {
            helper.fail("Pool " + poolId + " " + role + " resources: expected " + expected + ", got " + actual);
        }
    }

    private static void register(
                                 RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Recipe search-pool GameTest index out of range: " + index);
        }
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }

    private static final class StubRecipeType extends TopoRecipeType<TopoRecipe> {

        private final AtomicReference<RecipeSearchPoolId> active;
        private final AtomicInteger attempts;
        private final Predicate<RecipeSearchPoolId> shouldHit;
        private final RecipeHolder<TopoRecipe> hit;

        private StubRecipeType(
                               AtomicReference<RecipeSearchPoolId> active,
                               AtomicInteger attempts,
                               Predicate<RecipeSearchPoolId> shouldHit) {
            super(IdHelper.oi("gametest_pool_recipe_type"), TopoRecipe::new);
            this.active = active;
            this.attempts = attempts;
            this.shouldHit = shouldHit;
            TopoRecipe recipe = new TopoRecipe(this, TopoRecipe.EMPTY_INPUTS, TopoRecipe.EMPTY_OUTPUTS, 1);
            this.hit = new RecipeHolder<>(
                    ResourceKey.create(Registries.RECIPE, IdHelper.oi("gametest_pool_recipe")), recipe);
        }

        @Override
        public @Nullable RecipeHolder<TopoRecipe> findRecipe(MachineBlockEntity machine) {
            attempts.incrementAndGet();
            RecipeSearchPoolId poolId = active.get();
            return poolId != null && shouldHit.test(poolId) ? hit : null;
        }
    }

    private static final class FixedItemHandler implements ResourceHandler<ItemResource> {

        private final ItemResource resource;
        private final long amount;

        private FixedItemHandler(ItemResource resource, long amount) {
            this.resource = resource;
            this.amount = amount;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            return resource;
        }

        @Override
        public long getAmountAsLong(int index) {
            return amount;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return amount;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return true;
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return 0;
        }
    }
}
