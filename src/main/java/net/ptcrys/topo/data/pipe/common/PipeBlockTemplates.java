package net.ptcrys.topo.data.pipe.common;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.builders.ItemBuilder;
import net.ptcrys.registrylib.datagen.ProviderType;
import net.ptcrys.registrylib.datagen.provider.RegistryLibGeneralResourceProvider;
import net.ptcrys.topo.api.pipe.PipeBlock;
import net.ptcrys.topo.api.pipe.PipeBlockTemplate;
import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipeSideVisual;
import net.ptcrys.topo.apiv2.machine.MachineBlock;

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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Standard OI pipe block template: stone-based metal-sounding block dropping itself, pickaxe
 * mineable, with the raw-JSON asset set emitted through RegistryLib's general resource provider
 * so every generated file derives from the registered {@link PipeDefinition} (single source of
 * truth). Per pipe: a topology-aware multipart blockstate with one shaft-width core on every block
 * plus six directions x {pipe→arm, extract→arm+cap}, four child models binding the texture set, an
 * item display model and the client item definition. Four shared parent models carry the geometry.
 *
 * <p>
 * The OI texture set has a deliberately small contract: {@code center} carries the resource icon,
 * {@code arm} carries the tier rail, and {@code terminal} skins extraction faces and item ends. All
 * are original hard-edged 16x16 sheets. The default RegistryLib trivial-cube blockstate is
 * suppressed with a no-op blockstate hook; vanilla {@code multipart} JSON needs no datagen API
 * beyond plain JSON.
 */
public final class PipeBlockTemplates {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String MODELS_SUBDIR = "block/pipe";

    private PipeBlockTemplates() {}

    /** The standard pipe template for one original OI texture set. */
    public static PipeBlockTemplate standard(Identifier center, Identifier arm, Identifier terminal) {
        return new StandardTemplate(
                Objects.requireNonNull(center, "center texture"),
                Objects.requireNonNull(arm, "arm texture"),
                Objects.requireNonNull(terminal, "terminal texture"));
    }

    private record StandardTemplate(Identifier center, Identifier arm, Identifier terminal)
            implements PipeBlockTemplate {

        @Override
        public BlockBehaviour.Properties styleBlockProperties(BlockBehaviour.Properties properties) {
            return properties.strength(1.0F, 6.0F).sound(SoundType.METAL);
        }

        @Override
        public void configureBlock(BlockBuilder<PipeBlock, RegistryCore> builder, PipeDefinition definition) {
            builder.initialProperties(Blocks.STONE);
            builder.defaultLoot();
            // Pipes join oi:mineable_with_wrench alongside pickaxe so the wrench is a correct tool
            // for drops and out-speeds the pickaxe — dismantling pipes is as quick as machines
            // (MachineDefinition tags machines the same way).
            builder.addTag(BlockTags.MINEABLE_WITH_PICKAXE, MachineBlock.MINEABLE_WITH_WRENCH);
            builder.blockstate(() -> (block, prov) -> prov.blockStateOutput.accept(multipart(block, definition)));
            builder.addData(ProviderType.GENERAL_RESOURCE, (RegistryLibGeneralResourceProvider provider) -> {
                String path = definition.id().getPath();
                provider.add("models", MODELS_SUBDIR, jsonFile(path + "_core.json",
                        childModel("pipe_core", center, arm, terminal)));
                provider.add("models", MODELS_SUBDIR, jsonFile(path + "_terminal.json",
                        childModel("pipe_terminal", center, arm, terminal)));
                provider.add("models", MODELS_SUBDIR, jsonFile(path + "_arm.json",
                        childModel("pipe_arm", center, arm, terminal)));
                provider.add("models", MODELS_SUBDIR, jsonFile(path + "_extract.json",
                        childModel("pipe_arm_extract", center, arm, terminal)));
                provider.add("models", MODELS_SUBDIR, jsonFile(path + "_item.json",
                        itemDisplayModel(center, arm, terminal)));
            });
        }

        @Override
        public void configureItem(ItemBuilder<BlockItem, ?> item, PipeDefinition definition) {
            Identifier displayModel = Identifier.fromNamespaceAndPath(
                    definition.id().getNamespace(), "block/pipe/" + definition.id().getPath() + "_item");
            item.model(() -> (ctx, prov) -> prov.createWithExistingModel(ctx, displayModel));
        }

        @Override
        public Object sharedGroup() {
            return StandardTemplate.class;
        }

        /** Once per datagen run: the shared parent models carrying the geometry. */
        @Override
        public void contributeShared(RegistryCore core) {
            core.addDataGenerator(ProviderType.GENERAL_RESOURCE, (RegistryLibGeneralResourceProvider provider) -> {
                provider.add("models", MODELS_SUBDIR, jsonFile("pipe_core.json", parentCore()));
                provider.add("models", MODELS_SUBDIR, jsonFile("pipe_terminal.json", parentTerminal()));
                provider.add("models", MODELS_SUBDIR, jsonFile("pipe_arm.json", parentArm()));
                provider.add("models", MODELS_SUBDIR, jsonFile("pipe_arm_extract.json", parentArmExtract()));
            });
        }
    }

