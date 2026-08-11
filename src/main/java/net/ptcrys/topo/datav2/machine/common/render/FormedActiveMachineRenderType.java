package net.ptcrys.topo.datav2.machine.common.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.datagen.generator.RegistryLibBlockModelGenerator;
import net.ptcrys.topo.apiv2.machine.FormedMachineBlock;
import net.ptcrys.topo.apiv2.machine.MachineBlock;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderStrategy;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderType;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderUse;
import net.ptcrys.topo.apiv2.machine.render.MachineShellMaterial;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Formed-aware variant of {@link ShellOverlayMachineRenderType}: a multiblock controller shell whose
 * front face changes with the {@link FormedMachineBlock#FORMED multiblock-formed} state on top of the
 * recipe {@link OrientedActiveMachineBlock#ACTIVE active} state. It dispatches {@code (formed, active)}
 * to three distinct models — an unassembled casing front, a formed idle front, and a formed active
 * front — while reusing {@link BlockModelGenerators#ROTATION_FACING} for the 6-direction facing, exactly
 * mirroring the oriented/active/overlay render type.
 */
public final class FormedActiveMachineRenderType extends MachineBlockRenderType<FormedActiveMachineRenderType.Data> {

    public FormedActiveMachineRenderType(Identifier id) {
        super(id);
    }

    public MachineBlockRenderUse<Data> formed(
                                              MachineShellMaterial shell,
                                              Identifier unformedFront,
                                              Identifier formedFront,
                                              Identifier activeFront) {
        return use(new Data(shell, unformedFront, formedFront, activeFront));
    }

    public static final class Strategy implements MachineBlockRenderStrategy<Data> {

        @Override
        public void validate(Data data) {
            Objects.requireNonNull(data, "formed active machine render data");
            Objects.requireNonNull(data.shell(), "shell material");
            Objects.requireNonNull(data.unformedFront(), "unformed front texture");
            Objects.requireNonNull(data.formedFront(), "formed front texture");
            Objects.requireNonNull(data.activeFront(), "active front texture");
        }

        @Override
        public void applyBlockModel(
                                    BlockBuilder<? extends MachineBlock, RegistryCore> builder,
                                    Data data,
                                    MachineDefinition definition) {
            builder.blockstate(blockstate(data));
        }
    }

    public static final class Data {

        private final MachineShellMaterial shell;
        private final Identifier unformedFront;
        private final Identifier formedFront;
        private final Identifier activeFront;

        private Data(
                     MachineShellMaterial shell,
                     Identifier unformedFront,
                     Identifier formedFront,
                     Identifier activeFront) {
            this.shell = Objects.requireNonNull(shell, "shell");
            this.unformedFront = Objects.requireNonNull(unformedFront, "unformed front");
            this.formedFront = Objects.requireNonNull(formedFront, "formed front");
            this.activeFront = Objects.requireNonNull(activeFront, "active front");
        }

        public MachineShellMaterial shell() {
            return shell;
        }

        public Identifier unformedFront() {
            return unformedFront;
        }

        public Identifier formedFront() {
            return formedFront;
        }

        public Identifier activeFront() {
            return activeFront;
        }
    }

    private static <T extends Block> Supplier<BiConsumer<T, RegistryLibBlockModelGenerator>> blockstate(Data data) {
        return () -> (block, prov) -> generateBlockstate(block, prov, data);
    }

    private static void generateBlockstate(Block block, RegistryLibBlockModelGenerator prov, Data data) {
        TextureMapping unformedMap = textureMapping(data, data.unformedFront());
        TextureMapping formedMap = textureMapping(data, data.formedFront());
        TextureMapping activeMap = textureMapping(data, data.activeFront());

        Identifier formedModelId = ModelLocationUtils.getModelLocation(block);
        Identifier unformedModelId = ModelLocationUtils.getModelLocation(block, "_unformed");
        Identifier activeModelId = ModelLocationUtils.getModelLocation(block, "_active");
        prov.withBuilder(machineModelTemplate(), formedMap).build(formedModelId);
        prov.withBuilder(machineModelTemplate(), unformedMap).build(unformedModelId);
        prov.withBuilder(machineModelTemplate(), activeMap).build(activeModelId);

        MultiVariant unformedVariant = BlockModelGenerators.plainVariant(unformedModelId);
        MultiVariant formedVariant = BlockModelGenerators.plainVariant(formedModelId);
        MultiVariant activeVariant = BlockModelGenerators.plainVariant(activeModelId);

        prov.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block)
                        .with(PropertyDispatch.initial(FormedMachineBlock.FORMED, OrientedActiveMachineBlock.ACTIVE)
                                .select(false, false, unformedVariant)
                                .select(false, true, unformedVariant)
                                .select(true, false, formedVariant)
                                .select(true, true, activeVariant))
                        .with(BlockModelGenerators.ROTATION_FACING));
    }

    private static TextureMapping textureMapping(Data data, Identifier frontTexture) {
        MachineShellMaterial shell = data.shell();
        return new TextureMapping()
                .put(TextureSlot.SIDE, new Material(shell.sideTexture()))
                .put(TextureSlot.DOWN, new Material(shell.bottomTexture()))
                .put(TextureSlot.UP, new Material(shell.topTexture()))
                .put(TextureSlot.FRONT, new Material(frontTexture))
                .put(TextureSlot.PARTICLE, new Material(shell.sideTexture()));
    }

    private static ExtendedModelTemplateBuilder machineModelTemplate() {
        return ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.parse("minecraft:block/block"))
                .requiredTextureSlot(TextureSlot.SIDE)
                .requiredTextureSlot(TextureSlot.DOWN)
                .requiredTextureSlot(TextureSlot.UP)
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
                        .from(0f, 0f, -0.01f).to(16f, 16f, 0f)
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.FRONT).uvs(0f, 0f, 16f, 16f)));
    }
}
