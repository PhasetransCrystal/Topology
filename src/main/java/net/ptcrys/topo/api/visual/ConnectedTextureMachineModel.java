package net.ptcrys.topo.api.visual;

import net.ptcrys.topo.apiv2.machine.OrientedActiveMachineBlock;
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
 * Connected-texture machine model: the 64-mask CTM casing shell plus a front overlay following the
 * block's {@code FACING}, swapping between the idle and active art with
 * {@link OrientedActiveMachineBlock#ACTIVE}. Adapted from the old GTOdyssey recipe-machine model to
 * the 6-way machine facing; the multiblock {@code FORMED} flag needs no model branch — formed and
 * unformed share the idle front, only recipe activity changes the face.
 */
public record ConnectedTextureMachineModel(
                                           Identifier base,
                                           Identifier ctm,
                                           Identifier idleFront,
                                           Identifier activeFront,
                                           Identifier family)
        implements CustomUnbakedBlockStateModel {

    public static final Identifier LOADER = Identifier.parse("topo:ctm_machine");
    private static final ModelDebugName DEBUG_NAME = () -> "ConnectedTextureMachineModel";

    public static final MapCodec<ConnectedTextureMachineModel> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("base").forGetter(ConnectedTextureMachineModel::base),
            Identifier.CODEC.fieldOf("ctm").forGetter(ConnectedTextureMachineModel::ctm),
            Identifier.CODEC.fieldOf("idle_front").forGetter(ConnectedTextureMachineModel::idleFront),
            Identifier.CODEC.fieldOf("active_front").forGetter(ConnectedTextureMachineModel::activeFront),
            Identifier.CODEC.fieldOf("family").forGetter(ConnectedTextureMachineModel::family)).apply(instance, ConnectedTextureMachineModel::new));

    @Override
    public BlockStateModel bake(ModelBaker baker) {
        MaterialBaker materials = baker.materials();
        Material.Baked baseSprite = materials.get(new Material(base), DEBUG_NAME);
        Material.Baked ctmSprite = materials.get(new Material(ctm), DEBUG_NAME);
        Material.Baked idleSprite = materials.get(new Material(idleFront), DEBUG_NAME);
        Material.Baked activeSprite = materials.get(new Material(activeFront), DEBUG_NAME);

        return new Baked(
                CtmBakedParts.bakeMaskParts(baseSprite, ctmSprite),
                CtmBakedParts.bakeOverlayParts(baseSprite, CtmBakedParts.overlay(idleSprite, 0.01f)),
                CtmBakedParts.bakeOverlayParts(baseSprite, CtmBakedParts.overlay(activeSprite, 0.01f)),
                baseSprite,
                new ConnectedTextureFamily(family));
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        // No model dependencies: every part is baked from the four textures.
    }

    @Override
    public MapCodec<ConnectedTextureMachineModel> codec() {
        return CODEC;
    }

    @SuppressWarnings("deprecation")
    private static final class Baked implements DynamicBlockStateModel {

        private final BlockStateModelPart[] byMask;
        private final BlockStateModelPart[] idleOverlay;
        private final BlockStateModelPart[] activeOverlay;
        private final Material.Baked particle;
        private final ConnectedTextureFamily family;

        Baked(BlockStateModelPart[] byMask, BlockStateModelPart[] idleOverlay,
              BlockStateModelPart[] activeOverlay, Material.Baked particle,
              ConnectedTextureFamily family) {
            this.byMask = byMask;
            this.idleOverlay = idleOverlay;
            this.activeOverlay = activeOverlay;
            this.particle = particle;
            this.family = family;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 RandomSource random, List<BlockStateModelPart> parts) {
            CtmBakedParts.addMasked(level, pos, family, parts, byMask);
            Direction facing = state.hasProperty(OrientedMachineBlock.FACING) ? state.getValue(OrientedMachineBlock.FACING) : Direction.NORTH;
            boolean active = state.hasProperty(OrientedActiveMachineBlock.ACTIVE) && state.getValue(OrientedActiveMachineBlock.ACTIVE);
            CtmBakedParts.addFacing(parts, active ? activeOverlay : idleOverlay, facing);
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
