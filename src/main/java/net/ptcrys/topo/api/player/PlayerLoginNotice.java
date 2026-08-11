package net.ptcrys.topo.api.player;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.net.URI;

/** Sends the beta feedback notice to players when they join a server. */
public final class PlayerLoginNotice {

    static final String ISSUE_URL = "https://github.com/GregTech-Odyssey/OdysseyIndustrial/issues";
    static final String TEST_GROUP = "1107511091";

    private PlayerLoginNotice() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerLoginNotice::onPlayerLoggedIn);
    }

    public static Component noticeMessage() {
        return Component.literal("若有BUG请在")
                .append(Component.literal(ISSUE_URL)
                        .withStyle(style -> style
                                .withColor(ChatFormatting.AQUA)
                                .withUnderlined(true)
                                .withClickEvent(new ClickEvent.OpenUrl(URI.create(ISSUE_URL)))))
                .append("进行反馈，并且请添加 " + TEST_GROUP + " 测试QQ群");
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.sendSystemMessage(noticeMessage());
        }
    }
}
