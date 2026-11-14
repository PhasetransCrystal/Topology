package net.ptcrys.topodemo.api.equipment.common;

import net.ptcrys.topo.api.machine.multiblock.tool.StructureTool;
import net.ptcrys.topo.api.machine.multiblock.tool.StructureToolConfigPage;
import net.ptcrys.topo.api.machine.multiblock.tool.StructureToolManager;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Demo 的结构工具物品：只把交互交给框架；配置按玩家保存，配置页、菜单与虚影均由框架处理。 */
public final class StructureToolItem extends Item implements StructureTool {

    public StructureToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!StructureToolManager.use(player, context.getHand(), context.getClickedPos(), context.isSecondaryUseActive(), context.getClickedFace())) {
            StructureToolConfigPage.open(player);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) StructureToolConfigPage.open(player);
        return InteractionResult.SUCCESS;
    }

    /** 在方块交互权限事件结束后、方块激活及普通物品放置捕获前处理，防止打开控制器界面或重复派发放置事件。 */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return useOn(context);
    }
}
