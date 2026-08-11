package net.ptcrys.topo.api.api.visual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import org.jspecify.annotations.Nullable;

/** Shared connected-texture helpers: the family connectivity test and the direction bit packing. */
public final class ConnectedTextureProperties {

    /**
     * Visual "melt into the formed structure" flag on multiblock part blocks: while set, the part
     * reports its texture family and renders the CTM casing shell instead of its standalone look.
     * Written by the multiblock controller on form/unform; purely visual, never persisted logic.
     */
    public static final BooleanProperty CTM_ACTIVE = BooleanProperty.create("ctm_active");

    private static final int[] DIRECTION_BITS = createDirectionBits();

    private ConnectedTextureProperties() {}

    /** Server-side {@link #CTM_ACTIVE} writer; silently no-ops on blocks without the property. */
    public static void setActive(Level level, BlockPos pos, boolean active) {
        if (level.isClientSide() || !level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(CTM_ACTIVE)) {
            return;
        }
        BlockState updated = state.setValue(CTM_ACTIVE, active);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    public static boolean canConnect(@Nullable ConnectedTextureFamily family, BlockState neighbor) {
        if (family == null) {
            return false;
        }
        return neighbor.getBlock() instanceof ConnectedTextureHost host && family.equals(host.connectedTextureFamily(neighbor));
    }

    public static int bit(Direction direction) {
        return DIRECTION_BITS[direction.ordinal()];
    }

    private static int[] createDirectionBits() {
        Direction[] directions = Direction.values();
        int[] bits = new int[directions.length];
        for (Direction direction : directions) {
            bits[direction.ordinal()] = 1 << direction.get3DDataValue();
        }
        return bits;
    }
}
