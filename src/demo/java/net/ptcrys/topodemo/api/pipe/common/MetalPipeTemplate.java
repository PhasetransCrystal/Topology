package net.ptcrys.topodemo.api.pipe.common;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.registrylib.datagen.ProviderType;
import net.ptcrys.registrylib.datagen.provider.RegistryLibGeneralResourceProvider;
import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.pipe.*;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Multipart geometry matches PipeBlock's six-wide core, arms and extraction collars. */
public record MetalPipeTemplate(Identifier texture) implements PipeBlockTemplate {

    public MetalPipeTemplate {
        Objects.requireNonNull(texture, "texture");
    }

    @Override
    public BlockBehaviour.Properties styleBlockProperties(BlockBehaviour.Properties properties) {
        return properties.strength(1.5f).sound(SoundType.METAL);
    }

    @Override
    public void configureBlock(BlockBuilder<PipeBlock, RegistryCore> builder, PipeDefinition definition) {
        builder.initialProperties(Blocks.COPPER_BLOCK).defaultLoot()
                .addTag(BlockTags.MINEABLE_WITH_PICKAXE, MachineBlock.MINEABLE_WITH_WRENCH)
                .blockstate(() -> (block, provider) -> {
                    MultiPartGenerator state = MultiPartGenerator.multiPart(block)
                            .with(BlockModelGenerators.plainVariant(model(definition, "core")));
                    for (Direction side : Direction.values()) {
                        state.with(new ConditionBuilder().term(PipeBlock.property(side), PipeSideVisual.PIPE),
                                rotated(BlockModelGenerators.plainVariant(model(definition, "arm")), side));
                        state.with(new ConditionBuilder().term(PipeBlock.property(side), PipeSideVisual.EXTRACT),
                                rotated(BlockModelGenerators.plainVariant(model(definition, "extract")), side));
                    }
                    provider.blockStateOutput.accept(state);
                });
        builder.addData(ProviderType.GENERAL_RESOURCE, (RegistryLibGeneralResourceProvider provider) -> {
            addModel(provider, definition, "core", box(5, 5, 5, 11, 11, 11));
            addModel(provider, definition, "arm", box(5, 5, 0, 11, 11, 5));
            JsonArray extraction = box(5, 5, 0, 11, 11, 5);
            extraction.add(box(4, 4, 0, 12, 12, 2).get(0));
            addModel(provider, definition, "extract", extraction);
            JsonArray item = box(5, 5, 0, 11, 11, 16);
            item.add(box(4, 4, 0, 12, 12, 2).get(0));
            item.add(box(4, 4, 14, 12, 12, 16).get(0));
            addModel(provider, definition, "item", item);
        });
    }

    @Override
    public void configureItem(ItemBuilder<BlockItem, ?> item, PipeDefinition definition) {
        item.model(() -> (context, provider) -> provider.createWithExistingModel(context, model(definition, "item")));
    }

    private static Identifier model(PipeDefinition definition, String part) {
        return definition.id().withPath("block/pipe/" + definition.id().getPath() + "_" + part);
    }

    private void addModel(RegistryLibGeneralResourceProvider provider, PipeDefinition definition, String part, JsonArray elements) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", "minecraft:block/block");
        JsonObject textures = new JsonObject();
        textures.addProperty("metal", texture.toString());
        textures.addProperty("particle", texture.toString());
        json.add("textures", textures);
        json.add("elements", elements);
        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        String name = definition.id().getPath() + "_" + part + ".json";
        provider.add("models", "block/pipe", (directory, output) -> {
            try {
                output.write(bytes);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            return directory.resolve(name);
        });
    }

    private static JsonArray box(int x1, int y1, int z1, int x2, int y2, int z2) {
        JsonObject element = new JsonObject();
        JsonArray from = new JsonArray();
        from.add(x1);
        from.add(y1);
        from.add(z1);
        JsonArray to = new JsonArray();
        to.add(x2);
        to.add(y2);
        to.add(z2);
        element.add("from", from);
        element.add("to", to);
        JsonObject faces = new JsonObject();
        for (Direction direction : Direction.values()) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", "#metal");
            faces.add(direction.getSerializedName(), face);
        }
        element.add("faces", faces);
        JsonArray result = new JsonArray();
        result.add(element);
        return result;
    }

    private static MultiVariant rotated(MultiVariant variant, Direction side) {
        return switch (side) {
            case NORTH -> variant;
            case SOUTH -> variant.with(BlockModelGenerators.Y_ROT_180);
            case WEST -> variant.with(BlockModelGenerators.Y_ROT_270);
            case EAST -> variant.with(BlockModelGenerators.Y_ROT_90);
            case DOWN -> variant.with(BlockModelGenerators.X_ROT_90);
            case UP -> variant.with(BlockModelGenerators.X_ROT_270);
        };
    }
}
