package net.ptcrys.topo.api.visual;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/** Plain full-cube block whose casing texture merges with same-family neighbors. */
public class ConnectedTextureBlock extends Block implements ConnectedTextureHost {

    public static final MapCodec<ConnectedTextureBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            propertiesCodec(),
            Identifier.CODEC.fieldOf("connected_texture_family")
                    .forGetter(block -> block.family.id()))
            .apply(instance, (properties, familyId) -> new ConnectedTextureBlock(properties, new ConnectedTextureFamily(familyId))));

    private final ConnectedTextureFamily family;

    public ConnectedTextureBlock(Properties properties, ConnectedTextureFamily family) {
        super(properties);
        this.family = family;
    }

    @Override
    protected @NonNull MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public ConnectedTextureFamily connectedTextureFamily() {
        return family;
    }
}
