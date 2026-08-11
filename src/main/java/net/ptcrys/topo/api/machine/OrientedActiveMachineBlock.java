package net.ptcrys.topo.api.machine;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Property;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Set;

/** 6-direction oriented machine block with an active visual state. */
public class OrientedActiveMachineBlock extends OrientedMachineBlock {

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    private static final Set<Property<?>> RUNTIME_STATE_PROPERTIES = Set.of(ACTIVE);

    public static final MapCodec<OrientedActiveMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("machine").forGetter(OrientedActiveMachineBlock::machineId)).apply(instance, OrientedActiveMachineBlock::new));

    public OrientedActiveMachineBlock(Properties properties, Identifier machineId) {
        super(properties, machineId);
        registerDefaultState(defaultBlockState().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(ACTIVE, false);
    }

    @Override
    public Set<Property<?>> runtimeStateProperties() {
        return RUNTIME_STATE_PROPERTIES;
    }
}