    private static MultiPartGenerator multipart(PipeBlock block, PipeDefinition definition) {
        String modelBase = definition.id().getNamespace() + ":block/pipe/" + definition.id().getPath();
        Identifier core = Identifier.parse(modelBase + "_core");
        Identifier arm = Identifier.parse(modelBase + "_arm");
        Identifier extract = Identifier.parse(modelBase + "_extract");
        MultiPartGenerator generator = MultiPartGenerator.multiPart(block);
        for (int mask = 0; mask < 64; mask++) {
            generator = generator.with(connectionMask(mask), BlockModelGenerators.plainVariant(core));
        }
        for (Direction direction : Direction.values()) {
            generator = generator
                    .with(new ConditionBuilder().term(PipeBlock.property(direction), PipeSideVisual.PIPE),
                            rotated(BlockModelGenerators.plainVariant(arm), direction))
                    .with(new ConditionBuilder().term(PipeBlock.property(direction), PipeSideVisual.EXTRACT),
                            rotated(BlockModelGenerators.plainVariant(extract), direction));
        }
        return generator;
    }

    private static ConditionBuilder connectionMask(int mask) {
        ConditionBuilder condition = new ConditionBuilder();
        for (Direction direction : Direction.values()) {
            int bit = 1 << direction.ordinal();
            if ((mask & bit) == 0) {
                condition.term(PipeBlock.property(direction), PipeSideVisual.NONE);
            } else {
                condition.term(
                        PipeBlock.property(direction), PipeSideVisual.PIPE, PipeSideVisual.EXTRACT);
            }
        }
        return condition;
    }

    /** The arm parent model points north; rotate it onto the given side. */
    private static MultiVariant rotated(MultiVariant variant, Direction direction) {
        return switch (direction) {
            case NORTH -> variant;
            case SOUTH -> variant.with(BlockModelGenerators.Y_ROT_180);
            case WEST -> variant.with(BlockModelGenerators.Y_ROT_270);
            case EAST -> variant.with(BlockModelGenerators.Y_ROT_90);
            case DOWN -> variant.with(BlockModelGenerators.X_ROT_90);
            case UP -> variant.with(BlockModelGenerators.X_ROT_270);
        };
    }

    private static BiFunction<Path, OutputStream, Path> jsonFile(String fileName, JsonObject json) {
        byte[] bytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        return (dir, stream) -> {
            try {
                stream.write(bytes);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed writing pipe asset " + fileName, e);
            }
            return dir.resolve(fileName);
        };
    }

    // --- models -----------------------------------------------------------------------------

