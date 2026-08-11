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

/**
 * Oriented machine block with both recipe {@link #ACTIVE} and multiblock {@link #FORMED} state.
 * {@link #FORMED} is controller-owned: only the multiblock controller trait writes it after a
 * recheck; everything else treats it as a read-only runtime property.
 */
public class FormedMachineBlock extends OrientedActiveMachineBlock {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    private static final Set<Property<?>> RUNTIME_STATE_PROPERTIES = Set.of(FORMED, ACTIVE);

    public static final MapCodec<FormedMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("machine").forGetter(FormedMachineBlock::machineId)).apply(instance, FormedMachineBlock::new));

    public FormedMachineBlock(Properties properties, Identifier machineId) {
        super(properties, machineId);
        registerDefaultState(defaultBlockState().setValue(FORMED, false));
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(FORMED, false);
    }

    @Override
    public Set<Property<?>> runtimeStateProperties() {
        return RUNTIME_STATE_PROPERTIES;
    }
}
