package net.ptcrys.topo.datav2.material.common.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.datagen.ProviderType;
import net.ptcrys.registrylib.util.RegistryLibTintSources;
import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.registrylib.util.color.RgbColor;
import net.ptcrys.registrylib.util.visual.BlockModelLayer;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.render.MaterialBlockRender;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.material.common.RgbColorData;
import net.ptcrys.topo.datav2.material.common.RgbColorDataType;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.world.level.block.Block;

import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes.PRIMARY_COLOR;
import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes.SECONDARY_COLOR;

/** Reuses GTM material block textures with generated two-layer tinted block models. */
public final class GtmMaterialBlockRender implements MaterialBlockRender {

    private static final String DEFAULT_ICON_SET = "dull";
    private static final Map<String, String> SPECIAL_ICON_SETS = Map.of("uranium", "radioactive");
    private static final String DULL_BLOCK_TEXTURE = "block/material_sets/dull/block";
    private static final String RADIOACTIVE_BLOCK_OVERLAY = "block/material_sets/radioactive/block_secondary";
    private static boolean registeredMetadata;

    @Override
    public List<MaterialDataType<?>> requiredMaterialData() {
        return List.of(PRIMARY_COLOR, SECONDARY_COLOR);
    }

    @Override
    public void apply(BlockBuilder<Block, RegistryCore> builder, Material material) {
        TextureRef base = texture(DULL_BLOCK_TEXTURE);
        TextureRef overlay = texture(secondaryTexture(material));
        registerGeneratedMetadata(builder);
        builder.layeredCube(
                base,
                BlockModelLayer.tinted(base, 0),
                BlockModelLayer.tinted(overlay, 1))
                .blockTintSource(blockTints(material))
                .tintSource(itemTints(material));
    }

    private static String iconSet(Material material) {
        return SPECIAL_ICON_SETS.getOrDefault(material.id().getPath(), DEFAULT_ICON_SET);
    }

    private static String secondaryTexture(Material material) {
        if ("radioactive".equals(iconSet(material))) {
            return RADIOACTIVE_BLOCK_OVERLAY;
        }
        return DULL_BLOCK_TEXTURE;
    }

    private static TextureRef texture(String path) {
        return TextureRef.of(OfficialOIPlugin.INSTANCE.material().id(path));
    }

    private static void registerGeneratedMetadata(BlockBuilder<Block, RegistryCore> builder) {
        if (registeredMetadata) {
            return;
        }
        registeredMetadata = true;
        builder.addData(
                ProviderType.GENERAL_RESOURCE,
                provider -> provider.add("textures", "block", animationMetadata("material_sets/radioactive/block_secondary.png.mcmeta")));
    }

    private static BiFunction<Path, OutputStream, Path> animationMetadata(String resourcePath) {
        return (root, stream) -> {
            JsonObject json = new JsonObject();
            json.add("animation", new JsonObject());
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            try {
                stream.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException("Failed to write generated texture metadata for " + resourcePath, e);
            }
            return root.resolve(resourcePath);
        };
    }

    private static BlockTintSource[] blockTints(Material material) {
        return new BlockTintSource[] {
                RegistryLibTintSources.blockConstant(RgbColor.of(color(material, PRIMARY_COLOR))),
                RegistryLibTintSources.blockConstant(RgbColor.of(color(material, SECONDARY_COLOR)))
        };
    }

    private static ItemTintSource[] itemTints(Material material) {
        return new ItemTintSource[] {
                RegistryLibTintSources.itemConstant(RgbColor.of(color(material, PRIMARY_COLOR))),
                RegistryLibTintSources.itemConstant(RgbColor.of(color(material, SECONDARY_COLOR)))
        };
    }

    private static int color(Material material, RgbColorDataType type) {
        return material.strategy().data(type).map(RgbColorData::rgb).orElse(0xFFFFFF);
    }
}
