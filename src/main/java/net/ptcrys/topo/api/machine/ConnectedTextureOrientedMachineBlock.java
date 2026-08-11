package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.api.api.visual.ConnectedTextureFamily;
import net.ptcrys.topo.api.api.visual.ConnectedTextureHost;
import net.ptcrys.topo.api.api.visual.ConnectedTextureProperties;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

/**
 * {@link OrientedMachineBlock} for multiblock parts that visually melt into a formed structure: the
 * controller flips {@link ConnectedTextureProperties#CTM_ACTIVE} on form/unform, and only while
 * active does the block report its texture family — a standalone hatch keeps its own look and never
 * captures neighboring casings. The family is injected by the machine declaration (data).
 */
public class ConnectedTextureOrientedMachineBlock extends OrientedMachineBlock implements ConnectedTextureHost {

    public static final MapCodec<ConnectedTextureOrientedMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("machine").forGetter(ConnectedTextureOrientedMachineBlock::machineId),
            Identifier.CODEC.fieldOf("connected_texture_family").forGetter(block -> block.family.id())).apply(instance, (properties, machineId, familyId) -> new ConnectedTextureOrientedMachineBlock(properties, machineId, new ConnectedTextureFamily(familyId))));

    private static final Set<Property<?>> RUNTIME_STATE_PROPERTIES = Set.of(ConnectedTextureProperties.CTM_ACTIVE);

    private final ConnectedTextureFamily family;

    public ConnectedTextureOrientedMachineBlock(Properties properties, Identifier machineId, ConnectedTextureFamily family) {
        super(properties, machineId);
        this.family = Objects.requireNonNull(family, "connected texture family");
        registerDefaultState(defaultBlockState().setValue(ConnectedTextureProperties.CTM_ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public ConnectedTextureFamily connectedTextureFamily() {
        return family;
    }

    @Override
    public @Nullable ConnectedTextureFamily connectedTextureFamily(BlockState state) {
        return state.hasProperty(ConnectedTextureProperties.CTM_ACTIVE) && state.getValue(ConnectedTextureProperties.CTM_ACTIVE) ? family : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ConnectedTextureProperties.CTM_ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(ConnectedTextureProperties.CTM_ACTIVE, false);
    }

    @Override
    public Set<Property<?>> runtimeStateProperties() {
        return RUNTIME_STATE_PROPERTIES;
    }
}
