package net.ptcrys.topo.datav2.material.common;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 工艺模具物品:唯一职责是携带"不消耗"提示——催化剂语义对玩家处处可见
 * (JEI 配方悬停、背包、机器模具槽),不依赖任何 GUI 框架的槽位 tooltip 挂点。
 */
public final class ProcessDieItem extends Item {

    public ProcessDieItem(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable ItemStackTemplate getCraftingRemainder(ItemInstance instance) {
        if (instance instanceof ItemStack stack && !stack.isEmpty()) {
            return ItemStackTemplate.fromNonEmptyStack(stack.copyWithCount(1));
        }
        return new ItemStackTemplate(this, 1);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> output, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, output, flag);
        output.accept(Component.translatable("item.topo.process_die.tooltip")
                .withStyle(ChatFormatting.GRAY));
    }
}
