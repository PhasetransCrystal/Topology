package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 根据配方能力身份选择已注册的内容转换器，框架不识别物品、流体或标量实现。 */
public final class TopoRecipeToAe2StackConverter {

    /** 一种配方能力的 AE 内容表示；无法表示的已知能力可显式返回空结果。 */
    public interface ContentConverter<I, O> {

        List<GenericStack> inputs(I content);

        @Nullable
        GenericStack output(O content);
    }

    private static final Map<RecipeCapability<?, ?>, Binding<?, ?>> CONVERTERS = new IdentityHashMap<>();

    /** 在客户端集成注册阶段声明转换能力，未知能力不会被静默丢弃。 */
    public static synchronized <I, O> void register(RecipeCapability<I, O> capability, ContentConverter<I, O> converter) {
        Objects.requireNonNull(capability, "recipe capability");
        Objects.requireNonNull(converter, "content converter");
        if (CONVERTERS.containsKey(capability)) {
            throw new IllegalStateException("Duplicate AE2 converter for " + capability.id());
        }
        CONVERTERS.put(capability, new Binding<>(capability, converter));
    }

    private record Binding<I, O>(RecipeCapability<I, O> capability, ContentConverter<I, O> converter) {

        List<GenericStack> inputs(Object content) {
            return converter.inputs(capability.inputType().cast(content));
        }

        @Nullable
        GenericStack output(Object content) {
            return converter.output(capability.outputType().cast(content));
        }
    }

    private static synchronized Binding<?, ?> requireConverter(RecipeCapability<?, ?> capability) {
        Binding<?, ?> converter = CONVERTERS.get(capability);
        if (converter == null) {
            throw new IllegalStateException("AE2 pattern transfer has no converter for recipe capability " + capability.id());
        }
        return converter;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TopoRecipeToAe2StackConverter.class);

    private TopoRecipeToAe2StackConverter() {}

    public record Converted(
                            List<List<GenericStack>> inputs,
                            List<GenericStack> outputs) {

        public boolean isEmpty() {
            return inputs.isEmpty() && outputs.isEmpty();
        }
    }

    public static Converted convert(TopoRecipe recipe) {
        Converted out = convertEntries(recipe.inputs(), recipe.outputs());
        logDroppedExtras(recipe);
        return out;
    }

    /**
     * Pure entry-array translation. Exposed package-private so unit tests can drive the dispatch
     * logic without constructing a full {@link TopoRecipe} (which requires a registered
     * {@code TopoRecipeType}).
     */
    static Converted convertEntries(
                                    TopoRecipe.InputEntry<?>[] inputEntries,
                                    TopoRecipe.OutputEntry<?>[] outputEntries) {
        List<List<GenericStack>> inputs = new ArrayList<>(inputEntries.length);
        for (TopoRecipe.InputEntry<?> entry : inputEntries) {
            Binding<?, ?> converter = requireConverter(entry.capability());
            for (Object content : entry.contents()) {
                List<GenericStack> alternatives = converter.inputs(content);
                if (!alternatives.isEmpty()) {
                    inputs.add(List.copyOf(alternatives));
                }
            }
        }

        List<GenericStack> outputs = new ArrayList<>(outputEntries.length);
        for (TopoRecipe.OutputEntry<?> entry : outputEntries) {
            Binding<?, ?> converter = requireConverter(entry.capability());
            for (Object content : entry.contents()) {
                GenericStack output = converter.output(content);
                if (output != null) {
                    outputs.add(output);
                }
            }
        }

        return new Converted(List.copyOf(inputs), List.copyOf(outputs));
    }

    private static void logDroppedExtras(TopoRecipe recipe) {
        if (!recipe.hasTickIo()) {
            return;
        }
        LOGGER.debug(
                "AE2 pattern transfer for {}: dropped {} tick inputs / {} tick outputs " + "(AE2 processing patterns cannot represent per-tick IO)",
                recipe.recipeType().id(),
                recipe.tickInputs().length,
                recipe.tickOutputs().length);
    }
}
