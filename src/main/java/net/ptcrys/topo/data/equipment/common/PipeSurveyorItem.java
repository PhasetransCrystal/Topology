package net.ptcrys.topo.data.equipment.common;

import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyManager;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyTool;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.NonNull;

/**
 * 管网勘测仪:右键管道锚定测绘(扣 1 耐久),潜行右键管道循环选端点 A/B(不扣耐久),
 * 右键空处结束测绘。全部状态变更走服务端 {@link PipeSurveyManager},客户端只渲染快照。
 * 观测范围按材质档位由 {@link SurveyorRanges} 查表。潜行+持物时原版跳过方块交互、物品
 * 交互仍触发,故无需 sneakBypass;本品不得带 wrench 标签(避免误入扳手分支)。
 */
public final class PipeSurveyorItem extends Item implements PipeSurveyTool {

    public PipeSurveyorItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NonNull InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof PipeBlock)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        if (level.isClientSide() || player == null) {
            return InteractionResult.SUCCESS;
        }
        if (context.isSecondaryUseActive()) {
            PipeSurveyManager.selectPoint(player, pos);
            return InteractionResult.CONSUME;
        }
        PipeSurveyManager.activate(player, pos, SurveyorRanges.rangeOf(context.getItemInHand().getItem()));
        context.getItemInHand().hurtAndBreak(1, player, context.getHand());
        return InteractionResult.CONSUME;
    }

    @Override
    public @NonNull InteractionResult use(Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        // 右键空处(或穿透到物品交互的非管道场合)结束测绘:服务端清状态,客户端随快照清屏。
        if (!level.isClientSide()) {
            PipeSurveyManager.clear(player);
        }
        return InteractionResult.SUCCESS;
    }
}
