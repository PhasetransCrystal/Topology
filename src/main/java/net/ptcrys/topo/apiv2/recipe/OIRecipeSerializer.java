package net.ptcrys.topo.apiv2.recipe;

import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLine;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLines;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared codec logic for type-bound {@link OIRecipe} serializers.
 */
public final class OIRecipeSerializer {

    private OIRecipeSerializer() {}

    private static final Codec<Map<RecipeCapability<?, ?>, List<?>>> INPUT_CAP_MAP_CODEC = capMapCodec(true);
    private static final Codec<Map<RecipeCapability<?, ?>, List<?>>> OUTPUT_CAP_MAP_CODEC = capMapCodec(false);

    public static <R extends OIRecipe> MapCodec<R> codecFor(OIRecipeType<R> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                INPUT_CAP_MAP_CODEC.optionalFieldOf("inputs", Map.of())
                        .forGetter(recipe -> inputMapFrom(recipe.inputs())),
                OUTPUT_CAP_MAP_CODEC.optionalFieldOf("outputs", Map.of())
                        .forGetter(recipe -> outputMapFrom(recipe.outputs())),
                INPUT_CAP_MAP_CODEC.optionalFieldOf("tick_inputs", Map.of())
                        .forGetter(recipe -> inputMapFrom(recipe.tickInputs())),
                OUTPUT_CAP_MAP_CODEC.optionalFieldOf("tick_outputs", Map.of())
                        .forGetter(recipe -> outputMapFrom(recipe.tickOutputs())),
                Codec.INT.fieldOf("duration").forGetter(OIRecipe::duration),
                Identifier.CODEC.listOf().optionalFieldOf("production_lines", List.of())
                        .forGetter(recipe -> recipe.productionLines().stream().map(ProductionLine::id).toList()))
                .apply(instance, (inputs, outputs, tickInputs, tickOutputs, duration, productionLineIds) -> type.createRecipe(
                        toInputEntries(inputs),
                        toOutputEntries(outputs),
                        toInputEntries(tickInputs),
                        toOutputEntries(tickOutputs),
                        duration,
                        productionLineIds.stream().map(ProductionLines::require).toList())));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static Codec<Map<RecipeCapability<?, ?>, List<?>>> capMapCodec(boolean input) {
        return Codec.unboundedMap(Identifier.CODEC, Codec.list(Codec.PASSTHROUGH)).xmap(
                raw -> {
                    Map<RecipeCapability<?, ?>, List<?>> result = new LinkedHashMap<>();
                    for (Map.Entry<Identifier, List<Dynamic<?>>> entry : raw.entrySet()) {
                        RecipeCapability capability = RecipeCapabilities.require(entry.getKey());
                        Codec codec = input ? capability.inputCodec() : capability.outputCodec();
                        List<Object> contents = new ArrayList<>();
                        for (Dynamic<?> dynamic : entry.getValue()) {
                            contents.add(codec.parse(dynamic.getOps(), dynamic.getValue()).getOrThrow());
                        }
                        result.put(capability, List.copyOf(contents));
                    }
                    return result;
                },
                map -> {
                    Map<Identifier, List<Dynamic<?>>> result = new LinkedHashMap<>();
                    for (Map.Entry<RecipeCapability<?, ?>, List<?>> entry : map.entrySet()) {
                        RecipeCapability capability = entry.getKey();
                        Codec codec = input ? capability.inputCodec() : capability.outputCodec();
                        List<Dynamic<?>> encoded = new ArrayList<>();
                        for (Object content : entry.getValue()) {
                            JsonElement json = (JsonElement) codec
                                    .encodeStart(JsonOps.INSTANCE, content)
                                    .getOrThrow();
                            encoded.add(new Dynamic<>(JsonOps.INSTANCE, json));
                        }
                        result.put(capability.id(), List.copyOf(encoded));
                    }
                    return result;
                });
    }

    public static <R extends OIRecipe> StreamCodec<RegistryFriendlyByteBuf, R> streamCodecFor(OIRecipeType<R> type) {
        return new StreamCodec<>() {

            @Override
            public R decode(RegistryFriendlyByteBuf buf) {
                int duration = buf.readVarInt();
                InputEntryArray inputs = readInputEntries(buf);
                OutputEntryArray outputs = readOutputEntries(buf);
                InputEntryArray tickInputs = readInputEntries(buf);
                OutputEntryArray tickOutputs = readOutputEntries(buf);
                List<ProductionLine> productionLines = readProductionLines(buf);
                return type.createRecipe(
                        inputs.entries,
                        outputs.entries,
                        tickInputs.entries,
                        tickOutputs.entries,
                        duration,
                        productionLines);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, OIRecipe recipe) {
                buf.writeVarInt(recipe.duration());
                writeInputEntries(buf, recipe.inputs());
                writeOutputEntries(buf, recipe.outputs());
                writeInputEntries(buf, recipe.tickInputs());
                writeOutputEntries(buf, recipe.tickOutputs());
                writeProductionLines(buf, recipe.productionLines());
            }
        };
    }

    private static Map<RecipeCapability<?, ?>, List<?>> inputMapFrom(OIRecipe.InputEntry<?>[] entries) {
        Map<RecipeCapability<?, ?>, List<?>> map = new LinkedHashMap<>();
        for (OIRecipe.InputEntry<?> entry : entries) {
            map.put(entry.capability(), entry.contents());
        }
        return map;
    }

    private static Map<RecipeCapability<?, ?>, List<?>> outputMapFrom(OIRecipe.OutputEntry<?>[] entries) {
        Map<RecipeCapability<?, ?>, List<?>> map = new LinkedHashMap<>();
        for (OIRecipe.OutputEntry<?> entry : entries) {
            map.put(entry.capability(), entry.contents());
        }
        return map;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OIRecipe.InputEntry<?>[] toInputEntries(Map<RecipeCapability<?, ?>, List<?>> map) {
        OIRecipe.InputEntry<?>[] entries = new OIRecipe.InputEntry<?>[map.size()];
        int index = 0;
        for (Map.Entry<RecipeCapability<?, ?>, List<?>> entry : map.entrySet()) {
            entries[index++] = new OIRecipe.InputEntry(entry.getKey(), entry.getValue());
        }
        return entries;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OIRecipe.OutputEntry<?>[] toOutputEntries(Map<RecipeCapability<?, ?>, List<?>> map) {
        OIRecipe.OutputEntry<?>[] entries = new OIRecipe.OutputEntry<?>[map.size()];
        int index = 0;
        for (Map.Entry<RecipeCapability<?, ?>, List<?>> entry : map.entrySet()) {
            entries[index++] = new OIRecipe.OutputEntry(entry.getKey(), entry.getValue());
        }
        return entries;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void writeInputEntries(RegistryFriendlyByteBuf buf, OIRecipe.InputEntry<?>[] entries) {
        buf.writeVarInt(entries.length);
        for (OIRecipe.InputEntry<?> entry : entries) {
            buf.writeUtf(entry.capability().id().toString());
            StreamCodec streamCodec = entry.capability().inputStreamCodec();
            List<?> contents = entry.contents();
            buf.writeVarInt(contents.size());
            for (Object content : contents) {
                streamCodec.encode(buf, content);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void writeOutputEntries(RegistryFriendlyByteBuf buf, OIRecipe.OutputEntry<?>[] entries) {
        buf.writeVarInt(entries.length);
        for (OIRecipe.OutputEntry<?> entry : entries) {
            buf.writeUtf(entry.capability().id().toString());
            StreamCodec streamCodec = entry.capability().outputStreamCodec();
            List<?> contents = entry.contents();
            buf.writeVarInt(contents.size());
            for (Object content : contents) {
                streamCodec.encode(buf, content);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static InputEntryArray readInputEntries(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        OIRecipe.InputEntry<?>[] entries = new OIRecipe.InputEntry<?>[count];
        for (int i = 0; i < count; i++) {
            RecipeCapability capability = RecipeCapabilities.require(Identifier.parse(buf.readUtf()));
            int size = buf.readVarInt();
            List<Object> contents = new ArrayList<>(size);
            for (int contentIndex = 0; contentIndex < size; contentIndex++) {
                contents.add(capability.inputStreamCodec().decode(buf));
            }
            entries[i] = new OIRecipe.InputEntry(capability, contents);
        }
        return new InputEntryArray(entries);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OutputEntryArray readOutputEntries(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        OIRecipe.OutputEntry<?>[] entries = new OIRecipe.OutputEntry<?>[count];
        for (int i = 0; i < count; i++) {
            RecipeCapability capability = RecipeCapabilities.require(Identifier.parse(buf.readUtf()));
            int size = buf.readVarInt();
            List<Object> contents = new ArrayList<>(size);
            for (int contentIndex = 0; contentIndex < size; contentIndex++) {
                contents.add(capability.outputStreamCodec().decode(buf));
            }
            entries[i] = new OIRecipe.OutputEntry(capability, contents);
        }
        return new OutputEntryArray(entries);
    }

    private static List<ProductionLine> readProductionLines(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<ProductionLine> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(ProductionLines.require(Identifier.parse(buf.readUtf())));
        }
        return List.copyOf(lines);
    }

    private static void writeProductionLines(RegistryFriendlyByteBuf buf, List<ProductionLine> lines) {
        buf.writeVarInt(lines.size());
        for (ProductionLine line : lines) {
            buf.writeUtf(line.id().toString());
        }
    }

    private record InputEntryArray(OIRecipe.InputEntry<?>[] entries) {}

    private record OutputEntryArray(OIRecipe.OutputEntry<?>[] entries) {}
}
