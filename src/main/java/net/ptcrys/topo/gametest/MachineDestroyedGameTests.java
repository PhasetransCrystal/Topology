package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations.BuiltinResourceIntegration;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.function.Consumer;

/**
 * 机器被破坏时资源 Trait 行为的游戏内测试:物品掉落、电容放电三档(耗散/电弧/爆燃)、
 * 高级能量 10× 密度折算、热泄放点燃、流体静默。全部使用
 * {@link OIMachineDestroyedGameTestFixtures} 的纯储存机器,经 {@code helper.destroyBlock}
 * 触发原版 {@code preRemoveSideEffects} 真实破坏链路。
 *
 * <p>
 * 场地约定:石地板铺 y=0 的 5×5,机器立于 (2,1,2),奶牛(无 )站 (1,1,2),玻璃探针放
 * (3,1,2)。爆燃测试改用全封闭黑曜石围挡(地板/四壁/顶盖),防止爆炸损伤共享测试平台。
 */
public final class MachineDestroyedGameTests {

    private static final String SUITE = "machine_destroyed";
    private static final BlockPos MACHINE_POS = new BlockPos(2, 1, 2);
    private static final BlockPos COW_POS = new BlockPos(1, 1, 2);
    private static final BlockPos GLASS_POS = new BlockPos(3, 1, 2);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 8;
    private static final int ARENA_MIN = 0;
    private static final int ARENA_MAX = 4;

