package net.ptcrys.topo.datav2.machine.common.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.datagen.generator.RegistryLibBlockModelGenerator;
import net.ptcrys.topo.apiv2.machine.MachineBlock;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderStrategy;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderType;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderUse;
import net.ptcrys.topo.apiv2.machine.render.MachineShellMaterial;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Plain six-sided casing render for an oriented machine block with no active/overlay state — the
 * counterpart of {@link ShellOverlayMachineRenderType} for part blocks (hatches, buses) that only
 * need a uniform casing that rotates with the block's {@code FACING}. All four horizontal faces use
 * the shell side texture; top and bottom use their own faces. An optional static front overlay marks
 * the block's own front face — the face the player looked at the block from when placing it — so a
 * part's orientation stays readable in world.
 */
public final class ShellMachineRenderType extends MachineBlockRenderType<ShellMachineRenderType.Data> {

    public ShellMachineRenderType(Identifier id) {
        super(id);
    }

    /** Render use for a casing built entirely from {@code shell}'s side/top/bottom textures. */
    public MachineBlockRenderUse<Data> shell(MachineShellMaterial shell) {
        return use(new Data(shell, null));
    }

    /** Render use for a casing whose front face additionally carries the static {@code front} overlay. */
    public MachineBlockRenderUse<Data> shell(MachineShellMaterial shell, Identifier front) {
        return use(new Data(shell, Objects.requireNonNull(front, "front overlay texture")));
    }

    public static final class Strategy implements MachineBlockRenderStrategy<Data> {

        @Override
        public void validate(Data data) {
            Objects.requireNonNull(data, "shell machine render data");
            Objects.requireNonNull(data.shell(), "shell material");
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
        private final @Nullable Identifier front;

        private Data(MachineShellMaterial shell, @Nullable Identifier front) {
            this.shell = Objects.requireNonNull(shell, "shell");
            this.front = front;
        }

        public MachineShellMaterial shell() {
            return shell;
        }

        public @Nullable Identifier front() {
            return front;
        }
    }

    private static <T extends Block> Supplier<BiConsumer<T, RegistryLibBlockModelGenerator>> blockstate(Data data) {
        return () -> (block, prov) -> generateBlockstate(block, prov, data);
    }

    private static void generateBlockstate(Block block, RegistryLibBlockModelGenerator prov, Data data) {
        Identifier modelId = ModelLocationUtils.getModelLocation(block);
        prov.withBuilder(machineModelTemplate(data.front() != null), textureMapping(data)).build(modelId);

        MultiVariant variant = BlockModelGenerators.plainVariant(modelId);
        prov.blockStateOutput.accept(
                MultiVariantGenerator.dispatch(block, variant)
                        .with(BlockModelGenerators.ROTATION_FACING));
    }

    private static TextureMapping textureMapping(Data data) {
        MachineShellMaterial shell = data.shell();
        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.SIDE, new Material(shell.sideTexture()))
                .put(TextureSlot.DOWN, new Material(shell.bottomTexture()))
                .put(TextureSlot.UP, new Material(shell.topTexture()))
                .put(TextureSlot.PARTICLE, new Material(shell.sideTexture()));
        Identifier front = data.front();
        if (front != null) {
            mapping.put(TextureSlot.FRONT, new Material(front));
        }
        return mapping;
    }

    private static ExtendedModelTemplateBuilder machineModelTemplate(boolean withFront) {
        ExtendedModelTemplateBuilder builder = ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.parse("minecraft:block/block"))
                .requiredTextureSlot(TextureSlot.SIDE)
                .requiredTextureSlot(TextureSlot.DOWN)
                .requiredTextureSlot(TextureSlot.UP)
                .requiredTextureSlot(TextureSlot.PARTICLE)
                .element(eb -> eb
                        .from(0f, 0f, 0f).to(16f, 16f, 16f)
                        .face(Direction.DOWN, fb -> fb.texture(TextureSlot.DOWN).cullface(Direction.DOWN).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.UP, fb -> fb.texture(TextureSlot.UP).cullface(Direction.UP).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.NORTH).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.SOUTH, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.SOUTH).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.EAST, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.EAST).uvs(0f, 0f, 16f, 16f))
                        .face(Direction.WEST, fb -> fb.texture(TextureSlot.SIDE).cullface(Direction.WEST).uvs(0f, 0f, 16f, 16f)));
        if (!withFront) {
            return builder;
        }
        // Same overlay-plate element as ShellOverlayMachineRenderType: the front texture sits on a
        // thin plate over the casing's north face so transparent overlay pixels keep the casing visible.
        return builder
                .requiredTextureSlot(TextureSlot.FRONT)
                .element(eb -> eb
                        .from(0f, 0f, -0.01f).to(16f, 16f, 0f)
                        .face(Direction.NORTH, fb -> fb.texture(TextureSlot.FRONT).uvs(0f, 0f, 16f, 16f)));
    }
}
