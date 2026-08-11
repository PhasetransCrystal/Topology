package net.ptcrys.topo.datav2.material.common.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.datagen.generator.RegistryLibBlockModelGenerator;
import net.ptcrys.registrylib.util.RegistryLibTintSources;
import net.ptcrys.registrylib.util.color.RgbColor;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.data.MaterialDataType;
import net.ptcrys.topo.apiv2.material.render.MaterialBlockRender;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.material.common.RgbColorData;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.data.models.model.ModelLocationUtils;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.template.ExtendedModelTemplateBuilder;
import net.neoforged.neoforge.client.model.generators.template.FaceBuilder;

import java.util.List;

import static net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes.PRIMARY_COLOR;

/** Block-backed GTM-style single-strand wire item render; tier is represented by cable thickness. */
public final class WireTierBlockRender implements MaterialBlockRender {

    private static final String SIDE_TEXTURE = "block/material/wire_side";
    private static final String END_TEXTURE = "block/material/wire_end";

    private final float thicknessPixels;

    public WireTierBlockRender(float thicknessPixels) {
        if (thicknessPixels <= 0.0f || thicknessPixels > 16.0f) {
            throw new IllegalArgumentException("wire thickness must be within (0, 16]");
        }
        this.thicknessPixels = thicknessPixels;
    }

    @Override
    public List<MaterialDataType<?>> requiredMaterialData() {
        return List.of(PRIMARY_COLOR);
    }

    @Override
    public void apply(BlockBuilder<Block, RegistryCore> builder, Material material) {
        builder.blockstate(() -> (block, provider) -> emitWireModel(block, provider))
                .blockTintSource(blockTints(material))
                .tintSource(itemTints(material));
    }

    private void emitWireModel(Block block, RegistryLibBlockModelGenerator provider) {
        float min = (16.0f - thicknessPixels) / 2.0f;
        float max = min + thicknessPixels;
        ExtendedModelTemplateBuilder template = ExtendedModelTemplateBuilder.builder()
                .parent(Identifier.parse("minecraft:block/block"))
                .ambientOcclusion(false)
                .guiLight(UnbakedModel.GuiLight.SIDE)
                .requiredTextureSlot(TextureSlot.SIDE)
                .requiredTextureSlot(TextureSlot.END)
                .requiredTextureSlot(TextureSlot.PARTICLE);

        addBox(template, 0.0f, min, min, 16.0f, max, max, Direction.WEST, Direction.EAST);

        TextureMapping mapping = new TextureMapping()
                .put(TextureSlot.SIDE, texture(SIDE_TEXTURE))
                .put(TextureSlot.END, texture(END_TEXTURE))
                .put(TextureSlot.PARTICLE, texture(SIDE_TEXTURE));
        Identifier model = ModelLocationUtils.getModelLocation(block);
        provider.withBuilder(template, mapping).build(model);
        provider.create(block, model);
    }

    private static void addBox(
                               ExtendedModelTemplateBuilder template,
                               float minX,
                               float minY,
                               float minZ,
                               float maxX,
                               float maxY,
                               float maxZ,
                               Direction... endFaces) {
        template.element(element -> {
            element.from(minX, minY, minZ).to(maxX, maxY, maxZ);
            for (Direction direction : Direction.values()) {
                TextureSlot texture = isEndFace(direction, endFaces) ? TextureSlot.END : TextureSlot.SIDE;
                element.face(direction, face -> configureFace(face, texture));
            }
        });
    }

    private static boolean isEndFace(Direction direction, Direction[] endFaces) {
        for (Direction endFace : endFaces) {
            if (direction == endFace) {
                return true;
            }
        }
        return false;
    }

    private static void configureFace(FaceBuilder face, TextureSlot texture) {
        face.texture(texture).tintindex(0).uvs(0.0f, 0.0f, 16.0f, 16.0f);
    }

    private static net.minecraft.client.resources.model.sprite.Material texture(String path) {
        return new net.minecraft.client.resources.model.sprite.Material(
                OfficialOIPlugin.INSTANCE.material().id(path));
    }

    private static BlockTintSource[] blockTints(Material material) {
        return new BlockTintSource[] {
                RegistryLibTintSources.blockConstant(RgbColor.of(color(material)))
        };
    }

    private static ItemTintSource[] itemTints(Material material) {
        return new ItemTintSource[] {
                RegistryLibTintSources.itemConstant(RgbColor.of(color(material)))
        };
    }

    private static int color(Material material) {
        return material.strategy().data(PRIMARY_COLOR).map(RgbColorData::rgb).orElse(0xFFFFFF);
    }
}