    private MachineDestroyedGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!OIScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("machine_destroyed"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "destroyed_item_storage_drops_contents",
                "Tests MachineBlockEntity.preRemoveSideEffects fanout and ItemResourcePort.onMachineDestroyed: " + "destroying a machine with stored items spills every stack as item entities.",
                MachineDestroyedGameTests::destroyedItemStorageDropsContents);
        register(
                event,
                environment,
                index++,
                "destroyed_energy_below_threshold_is_silent",
                "Tests the energy discharge dissipate tier: a charge below 10k leaves nearby entities " + "unharmed and adjacent blocks intact when the machine is destroyed.",
                MachineDestroyedGameTests::destroyedEnergyBelowThresholdIsSilent);
        register(
                event,
                environment,
                index++,
                "destroyed_energy_arc_shocks_nearby_entities",
                "Tests the energy discharge arc tier: a 100k charge shocks living entities in radius " + "without any block damage.",
                MachineDestroyedGameTests::destroyedEnergyArcShocksNearbyEntities);
        register(
                event,
                environment,
                index++,
                "destroyed_energy_blast_breaks_blocks_inside_containment",
                "Tests the energy discharge blast tier: a 1M charge detonates a real block-breaking " + "explosion that destroys a glass probe but cannot pierce the obsidian containment.",
                MachineDestroyedGameTests::destroyedEnergyBlastBreaksBlocksInsideContainment);
        register(
                event,
                environment,
                index++,
                "destroyed_advanced_energy_applies_density_ratio",
                "Tests the advanced-energy 10x density: a 5k advanced charge (silent for plain energy) " + "reaches the arc tier and shocks a nearby entity.",
                MachineDestroyedGameTests::destroyedAdvancedEnergyAppliesDensityRatio);
        register(
                event,
                environment,
                index++,
                "destroyed_heat_below_threshold_is_silent",
                "Tests the heat flash silent tier: residual heat below 10k neither ignites nor hurts " + "a nearby entity and places no fire.",
                MachineDestroyedGameTests::destroyedHeatBelowThresholdIsSilent);
        register(
                event,
                environment,
                index++,
                "destroyed_heat_flash_ignites_entities_and_ground",
                "Tests the heat flash active tier: full stored heat sets a nearby entity on fire, deals " + "fire damage, and ignites at least one ground position, without breaking blocks.",
                MachineDestroyedGameTests::destroyedHeatFlashIgnitesEntitiesAndGround);
        register(
                event,
                environment,
                index,
                "destroyed_fluid_storage_spills_nothing",
                "Tests the fluid storage trivial path: destroying a machine with a full tank places no " + "fluid in the world and leaves nearby entities and blocks untouched.",
                MachineDestroyedGameTests::destroyedFluidStorageSpillsNothing);
    }

    /** 测试 113:物品储存破坏后内容物全部以掉落物形式落地。 */
    private static void destroyedItemStorageDropsContents(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.itemChest());
        insertItems(helper, machine, new ItemStack(Items.COAL, 5), new ItemStack(Items.IRON_INGOT, 3));

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertItemEntityCountIs(Items.COAL, MACHINE_POS, 3.0, 5);
                    helper.assertItemEntityCountIs(Items.IRON_INGOT, MACHINE_POS, 3.0, 3);
                })
                .thenSucceed();
    }

    /** 测试 114:能量低于电弧门槛,破坏后牛无伤、不着火、玻璃完好。 */
    private static void destroyedEnergyBelowThresholdIsSilent(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.energyBuffer());
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_STORAGE, BuiltinOIResourceIntegrations.ENERGY, 5_000);
        helper.setBlock(GLASS_POS, Blocks.GLASS);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertUnharmed(helper, cow, "Dissipate-tier discharge must not hurt nearby entities");
                    helper.assertBlockPresent(Blocks.GLASS, GLASS_POS);
                })
                .thenSucceed();
    }

    /** 测试 115:10 万能量落在电弧档,牛受电击伤害但无任何方块损坏。 */
    private static void destroyedEnergyArcShocksNearbyEntities(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.energyBuffer());
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_STORAGE, BuiltinOIResourceIntegrations.ENERGY, 100_000);
        helper.setBlock(GLASS_POS, Blocks.GLASS);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertHurt(helper, cow, "Arc-tier discharge must shock a cow standing one block away");
                    helper.assertBlockPresent(Blocks.GLASS, GLASS_POS);
                })
                .thenSucceed();
    }

    /** 测试 116:100 万能量爆燃,炸毁围挡内玻璃探针,黑曜石围挡无恙。 */
    private static void destroyedEnergyBlastBreaksBlocksInsideContainment(GameTestHelper helper) {
        placeContainment(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.energyBuffer());
        fillScalar(helper, machine, ScalarResourcePort.ENERGY_STORAGE, BuiltinOIResourceIntegrations.ENERGY, 1_000_000);
        helper.setBlock(GLASS_POS, Blocks.GLASS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    helper.assertBlockNotPresent(Blocks.GLASS, GLASS_POS);
                    helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(ARENA_MAX, 1, 2));
                    helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(ARENA_MIN, 1, 2));
                    helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(2, 0, 2));
                    helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(2, ARENA_MAX, 2));
                })
                .thenSucceed();
    }

    /** 测试 117:5 千高级能量按 10× 密度折算成 5 万有效能量,达到电弧档电击周围生物。 */
    private static void destroyedAdvancedEnergyAppliesDensityRatio(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.advancedEnergyBuffer());
        fillScalar(
                helper,
                machine,
                ScalarResourcePort.ADVANCED_ENERGY_STORAGE,
                BuiltinOIResourceIntegrations.ADVANCED_ENERGY,
                5_000);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> assertHurt(
                        helper, cow, "A 5k advanced charge must arc like a 50k plain charge"))
                .thenSucceed();
    }

    /** 测试 118:热量低于灼烤门槛,破坏后牛无伤不着火、场地无火焰。 */
    private static void destroyedHeatBelowThresholdIsSilent(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.heatBuffer());
        fillScalar(helper, machine, ScalarResourcePort.HEAT_STORAGE, BuiltinOIResourceIntegrations.HEAT, 5_000);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertUnharmed(helper, cow, "Residual heat must not hurt nearby entities");
                    if (cow.getRemainingFireTicks() > 0) {
                        helper.fail("Residual heat must not ignite nearby entities");
                    }
                    assertFireCount(helper, 0, "Residual heat must not place fire");
                })
                .thenSucceed();
    }

    /** 测试 119:满档热闪点燃并烫伤牛、在场地点燃至少一处火焰,且不破坏方块。 */
    private static void destroyedHeatFlashIgnitesEntitiesAndGround(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.heatBuffer());
        fillScalar(helper, machine, ScalarResourcePort.HEAT_STORAGE, BuiltinOIResourceIntegrations.HEAT, 100_000);
        helper.setBlock(GLASS_POS, Blocks.GLASS);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertHurt(helper, cow, "A full heat flash must scorch a cow standing one block away");
                    if (cow.getRemainingFireTicks() <= 0) {
                        helper.fail("A full heat flash must set the cow on fire");
                    }
                    assertFireMinimum(helper, 1, "A full heat flash must ignite at least one ground position");
                    helper.assertBlockPresent(Blocks.GLASS, GLASS_POS);
                })
                .thenSucceed();
    }

    /** 测试 120:满罐流体破坏后无事发生——不放置流体、牛无伤、玻璃完好。 */
    private static void destroyedFluidStorageSpillsNothing(GameTestHelper helper) {
        placeFloor(helper);
        MachineBlockEntity machine = place(helper, OIMachineDestroyedGameTestFixtures.fluidTank());
        fillWater(helper, machine, OIMachineDestroyedGameTestFixtures.FLUID_CAPACITY_MB);
        helper.setBlock(GLASS_POS, Blocks.GLASS);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, COW_POS);

        helper.destroyBlock(MACHINE_POS);

        helper.startSequence()
                .thenExecuteAfter(1, () -> {
                    assertUnharmed(helper, cow, "Destroying a fluid tank must not hurt nearby entities");
                    assertNoFluid(helper, "Destroying a fluid tank must not place fluid in the world");
                    helper.assertBlockPresent(Blocks.GLASS, GLASS_POS);
                })
                .thenSucceed();
    }

    private static void placeFloor(GameTestHelper helper) {
        for (int x = ARENA_MIN; x <= ARENA_MAX; x++) {
            for (int z = ARENA_MIN; z <= ARENA_MAX; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** 全封闭黑曜石围挡:地板、四壁(y 1..3)、顶盖,内腔 3×3×3,机器与探针都在腔内。 */
    private static void placeContainment(GameTestHelper helper) {
        for (int x = ARENA_MIN; x <= ARENA_MAX; x++) {
            for (int z = ARENA_MIN; z <= ARENA_MAX; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.OBSIDIAN);
                helper.setBlock(new BlockPos(x, ARENA_MAX, z), Blocks.OBSIDIAN);
                for (int y = 1; y < ARENA_MAX; y++) {
                    boolean wall = x == ARENA_MIN || x == ARENA_MAX || z == ARENA_MIN || z == ARENA_MAX;
                    if (wall) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.OBSIDIAN);
                    }
                }
            }
        }
    }

    private static MachineBlockEntity place(GameTestHelper helper, MachineDefinition definition) {
        helper.setBlock(MACHINE_POS, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static void insertItems(GameTestHelper helper, MachineBlockEntity machine, ItemStack... stacks) {
        ItemResourcePort storage = machine.machineComponents().require(ItemResourcePort.ITEM_STORAGE);
        for (ItemStack stack : stacks) {
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = storage.handler().insert(ItemResource.of(stack), stack.getCount(), transaction);
                if (inserted != stack.getCount()) {
                    helper.fail("Setup: expected to insert " + stack.getCount() + "x " + stack.getItem() + ", inserted " + inserted);
                }
                transaction.commit();
            }
        }
    }

    private static void fillWater(GameTestHelper helper, MachineBlockEntity machine, int amount) {
        FluidResourcePort tank = machine.machineComponents().require(FluidResourcePort.FLUID_STORAGE);
        insertResource(helper, tank.handler(), FluidResource.of(Fluids.WATER), amount, "mB water");
    }

    private static void fillScalar(
                                   GameTestHelper helper,
                                   MachineBlockEntity machine,
                                   ComponentKey<ScalarResourcePort> key,
                                   BuiltinResourceIntegration<ScalarResource, ScalarRecipeCapability> integration,
                                   int amount) {
        ScalarResourcePort storage = machine.machineComponents().require(key);
        insertResource(
                helper,
                storage.handler(),
                integration.recipeCapability().resource(),
                amount,
                integration.recipeCapability().resource().id().toString());
    }

    private static <R extends Resource> void insertResource(
                                                            GameTestHelper helper,
                                                            net.neoforged.neoforge.transfer.ResourceHandler<R> handler,
                                                            R resource,
                                                            int amount,
                                                            String what) {
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(resource, amount, transaction);
            if (inserted != amount) {
                helper.fail("Setup: expected to preload " + amount + " " + what + ", inserted " + inserted);
            }
            transaction.commit();
        }
    }

    private static void assertUnharmed(GameTestHelper helper, Mob mob, String message) {
        if (!mob.isAlive() || mob.getHealth() < mob.getMaxHealth()) {
            helper.fail(message + " (health " + mob.getHealth() + "/" + mob.getMaxHealth() + ")");
        }
    }

    private static void assertHurt(GameTestHelper helper, Mob mob, String message) {
        if (mob.isAlive() && mob.getHealth() >= mob.getMaxHealth()) {
            helper.fail(message + " (health " + mob.getHealth() + "/" + mob.getMaxHealth() + ")");
        }
    }

    private static void assertFireCount(GameTestHelper helper, int expected, String message) {
        int fires = countFire(helper);
        if (fires != expected) {
            helper.fail(message + " (expected " + expected + " fire blocks, found " + fires + ")");
        }
    }

    private static void assertFireMinimum(GameTestHelper helper, int minimum, String message) {
        int fires = countFire(helper);
        if (fires < minimum) {
            helper.fail(message + " (expected at least " + minimum + " fire blocks, found " + fires + ")");
        }
    }

    private static int countFire(GameTestHelper helper) {
        int fires = 0;
        for (int x = ARENA_MIN; x <= ARENA_MAX; x++) {
            for (int y = 0; y <= ARENA_MAX; y++) {
                for (int z = ARENA_MIN; z <= ARENA_MAX; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.FIRE)) {
                        fires++;
                    }
                }
            }
        }
        return fires;
    }

    private static void assertNoFluid(GameTestHelper helper, String message) {
        for (int x = ARENA_MIN; x <= ARENA_MAX; x++) {
            for (int y = 0; y <= ARENA_MAX; y++) {
                for (int z = ARENA_MIN; z <= ARENA_MAX; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!helper.getBlockState(pos).getFluidState().isEmpty()) {
                        helper.fail(message + " (found fluid at local " + pos + ")");
                    }
                }
            }
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
            throw new IllegalArgumentException("Machine destroyed GameTest index out of range: " + index);
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
