package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureView;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/** 世界线程上的只读结构视图，供同步检测、诊断和放置规划共用；不会主动加载区块。 */
public final class MultiblockLiveView implements StructureView {

    private final Level level;
    private final BlockPos controllerPos;

    public MultiblockLiveView(Level level, BlockPos controllerPos) {
        this.level = level;
        this.controllerPos = controllerPos;
    }

    @Override
    public BlockPos controllerPos() {
        return controllerPos;
    }

    @Override
    public StructureView.@Nullable CellInfo cellInfo(BlockPos worldPos) {
        if (level.isOutsideBuildHeight(worldPos)) {
            return null;
        }
        StructureCaptures.CellState cell = StructureCaptures.captureCell(level, worldPos);
        return cell.state() == null ? null : new StructureView.CellInfo(cell.state(), cell.roles(), cell.member());
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
    public StructureView.@Nullable Member member(BlockPos worldPos) {
        return StructureCaptures.captureCell(level, worldPos).member();
    }
}
