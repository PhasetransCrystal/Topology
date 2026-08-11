package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyManager;
import net.ptcrys.topo.api.pipe.survey.PipeSurveySnapshot;
import net.ptcrys.topo.apiv2.equipment.EquipmentRegistry;
import net.ptcrys.topo.data.pipe.BuiltinOIPipes;
import net.ptcrys.topo.datav2.equipment.BuiltinOIEquipment;
import net.ptcrys.topo.datav2.equipment.common.SurveyorRanges;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.function.Consumer;

/**
 * 管网勘测仪游戏内测试(全局编号 293-295):交互状态机(锚定/端点循环/清除)与耐久消耗、
 * 快照语义(节点占用/端口位/车道瓶颈/codec 往返)、材质观测范围档位裁剪。客户端渲染为
 * 薄层不在此测;服务端快照即渲染的全部输入。
 */
public final class PipeSurveyorGameTests {

    private static final String SUITE = "pipe_surveyor";
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 3;

    private PipeSurveyorGameTests() {}

    public static void register(RegisterGameTestsEvent event) {
        if (!OIScalarGameTestFixtures.enabled()) {
            return;
        }
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("pipe_surveyor"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(event, environment, index++,
                "surveyor_anchors_selects_and_clears",
                "Tests the surveyor interaction state machine: anchoring costs one durability and " + "activates a survey, sneak-clicks cycle endpoints A/B for free, air-use " + "clears, and the snapshot codec round-trips.",
                PipeSurveyorGameTests::surveyorAnchorsSelectsAndClears);
        register(event, environment, index++,
                "surveyor_snapshot_reports_ports_occupancy_and_lanes",
                "Tests snapshot semantics on a flowing 3x3 grid: port bits mark the extract and " + "destination sides, node occupancy reflects the last display window, and " + "the endpoint pair reports three lanes with basic-node bottlenecks.",
                PipeSurveyorGameTests::surveyorSnapshotReportsPortsOccupancyAndLanes);
        register(event, environment, index,
                "surveyor_material_tiers_bound_range",
                "Tests the material range tiers: iron surveys 16 blocks and bronze 32, bounding " + "the snapshot node set along a long line.",
                PipeSurveyorGameTests::surveyorMaterialTiersBoundRange);
    }

    /** 测试 293:锚定扣 1 耐久并激活;潜行选点 A→B→重选 A 不扣耐久;右键空气清除;codec 往返。 */
    private static void surveyorAnchorsSelectsAndClears(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        chest(helper, new BlockPos(4, 1, 2));
        source.setItem(0, new ItemStack(Items.COAL, 64));
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinOIPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(2, 1, 2), BuiltinOIPipes.ITEM_PIPE_BASIC);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinOIPipes.ITEM_PIPE_BASIC);
        PipeNetworkEngine.runtime(helper.getLevel())
                .setSideIntent(helper.absolutePos(new BlockPos(1, 1, 2)), Direction.WEST, PipeSideIntent.EXTRACT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack surveyor = surveyorStack("iron");
        player.setItemInHand(InteractionHand.MAIN_HAND, surveyor);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    useOnPipe(helper, player, surveyor, new BlockPos(1, 1, 2), false);
                    PipeSurveyManager.Survey survey = PipeSurveyManager.activeSurvey(player.getUUID());
                    if (survey == null || survey.anchor != helper.absolutePos(new BlockPos(1, 1, 2)).asLong()) {
                        helper.fail("Anchoring must record an active survey at the clicked pipe");
                        return;
                    }
                    if (survey.range != 16) {
                        helper.fail("Iron surveyor must carry the 16-block tier, got " + survey.range);
                        return;
                    }
                    if (surveyor.getDamageValue() != 1) {
                        helper.fail("Anchoring must charge exactly one durability, got " + surveyor.getDamageValue());
                    }
                })
                .thenExecuteAfter(1, () -> {
                    useOnPipe(helper, player, surveyor, new BlockPos(1, 1, 2), true);
                    useOnPipe(helper, player, surveyor, new BlockPos(3, 1, 2), true);
                    PipeSurveyManager.Survey survey = PipeSurveyManager.activeSurvey(player.getUUID());
                    if (survey == null || survey.pointA != helper.absolutePos(new BlockPos(1, 1, 2)).asLong() || survey.pointB != helper.absolutePos(new BlockPos(3, 1, 2)).asLong()) {
                        helper.fail("Sneak-clicks must set endpoints A then B");
                        return;
                    }
                    if (surveyor.getDamageValue() != 1) {
                        helper.fail("Endpoint selection must not charge durability, got " + surveyor.getDamageValue());
                        return;
                    }
                    PipeSurveySnapshot snapshot = PipeSurveyManager.buildSnapshot(helper.getLevel(), survey);
                    if (snapshot.lanes().size() != 1) {
                        helper.fail("A straight line offers exactly one lane, got " + snapshot.lanes().size());
                        return;
                    }
                    if (snapshot.pairCapacity() != 8) {
                        helper.fail("Basic line capacity must be the 8/t node throughput, got " + snapshot.pairCapacity());
                        return;
                    }
                    RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                            io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
                    snapshot.write(buffer);
                    PipeSurveySnapshot decoded = PipeSurveySnapshot.read(buffer);
                    if (decoded.nodePos().length != snapshot.nodePos().length || decoded.pairCapacity() != snapshot.pairCapacity() || decoded.lanes().size() != snapshot.lanes().size() || decoded.pointA() != snapshot.pointA() || decoded.windowTicks() != snapshot.windowTicks()) {
                        helper.fail("Snapshot codec round-trip must preserve every field");
                    }
                })
                .thenExecuteAfter(1, () -> {
                    useOnPipe(helper, player, surveyor, new BlockPos(2, 1, 2), true);
                    PipeSurveyManager.Survey survey = PipeSurveyManager.activeSurvey(player.getUUID());
                    if (survey == null || survey.pointA != helper.absolutePos(new BlockPos(2, 1, 2)).asLong() || survey.pointB != PipeSurveySnapshot.NO_POINT) {
                        helper.fail("A third sneak-click must restart the endpoint cycle at A");
                        return;
                    }
                    surveyor.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
                    if (PipeSurveyManager.activeSurvey(player.getUUID()) != null) {
                        helper.fail("Air-use must clear the active survey");
                    }
                })
                .thenSucceed();
    }

    /** 测试 294:3×3 网格流动后的快照——端口位、上窗占用、双端点三车道与基础瓶颈、容量 24。 */
    private static void surveyorSnapshotReportsPortsOccupancyAndLanes(GameTestHelper helper) {
        placeFloor(helper);
        ChestBlockEntity source = chest(helper, new BlockPos(0, 1, 2));
        chest(helper, new BlockPos(4, 1, 2));
        fillChest(source, Items.COAL, 27 * 64);
        placePipe(helper, new BlockPos(1, 1, 2), BuiltinOIPipes.ITEM_PIPE_ELITE);
        placePipe(helper, new BlockPos(3, 1, 2), BuiltinOIPipes.ITEM_PIPE_ELITE);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                if (z == 2 && (x == 1 || x == 3)) {
                    continue;
                }
                placePipe(helper, new BlockPos(x, 1, z), BuiltinOIPipes.ITEM_PIPE_BASIC);
            }
        }
        BlockPos extractor = helper.absolutePos(new BlockPos(1, 1, 2));
        PipeNetworkEngine.runtime(helper.getLevel()).setSideIntent(extractor, Direction.WEST, PipeSideIntent.EXTRACT);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack surveyor = surveyorStack("iron");
        player.setItemInHand(InteractionHand.MAIN_HAND, surveyor);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    useOnPipe(helper, player, surveyor, new BlockPos(1, 1, 2), false);
                    useOnPipe(helper, player, surveyor, new BlockPos(1, 1, 2), true);
                    useOnPipe(helper, player, surveyor, new BlockPos(3, 1, 2), true);
                })
                // 终极口展示窗口 = 最大聚合窗(40t);跑满两个窗口让"上一窗口"读数非零。
                .thenExecuteAfter(90, () -> {
                    PipeSurveyManager.Survey survey = PipeSurveyManager.activeSurvey(player.getUUID());
                    if (survey == null) {
                        helper.fail("Survey must stay active");
                        return;
                    }
                    PipeSurveySnapshot snapshot = PipeSurveyManager.buildSnapshot(helper.getLevel(), survey);
                    if (snapshot.nodePos().length != 9) {
                        helper.fail("The 3x3 grid must survey 9 nodes, got " + snapshot.nodePos().length);
                        return;
                    }
                    int extractIndex = ordinalOf(snapshot, extractor.asLong());
                    int destinationIndex = ordinalOf(snapshot, helper.absolutePos(new BlockPos(3, 1, 2)).asLong());
                    if (extractIndex < 0 || destinationIndex < 0) {
                        helper.fail("Both elite endpoints must appear in the snapshot");
                        return;
                    }
                    if ((PipeSurveySnapshot.extractMask(snapshot.nodePortBits()[extractIndex]) & (1 << Direction.WEST.ordinal())) == 0) {
                        helper.fail("The west endpoint must report an EXTRACT port on its west face");
                        return;
                    }
                    if ((PipeSurveySnapshot.destinationMask(snapshot.nodePortBits()[destinationIndex]) & (1 << Direction.EAST.ordinal())) == 0) {
                        helper.fail("The east endpoint must report a DESTINATION port on its east face");
                        return;
                    }
                    if (snapshot.nodeUsed()[extractIndex] <= 0) {
                        helper.fail("After two display windows the flowing endpoint must show occupancy");
                        return;
                    }
                    if (snapshot.lanes().size() != 3 || snapshot.pairCapacity() != 24) {
                        helper.fail("Mid-to-mid endpoints must analyze three lanes x 8/t = 24, got " + snapshot.lanes().size() + " lanes x capacity " + snapshot.pairCapacity());
                        return;
                    }
                    for (PipeSurveySnapshot.Lane lane : snapshot.lanes()) {
                        long bottleneck = lane.positions()[lane.bottleneckOrdinal()];
                        int ordinal = ordinalOf(snapshot, bottleneck);
                        if (ordinal < 0 || snapshot.nodeThroughput()[ordinal] != 8) {
                            helper.fail("Every lane bottleneck must be a basic 8/t node");
                            return;
                        }
                    }
                })
                .thenSucceed();
    }

    /** 测试 295:长直线上铁(16)/青铜(32)档裁剪——快照节点数 17/33;查表值一致。 */
    private static void surveyorMaterialTiersBoundRange(GameTestHelper helper) {
        for (int x = 0; x < 40; x++) {
            placePipe(helper, new BlockPos(x, 1, 0), BuiltinOIPipes.ITEM_PIPE_BASIC);
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack iron = surveyorStack("iron");
        ItemStack bronze = surveyorStack("bronze");

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (SurveyorRanges.rangeOf(iron.getItem()) != 16 || SurveyorRanges.rangeOf(bronze.getItem()) != 32) {
                        helper.fail("Range table must read 16 (iron) / 32 (bronze), got " + SurveyorRanges.rangeOf(iron.getItem()) + "/" + SurveyorRanges.rangeOf(bronze.getItem()));
                        return;
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, iron);
                    useOnPipe(helper, player, iron, new BlockPos(0, 1, 0), false);
                    PipeSurveySnapshot ironSnapshot = PipeSurveyManager.buildSnapshot(
                            helper.getLevel(), PipeSurveyManager.activeSurvey(player.getUUID()));
                    if (ironSnapshot.nodePos().length != 17) {
                        helper.fail("Iron tier must bound the line survey to 17 nodes (anchor + 16), got " + ironSnapshot.nodePos().length);
                        return;
                    }
                    player.setItemInHand(InteractionHand.MAIN_HAND, bronze);
                    useOnPipe(helper, player, bronze, new BlockPos(0, 1, 0), false);
                    PipeSurveySnapshot bronzeSnapshot = PipeSurveyManager.buildSnapshot(
                            helper.getLevel(), PipeSurveyManager.activeSurvey(player.getUUID()));
                    if (bronzeSnapshot.nodePos().length != 33) {
                        helper.fail("Bronze tier must bound the line survey to 33 nodes (anchor + 32), got " + bronzeSnapshot.nodePos().length);
                    }
                })
                .thenSucceed();
    }

    // --- shared helpers ------------------------------------------------------------------------

    /** 服务端直驱物品交互:与玩家右键同管线(useOn),sneak 切端点选择分支。 */
    private static void useOnPipe(GameTestHelper helper, Player player, ItemStack stack,
                                  BlockPos pipePos, boolean sneak) {
        player.setShiftKeyDown(sneak);
        BlockPos absolute = helper.absolutePos(pipePos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setShiftKeyDown(false);
    }

    private static int ordinalOf(PipeSurveySnapshot snapshot, long pos) {
        for (int i = 0; i < snapshot.nodePos().length; i++) {
            if (snapshot.nodePos()[i] == pos) {
                return i;
            }
        }
        return -1;
    }

    private static ItemStack surveyorStack(String material) {
        for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
            if (record.equipment() == BuiltinOIEquipment.PIPE_SURVEYOR && record.material().id().getPath().equals(material)) {
                Item item = record.entry().get();
                return new ItemStack(item);
            }
        }
        throw new IllegalStateException("Setup: no pipe surveyor registered for material " + material);
    }

    private static void placeFloor(GameTestHelper helper) {
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    private static void placePipe(GameTestHelper helper, BlockPos pos, PipeDefinition definition) {
        helper.setBlock(pos, definition.registeredBlock().get().defaultBlockState());
    }

    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        if (!(blockEntity instanceof ChestBlockEntity chestEntity)) {
            throw new IllegalStateException("Setup: expected a chest at " + pos);
        }
        return chestEntity;
    }

    private static void fillChest(ChestBlockEntity chestEntity, net.minecraft.world.item.Item item, int count) {
        int slot = 0;
        int remaining = count;
        while (remaining > 0 && slot < chestEntity.getContainerSize()) {
            int put = Math.min(64, remaining);
            chestEntity.setItem(slot++, new ItemStack(item, put));
            remaining -= put;
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
            throw new IllegalArgumentException("Pipe surveyor GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 400, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
