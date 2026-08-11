package net.ptcrys.topo.api.visual;

import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/** A block that participates in connected-texture merging; neighbors of the same family connect. */
public interface ConnectedTextureHost {

    ConnectedTextureFamily connectedTextureFamily();

    default @Nullable ConnectedTextureFamily connectedTextureFamily(BlockState state) {
        return connectedTextureFamily();
    }
}
