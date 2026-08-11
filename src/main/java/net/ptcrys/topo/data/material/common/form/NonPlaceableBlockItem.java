package net.ptcrys.topo.data.material.common.form;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Block-backed material item whose block exists for models/tags but is not placeable by players. */
public final class NonPlaceableBlockItem extends BlockItem {

    public NonPlaceableBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.FAIL;
    }
}
