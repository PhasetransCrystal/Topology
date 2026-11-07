package net.ptcrys.topo.api.machine.multiblock.tool;

import net.ptcrys.topo.api.api.lang.TopoApiLang;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

/** 可复用的多方块结构工具物品。右键控制器检测，潜行右键控制器补建；右键空气打开按选项注册表生成的配置页。 */
public class StructureToolItem extends Item implements HeldItemUIMenuType.HeldItemUI {

    public StructureToolItem(Properties properties) {
        super(properties);
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return open(player, hand);
    }

    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        return player == null ? InteractionResult.PASS : interact(player, context.getHand(), context.getClickedPos(), context.getClickedFace());
    }

    /** 在方块交互权限事件结束后、方块激活及普通物品放置捕获前处理，防止打开控制器界面或重复派发放置事件。 */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }

    /** 世界交互统一入口；服务端权限验证和控制器读取均委托给管理器。 */
    public static InteractionResult interact(Player player, InteractionHand hand, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction face) {
        if (player instanceof ServerPlayer server && !StructureToolManager.use(server, hand, pos, player.isShiftKeyDown(), face)) open(player, hand);
        return player.level().isClientSide() ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    private static InteractionResult open(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer server) HeldItemUIMenuType.openUI(server, hand);
        return InteractionResult.SUCCESS;
    }

    public ModularUI createUI(HeldItemUIMenuType.HeldItemUIHolder holder) {
        return StructureToolConfigPage.build(holder);
    }

    public boolean stillValid(HeldItemUIMenuType.HeldItemUIHolder holder) {
        ItemStack current = holder.player.getItemInHand(holder.hand);
        return current.getItem() == this && (holder.player.level().isClientSide() || current == holder.itemStack);
    }

    public net.minecraft.network.chat.Component getUIDisplayName(HeldItemUIMenuType.HeldItemUIHolder holder) {
        return TopoApiLang.TOOL_STRUCTURE_TITLE.getComponent();
    }
}
