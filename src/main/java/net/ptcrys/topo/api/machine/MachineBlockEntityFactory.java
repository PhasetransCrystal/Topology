package net.ptcrys.topo.api.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface MachineBlockEntityFactory<T extends MachineBlockEntity> {

    T create(BlockEntityType<?> type, BlockPos pos, BlockState state);
}
