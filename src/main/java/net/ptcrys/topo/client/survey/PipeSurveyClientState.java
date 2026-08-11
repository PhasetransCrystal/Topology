package net.ptcrys.topo.client.survey;

import net.ptcrys.topo.api.pipe.survey.PipeSurveySnapshot;

import net.minecraft.client.Minecraft;

import org.jspecify.annotations.Nullable;

/**
 * 客户端勘测状态:只缓存服务端最近一帧快照与收包时刻,渲染与轮询都读这里。
 * 80t 无刷新自动判定过期(服务端清状态/玩家收起工具后叠加层自然淡出)。主线程独占。
 */
public final class PipeSurveyClientState {

    /** 快照保鲜期(tick):超过未刷新即停渲染。 */
    public static final int FRESH_TICKS = 80;

    private static @Nullable PipeSurveySnapshot snapshot;
    private static long receivedGameTime = Long.MIN_VALUE;

    private PipeSurveyClientState() {}

    /** 收包入口(S2C handler 主线程):空帧清屏。 */
    public static void apply(PipeSurveySnapshot received) {
        if (!received.active()) {
            clear();
            return;
        }
        snapshot = received;
        Minecraft minecraft = Minecraft.getInstance();
        receivedGameTime = minecraft.level != null ? minecraft.level.getGameTime() : Long.MIN_VALUE;
    }

    public static void clear() {
        snapshot = null;
        receivedGameTime = Long.MIN_VALUE;
    }

    public static @Nullable PipeSurveySnapshot current() {
        return snapshot;
    }

    public static boolean fresh(long gameTime) {
        return snapshot != null && receivedGameTime != Long.MIN_VALUE && gameTime - receivedGameTime <= FRESH_TICKS;
    }
}
