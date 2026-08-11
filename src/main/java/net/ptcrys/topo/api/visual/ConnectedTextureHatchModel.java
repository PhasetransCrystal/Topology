package net.ptcrys.topo.api.visual;

import net.ptcrys.topo.apiv2.machine.OrientedMachineBlock;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.MaterialBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Connected-texture hatch model, ported from the old GTOdyssey visual API. Two looks selected by
 * {@link ConnectedTextureProperties#CTM_ACTIVE}: standalone (plain bottom/side/top casing with the
 * pipe + resource-icon overlays on the {@code FACING} front) and formed (the CTM casing shell that
 * merges with the surrounding wall, keeping the pipe + icon overlays so the port stays readable).
 */
public record ConnectedTextureHatchModel(
                                         Identifier base,
                                         Identifier ctm,
                                         Identifier inactiveBottom,
                                         Identifier inactiveSide,
                                         Identifier inactiveTop,
                                         Identifier pipe,
                                         Identifier icon,
                                         Identifier family)
        implements CustomUnbakedBlockStateModel {

    public static final Identifier LOADER = Identifier.parse("topo:ctm_hatch");
    private static final ModelDebugName DEBUG_NAME = () -> "ConnectedTextureHatchModel";

    public static final MapCodec<ConnectedTextureHatchModel> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("base").forGetter(ConnectedTextureHatchModel::base),
            Identifier.CODEC.fieldOf("ctm").forGetter(ConnectedTextureHatchModel::ctm),
            Identifier.CODEC.fieldOf("inactive_bottom").forGetter(ConnectedTextureHatchModel::inactiveBottom),
            Identifier.CODEC.fieldOf("inactive_side").forGetter(ConnectedTextureHatchModel::inactiveSide),
            Identifier.CODEC.fieldOf("inactive_top").forGetter(ConnectedTextureHatchModel::inactiveTop),
            Identifier.CODEC.fieldOf("pipe").forGetter(ConnectedTextureHatchModel::pipe),
            Identifier.CODEC.fieldOf("icon").forGetter(ConnectedTextureHatchModel::icon),
            Identifier.CODEC.fieldOf("family").forGetter(ConnectedTextureHatchModel::family)).apply(instance, ConnectedTextureHatchModel::new));

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        MaterialBaker materials = baker.materials();
        Material.Baked baseSprite = materials.get(new Material(base), DEBUG_NAME);
        Material.Baked ctmSprite = materials.get(new Material(ctm), DEBUG_NAME);
        Material.Baked bottomSprite = materials.get(new Material(inactiveBottom), DEBUG_NAME);
        Material.Baked sideSprite = materials.get(new Material(inactiveSide), DEBUG_NAME);
        Material.Baked topSprite = materials.get(new Material(inactiveTop), DEBUG_NAME);
        Material.Baked pipeSprite = materials.get(new Material(pipe), DEBUG_NAME);
        Material.Baked iconSprite = materials.get(new Material(icon), DEBUG_NAME);

        return new Baked(
                CtmBakedParts.bakeMaskParts(baseSprite, ctmSprite),
                CtmBakedParts.bakeInactiveHatchParts(
                        bottomSprite, sideSprite, topSprite, pipeSprite, iconSprite, baseSprite),
                CtmBakedParts.bakeOverlayParts(
                        baseSprite,
                        CtmBakedParts.overlay(pipeSprite, 0.01f),
                        CtmBakedParts.overlay(iconSprite, 0.02f)),
                baseSprite,
                new ConnectedTextureFamily(family));
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        // No model dependencies: every part is baked from the seven textures.
    }

    @Override
    public MapCodec<ConnectedTextureHatchModel> codec() {
        return CODEC;
    }

    @SuppressWarnings("deprecation")
    private static final class Baked implements DynamicBlockStateModel {

        private final BlockStateModelPart[] formedByMask;
        private final BlockStateModelPart[] inactiveByFacing;
        private final BlockStateModelPart[] overlayByFacing;
        private final Material.Baked particle;
        private final ConnectedTextureFamily family;

        Baked(BlockStateModelPart[] formedByMask, BlockStateModelPart[] inactiveByFacing,
              BlockStateModelPart[] overlayByFacing, Material.Baked particle,
              ConnectedTextureFamily family) {
            this.formedByMask = formedByMask;
            this.inactiveByFacing = inactiveByFacing;
            this.overlayByFacing = overlayByFacing;
            this.particle = particle;
            this.family = family;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 RandomSource random, List<BlockStateModelPart> parts) {
            Direction facing = state.hasProperty(OrientedMachineBlock.FACING) ? state.getValue(OrientedMachineBlock.FACING) : Direction.NORTH;
            boolean ctmActive = state.hasProperty(ConnectedTextureProperties.CTM_ACTIVE) && state.getValue(ConnectedTextureProperties.CTM_ACTIVE);
            if (ctmActive) {
                CtmBakedParts.addMasked(level, pos, family, parts, formedByMask);
                CtmBakedParts.addFacing(parts, overlayByFacing, facing);
            } else {
                CtmBakedParts.addFacing(parts, inactiveByFacing, facing);
            }
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
