package net.ptcrys.topo.client.debug;

import net.ptcrys.topo.datav2.machine.BuiltinOIMachineFeedbackLang;
import net.ptcrys.topo.datav2.machine.common.component.resource.MachineDestroyedReport;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResource;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 破坏反馈聊天样式的全自动游戏内视觉确认探针(同 {@link PortHighlightProbe} 的流水线骨架,独立 flag):
 * 仅当工作目录存在 {@code oi-destroyed-feedback-probe.flag} 时激活。自动建创造平坦世界,经真实
 * {@link MachineDestroyedReport#message} 构建并向玩家发送五条覆盖全档位/全资源色的样式化破坏提示
 * (耗散=灰、电弧=黄、爆燃=红、热闪=金;资源名取各自资源色:能量金黄/高级能量紫/热量橙红),打开
 * 聊天框截图肉眼验收,写报告后自动退出。
 *
 * <p>
 * 样式即数据——与真实破坏链路同一构建代码;真实触发(preRemoveSideEffects→onDestroyed→deliver)
 * 由 gametest 与单测覆盖,本探针只为人眼确认配色与排版,故直接发样式组件而不引爆机器。
 */
public final class MachineDestroyedFeedbackProbe {

    private static final Logger LOGGER = LoggerFactory.getLogger("OI-DestroyedFeedbackProbe");
    private static final Path FLAG_FILE = Path.of("oi-destroyed-feedback-probe.flag");
    private static final Path REPORT_FILE = Path.of("oi-destroyed-feedback-probe-report.txt");
    private static final String SHOT = "oi-destroyed-feedback-probe";
    /** 每轮唯一世界名:复用同名存档会撞上一轮残留场景(并行会话共用 run/ 时尤甚)。 */
    private static final String LEVEL_ID = "oi-destroyed-feedback-probe-" + (System.currentTimeMillis() % 100_000_000L);
    private static final int WAIT_TIMEOUT_TICKS = 2400;

    private enum State {
        WAIT_TITLE,
        WAIT_WORLD,
        SEND_WAIT,
        SHOOT,
        FLUSH,
        DONE
    }

    private static MachineDestroyedFeedbackProbe instance;

    private final StringBuilder report = new StringBuilder();
    private State state = State.WAIT_TITLE;
    private int countdown;
    private int waitTicks;

    private MachineDestroyedFeedbackProbe() {}

    public static void register() {
        if (FMLEnvironment.getDist() != Dist.CLIENT || !Files.exists(FLAG_FILE)) {
            return;
        }
        instance = new MachineDestroyedFeedbackProbe();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> instance.onClientTick());
        LOGGER.warn("Destroyed-feedback probe armed: will broadcast styled destruction lines and screenshot the chat");
    }

    private void onClientTick() {
        Minecraft minecraft = Minecraft.getInstance();
        switch (state) {
            case WAIT_TITLE -> {
                if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                    minecraft.options.pauseOnLostFocus = false;
                    if (minecraft.getWindow().getWidth() < 1280) {
                        minecraft.getWindow().setWindowed(1280, 720);
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
                    broadcastLines(minecraft);
                    countdown = 20;
                    state = State.SEND_WAIT;
                }
            }
            case SEND_WAIT -> {
                // 给客户端几个 tick 收下系统聊天,再打开聊天框——焦点态聊天历史更高、全 5 行清晰不裁切。
                if (--countdown <= 0) {
                    minecraft.setScreen(new ChatScreen("", false));
                    countdown = 10;
                    state = State.SHOOT;
                }
            }
            case SHOOT -> {
                if (--countdown <= 0) {
                    grabScreenshot(minecraft, SHOT);
                    countdown = 5;
                    state = State.FLUSH;
                }
            }
            case FLUSH -> {
                if (--countdown <= 0) {
                    finish(minecraft);
                }
            }
            case DONE -> {}
        }
    }

    private void broadcastLines(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) {
            report.append("no singleplayer server -> FAIL\n");
            return;
        }
        List<Component> lines = styledLines();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.sendSystemMessage(
                    Component.literal("== OI machine destruction feedback — styling preview ==")
                            .withStyle(ChatFormatting.AQUA));
            for (Component line : lines) {
                player.sendSystemMessage(line);
            }
            LOGGER.warn("Destroyed-feedback probe: sent {} styled lines", lines.size());
        });
        report.append("sent ").append(lines.size()).append(" styled lines\n");
        for (Component line : lines) {
            report.append("  ").append(line.getString()).append('\n');
        }
    }

    /** 五条覆盖全档位与全资源色的样式提示,与真实链路同一构建代码(报告工厂 + message 注入名)。 */
    private static List<Component> styledLines() {
        ScalarResource energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability().resource();
        ScalarResource advanced = BuiltinOIResourceIntegrations.ADVANCED_ENERGY.recipeCapability().resource();
        ScalarResource heat = BuiltinOIResourceIntegrations.HEAT.recipeCapability().resource();
        List<Component> lines = new ArrayList<>();
        add(lines, MachineDestroyedReport.benign(
                BuiltinOIMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_DISSIPATE,
                ChatFormatting.GRAY,
                "8,500"), "Energy Generator", energy);
        add(lines, MachineDestroyedReport.harmful(
                BuiltinOIMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_ARC,
                ChatFormatting.YELLOW,
                "120,480",
                "5"), "Energy Generator", energy);
        add(lines, MachineDestroyedReport.harmful(
                BuiltinOIMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_BLAST,
                ChatFormatting.RED,
                "2,400,000"), "Energy Storage Block", energy);
        add(lines, MachineDestroyedReport.harmful(
                BuiltinOIMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_ENERGY_ARC,
                ChatFormatting.YELLOW,
                "48,000",
                "7"), "Advanced Energy Hatch", advanced);
        add(lines, MachineDestroyedReport.harmful(
                BuiltinOIMachineFeedbackLang.MESSAGE_MACHINE_DESTROYED_HEAT_FLASH,
                ChatFormatting.GOLD,
                "150,000",
                "8"), "Boiler", heat);
        return lines;
    }

    private static void add(List<Component> lines, MachineDestroyedReport report, String machineName, ScalarResource resource) {
        Component resourceName = resource.displayName().copy().withColor(resource.color() & 0xFFFFFF).withStyle(ChatFormatting.BOLD);
        @Nullable
        Component message = report.message(Component.literal(machineName), resourceName);
        if (message != null) {
            lines.add(message);
        }
    }

    private void grabScreenshot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(), 1, c -> {});
        report.append("screenshot ").append(name).append(".png captured\n");
    }

    private boolean timedOut(Minecraft minecraft, String what) {
        if (++waitTicks > WAIT_TIMEOUT_TICKS) {
            report.append("timeout ").append(what).append(" -> FAIL\n");
            finish(minecraft);
            return true;
        }
        return false;
    }

    private void finish(Minecraft minecraft) {
        if (state == State.DONE) {
            return;
        }
        state = State.DONE;
        try {
            Files.writeString(REPORT_FILE, report.toString(), StandardCharsets.UTF_8);
            Files.deleteIfExists(FLAG_FILE);
        } catch (IOException exception) {
            LOGGER.error("Destroyed-feedback probe failed to flush its report", exception);
        }
        LOGGER.warn("Destroyed-feedback probe done:\n{}", report);
        minecraft.stop();
    }
}
