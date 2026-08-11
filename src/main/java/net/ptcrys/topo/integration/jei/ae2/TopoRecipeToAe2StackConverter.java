package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.api.recipe.content.TopoFluidIngredient;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the {@link Ae2PatternEncoder#encode encoder} payload from an {@link TopoRecipe}.
 *
 * <p>
 * Dispatch is by capability handle identity, not by content {@code instanceof}: each entry
 * already carries its capability, so the item/fluid translations key on the builtin handles from
 * {@link BuiltinTopoResourceIntegrations} (handles resolve lazily inside the dispatch — the recipe
 * API layer stays free of AE2 imports, and this class stays loadable in plain JUnit through its
 * nested {@link Converted} type). {@link TopoItemInput} expands to its display stacks as one slot's
 * alternatives (tag inputs capped at {@link #ALTERNATIVES_CAP} entries); {@link TopoFluidIngredient}
 * maps to one fluid stack. Scalar capabilities are known-unrepresentable in AE2 processing patterns and
 * are skipped by contract; any other capability this dispatcher does not know fails hard (throw)
 * so a new convertible family cannot be silently dropped from encoded patterns.
 *
 * <p>
 * {@code tickInputs / tickOutputs} are not consulted (AE2 processing patterns cannot represent
 * per-tick IO). When present we emit a single debug-log line so a user inspecting their log can
 * understand a "thin" pattern.
 */
public final class TopoRecipeToAe2StackConverter {

    /**
     * Upper bound of alternatives emitted per input content — a huge tag (e.g. all dyed blocks)
     * would otherwise bloat the encoded pattern's slot alternative list.
     */
    static final int ALTERNATIVES_CAP = 64;

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
            List<GenericStack> slot = inputSlotAlternatives(entry);
            if (!slot.isEmpty()) {
                inputs.add(List.copyOf(slot));
            }
        }

        List<GenericStack> outputs = new ArrayList<>(outputEntries.length);
        for (TopoRecipe.OutputEntry<?> entry : outputEntries) {
            GenericStack out = firstOutputStack(entry);
            if (out != null) {
                outputs.add(out);
            }
        }

        return new Converted(List.copyOf(inputs), List.copyOf(outputs));
    }

    /** One input entry &rarr; one pattern slot: every convertible content becomes an alternative. */
    private static List<GenericStack> inputSlotAlternatives(TopoRecipe.InputEntry<?> entry) {
        RecipeCapability<?, ?> capability = entry.capability();
        if (capability == BuiltinTopoResourceIntegrations.ITEM.recipeCapability()) {
            List<GenericStack> alternatives = new ArrayList<>();
            for (Object content : entry.contents()) {
                appendItemAlternatives(alternatives, (TopoItemInput) content);
            }
            return alternatives;
        }
        if (capability == BuiltinTopoResourceIntegrations.FLUID.recipeCapability()) {
            List<GenericStack> alternatives = new ArrayList<>();
            for (Object content : entry.contents()) {
                // TopoFluidIngredient's constructor rejects empty fluid, so no isEmpty guard needed.
                FluidStack stack = ((TopoFluidIngredient) content).fluid();
                alternatives.add(new GenericStack(AEFluidKey.of(stack.getFluid()), stack.getAmount()));
            }
            return alternatives;
        }
        requireRepresentableOrScalar(capability);
        return List.of();
    }

    private static void appendItemAlternatives(List<GenericStack> alternatives, TopoItemInput itemInput) {
        List<ItemStack> displayStacks = itemInput.displayStacks();
        int limit = Math.min(displayStacks.size(), ALTERNATIVES_CAP);
        for (int i = 0; i < limit; i++) {
            ItemStack stack = displayStacks.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key != null) {
                alternatives.add(new GenericStack(key, stack.getCount()));
            }
        }
    }

    /** First convertible output content of an entry (AE2 output slots bear no alternatives). */
    private static @Nullable GenericStack firstOutputStack(TopoRecipe.OutputEntry<?> entry) {
        RecipeCapability<?, ?> capability = entry.capability();
        if (capability == BuiltinTopoResourceIntegrations.ITEM.recipeCapability()) {
            for (Object content : entry.contents()) {
                ItemStack stack = ((TopoItemOutput) content).stack();
                if (stack.isEmpty()) {
                    continue;
                }
                AEItemKey key = AEItemKey.of(stack);
                if (key != null) {
                    return new GenericStack(key, stack.getCount());
                }
            }
            return null;
        }
        if (capability == BuiltinTopoResourceIntegrations.FLUID.recipeCapability()) {
            for (Object content : entry.contents()) {
                FluidStack stack = ((TopoFluidIngredient) content).fluid();
                return new GenericStack(AEFluidKey.of(stack.getFluid()), stack.getAmount());
            }
            return null;
        }
        requireRepresentableOrScalar(capability);
        return null;
    }

    /**
     * Scalar capabilities: skip (no AE2 processing representation). Any other unhandled capability: throw —
     * dropping unknown families would encode incomplete patterns without the player noticing.
     */
    private static void requireRepresentableOrScalar(RecipeCapability<?, ?> capability) {
        if (capability instanceof ScalarRecipeCapability) {
            return;
        }
        throw new IllegalStateException(
                "AE2 pattern transfer has no converter for recipe capability " + capability.id() + "; add a dispatch branch in TopoRecipeToAe2StackConverter");
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
