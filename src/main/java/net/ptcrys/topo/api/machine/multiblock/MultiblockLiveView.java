package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Live, read-only structure view over a level; used by synchronous and diagnosis passes. */
final class MultiblockLiveView implements StructureView {

    private final Level level;
    private final BlockPos controllerPos;

    MultiblockLiveView(Level level, BlockPos controllerPos) {
        this.level = level;
        this.controllerPos = controllerPos;
    }

    @Override
    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override
    public @Nullable BlockState blockState(BlockPos worldPos) {
        return StructureCaptures.captureCell(level, worldPos).state();
    }

    @Override
    public boolean isLoaded(BlockPos worldPos) {
        return level.hasChunkAt(worldPos) && !level.isOutsideBuildHeight(worldPos);
    }

    @Override
    public Set<PartRole> roles(BlockPos worldPos) {
        return StructureCaptures.captureCell(level, worldPos).roles();
    }

    @Override
    public boolean isMember(BlockPos worldPos) {
        return StructureCaptures.captureCell(level, worldPos).member();
    }
}
