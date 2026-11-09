package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/** 引擎一次读取的单元事实。不持有世界或方块实体；位置为世界坐标，expected 为已变换的规范状态。 */
public record CellContext(BlockPos worldPos, StructureView.CellInfo info, boolean controller, @Nullable BlockState expected) {

    public CellContext {
        worldPos = worldPos.immutable();
    }

    public @Nullable BlockState actual() {
        return info.state();
    }
}
