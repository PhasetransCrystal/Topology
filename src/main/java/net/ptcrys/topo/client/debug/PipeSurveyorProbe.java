package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.network.PipeNetwork;
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyManager;
import net.ptcrys.topo.api.pipe.survey.PipeSurveySnapshot;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * 管网勘测仪世界内渲染全自动验收探针(同 {@link PipeProbe} 流水线骨架,独立 flag):仅当工作
 * 目录存在 {@code topo-surveyor-probe.flag} 时激活。建平坦世界 → 摆"源-终极口-3×3 基础网格-
 * 终极口-汇"的真实流动场景(镜像 gametest 294,3 车道、基础段瓶颈)→ 经真实
 * {@code ServerPlayerGameMode.useItemOn} 管线右键管道锚定测绘(验证勘测仪走的是物品 useOn
 * 而非方块 useItemOn——gametest 直调 useOn 测不出 PipeBlock 返回 PASS 这层)→ 经服务端
 * {@code PipeSurveyManager} 选端点 A/B → 把相机摆到网格斜上方 → 截两张世界叠加层图(仅节点
 * /端口/占用,与 +车道/瓶颈/容量浮签)+ 服务端快照断言写入 {@code topo-surveyor-probe-report.txt}
 * 后自动退出。客户端渲染走真实 S2C 快照同步路径(集成服务端的 ServerPlayer 有 connection)。
 */
