package net.ptcrys.topodemo.api.material.common.form;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

import org.jspecify.annotations.NonNull;

/** Block-backed material item whose block exists for models/tags but is not placeable by players. */
public final class NonPlaceableBlockItem extends BlockItem {

    public NonPlaceableBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public @NonNull InteractionResult useOn(@NonNull UseOnContext context) {
        return InteractionResult.FAIL;
    }
}
