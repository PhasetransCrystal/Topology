package net.ptcrys.topo.apiv2.machine;

import net.ptcrys.topo.api.visual.ConnectedTextureFamily;
import net.ptcrys.topo.api.visual.ConnectedTextureHost;

import net.minecraft.resources.Identifier;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Objects;

/**
 * {@link FormedMachineBlock} that participates in connected-texture merging: a multiblock controller
 * embedded in a casing wall joins the wall's texture family so the shell reads as one surface. The
 * family is injected by the machine declaration (data), keeping this class content-agnostic.
 */
public class ConnectedTextureFormedMachineBlock extends FormedMachineBlock implements ConnectedTextureHost {

    public static final MapCodec<ConnectedTextureFormedMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("machine").forGetter(ConnectedTextureFormedMachineBlock::machineId),
            Identifier.CODEC.fieldOf("connected_texture_family").forGetter(block -> block.family.id())).apply(instance, (properties, machineId, familyId) -> new ConnectedTextureFormedMachineBlock(properties, machineId, new ConnectedTextureFamily(familyId))));

    private final ConnectedTextureFamily family;

    public ConnectedTextureFormedMachineBlock(Properties properties, Identifier machineId, ConnectedTextureFamily family) {
        super(properties, machineId);
        this.family = Objects.requireNonNull(family, "connected texture family");
    }

    @Override
    protected MapCodec<? extends MachineBlock> codec() {
        return CODEC;
    }

    @Override
    public ConnectedTextureFamily connectedTextureFamily() {
        return family;
    }
}
