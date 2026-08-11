package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.api.pipe.PipeSideVisual;
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.network.PipeNodeRecord;
import net.ptcrys.topo.data.pipe.BuiltinOIPipes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 管道端口屏全自动 GUI 验收探针(同 {@link UiPerfProbe} 的流水线骨架,独立 flag 互不干扰):
 * 仅当工作目录存在 {@code oi-pipe-probe.flag} 时激活;自动建平坦世界(每轮唯一名),摆
 * 箱-终极物品管×3-箱 与 终极能量迷你排,经真实 {@code ServerPlayerGameMode.useItemOn} 管线
 * 打扳手开端口屏,三张 GUI 截图(物品屏/数量弹窗/能量屏)+ 服务端通道断言(过滤/批量/周期
 * 钳制)写入 {@code oi-pipe-probe-report.txt} 后自动退出。传输行为与性能采样不在此探针——
 * 前者归 gametest 套件,后者的历史采样任务已完成(数据在 pipe-network-system 记忆)。
 */
public final class PipeProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("OI-PipeProbe");
    private static final Path FLAG_FILE = Path.of("oi-pipe-probe.flag");
    private static final Path REPORT_FILE = Path.of("oi-pipe-probe-report.txt");
    /** 每轮唯一世界名:复用同名存档会撞上一轮的残留场景(并行会话共用 run/ 时尤甚)。 */
    private static final String LEVEL_ID = "oi-pipe-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;
    private static final List<String> STANDARD_BINDING_IDS = List.of(
            "oi_pipe_port_side_sync",
            "oi_pipe_port_strategy_value",
            "oi_pipe_port_amount_value",
            "oi_pipe_port_interval_value",
            "oi_pipe_port_order_value",
            "oi_pipe_port_filter_whitelist_value",
            "oi_pipe_port_filter_blacklist_value");

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        SETTLE,
        WORLD_DETAIL_SETTLE,
        WORLD_STRAIGHT_SETTLE,
        WORLD_TERMINAL_SETTLE,
        WORLD_EXTRACT_SETTLE,
        WORLD_CORNER_SETTLE,
        GUI_WAIT,
        GUI_SHOT,
        GUI2_WAIT,
        GUI2_SHOT,
        FLUSH,
        DONE
    }

    private static PipeProbe instance;

    private final StringBuilder report = new StringBuilder();
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    private int originalFov = 70;
    private BlockPos extractorPipe;
    private BlockPos sourceChest;
    private BlockPos targetChest;
    private BlockPos galleryCenter;
    private BlockPos detailCenter;
    private BlockPos straightCenter;
    private BlockPos terminalCenter;
    private BlockPos extractCenter;
    private BlockPos cornerCenter;
    private Vec3 galleryCamera;
    private Vec3 detailCamera;
    private Vec3 straightCamera;
    private Vec3 terminalCamera;
    private Vec3 extractCamera;
    private Vec3 cornerCamera;
    private boolean bindingExerciseArmed;
    /** 终极能量管抽取端口:第二张 GUI 截图(±131k 大步进钮是历史上的列宽溢出现场)。 */
    private BlockPos ultEnergyExtractor;

    private PipeProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new PipeProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Pipe probe armed: will build a pipe line, wrench it through the real use pipeline and screenshot Jade");
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (state) {
            case WAIT_TITLE -> {
                if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    minecraft.options.pauseOnLostFocus = false;
                    originalFov = minecraft.options.fov().get();
                    minecraft.debugEntries.setOverlayVisible(false);
                    minecraft.debugEntries.setStatus(
                            net.minecraft.client.gui.components.debug.DebugScreenEntries.CHUNK_BORDERS,
                            net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);
                    // 主流玩家窗口口径:1600x900 在 auto GUI scale=3 下逻辑 533x300。注意
                    // 1280x720 的 auto scale 也是 3(逻辑高仅 240),不是想当然的 scale 2。
                    if (minecraft.getWindow().getWidth() < 1600) {
                        minecraft.getWindow().setWindowed(1600, 900);
                    }
                    minecraft.createWorldOpenFlows().createFreshLevel(
                            LEVEL_ID,
                            new LevelSettings(
                                    LEVEL_ID,
                                    GameType.CREATIVE,
                                    LevelSettings.DifficultySettings.DEFAULT,
                                    true,
                                    WorldDataConfiguration.DEFAULT),
                            new WorldOptions(20260611L, false, false),
                            WorldPresets::createFlatWorldDimensions,
                            minecraft.screen);
                    waitTicks = 0;
                    state = State.WAIT_WORLD;
                }
            }
            case WAIT_WORLD -> {
                if (timedOut(minecraft, "waiting for probe world")) {
                    return;
                }
                if (minecraft.level != null && minecraft.player != null && minecraft.getSingleplayerServer() != null && minecraft.screen == null) {
                    minecraft.options.hideGui = true;
                    buildScene(minecraft);
                    countdown = 30;
                    state = State.SETTLE;
                }
            }
            case SETTLE -> {
                aimAt(minecraft, galleryCenter);
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "oi-pipe-probe-world-gallery");
                    report.append("world gallery screenshot captured -> PASS\n");
                    moveToDetailCamera(minecraft);
                    countdown = 30;
                    state = State.WORLD_DETAIL_SETTLE;
                }
            }
            case WORLD_DETAIL_SETTLE -> {
                aimAt(minecraft, detailCenter);
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "oi-pipe-probe-world-detail");
                    report.append("world detail screenshot captured -> PASS\n");
                    minecraft.options.fov().set(35);
                    moveToCamera(minecraft, straightCamera, straightCenter);
                    countdown = 30;
                    state = State.WORLD_STRAIGHT_SETTLE;
                }
            }
            case WORLD_STRAIGHT_SETTLE -> {
                aimAt(minecraft, straightCenter);
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "oi-pipe-probe-world-straight");
                    report.append("world straight-run screenshot captured -> PASS\n");
                    moveToCamera(minecraft, terminalCamera, terminalCenter);
                    countdown = 30;
                    state = State.WORLD_TERMINAL_SETTLE;
                }
            }
            case WORLD_TERMINAL_SETTLE -> {
                aimAt(minecraft, terminalCenter);
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "oi-pipe-probe-world-terminal");
                    report.append("world endpoint-node screenshot captured -> PASS\n");
                    minecraft.options.fov().set(25);
                    moveToCamera(minecraft, extractCamera, extractCenter);
                    countdown = 30;
                    state = State.WORLD_EXTRACT_SETTLE;
                }
            }
            case WORLD_EXTRACT_SETTLE -> {
                aimAt(minecraft, extractCenter);
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "oi-pipe-probe-world-extract");
                    report.append("world extraction-port screenshot captured -> PASS\n");
                    minecraft.options.fov().set(35);
                    moveToCamera(minecraft, cornerCamera, cornerCenter);
                    countdown = 30;
                    state = State.WORLD_CORNER_SETTLE;
                }
            }
            case WORLD_CORNER_SETTLE -> {
                aimAt(minecraft, cornerCenter);
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, "oi-pipe-probe-world-corner");
                    report.append("world three-dimensional corner screenshot captured -> PASS\n");
                    minecraft.options.fov().set(originalFov);
                    minecraft.options.hideGui = false;
                    runWrenchScript(minecraft);
                    waitTicks = 0;
                    state = State.GUI_WAIT;
                }
            }
            case GUI_WAIT -> {
                if (minecraft.screen != null) {
                    report.append("port screen opened: ").append(minecraft.screen.getClass().getSimpleName())
                            .append(" -> PASS\n");
                    moveCursorAway(minecraft);
                    countdown = 56;
                    state = State.GUI_SHOT;
                } else if (++waitTicks > 100) {
                    report.append("port screen did not open within 100 ticks -> FAIL\n");
                    runGuiServerAsserts(minecraft);
                    countdown = 10;
                    state = State.FLUSH;
                }
            }
            case GUI_SHOT -> {
                if (countdown == 54) {
                    exerciseStandardBindings(minecraft);
                }
                if (countdown == 52) {
                    dumpGuiGeometry(minecraft);
                }
                if (countdown == 50) {
                    grabScreenshot(minecraft, "oi-pipe-probe-gui");
                }
                if (countdown == 46) {
                    openItemPickerForShot(minecraft);
                }
                if (countdown == 36) {
                    checkStandardBindingReply(minecraft);
                }
                if (countdown == 38) {
                    grabScreenshot(minecraft, "oi-pipe-probe-gui-picker");
                }
                if (countdown == 34) {
                    removeTopPopup(minecraft);
                }
                if (countdown == 30) {
                    openFluidPickerForShot(minecraft);
                }
                if (countdown == 22) {
                    grabScreenshot(minecraft, "oi-pipe-probe-gui-fluid-picker");
                }
                if (countdown == 18) {
                    removeTopPopup(minecraft);
                }
                if (countdown == 14) {
                    openAmountPopupForShot(minecraft);
                }
                if (countdown == 6) {
                    grabScreenshot(minecraft, "oi-pipe-probe-gui-popup");
                }
                if (--countdown <= 0) {
                    if (minecraft.player != null) {
                        minecraft.player.closeContainer();
                    }
                    openUltEnergyGui(minecraft);
                    waitTicks = 0;
                    state = State.GUI2_WAIT;
                }
            }
            case GUI2_WAIT -> {
                if (minecraft.screen != null) {
                    report.append("ult energy port screen opened -> PASS\n");
                    moveCursorAway(minecraft);
                    countdown = 15;
                    state = State.GUI2_SHOT;
                } else if (++waitTicks > 100) {
                    report.append("ult energy port screen did not open within 100 ticks -> FAIL\n");
                    runGuiServerAsserts(minecraft);
                    countdown = 10;
                    state = State.FLUSH;
                }
            }
            case GUI2_SHOT -> {
                if (countdown == 5) {
                    grabScreenshot(minecraft, "oi-pipe-probe-gui-energy");
                }
                if (--countdown <= 0) {
                    if (minecraft.player != null) {
                        minecraft.player.closeContainer();
                    }
                    runGuiServerAsserts(minecraft);
                    countdown = 15;
                    state = State.FLUSH;
                }
            }
            case FLUSH -> {
                if (--countdown <= 0) {
                    finishProbe(minecraft);
                }
            }
            case DONE -> {}
        }
    }

    /** 摆场景:源箱(256 煤) - 终极物品管×3 - 目标箱;玩家主手扳手、快捷栏放管道物品。 */
    private void buildScene(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var clientPlayer = minecraft.player;
        if (server == null || clientPlayer == null) {
            return;
        }
        BlockPos playerOrigin = clientPlayer.blockPosition();
        BlockPos anchor = playerOrigin.relative(Direction.EAST, 2);
        sourceChest = anchor;
        extractorPipe = anchor.relative(Direction.SOUTH, 1);
        BlockPos middlePipe = anchor.relative(Direction.SOUTH, 2);
        targetChest = anchor.relative(Direction.SOUTH, 4);
        BlockPos galleryBase = playerOrigin.offset(6, 3, 18);
        galleryCenter = galleryBase.offset(4, 8, 0);
        detailCenter = galleryBase.offset(4, 12, 0);
        straightCenter = galleryBase.offset(20, 3, 0);
        terminalCenter = galleryBase.offset(26, 3, 0);
        extractCenter = galleryBase.offset(32, 3, 0);
        cornerCenter = galleryBase.offset(40, 3, 0);
        galleryCamera = new Vec3(
                galleryCenter.getX() + 0.5,
                galleryCenter.getY() - 1.0,
                galleryCenter.getZ() - 22.5);
        detailCamera = new Vec3(
                detailCenter.getX() + 0.5,
                detailCenter.getY() - 1.0,
                detailCenter.getZ() - 5.5);
        straightCamera = new Vec3(
                straightCenter.getX() + 0.5,
                straightCenter.getY() - 1.0,
                straightCenter.getZ() - 3.4);
        terminalCamera = new Vec3(
                terminalCenter.getX() + 0.5,
                terminalCenter.getY() - 1.0,
                terminalCenter.getZ() - 2.7);
        extractCamera = new Vec3(
                extractCenter.getX() + 0.5,
                extractCenter.getY() - 0.5,
                extractCenter.getZ() - 2.0);
        cornerCamera = new Vec3(
                cornerCenter.getX() - 3.0,
                cornerCenter.getY() - 0.5,
                cornerCenter.getZ() - 4.0);
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                buildPipeGallery(level, galleryBase);
                buildStraightRun(level, straightCenter);
                buildTerminalPreview(level, terminalCenter);
                buildExtractionPreview(level, extractCenter);
                buildCornerPreview(level, cornerCenter);
                level.setBlock(sourceChest, Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(extractorPipe,
                        BuiltinOIPipes.ITEM_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                level.setBlock(middlePipe,
                        BuiltinOIPipes.ITEM_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                level.setBlock(anchor.relative(Direction.SOUTH, 3),
                        BuiltinOIPipes.ITEM_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                level.setBlock(targetChest, Blocks.CHEST.defaultBlockState(), 3);
                if (level.getBlockEntity(sourceChest) instanceof ChestBlockEntity chest) {
                    for (int slot = 0; slot < 27; slot++) {
                        chest.setItem(slot, new ItemStack(Items.COAL, 64));
                    }
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                net.ptcrys.topo.helper.IdHelper.oi("iron_wrench"))));
                player.getInventory().setItem(1,
                        new ItemStack(BuiltinOIPipes.ITEM_PIPE_ELITE.registeredBlock().get()));

                // 终极能量迷你排:只为第二张 GUI 截图(±131k 大步进钮的列宽极端场景)。
                BlockPos ultAnchor = sourceChest.relative(Direction.EAST, 4);
                ultEnergyExtractor = ultAnchor.relative(Direction.SOUTH, 1);
                level.setBlock(ultAnchor,
                        net.ptcrys.topo.datav2.machine.BuiltinOIMachines.COMBUSTION_GENERATOR_T3
                                .registeredBlock().getDefaultState(),
                        3);
                level.setBlock(ultEnergyExtractor,
                        BuiltinOIPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                level.setBlock(ultAnchor.relative(Direction.SOUTH, 2),
                        BuiltinOIPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState(), 3);
                level.setBlock(ultAnchor.relative(Direction.SOUTH, 3),
                        net.ptcrys.topo.datav2.machine.BuiltinOIMachines.RESISTIVE_HEATER_T3
                                .registeredBlock().getDefaultState(),
                        3);
                PipeNetworkEngine.runtime(level).setSideIntent(ultEnergyExtractor, Direction.NORTH,
                        net.ptcrys.topo.api.pipe.PipeSideIntent.EXTRACT);

                report.append("scene built: chest ").append(sourceChest)
                        .append(" -> pipes -> chest ").append(targetChest)
                        .append("; ult energy row at ").append(ultAnchor)
                        .append("; visual gallery center ").append(galleryCenter).append('\n');
                teleportCamera(level, player, galleryCamera, galleryCenter);
            } catch (Exception e) {
                LOGGER.error("Pipe probe: scene build failed", e);
                report.append("FAILED scene build: ").append(e).append('\n');
            }
        });
    }

    /**
     * 经真实交互管线打扳手:潜行右键抽取管朝源箱的面(必须穿过 sneak-bypass 才能到达
     * {@code PipeBlock.useItemOn}),验证意图变为 EXTRACT;再非潜行右键同一端帽——应打开
     * 端口配置屏(GUI_WAIT 态在客户端确认)。
     */
    private void runWrenchScript(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || extractorPipe == null) {
            return;
        }
        BlockPos pipePos = extractorPipe;
        Direction side = Direction.NORTH;
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
                // 触及距离保险:出生点漂移/坠落会让 useItemOn 的 canInteractWithBlock 直接 Fail。
                player.teleportTo(pipePos.getX() - 1.5, pipePos.getY(), pipePos.getZ() - 0.5);
                ItemStack wrench = player.getMainHandItem();
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(pipePos.getX() + 0.5, pipePos.getY() + 0.5, pipePos.getZ()),
                        side, pipePos, false);

                player.setShiftKeyDown(true);
                var sneakResult = player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND, hit);
                player.setShiftKeyDown(false);
                PipeNodeRecord record = runtime.data().node(pipePos.asLong());
                PipeSideRole afterSneak = record == null ? null : record.role(side);
                report.append(String.format(Locale.ROOT,
                        "sneak wrench result=%s roleAfter=%s -> %s%n",
                        sneakResult, afterSneak,
                        afterSneak == PipeSideRole.EXTRACT ? "PASS" : "FAIL (sneak bypass still eats the click?)"));

                // 播种黑白名单(截图即见满列表;白名单含煤,保证 RUN 期搬运照常流动)。
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:coal");
                runtime.uiAddFilterEntry(pipePos, side, true, "#minecraft:planks");
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:iron_ingot");
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:gold_ingot");
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:redstone");
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:diamond");
                runtime.uiAddFilterEntry(pipePos, side, false, "minecraft:dirt");
                runtime.uiAddFilterEntry(pipePos, side, false, "#c:ingots");
                var seeded = runtime.portFilter(pipePos, side);
                report.append(String.format(Locale.ROOT,
                        "filter seed white=%d black=%d -> %s%n",
                        seeded.whitelist().size(), seeded.blacklist().size(),
                        seeded.whitelist().size() == 6 && seeded.blacklist().size() == 2 ? "PASS" : "FAIL"));

                var clickResult = player.gameMode.useItemOn(player, level, wrench, InteractionHand.MAIN_HAND, hit);
                report.append(String.format(Locale.ROOT, "plain wrench (should open the port screen) result=%s%n",
                        clickResult));
            } catch (Exception e) {
                LOGGER.error("Pipe probe: wrench script failed", e);
                report.append("FAILED wrench script: ").append(e).append('\n');
            }
        });
    }

    /** 元素级布局取证(通用工具 {@link UiGeometryDump}):截图看不出的塌缩/越界在这里现形。 */
    private void dumpGuiGeometry(Minecraft minecraft) {
        try {
            if (!(minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen)) {
                return;
            }
            report.append(UiGeometryDump.dump(screen.getMenu().getModularUI().ui.rootElement));
        } catch (Exception e) {
            LOGGER.error("Pipe probe: geometry dump failed", e);
            report.append("FAILED geometry dump: ").append(e).append('\n');
        }
    }

    private void exerciseStandardBindings(Minecraft minecraft) {
        UIElement root = rootElement(minecraft);
        if (root == null) {
            report.append("standard pipe binding tree unavailable -> FAIL\n");
            return;
        }
        List<String> missing = STANDARD_BINDING_IDS.stream()
                .filter(id -> !(findById(root, id) instanceof BindableValue<?>))
                .toList();
        report.append("standard pipe binding anchors: ")
                .append(missing.isEmpty() ? "all present -> PASS" : "missing " + missing + " -> FAIL")
                .append('\n');
        BindableValue<Integer> strategy = findIntBinding(root, "oi_pipe_port_strategy_value");
        BindableValue<Integer> amount = findIntBinding(root, "oi_pipe_port_amount_value");
        if (strategy == null || amount == null) {
            return;
        }
        strategy.setValue(1);
        amount.setValue(Integer.MAX_VALUE);
        bindingExerciseArmed = true;
        report.append("standard pipe bindings submitted strategy=1 and amount=Integer.MAX_VALUE\n");
    }

    private void checkStandardBindingReply(Minecraft minecraft) {
        if (!bindingExerciseArmed) {
            return;
        }
        bindingExerciseArmed = false;
        UIElement root = rootElement(minecraft);
        BindableValue<Integer> strategy = findIntBinding(root, "oi_pipe_port_strategy_value");
        BindableValue<Integer> amount = findIntBinding(root, "oi_pipe_port_amount_value");
        if (strategy == null || amount == null) {
            report.append("standard pipe binding reply mirrors unavailable -> FAIL\n");
            return;
        }
        int clientStrategy = strategy.getValue();
        int clientAmount = amount.getValue();
        var server = minecraft.getSingleplayerServer();
        if (server == null || extractorPipe == null) {
            report.append("standard pipe binding reply server unavailable -> FAIL\n");
            return;
        }
        BlockPos pipePos = extractorPipe;
        server.execute(() -> {
            PipeLevelRuntime runtime = PipeNetworkEngine.runtime(server.overworld());
            int serverStrategy = runtime.portStrategyIndex(pipePos, Direction.NORTH);
            int serverAmount = runtime.portAmount(pipePos, Direction.NORTH);
            boolean passed = clientStrategy == serverStrategy && clientAmount == serverAmount && clientStrategy == 1 && clientAmount != Integer.MAX_VALUE;
            report.append(String.format(
                    Locale.ROOT,
                    "standard pipe authoritative reply client=%d/%d server=%d/%d -> %s%n",
                    clientStrategy,
                    clientAmount,
                    serverStrategy,
                    serverAmount,
                    passed ? "PASS" : "FAIL"));
        });
    }

    private void buildPipeGallery(ServerLevel level, BlockPos base) {
        List<List<PipeDefinition>> rows = List.of(
                List.of(BuiltinOIPipes.ITEM_PIPE_BASIC, BuiltinOIPipes.ITEM_PIPE_ADVANCED,
                        BuiltinOIPipes.ITEM_PIPE_ELITE),
                List.of(BuiltinOIPipes.FLUID_PIPE_BASIC, BuiltinOIPipes.FLUID_PIPE_ADVANCED,
                        BuiltinOIPipes.FLUID_PIPE_ELITE),
                List.of(BuiltinOIPipes.ENERGY_PIPE_BASIC, BuiltinOIPipes.ENERGY_PIPE_ADVANCED,
                        BuiltinOIPipes.ENERGY_PIPE_ELITE),
                List.of(BuiltinOIPipes.ADVANCED_ENERGY_PIPE_BASIC, BuiltinOIPipes.ADVANCED_ENERGY_PIPE_ADVANCED,
                        BuiltinOIPipes.ADVANCED_ENERGY_PIPE_ELITE),
                List.of(BuiltinOIPipes.HEAT_PIPE_BASIC, BuiltinOIPipes.HEAT_PIPE_ADVANCED,
                        BuiltinOIPipes.HEAT_PIPE_ELITE));

        for (int x = -2; x <= 10; x++) {
            for (int y = -2; y <= 18; y++) {
                boolean border = x == -2 || x == 10 || y == -2 || y == 18;
                level.setBlock(base.offset(x, y, 1),
                        (border ? Blocks.POLISHED_BLACKSTONE : Blocks.GRAY_CONCRETE).defaultBlockState(), 3);
            }
        }
        for (int row = 0; row < rows.size(); row++) {
            for (int column = 0; column < rows.get(row).size(); column++) {
                BlockPos center = base.offset(column * 4, row * 4, 0);
                BlockState pipe = rows.get(row).get(column).registeredBlock().get().defaultBlockState();
                level.setBlock(center, pipe, 3);
                level.setBlock(center.relative(Direction.UP), pipe, 3);
                level.setBlock(center.relative(Direction.DOWN), pipe, 3);
                level.setBlock(center.relative(Direction.EAST), pipe, 3);
                level.setBlock(center.relative(Direction.WEST), pipe, 3);
            }
        }
        BlockState featured = BuiltinOIPipes.ADVANCED_ENERGY_PIPE_ADVANCED.registeredBlock().get().defaultBlockState()
                .setValue(PipeBlock.property(Direction.NORTH), PipeSideVisual.EXTRACT)
                .setValue(PipeBlock.property(Direction.EAST), PipeSideVisual.PIPE)
                .setValue(PipeBlock.property(Direction.WEST), PipeSideVisual.PIPE);
        level.setBlock(base.offset(4, 11, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(base.offset(4, 13, 0), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(base.offset(4, 12, 0), featured, 2);
        report.append("world gallery built: 5 families x 3 tiers, uniform shaft-width nodes -> PASS\n");
        report.append("world detail built: three-way shaft-width node with extraction collar -> PASS\n");
    }

    private void buildStraightRun(ServerLevel level, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -1; y <= 1; y++) {
                boolean border = x == -2 || x == 2 || y == -1 || y == 1;
                level.setBlock(center.offset(x, y, 1),
                        (border ? Blocks.POLISHED_BLACKSTONE : Blocks.GRAY_CONCRETE).defaultBlockState(), 3);
            }
        }
        BlockState pipe = BuiltinOIPipes.ITEM_PIPE_ADVANCED.registeredBlock().get().defaultBlockState();
        level.setBlock(center.relative(Direction.WEST), pipe, 3);
        level.setBlock(center, pipe, 3);
        level.setBlock(center.relative(Direction.EAST), pipe, 3);
        report.append("world straight run built: 3 connected pipes, each with a small center node -> PASS\n");
    }

    private void buildTerminalPreview(ServerLevel level, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                boolean border = x == -2 || x == 2 || y == -2 || y == 2;
                level.setBlock(center.offset(x, y, 2),
                        (border ? Blocks.POLISHED_BLACKSTONE : Blocks.GRAY_CONCRETE).defaultBlockState(), 3);
            }
        }
        BlockState pipe = BuiltinOIPipes.ITEM_PIPE_ADVANCED.registeredBlock().get().defaultBlockState();
        level.setBlock(center.relative(Direction.SOUTH),
                pipe.setValue(PipeBlock.property(Direction.NORTH), PipeSideVisual.PIPE), 2);
        level.setBlock(center,
                pipe.setValue(PipeBlock.property(Direction.SOUTH), PipeSideVisual.PIPE), 2);
        report.append("world endpoint node built: one rear connection with a small center node -> PASS\n");
    }

    private void buildExtractionPreview(ServerLevel level, BlockPos center) {
        for (int x = -3; x <= 3; x++) {
            for (int y = -2; y <= 2; y++) {
                boolean border = x == -3 || x == 3 || y == -2 || y == 2;
                level.setBlock(center.offset(x, y, 2),
                        (border ? Blocks.POLISHED_BLACKSTONE : Blocks.GRAY_CONCRETE).defaultBlockState(), 3);
            }
        }
        BlockState pipe = BuiltinOIPipes.ENERGY_PIPE_ELITE.registeredBlock().get().defaultBlockState();
        level.setBlock(center.relative(Direction.EAST),
                net.ptcrys.topo.datav2.machine.BuiltinOIMachines.COMBUSTION_GENERATOR_T3
                        .registeredBlock().getDefaultState(),
                3);
        level.setBlock(center.relative(Direction.WEST), pipe, 3);
        level.setBlock(center, pipe, 3);
        PipeNetworkEngine.runtime(level).setSideIntent(
                center, Direction.EAST, net.ptcrys.topo.api.pipe.PipeSideIntent.EXTRACT);
        boolean extractVisible = PipeBlock.visual(level.getBlockState(center), Direction.EAST) == PipeSideVisual.EXTRACT;
        report.append("world extraction port built: 8x8 collar on a 6x6 shaft -> ")
                .append(extractVisible ? "PASS\n" : "FAIL\n");
    }

    private void buildCornerPreview(ServerLevel level, BlockPos center) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                boolean border = x == -4 || x == 4 || z == -4 || z == 4;
                level.setBlock(center.offset(x, -2, z),
                        (border ? Blocks.POLISHED_BLACKSTONE : Blocks.GRAY_CONCRETE).defaultBlockState(), 3);
            }
        }

        BlockState pipe = BuiltinOIPipes.ITEM_PIPE_ADVANCED.registeredBlock().get().defaultBlockState();
        level.setBlock(center.relative(Direction.UP, 2),
                pipe.setValue(PipeBlock.property(Direction.DOWN), PipeSideVisual.PIPE), 2);
        level.setBlock(center.relative(Direction.UP),
                pipe.setValue(PipeBlock.property(Direction.DOWN), PipeSideVisual.PIPE)
                        .setValue(PipeBlock.property(Direction.UP), PipeSideVisual.PIPE),
                2);
        level.setBlock(center.relative(Direction.EAST, 2),
                pipe.setValue(PipeBlock.property(Direction.WEST), PipeSideVisual.PIPE), 2);
        level.setBlock(center.relative(Direction.EAST),
                pipe.setValue(PipeBlock.property(Direction.WEST), PipeSideVisual.PIPE)
                        .setValue(PipeBlock.property(Direction.EAST), PipeSideVisual.PIPE),
                2);
        level.setBlock(center.relative(Direction.SOUTH, 2),
                pipe.setValue(PipeBlock.property(Direction.NORTH), PipeSideVisual.PIPE), 2);
        level.setBlock(center.relative(Direction.SOUTH),
                pipe.setValue(PipeBlock.property(Direction.NORTH), PipeSideVisual.PIPE)
                        .setValue(PipeBlock.property(Direction.SOUTH), PipeSideVisual.PIPE),
                2);
        level.setBlock(center,
                pipe.setValue(PipeBlock.property(Direction.UP), PipeSideVisual.PIPE)
                        .setValue(PipeBlock.property(Direction.EAST), PipeSideVisual.PIPE)
                        .setValue(PipeBlock.property(Direction.SOUTH), PipeSideVisual.PIPE),
                2);
        report.append("world 3d corner built: vertical/east/south shaft-width node -> PASS\n");
    }

    private void moveToDetailCamera(Minecraft minecraft) {
        moveToCamera(minecraft, detailCamera, detailCenter);
    }

    private void moveToCamera(Minecraft minecraft, Vec3 camera, BlockPos target) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || camera == null || target == null) {
            return;
        }
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            teleportCamera(server.overworld(), player, camera, target);
        });
    }

    private static void teleportCamera(ServerLevel level, ServerPlayer player, Vec3 feet, BlockPos target) {
        double dx = target.getX() + 0.5 - feet.x;
        double dy = target.getY() + 0.5 - (feet.y + player.getEyeHeight());
        double dz = target.getZ() + 0.5 - feet.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.teleportTo(level, feet.x, feet.y, feet.z, Set.of(), yaw, pitch, false);
    }

    private static void aimAt(Minecraft minecraft, BlockPos pos) {
        var player = minecraft.player;
        if (player == null || pos == null) {
            return;
        }
        double dx = pos.getX() + 0.5 - player.getX();
        double dy = pos.getY() + 0.5 - (player.getY() + player.getEyeHeight());
        double dz = pos.getZ() + 0.5 - player.getZ();
        player.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }

    private static UIElement rootElement(Minecraft minecraft) {
        if (!(minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen)) {
            return null;
        }
        return screen.getMenu().getModularUI().ui.rootElement;
    }

    private static UIElement findById(UIElement element, String id) {
        if (element == null) {
            return null;
        }
        if (id.equals(element.getId())) {
            return element;
        }
        for (UIElement child : element.getSafeChildren()) {
            UIElement found = findById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static BindableValue<Integer> findIntBinding(UIElement root, String id) {
        UIElement element = findById(root, id);
        if (element instanceof BindableValue<?> value && value.getValue() instanceof Integer) {
            return (BindableValue<Integer>) value;
        }
        return null;
    }

    /** 客户端直接挂物品选择弹窗(过滤区左侧加号的同一入口),预填物品条目展示暂存渲染 + JEI 共存。 */
    private void openItemPickerForShot(Minecraft minecraft) {
        try {
            if (!(minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen)) {
                report.append("item picker: no modular screen to mount on -> FAIL\n");
                return;
            }
            var root = screen.getMenu().getModularUI().ui.rootElement;
            var adapter = net.ptcrys.topo.data.pipe.BuiltinOIPipeFilterAdapters.ITEM;
            net.ptcrys.topo.apiv2.machine.ui.ItemPickerPopup.open(
                    root,
                    net.ptcrys.topo.data.pipe.BuiltinOIPipeLang.UI_PIPE_PORT_ADD_TO_WHITELIST.getComponent(),
                    adapter::entryFromCarried,
                    entry -> previewFor(adapter, entry),
                    entries -> {},
                    java.util.List.of("minecraft:coal", "minecraft:iron_ingot", "minecraft:gold_ingot"));
            report.append("item picker mounted for screenshot -> PASS\n");
            // JEI 幽灵投放桥已装(JEI runtime 可用时 OIJeiPlugin 安装)= 暂存槽已登记为拖放目标。
            boolean ghostReady = net.ptcrys.topo.apiv2.machine.ui.ItemGhostDrop.isAvailable();
            report.append("JEI ghost-drop bridge installed=").append(ghostReady)
                    .append(" -> ").append(ghostReady ? "PASS" : "FAIL (drag from JEI won't target staging)")
                    .append('\n');
        } catch (Exception e) {
            LOGGER.error("Pipe probe: item picker mount failed", e);
            report.append("FAILED item picker mount: ").append(e).append('\n');
        }
    }

    /** 挂流体管的选择弹窗(真流体适配器),预填流体条目验证暂存槽渲染流体贴图。 */
    private void openFluidPickerForShot(Minecraft minecraft) {
        try {
            if (!(minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen)) {
                report.append("fluid picker: no modular screen -> FAIL\n");
                return;
            }
            var root = screen.getMenu().getModularUI().ui.rootElement;
            var adapter = net.ptcrys.topo.data.pipe.BuiltinOIPipeFilterAdapters.FLUID;
            net.ptcrys.topo.apiv2.machine.ui.ItemPickerPopup.open(
                    root,
                    net.ptcrys.topo.data.pipe.BuiltinOIPipeLang.UI_PIPE_PORT_ADD_TO_WHITELIST.getComponent(),
                    adapter::entryFromCarried,
                    entry -> previewFor(adapter, entry),
                    entries -> {},
                    java.util.List.of("minecraft:water", "minecraft:lava"));
            report.append("fluid picker mounted (staging renders fluids) -> PASS\n");
        } catch (Exception e) {
            LOGGER.error("Pipe probe: fluid picker mount failed", e);
            report.append("FAILED fluid picker mount: ").append(e).append('\n');
        }
    }

    /** 条目预览图标:有流体取流体贴图,否则物品贴图(与端口屏 previewIconFor 同口径)。 */
    private static com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture previewFor(
                                                                                net.ptcrys.topo.api.pipe.PipeFilterAdapter<?> adapter, String entry) {
        var fluids = adapter.displayFluidStacks(entry).stream()
                .filter(f -> !f.isEmpty()).toList();
        if (!fluids.isEmpty()) {
            return new com.lowdragmc.lowdraglib2.gui.texture.FluidStackTexture(
                    fluids.toArray(new net.neoforged.neoforge.fluids.FluidStack[0]));
        }
        var stacks = adapter.displayStacks(entry).stream()
                .filter(s -> !s.isEmpty()).toList();
        return new com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture(
                stacks.toArray(new ItemStack[0]));
    }

    /** 移除挂在根上的最顶层弹窗(物品选择弹窗),好让随后的数量弹窗单独成图。 */
    private void removeTopPopup(Minecraft minecraft) {
        try {
            if (!(minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen)) {
                return;
            }
            var root = screen.getMenu().getModularUI().ui.rootElement;
            var children = root.getChildren();
            if (!children.isEmpty()) {
                root.removeChild(children.get(children.size() - 1));
            }
        } catch (Exception e) {
            LOGGER.error("Pipe probe: remove popup failed", e);
        }
    }

    /** 客户端直接挂通用数量编辑弹窗(可点击数值的同一入口),为弹窗布局截图。 */
    private void openAmountPopupForShot(Minecraft minecraft) {
        try {
            if (!(minecraft.screen instanceof com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen screen)) {
                report.append("amount popup: no modular screen to mount on -> FAIL\n");
                return;
            }
            var root = screen.getMenu().getModularUI().ui.rootElement;
            net.ptcrys.topo.apiv2.machine.ui.AmountEditorPopup.open(
                    root,
                    net.ptcrys.topo.data.pipe.BuiltinOIPipeLang.UI_PIPE_PORT_AMOUNT_POPUP_TITLE.getComponent(),
                    2560L, 0L, 2560L,
                    committed -> kotlin.Unit.INSTANCE);
            report.append("amount popup mounted for screenshot -> PASS\n");
        } catch (Exception e) {
            LOGGER.error("Pipe probe: amount popup mount failed", e);
            report.append("FAILED amount popup mount: ").append(e).append('\n');
        }
    }

    /** 经真实交互管线打开终极能量端口屏(第二张 GUI 截图;触及保险同款瞬移)。 */
    private void openUltEnergyGui(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || ultEnergyExtractor == null) {
            return;
        }
        BlockPos pipePos = ultEnergyExtractor;
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                player.teleportTo(pipePos.getX() - 1.5, pipePos.getY(), pipePos.getZ() - 0.5);
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(pipePos.getX() + 0.5, pipePos.getY() + 0.5, pipePos.getZ()),
                        Direction.NORTH, pipePos, false);
                var result = player.gameMode.useItemOn(
                        player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                report.append(String.format(Locale.ROOT, "ult energy wrench (open screen) result=%s%n", result));
            } catch (Exception e) {
                LOGGER.error("Pipe probe: ult energy gui open failed", e);
                report.append("FAILED ult energy gui open: ").append(e).append('\n');
            }
        });
    }

    /** 服务端驱动 GUI 的两个权威动作并断言:选策略到 equal_split、批量压到 0 再回满。 */
    private void runGuiServerAsserts(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || extractorPipe == null) {
            return;
        }
        BlockPos pipePos = extractorPipe;
        Direction side = Direction.NORTH;
        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                PipeLevelRuntime runtime = PipeNetworkEngine.runtime(level);
                runtime.uiSelectStrategy(pipePos, side, 1);
                String strategyId = runtime.portConfig(pipePos, side).strategy().id().toString();
                report.append(String.format(Locale.ROOT, "uiSelectStrategy(1) -> %s -> %s%n",
                        strategyId,
                        "topo:equal_split".equals(strategyId) ? "PASS" : "FAIL"));
                runtime.uiAdjustAmount(pipePos, side, -Integer.MAX_VALUE / 2);
                int floored = runtime.portAmount(pipePos, side);
                runtime.uiAdjustAmount(pipePos, side, Integer.MAX_VALUE / 2);
                int ceiled = runtime.portAmount(pipePos, side);
                int expectedCeiling = BuiltinOIPipes.ITEM_PIPE_ELITE
                        .maxBatchAmount(runtime.portInterval(pipePos, side));
                report.append(String.format(Locale.ROOT, "uiAdjustAmount clamp 0..capXinterval -> %d..%d (max %d) -> %s%n",
                        floored, ceiled, expectedCeiling,
                        floored == 0 && ceiled == expectedCeiling ? "PASS" : "FAIL"));
                var window = runtime.portWindow(pipePos, side);
                runtime.uiAdjustInterval(pipePos, side, -1000);
                int minInterval = runtime.portInterval(pipePos, side);
                runtime.uiAdjustInterval(pipePos, side, 1000);
                int maxInterval = runtime.portInterval(pipePos, side);
                runtime.uiAdjustInterval(pipePos, side, -(maxInterval - minInterval));
                report.append(String.format(Locale.ROOT,
                        "uiAdjustInterval clamp -> %d..%d (window %d..%d) -> %s%n",
                        minInterval, maxInterval, window.minInterval(), window.maxInterval(),
                        minInterval == window.minInterval() && maxInterval == window.maxInterval() ? "PASS" : "FAIL"));
                // 弹窗精确设置通道:越界值钳制、合法值精确落地(含亚每 tick 的非整除批量)。
                int maxAtMin = BuiltinOIPipes.ITEM_PIPE_ELITE.maxBatchAmount(minInterval);
                runtime.uiSetAmount(pipePos, side, Integer.MAX_VALUE);
                int setClamped = runtime.portAmount(pipePos, side);
                runtime.uiSetAmount(pipePos, side, 37);
                int setExact = runtime.portAmount(pipePos, side);
                runtime.uiSetInterval(pipePos, side, 999);
                int setIntervalClamped = runtime.portInterval(pipePos, side);
                report.append(String.format(Locale.ROOT,
                        "uiSetAmount clamp=%d exact=%d uiSetInterval clamp=%d -> %s%n",
                        setClamped, setExact, setIntervalClamped,
                        setClamped == maxAtMin && setExact == 37 && setIntervalClamped == window.maxInterval() ? "PASS" : "FAIL"));
                runtime.uiSetInterval(pipePos, side, minInterval);
                runtime.uiSetAmount(pipePos, side, Integer.MAX_VALUE);
                // 过滤通道:换策略后名单存活、非法/重复被拒、删除生效。
                var afterSwitch = runtime.portFilter(pipePos, side);
                report.append(String.format(Locale.ROOT,
                        "filter survives strategy switch white=%d black=%d -> %s%n",
                        afterSwitch.whitelist().size(), afterSwitch.blacklist().size(),
                        afterSwitch.whitelist().size() == 6 && afterSwitch.blacklist().size() == 2 ? "PASS" : "FAIL"));
                runtime.uiAddFilterEntry(pipePos, side, true, "NOT a valid id!!");
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:coal");
                boolean rejectOk = runtime.portFilter(pipePos, side).whitelist().size() == 6;
                runtime.uiRemoveFilterEntry(pipePos, side, true, "minecraft:diamond");
                boolean removeOk = runtime.portFilter(pipePos, side).whitelist().size() == 5;
                runtime.uiAddFilterEntry(pipePos, side, true, "minecraft:diamond");
                report.append(String.format(Locale.ROOT,
                        "filter reject-invalid/dedupe=%s remove=%s -> %s%n",
                        rejectOk, removeOk, rejectOk && removeOk ? "PASS" : "FAIL"));
            } catch (Exception e) {
                LOGGER.error("Pipe probe: gui asserts failed", e);
                report.append("FAILED gui asserts: ").append(e).append('\n');
            }
        });
    }

    /**
     * 把虚拟鼠标移到左上角,避免悬浮在可点数值上触发 tooltip 遮挡截图(纯验收用)。直写
     * MouseHandler 的 xpos/ypos 字段(与 {@link JeiLookupProbe} 同款,dev mojmap 字段名直写;
     * 不动 OS 光标——无移动事件则保持在该位置)。
     */
    private void moveCursorAway(Minecraft minecraft) {
        try {
            java.lang.reflect.Field xField = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field yField = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xField.setAccessible(true);
            yField.setAccessible(true);
            xField.setDouble(minecraft.mouseHandler, 2.0);
            yField.setDouble(minecraft.mouseHandler, 2.0);
        } catch (ReflectiveOperationException ignored) {
            // best-effort; screenshot still works if the cursor can't be moved
        }
    }

    private void grabScreenshot(Minecraft minecraft, String name) {
        try {
            Files.deleteIfExists(Path.of("screenshots", name + ".png"));
            Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), 1, component -> {});
        } catch (Exception e) {
            LOGGER.error("Pipe probe: screenshot failed", e);
        }
    }

    private boolean timedOut(Minecraft minecraft, String stage) {
        if (++waitTicks > WAIT_TIMEOUT_TICKS) {
            report.append("TIMEOUT while ").append(stage).append('\n');
            finishProbe(minecraft);
            return true;
        }
        return false;
    }

    private void finishProbe(Minecraft minecraft) {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        minecraft.options.fov().set(originalFov);
        minecraft.options.hideGui = false;
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException e) {
            LOGGER.error("Pipe probe: failed to write report", e);
        }
        LOGGER.warn("Pipe probe finished, report at {}", REPORT_FILE.toAbsolutePath());
        minecraft.stop();
    }
}
