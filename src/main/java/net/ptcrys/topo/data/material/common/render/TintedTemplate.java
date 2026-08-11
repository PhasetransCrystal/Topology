package net.ptcrys.topo.data.material.common.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.registrylib.util.RegistryLibTintSources;
import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.registrylib.util.color.RgbColor;
import net.ptcrys.registrylib.util.visual.BlockModelLayer;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.render.MaterialBlockRender;
import net.ptcrys.topo.api.material.render.MaterialItemRender;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.common.RgbColorData;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Grayscale template renders tinted by material colors. Renders are plain values composed at the
 * form declaration site, e.g. {@code TintedTemplate.item(tinted(PRIMARY_COLOR, "item/material/ingot"),
 * flat("item/material/ingot_overlay"))}; configuration is validated at construction.
 */
public final class TintedTemplate {

    private TintedTemplate() {}

    /** A grayscale layer tinted with the material's {@code requiredData} color. */
    public static Layer tinted(MaterialDataType<RgbColorData> requiredData, String texturePath) {
        return new Layer(texturePath, Objects.requireNonNull(requiredData, "requiredData"));
    }

    /** An untinted layer drawn as-is. */
    public static Layer flat(String texturePath) {
        return new Layer(texturePath, null);
    }

    public static BlockLayer opaque(Layer layer) {
        return new BlockLayer(layer, false);
    }

    public static BlockLayer translucent(Layer layer) {
        return new BlockLayer(layer, true);
    }

    public static MaterialItemRender item(Layer... layers) {
        List<Layer> list = List.of(layers);
        if (list.isEmpty() || list.size() > 3) {
            throw new IllegalArgumentException("tinted template render requires 1 to 3 item render layers");
        }
        return new ItemRender(list, false);
    }

    /** Like {@link #item}, but the model parent is {@code item/handheld} (tool holding pose). */
    public static MaterialItemRender handheldItem(Layer... layers) {
        List<Layer> list = List.of(layers);
        if (list.isEmpty() || list.size() > 3) {
            throw new IllegalArgumentException("tinted template render requires 1 to 3 item render layers");
        }
        return new ItemRender(list, true);
    }

    public static MaterialBlockRender block(BlockLayer... layers) {
        List<BlockLayer> list = List.of(layers);
        if (list.isEmpty() || list.size() > 3) {
            throw new IllegalArgumentException("tinted template render requires 1 to 3 block render layers");
        }
        return new BlockRender(list);
    }

    public static final class Layer {

        private final String texturePath;
        private final MaterialDataType<RgbColorData> requiredData;

        private Layer(String texturePath, MaterialDataType<RgbColorData> requiredData) {
            if (texturePath == null || texturePath.isBlank()) {
                throw new IllegalArgumentException("tinted template render layer requires a texture path");
            }
            this.texturePath = texturePath;
            this.requiredData = requiredData;
        }
    }

    public static final class BlockLayer {

        private final Layer layer;
        private final boolean forceTranslucent;

        private BlockLayer(Layer layer, boolean forceTranslucent) {
            this.layer = Objects.requireNonNull(layer, "layer");
            this.forceTranslucent = forceTranslucent;
        }
    }

    private static final class ItemRender implements MaterialItemRender {

        /** Vanilla only ships layered templates for the generated parent; handheld needs its own. */
        private static final net.minecraft.client.data.models.model.ModelTemplate TWO_LAYERED_HANDHELD = new net.minecraft.client.data.models.model.ModelTemplate(
                java.util.Optional.of(Identifier.withDefaultNamespace("item/handheld")),
                java.util.Optional.empty(),
                net.minecraft.client.data.models.model.TextureSlot.LAYER0,
                net.minecraft.client.data.models.model.TextureSlot.LAYER1);
        private static final net.minecraft.client.data.models.model.ModelTemplate THREE_LAYERED_HANDHELD = new net.minecraft.client.data.models.model.ModelTemplate(
                java.util.Optional.of(Identifier.withDefaultNamespace("item/handheld")),
                java.util.Optional.empty(),
                net.minecraft.client.data.models.model.TextureSlot.LAYER0,
                net.minecraft.client.data.models.model.TextureSlot.LAYER1,
                net.minecraft.client.data.models.model.TextureSlot.LAYER2);

        private final List<Layer> layers;
        private final boolean handheld;

        private ItemRender(List<Layer> layers, boolean handheld) {
            this.layers = layers;
            this.handheld = handheld;
        }

        @Override
        public List<MaterialDataType<?>> requiredMaterialData() {
            List<MaterialDataType<?>> requirements = new ArrayList<>();
            for (Layer layer : layers) {
                if (layer.requiredData != null) {
                    requirements.add(layer.requiredData);
                }
            }
            return List.copyOf(requirements);
        }

