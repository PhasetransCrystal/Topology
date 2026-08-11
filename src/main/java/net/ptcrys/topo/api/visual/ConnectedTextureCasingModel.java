package net.ptcrys.topo.api.visual;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Connected-texture casing model: bakes all 64 neighbor-mask variants once, then picks the variant
 * for the actual neighborhood at render time. Ported from the old GTOdyssey visual API.
 */
public record ConnectedTextureCasingModel(Identifier base, Identifier ctm, Identifier family)
        implements CustomUnbakedBlockStateModel {

    public static final Identifier LOADER = Identifier.parse("topo:ctm_casing");
    private static final ModelDebugName DEBUG_NAME = () -> "ConnectedTextureCasingModel";

    public static final MapCodec<ConnectedTextureCasingModel> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("base").forGetter(ConnectedTextureCasingModel::base),
            Identifier.CODEC.fieldOf("ctm").forGetter(ConnectedTextureCasingModel::ctm),
            Identifier.CODEC.fieldOf("family").forGetter(ConnectedTextureCasingModel::family)).apply(instance, ConnectedTextureCasingModel::new));

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        MaterialBaker materials = baker.materials();
        Material.Baked baseSprite = materials.get(new Material(base), DEBUG_NAME);
        Material.Baked ctmSprite = materials.get(new Material(ctm), DEBUG_NAME);
        return new Baked(CtmBakedParts.bakeMaskParts(baseSprite, ctmSprite), baseSprite,
                new ConnectedTextureFamily(family));
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        // No model dependencies: every part is baked from the two textures.
    }

    @Override
    public MapCodec<ConnectedTextureCasingModel> codec() {
        return CODEC;
    }

    @SuppressWarnings("deprecation")
    private static final class Baked implements DynamicBlockStateModel {

        private final BlockStateModelPart[] byMask;
        private final Material.Baked particle;
        private final ConnectedTextureFamily family;

        Baked(BlockStateModelPart[] byMask, Material.Baked particle, ConnectedTextureFamily family) {
            this.byMask = byMask;
            this.particle = particle;
            this.family = family;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 RandomSource random, List<BlockStateModelPart> parts) {
            CtmBakedParts.addMasked(level, pos, family, parts, byMask);
        }

        @Override
        public int materialFlags() {
            return 0;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }
    }
}