    private static JsonObject childModel(
                                         String parent, Identifier center, Identifier arm, Identifier terminal) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "topo:block/pipe/" + parent);
        root.add("textures", textures(center, arm, terminal));
        return root;
    }

    private static JsonObject textures(Identifier center, Identifier arm, Identifier terminal) {
        JsonObject textures = new JsonObject();
        textures.addProperty("center", center.toString());
        textures.addProperty("arm", arm.toString());
        textures.addProperty("terminal", terminal.toString());
        return textures;
    }

    /** Native-resolution OI sheets: a restrained junction cross and one axial color stripe. */
    private static final int[] CENTER_FACE = { 0, 0, 16, 16 };
    private static final int[] ARM_FACE = { 0, 0, 16, 16 };

    /** Shaft-width center node emitted for every topology regardless of connection count. */
    private static JsonObject parentCore() {
        JsonObject root = modelRoot();
        JsonArray elements = new JsonArray();
        elements.add(box(5, 5, 5, 11, 11, 11,
                face("#center", CENTER_FACE), face("#center", CENTER_FACE),
                face("#center", CENTER_FACE), face("#center", CENTER_FACE),
                face("#center", CENTER_FACE), face("#center", CENTER_FACE)));
        root.add("elements", elements);
        return root;
    }

    /** Compatibility plate retained for generated model stability; world dead ends use the core. */
    private static JsonObject parentTerminal() {
        JsonObject root = modelRoot();
        JsonArray elements = new JsonArray();
        addTerminalCap(elements, 7.99, 8, false);
        root.add("elements", elements);
        return root;
    }

    /** Adds one flush face matching the shaft's exact 6x6 cross-section. */
    private static void addTerminalCap(JsonArray elements, double z1, double z2, boolean north) {
        elements.add(box(5, 5, z1, 11, 11, z2,
                null, null,
                north ? face("#terminal", CENTER_FACE) : null,
                north ? null : face("#terminal", CENTER_FACE),
                null, null));
    }

    /** Arm points north (-Z) and ends exactly at the shaft-width center node boundary. */
    private static JsonObject parentArm() {
        JsonObject root = modelRoot();
        JsonArray elements = new JsonArray();
        addShaft(elements, 0, 5);
        root.add("elements", elements);
        return root;
    }

    private static JsonObject parentArmExtract() {
        JsonObject root = modelRoot();
        JsonArray elements = new JsonArray();
        addShaft(elements, 2, 5);
        addExtractionPort(elements);
        root.add("elements", elements);
        return root;
    }

    /** Slightly wider collar rendered only for extraction sides at the container boundary. */
    private static void addExtractionPort(JsonArray elements) {
        elements.add(box(4, 4, 0, 12, 12, 2,
                face("#arm", ARM_FACE), face("#arm", ARM_FACE),
                faceCull("#terminal", CENTER_FACE, "north"), face("#terminal", CENTER_FACE),
                faceRotated("#arm", ARM_FACE, 90), faceRotated("#arm", ARM_FACE, 90)));
    }

    private static void addShaft(JsonArray elements, double z1, double z2) {
        elements.add(box(5, 5, z1, 11, 11, z2,
                face("#arm", ARM_FACE), face("#arm", ARM_FACE),
                null, null,
                faceRotated("#arm", ARM_FACE, 90), faceRotated("#arm", ARM_FACE, 90)));
    }

    /** Hand/GUI model mirrors the world rule: a small center node with two shaft extensions. */
    private static JsonObject itemDisplayModel(
                                               Identifier center, Identifier arm, Identifier terminal) {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/block");
        JsonObject textures = textures(center, arm, terminal);
        textures.addProperty("particle", center.toString());
        root.add("textures", textures);
        JsonArray elements = new JsonArray();
        addShaft(elements, 0, 5);
        addShaft(elements, 11, 16);
        elements.add(box(5, 5, 5, 11, 11, 11,
                face("#center", CENTER_FACE), face("#center", CENTER_FACE),
                face("#center", CENTER_FACE), face("#center", CENTER_FACE),
                face("#center", CENTER_FACE), face("#center", CENTER_FACE)));
        addTerminalCap(elements, 0, 0.01, true);
        addTerminalCap(elements, 15.99, 16, false);
        root.add("elements", elements);
        return root;
    }

    private static JsonObject modelRoot() {
        JsonObject root = new JsonObject();
        root.addProperty("parent", "minecraft:block/block");
        JsonObject textures = new JsonObject();
        textures.addProperty("particle", "#center");
        root.add("textures", textures);
        return root;
    }

    /** Faces in order down, up, north, south, west, east; null skips the face. */
    private static JsonObject box(double x1, double y1, double z1, double x2, double y2, double z2,
                                  JsonObject down, JsonObject up, JsonObject north, JsonObject south, JsonObject west, JsonObject east) {
        JsonObject element = new JsonObject();
        element.add("from", position(x1, y1, z1));
        element.add("to", position(x2, y2, z2));
        JsonObject faces = new JsonObject();
        addFace(faces, "down", down);
        addFace(faces, "up", up);
        addFace(faces, "north", north);
        addFace(faces, "south", south);
        addFace(faces, "west", west);
        addFace(faces, "east", east);
        element.add("faces", faces);
        return element;
    }

    private static void addFace(JsonObject faces, String name, JsonObject face) {
        if (face != null) {
            faces.add(name, face);
        }
    }

    private static JsonObject face(String texture, int[] uv) {
        JsonObject face = new JsonObject();
        face.add("uv", vector(uv));
        face.addProperty("texture", texture);
        return face;
    }

    private static JsonObject faceRotated(String texture, int[] uv, int rotation) {
        JsonObject face = face(texture, uv);
        face.addProperty("rotation", rotation);
        return face;
    }

    private static JsonObject faceCull(String texture, int[] uv, String cullface) {
        JsonObject face = face(texture, uv);
        face.addProperty("cullface", cullface);
        return face;
    }

    private static JsonArray vector(int... values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray position(double... values) {
        JsonArray array = new JsonArray();
        for (double value : values) {
            array.add(value);
        }
        return array;
    }
}