        @Override
        public void apply(ItemBuilder<Item, RegistryCore> builder, Material material) {
            ItemTintSource[] tints = itemTintSources(layers, material);
            builder.model(() -> (item, generator) -> {
                Identifier model = switch (layers.size()) {
                    case 1 -> (handheld ? ModelTemplates.FLAT_HANDHELD_ITEM : ModelTemplates.FLAT_ITEM).create(
                            item,
                            TextureMapping.layer0(templateMaterial(layers.get(0))),
                            generator.modelOutput);
                    case 2 -> (handheld ? TWO_LAYERED_HANDHELD : ModelTemplates.TWO_LAYERED_ITEM).create(
                            item,
                            TextureMapping.layered(
                                    templateMaterial(layers.get(0)),
                                    templateMaterial(layers.get(1))),
                            generator.modelOutput);
                    case 3 -> (handheld ? THREE_LAYERED_HANDHELD : ModelTemplates.THREE_LAYERED_ITEM).create(
                            item,
                            TextureMapping.layered(
                                    templateMaterial(layers.get(0)),
                                    templateMaterial(layers.get(1)),
                                    templateMaterial(layers.get(2))),
                            generator.modelOutput);
                    default -> throw new IllegalStateException(
                            "Unsupported tinted template item layer count: " + layers.size());
                };
                generator.itemModelOutput.accept(item, ItemModelUtils.tintedModel(model, tints));
            });
        }
    }

    private static final class BlockRender implements MaterialBlockRender {

        private final List<BlockLayer> layers;

        private BlockRender(List<BlockLayer> layers) {
            this.layers = layers;
        }

        @Override
        public List<MaterialDataType<?>> requiredMaterialData() {
            List<MaterialDataType<?>> requirements = new ArrayList<>();
            for (BlockLayer blockLayer : layers) {
                if (blockLayer.layer.requiredData != null) {
                    requirements.add(blockLayer.layer.requiredData);
                }
            }
            return List.copyOf(requirements);
        }

        @Override
        public void apply(BlockBuilder<Block, RegistryCore> builder, Material material) {
            TextureRef particle = templateTextureRef(layers.get(0).layer);
            builder.layeredCube(particle, blockModelLayers(layers))
                    .blockTintSource(blockTintSources(layers, material))
                    .tintSource(blockItemTintSources(layers, material));
        }
    }

    private static net.minecraft.client.resources.model.sprite.Material templateMaterial(Layer layer) {
        return new net.minecraft.client.resources.model.sprite.Material(
                OfficialTopoPlugin.INSTANCE.material().id(layer.texturePath));
    }

    private static ItemTintSource[] itemTintSources(List<Layer> layers, Material material) {
        ItemTintSource[] tints = new ItemTintSource[layers.size()];
        for (int index = 0; index < layers.size(); index++) {
            tints[index] = RegistryLibTintSources.itemConstant(RgbColor.of(layerColor(layers.get(index), material)));
        }
        return tints;
    }

    private static int layerColor(Layer layer, Material material) {
        if (layer.requiredData == null) {
            return 0xFFFFFF;
        }
        return material.strategy()
                .data(layer.requiredData)
                .map(RgbColorData::rgb)
                .orElse(0xFFFFFF);
    }

    private static TextureRef templateTextureRef(Layer layer) {
        return TextureRef.of(OfficialTopoPlugin.INSTANCE.material().id(layer.texturePath));
    }

    private static BlockModelLayer[] blockModelLayers(List<BlockLayer> layers) {
        BlockModelLayer[] result = new BlockModelLayer[layers.size()];
        for (int index = 0; index < layers.size(); index++) {
            BlockLayer blockLayer = layers.get(index);
            Layer layer = blockLayer.layer;
            TextureRef texture = templateTextureRef(layer);
            if (layer.requiredData != null) {
                result[index] = BlockModelLayer.tinted(texture, index, blockLayer.forceTranslucent);
            } else {
                result[index] = BlockModelLayer.untinted(texture, blockLayer.forceTranslucent);
            }
        }
        return result;
    }

    private static BlockTintSource[] blockTintSources(List<BlockLayer> layers, Material material) {
        BlockTintSource[] tints = new BlockTintSource[layers.size()];
        for (int index = 0; index < layers.size(); index++) {
            tints[index] = RegistryLibTintSources.blockConstant(
                    RgbColor.of(layerColor(layers.get(index).layer, material)));
        }
        return tints;
    }

    private static ItemTintSource[] blockItemTintSources(List<BlockLayer> layers, Material material) {
        ItemTintSource[] tints = new ItemTintSource[layers.size()];
        for (int index = 0; index < layers.size(); index++) {
            tints[index] = RegistryLibTintSources.itemConstant(
                    RgbColor.of(layerColor(layers.get(index).layer, material)));
        }
        return tints;
    }
}
