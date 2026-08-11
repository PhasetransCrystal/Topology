package net.ptcrys.topo.data.visual;

import net.ptcrys.registrylib.datagen.generator.RegistryLibBlockModelGenerator;
import net.ptcrys.topo.api.visual.ConnectedTextureBlock;
import net.ptcrys.topo.api.visual.ConnectedTextureCasingModel;
import net.ptcrys.topo.api.visual.ConnectedTextureHatchModel;
import net.ptcrys.topo.api.visual.ConnectedTextureMachineModel;

import net.minecraft.client.data.models.blockstates.BlockModelDefinitionGenerator;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelDispatcher;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Datagen emitters for the CTM blockstate models: every variant of the block maps to ONE custom
 * dynamic model (the model itself reads neighbors/facing/active at render time), plus a static item
 * model showing the base texture. Ported from the old GTOdyssey visual datagen.
 */
public final class ConnectedTextureModelDatagen {

    private static final float OVERLAY_EPSILON = 0.01f;
    /** Custom texture slot for the hatch's pipe-base layer (drawn under the resource icon). */
    private static final TextureSlot HATCH_PIPE_SLOT = TextureSlot.create("pipe");

    static {
        CtmDatagenCodecBootstrap.ensureRegistered();
    }

    private ConnectedTextureModelDatagen() {}

    /** Casing blocks: CTM shell blockstate + cube_all item-side block model. */
    public static Supplier<BiConsumer<ConnectedTextureBlock, RegistryLibBlockModelGenerator>> casingBlockstate(
                                                                                                               Identifier baseTexture,
                                                                                                               Identifier connectedTexture) {
        return () -> (block, prov) -> {
            Identifier family = block.connectedTextureFamily().id();
            emitSingleVariant(prov, block,
                    new ConnectedTextureCasingModel(baseTexture, connectedTexture, family));
            emitCubeAllItemModel(prov, block, baseTexture);
        };
    }

    /**
     * Controller machines: CTM shell + facing-following idle/active front overlay blockstate, plus a
     * static base-texture-with-front item-side block model. The {@code family} comes from the
     * declaration so the emitter stays generic over the machine block class.
     */
    public static <T extends Block> Supplier<BiConsumer<T, RegistryLibBlockModelGenerator>> machineBlockstate(
                                                                                                              Identifier family,
                                                                                                              Identifier baseTexture,
                                                                                                              Identifier connectedTexture,
                                                                                                              Identifier idleFront,
                                                                                                              Identifier activeFront) {
        return () -> (block, prov) -> {
            emitSingleVariant(prov, block,
                    new ConnectedTextureMachineModel(
                            baseTexture, connectedTexture, idleFront, activeFront, family));
            emitMachineItemModel(prov, block, baseTexture, idleFront);
        };
    }

    /**
     * Hatch parts: standalone bottom/side/top casing with the pipe + resource-icon front while free,
     * the family's CTM shell while melted into a formed structure
     * ({@code ConnectedTextureProperties.CTM_ACTIVE}); plus a static standalone-look item-side model.
     */
    public static <T extends Block> Supplier<BiConsumer<T, RegistryLibBlockModelGenerator>> hatchBlockstate(
                                                                                                            Identifier family,
                                                                                                            Identifier inactiveBottom,
                                                                                                            Identifier inactiveSide,
                                                                                                            Identifier inactiveTop,
                                                                                                            Identifier formedBase,
                                                                                                            Identifier formedConnected,
                                                                                                            Identifier pipe,
                                                                                                            Identifier icon) {
        return () -> (block, prov) -> {
            emitSingleVariant(prov, block,
                    new ConnectedTextureHatchModel(
                            formedBase, formedConnected,
                            inactiveBottom, inactiveSide, inactiveTop,
                            pipe, icon, family));
            emitHatchItemModel(prov, block, inactiveBottom, inactiveSide, inactiveTop, pipe, icon);
        };
    }

    private static void emitSingleVariant(
                                          RegistryLibBlockModelGenerator prov,
                                          Block block,
                                          BlockStateModel.Unbaked model) {
        prov.blockStateOutput.accept(new BlockModelDefinitionGenerator() {

            @Override
            public Block block() {
                return block;
            }

            @Override
            public BlockStateModelDispatcher create() {
                return new BlockStateModelDispatcher(
                        Optional.of(new BlockStateModelDispatcher.SimpleModelSelectors(
                                Map.of("", model))),
                        Optional.empty());
            }
        });
    }

