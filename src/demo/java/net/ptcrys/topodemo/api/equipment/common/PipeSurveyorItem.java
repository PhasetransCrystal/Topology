package net.ptcrys.topodemo.api.equipment.common;

import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyManager;
import net.ptcrys.topo.api.pipe.survey.PipeSurveyTool;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class PipeSurveyorItem extends Item implements PipeSurveyTool {

    private final int range;

    public PipeSurveyorItem(Properties properties, int range) {
        super(properties);
        if (range <= 0) throw new IllegalArgumentException("range must be positive");
        this.range = range;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof PipeBlock)) return InteractionResult.PASS;
        Player player = context.getPlayer();
        if (player == null || context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (context.isSecondaryUseActive()) PipeSurveyManager.selectPoint(player, context.getClickedPos());
        else PipeSurveyManager.activate(player, context.getClickedPos(), range);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) PipeSurveyManager.clear(player);
        return InteractionResult.SUCCESS;
    }
}