public final class PipeSurveyorProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("Topo-SurveyorProbe");
    private static final Path FLAG_FILE = Path.of("topo-surveyor-probe.flag");
    private static final Path REPORT_FILE = Path.of("topo-surveyor-probe-report.txt");
    private static final String LEVEL_ID = "topo-surveyor-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        SETTLE,
        FLOW,
        ANCHOR_SHOT,
        SELECT_SHOT,
        ASSERT,
        FLUSH,
        DONE
    }

    private static PipeSurveyorProbe instance;

    private final StringBuilder report = new StringBuilder();
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;
    /** 网格基准(左中终极口的西侧第一根管所在;场景相对它铺)。 */
    private BlockPos base;
    private BlockPos leftEndpoint;
    private BlockPos rightEndpoint;
    private BlockPos centerNode;
    private BlockPos sourceChest;
    private BlockPos destChest;

    private PipeSurveyorProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new PipeSurveyorProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Surveyor probe armed: will build a flowing pipe grid, survey it and screenshot the world overlay");
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (state) {
            case WAIT_TITLE -> {
                if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    minecraft.options.pauseOnLostFocus = false;
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
                            new WorldOptions(20260613L, false, false),
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
                    buildScene(minecraft);
                    countdown = 30;
                    state = State.SETTLE;
                }
            }
            case SETTLE -> {
                if (--countdown <= 0) {
                    startFlow(minecraft);
                    // 两个终极展示窗口(40t)让基础段占用累计到上一窗口读数,配色才非全绿。
                    countdown = 95;
                    state = State.FLOW;
                }
            }
            case FLOW -> {
                feedSource(minecraft);
                if (--countdown <= 0) {
                    anchorSurveyThroughRealUse(minecraft);
                    aimCameraAtGrid(minecraft);
                    countdown = 35;
                    state = State.ANCHOR_SHOT;
                }
            }
            case ANCHOR_SHOT -> {
                feedSource(minecraft);
                if (countdown == 10) {
                    grabScreenshot(minecraft, "topo-surveyor-nodes");
                }
                if (--countdown <= 0) {
                    selectEndpoints(minecraft);
                    countdown = 35;
                    state = State.SELECT_SHOT;
                }
            }
            case SELECT_SHOT -> {
                feedSource(minecraft);
                if (countdown == 10) {
                    grabScreenshot(minecraft, "topo-surveyor-lanes");
                }
                if (--countdown <= 0) {
                    runSnapshotAsserts(minecraft);
                    countdown = 15;
                    state = State.ASSERT;
                }
            }
            case ASSERT -> {
                feedSource(minecraft);
                if (--countdown <= 0) {
                    state = State.FLUSH;
                    countdown = 5;
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

    /**
     * 摆场景(镜像 gametest 294):源箱 - 终极口(左中) - 3×3 基础网格(中左/中右为终极端点) -
     * 终极口(右中) - 汇箱。网格相对 {@code base} 铺在 (x∈0..2, z∈0..2),抬高 2 格悬空便于取景。
     */
    private void buildScene(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        var clientPlayer = minecraft.player;
        if (server == null || clientPlayer == null) {
            return;
        }
        // 抬高 6 格悬空:相机平视/略仰拍网格时背景是天空,彩色叠加层对比度远好过草地底。
        BlockPos origin = clientPlayer.blockPosition().relative(Direction.EAST, 4).above(6);
        base = origin;
        leftEndpoint = base.offset(0, 0, 1);
        rightEndpoint = base.offset(2, 0, 1);
        centerNode = base.offset(1, 0, 1);
        sourceChest = leftEndpoint.relative(Direction.WEST);
        destChest = rightEndpoint.relative(Direction.EAST);
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                level.setBlock(sourceChest, Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(destChest, Blocks.CHEST.defaultBlockState(), 3);
                for (int x = 0; x <= 2; x++) {
                    for (int z = 0; z <= 2; z++) {
                        boolean endpoint = z == 1 && (x == 0 || x == 2);
                        level.setBlock(base.offset(x, 0, z),
                                (endpoint ? BuiltinTopoPipes.ITEM_PIPE_ELITE : BuiltinTopoPipes.ITEM_PIPE_BASIC)
                                        .registeredBlock().get().defaultBlockState(),
                                3);
                    }
                }
                if (level.getBlockEntity(sourceChest) instanceof ChestBlockEntity chest) {
                    for (int slot = 0; slot < 27; slot++) {
                        chest.setItem(slot, new ItemStack(Items.COAL, 64));
                    }
                }
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(
                        BuiltInRegistries.ITEM.getValue(IdHelper.oi("iron_pipe_surveyor"))));
                report.append("scene built: 3x3 basic grid, ult endpoints ").append(leftEndpoint)
                        .append(" & ").append(rightEndpoint).append(", flow ")
                        .append(sourceChest).append(" -> ").append(destChest).append('\n');
            } catch (Exception e) {
                LOGGER.error("Surveyor probe: scene build failed", e);
                report.append("FAILED scene build: ").append(e).append('\n');
            }
        });
    }

    /** 左中终极口西侧设 EXTRACT,煤从源箱经网格流向汇箱(右中口东侧自动 DESTINATION)。 */
    private void startFlow(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || leftEndpoint == null) {
            return;
        }
        server.execute(() -> {
            try {
                ServerLevel level = server.overworld();
                PipeNetworkEngine.runtime(level).setSideIntent(leftEndpoint, Direction.WEST, PipeSideIntent.EXTRACT);
                report.append("flow started: extractor at ").append(leftEndpoint).append(" WEST\n");
            } catch (Exception e) {
                LOGGER.error("Surveyor probe: start flow failed", e);
                report.append("FAILED start flow: ").append(e).append('\n');
            }
        });
    }

    /**
     * 经真实交互管线右键中心管锚定测绘:勘测仪逻辑在物品 {@code useOn},非潜行右键管道时
     * {@code PipeBlock.useItemOn} 返回 PASS、物品 useOn 才接手——验证这层(gametest 直调
     * useOn 绕过了它)。触及保险:先瞬移到管旁。
     */
    private void anchorSurveyThroughRealUse(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || centerNode == null) {
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                player.teleportTo(centerNode.getX() - 1.5, centerNode.getY(), centerNode.getZ() + 0.5);
                BlockHitResult hit = new BlockHitResult(
                        new Vec3(centerNode.getX() + 0.5, centerNode.getY() + 0.5, centerNode.getZ()),
                        Direction.WEST, centerNode, false);
                var result = player.gameMode.useItemOn(
                        player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                PipeSurveyManager.Survey survey = PipeSurveyManager.activeSurvey(player.getUUID());
                report.append(String.format(Locale.ROOT,
                        "real anchor click result=%s active=%s range=%s -> %s%n",
                        result, survey != null, survey == null ? "-" : survey.range,
                        survey != null && survey.range == 16 ? "PASS" : "FAIL"));
            } catch (Exception e) {
                LOGGER.error("Surveyor probe: anchor click failed", e);
                report.append("FAILED anchor click: ").append(e).append('\n');
            }
        });
    }

    /** 经服务端选端点 A=左中口、B=右中口(选择逻辑 gametest 已证,此处只为渲染车道)。 */
    private void selectEndpoints(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || leftEndpoint == null) {
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                PipeSurveyManager.selectPoint(player, leftEndpoint);
                PipeSurveyManager.selectPoint(player, rightEndpoint);
                report.append("endpoints selected A=").append(leftEndpoint)
                        .append(" B=").append(rightEndpoint).append('\n');
            } catch (Exception e) {
                LOGGER.error("Surveyor probe: select endpoints failed", e);
                report.append("FAILED select endpoints: ").append(e).append('\n');
            }
        });
    }

    /** 把相机摆到网格东南近处、略低于中心仰拍,网格嵌在天空背景里整片叠加层入景。 */
    private void aimCameraAtGrid(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || centerNode == null) {
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                double cx = centerNode.getX() + 0.5;
                double cy = centerNode.getY() + 0.5;
                double cz = centerNode.getZ() + 0.5;
                double px = cx + 2.5;
                double py = cy - 1.0;
                double pz = cz + 4.5;
                double dx = cx - px;
                // 相机在玩家眼高(立姿 ≈1.62),非脚底:俯仰须按眼睛算,否则网格整体偏上出框。
                double dy = cy - (py + 1.62);
                double dz = cz - pz;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                player.connection.teleport(px, py, pz, yaw, pitch);
                report.append(String.format(Locale.ROOT, "camera at (%.1f,%.1f,%.1f) yaw=%.1f pitch=%.1f%n",
                        px, py, pz, yaw, pitch));
            } catch (Exception e) {
                LOGGER.error("Surveyor probe: camera aim failed", e);
                report.append("FAILED camera aim: ").append(e).append('\n');
            }
        });
    }

    /**
     * 每 tick 把源箱补满、把汇箱清空,保证整个截图/断言窗口持续 24/t 流动:源不补则 1728 煤
     * ~72t 排空、汇不清则 ~72t 灌满回压,两头都会让流动停摆、占用归零。
     */
    private void feedSource(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null || sourceChest == null) {
            return;
        }
        server.execute(() -> {
            ServerLevel level = server.overworld();
            if (level.getBlockEntity(sourceChest) instanceof ChestBlockEntity src) {
                for (int slot = 0; slot < 9; slot++) {
                    src.setItem(slot, new ItemStack(Items.COAL, 64));
                }
            }
            if (level.getBlockEntity(destChest) instanceof ChestBlockEntity sink) {
                sink.clearContent();
            }
        });
    }

    /** 服务端重建快照并断言(数字与截图互证):9 节点、3 车道、容量 24、流动端口占用 > 0。 */
    private void runSnapshotAsserts(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        server.execute(() -> {
            try {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                ServerLevel level = server.overworld();
                PipeSurveyManager.Survey survey = PipeSurveyManager.activeSurvey(player.getUUID());
                if (survey == null) {
                    report.append("snapshot asserts: no active survey -> FAIL\n");
                    return;
                }
                PipeSurveySnapshot snapshot = PipeSurveyManager.buildSnapshot(level, survey);
                long occupiedNodes = 0;
                for (long used : snapshot.nodeUsed()) {
                    if (used > 0) {
                        occupiedNodes++;
                    }
                }
                report.append(String.format(Locale.ROOT,
                        "snapshot nodes=%d lanes=%d capacity=%d occupiedNodes=%d window=%dt -> %s%n",
                        snapshot.nodePos().length, snapshot.lanes().size(), snapshot.pairCapacity(),
                        occupiedNodes, snapshot.windowTicks(),
                        snapshot.nodePos().length == 9 && snapshot.lanes().size() == 3 && snapshot.pairCapacity() == 24 && occupiedNodes > 0 ? "PASS" : "FAIL"));
                PipeNetwork<?> network = PipeNetworkEngine.networkAt(level, centerNode);
                report.append("network nodeCount=").append(network == null ? "null" : network.nodeCount())
                        .append(" movedLastWindow=").append(network == null ? "-" : network.windowMovedLast())
                        .append('\n');
            } catch (Exception e) {
                LOGGER.error("Surveyor probe: snapshot asserts failed", e);
                report.append("FAILED snapshot asserts: ").append(e).append('\n');
            }
        });
    }

    private void grabScreenshot(Minecraft minecraft, String name) {
        try {
            Files.deleteIfExists(Path.of("screenshots", name + ".png"));
            Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), 1, c -> {});
            report.append("screenshot ").append(name).append(".png grabbed\n");
        } catch (Exception e) {
            LOGGER.error("Surveyor probe: screenshot failed", e);
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
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException e) {
            LOGGER.error("Surveyor probe: failed to write report", e);
        }
        LOGGER.warn("Surveyor probe finished, report at {}", REPORT_FILE.toAbsolutePath());
        minecraft.stop();
    }
}