    private static void emitCubeAllItemModel(RegistryLibBlockModelGenerator prov, Block block, Identifier texture) {
        ModelTemplates.CUBE_ALL.create(
                ModelLocationUtils.getModelLocation(block),
                new TextureMapping().put(TextureSlot.ALL, new Material(texture)),
                prov.modelOutput);
    }

    /** Standalone hatch look for the inventory: casing faces + pipe and icon overlays on NORTH. */
    private static void emitHatchItemModel(
                                           RegistryLibBlockModelGenerator prov, Block block,
                                           Identifier inactiveBottom, Identifier inactiveSide, Identifier inactiveTop,
                                           Identifier pipe, Identifier icon) {
        ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.parse("minecraft:block/block"))
                .requiredTextureSlot(TextureSlot.DOWN)
                .requiredTextureSlot(TextureSlot.UP)
                .requiredTextureSlot(TextureSlot.SIDE)
                .requiredTextureSlot(HATCH_PIPE_SLOT)
                .requiredTextureSlot(TextureSlot.FRONT)
                .requiredTextureSlot(TextureSlot.PARTICLE)
                .element(eb -> eb
                        .from(0f, 0f, 0f).to(16f, 16f, 16f)
                        .face(Direction.DOWN, fb -> fb.texture(TextureSlot.DOWN).cullface(Direction.DOWN).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.UP, fb -> fb.texture(TextureSlot.UP).cullface(Direction.UP).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.NORTH).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.SOUTH, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.SOUTH).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.EAST, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.EAST).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.WEST, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.WEST).uvs(0f, 0f, 16f, 16f)))
                .element(eb -> eb
                        .from(0f, 0f, -OVERLAY_EPSILON).to(16f, 16f, 0f)
                        .face(Direction.NORTH, fb -> fb.texture(HATCH_PIPE_SLOT).uvs(0f, 0f, 16f, 16f)))
                .element(eb -> eb
                        .from(0f, 0f, -OVERLAY_EPSILON * 2).to(16f, 16f, -OVERLAY_EPSILON)
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.FRONT).uvs(0f, 0f, 16f, 16f)));
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.DOWN, new Material(inactiveBottom))
                .put(TextureSlot.UP, new Material(inactiveTop))
                .put(TextureSlot.SIDE, new Material(inactiveSide))
                .put(HATCH_PIPE_SLOT, new Material(pipe))
                .put(TextureSlot.FRONT, new Material(icon))
                .put(TextureSlot.PARTICLE, new Material(inactiveSide));
        prov.withBuilder(template, mapping).build(ModelLocationUtils.getModelLocation(block));
    }

    /** Full cube with the base texture + front overlay on NORTH (the inventory-facing side). */
    private static void emitMachineItemModel(
                                             RegistryLibBlockModelGenerator prov, Block block,
                                             Identifier baseTexture, Identifier idleFront) {
        ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.parse("minecraft:block/block"))
                .requiredTextureSlot(TextureSlot.SIDE)
                .requiredTextureSlot(TextureSlot.FRONT)
                .requiredTextureSlot(TextureSlot.PARTICLE)
                .element(eb -> eb
                        .from(0f, 0f, 0f).to(16f, 16f, 16f)
                        .face(Direction.DOWN, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.DOWN).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.UP, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.UP).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.NORTH).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.SOUTH, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.SOUTH).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.EAST, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.EAST).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.WEST, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.WEST).uvs(0f, 0f, 16f, 16f)))
                .element(eb -> eb
                        .from(0f, 0f, -OVERLAY_EPSILON).to(16f, 16f, 0f)
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.FRONT).uvs(0f, 0f, 16f, 16f)));
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.SIDE, new Material(baseTexture))
                .put(TextureSlot.FRONT, new Material(idleFront))
                .put(TextureSlot.PARTICLE, new Material(baseTexture));
        prov.withBuilder(template, mapping).build(ModelLocationUtils.getModelLocation(block));
    }
}
