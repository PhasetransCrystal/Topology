package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Live, read-only structure view over the server level, used for targeted single-cell re-tests. */
final class MultiblockLiveView implements StructureView {

    private final ServerLevel level;
    private final BlockPos controllerPos;

    MultiblockLiveView(ServerLevel level, BlockPos controllerPos) {
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
    public Set<PartRole> roles(BlockPos worldPos) {
        return StructureCaptures.captureCell(level, worldPos).roles();
    }

    @Override
    public boolean isMember(BlockPos worldPos) {
        return StructureCaptures.captureCell(level, worldPos).member();
    }
}
