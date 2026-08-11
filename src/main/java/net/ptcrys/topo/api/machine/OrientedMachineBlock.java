package net.ptcrys.topo.api.machine;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Machine block with a 6-direction {@link #FACING}. A worked example of the block-preset pattern:
 * the machine definition selects this class via {@code .block(OrientedMachineBlock::new)} — the
 * block "kind" is a strong-typed factory reference, not a boolean flag.
 */
public class OrientedMachineBlock extends MachineBlock {

    public static final EnumProperty<Direction> FACING = DirectionalBlock.FACING;

    public static final MapCodec<OrientedMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("machine").forGetter(OrientedMachineBlock::machineId)).apply(instance, OrientedMachineBlock::new));

    public OrientedMachineBlock(Properties properties, Identifier machineId) {
        super(properties, machineId);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Yaw only: the front always faces the placing player. Pitch-derived UP/DOWN placement made
        // the front land on the top/bottom face (invisible from the side) and, for multiblock
        // controllers, silently tipped the whole expected structure over — the forming direction is
        // inferred from this front, never the other way around. Vertical facing stays reachable
        // through rotate()/structure loading for the blocks that ever need it.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
