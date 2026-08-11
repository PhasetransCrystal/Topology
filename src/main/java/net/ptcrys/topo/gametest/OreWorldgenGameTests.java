package net.ptcrys.topo.gametest;

import net.ptcrys.topo.apiv2.ore.engine.OreBlockResolver;
import net.ptcrys.topo.apiv2.ore.engine.OreVeinPlanner;
import net.ptcrys.topo.apiv2.ore.engine.PlannedOreBlock;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 * End-to-end deterministic worldgen: plan the GameTest lead deepslate grid vein for a chunk against
 * a deepslate host, assert every generated block resolves to lead deepslate ore, place a sample and
 * probe it back.
 */
public final class OreWorldgenGameTests {

    private static final String SUITE = "ore_worldgen";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 1;
    private static final long TEST_SEED = 0x6F72655F73656564L;

    private OreWorldgenGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("ore_worldgen"), new TestEnvironmentDefinition.AllOf());
        register(
                event,
                environment,
                1,
                "ore_planner_generates_and_probes_lead_deepslate_ore",
                "Tests OreVeinPlanner / OreVein / OreBlockResolver end to end: the GameTest " + "deterministic lead deepslate grid vein is planned for a chunk against a deepslate " + "host, every planned block resolves to the lead deepslate ore block, and a sample is " + "placed into the world and probed back.",
                OreWorldgenGameTests::orePlannerGeneratesAndProbes);
    }

    private static void orePlannerGeneratesAndProbes(GameTestHelper helper) {
        Block expected = OreBlockResolver.materialHelper()
                .block(BuiltinOIMaterials.LEAD, BuiltinOIMaterialForms.DEEPSLATE_ORE)
                .orElseThrow(() -> new IllegalStateException("lead deepslate ore block is not registered"));

        OreVeinPlanner planner = new OreVeinPlanner(List.of(OIOreGameTestFixtures.leadBlob()), OreBlockResolver.materialHelper());
        List<PlannedOreBlock> planned = planner.planChunk(
                TEST_SEED, Level.OVERWORLD, new ChunkPos(0, 0),
                pos -> Blocks.DEEPSLATE.defaultBlockState());

        if (planned.isEmpty()) {
            helper.fail("deterministic planner produced no ore blocks for the test vein", BlockPos.ZERO);
            return;
        }
        for (PlannedOreBlock block : planned) {
            if (block.material() != BuiltinOIMaterials.LEAD || block.form() != BuiltinOIMaterialForms.DEEPSLATE_ORE || !block.blockState().is(expected)) {
                helper.fail(
                        "planned block is not lead deepslate ore: " + block.material().id() + " / " + block.form().id(),
                        BlockPos.ZERO);
                return;
            }
        }

        int sample = Math.min(planned.size(), 6);
        for (int i = 0; i < sample; i++) {
            helper.setBlock(new BlockPos(i % 3, 1, i / 3), planned.get(i).blockState());
        }
        for (int i = 0; i < sample; i++) {
            BlockPos rel = new BlockPos(i % 3, 1, i / 3);
            if (!helper.getBlockState(rel).is(expected)) {
                helper.fail("probed world block is not lead deepslate ore", rel);
                return;
            }
        }
        helper.succeed();
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
                        testData(environment),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }

    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
                                                                           Holder<TestEnvironmentDefinition<?>> environment) {
        return new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE);
    }
}
