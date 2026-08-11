package net.ptcrys.topo.data.equipment.common;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;

/**
 * A plain item whose only extra behavior is reaching block interaction while sneaking: vanilla
 * skips {@code useItemOn} entirely when sneaking with an item in hand, so a tool whose block-side
 * interactions include a shift-click mode (the wrench's pipe side-intent cycling) must opt in here.
 */
final class SneakBypassToolItem extends Item {

    SneakBypassToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean doesSneakBypassUse(ItemStack stack, LevelReader level, BlockPos pos, Player player) {
        return true;
    }
}
