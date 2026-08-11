package net.ptcrys.topo.integration.jei.ae2;

import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability;
import net.ptcrys.topo.apiv2.recipe.content.OIFluidIngredient;
import net.ptcrys.topo.apiv2.recipe.content.OIItemInput;
import net.ptcrys.topo.apiv2.recipe.content.OIItemOutput;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;

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
 * Assembles the {@link Ae2PatternEncoder#encode encoder} payload from an {@link OIRecipe}.
 *
 * <p>
 * Dispatch is by capability handle identity, not by content {@code instanceof}: each entry
 * already carries its capability, so the item/fluid translations key on the builtin handles from
 * {@link BuiltinOIResourceIntegrations} (handles resolve lazily inside the dispatch — the recipe
 * API layer stays free of AE2 imports, and this class stays loadable in plain JUnit through its
 * nested {@link Converted} type). {@link OIItemInput} expands to its display stacks as one slot's
 * alternatives (tag inputs capped at {@link #ALTERNATIVES_CAP} entries); {@link OIFluidIngredient}
 * maps to one fluid stack. Scalar capabilities are known-unrepresentable in AE2 processing patterns and
 * are skipped by contract; any other capability this dispatcher does not know fails hard (throw)
 * so a new convertible family cannot be silently dropped from encoded patterns.
 *
 * <p>
 * {@code tickInputs / tickOutputs} are not consulted (AE2 processing patterns cannot represent
 * per-tick IO). When present we emit a single debug-log line so a user inspecting their log can
 * understand a "thin" pattern.
 */
public final class OIRecipeToAe2StackConverter {

    /**
     * Upper bound of alternatives emitted per input content — a huge tag (e.g. all dyed blocks)
     * would otherwise bloat the encoded pattern's slot alternative list.
     */
    static final int ALTERNATIVES_CAP = 64;

    private static final Logger LOGGER = LoggerFactory.getLogger(OIRecipeToAe2StackConverter.class);

    private OIRecipeToAe2StackConverter() {}

    public record Converted(
                            List<List<GenericStack>> inputs,
                            List<GenericStack> outputs) {

        public boolean isEmpty() {
            return inputs.isEmpty() && outputs.isEmpty();
        }
    }

    public static Converted convert(OIRecipe recipe) {
        Converted out = convertEntries(recipe.inputs(), recipe.outputs());
        logDroppedExtras(recipe);
        return out;
    }

    /**
     * Pure entry-array translation. Exposed package-private so unit tests can drive the dispatch
     * logic without constructing a full {@link OIRecipe} (which requires a registered
     * {@code OIRecipeType}).
     */
    static Converted convertEntries(
                                    OIRecipe.InputEntry<?>[] inputEntries,
                                    OIRecipe.OutputEntry<?>[] outputEntries) {
        List<List<GenericStack>> inputs = new ArrayList<>(inputEntries.length);
        for (OIRecipe.InputEntry<?> entry : inputEntries) {
            List<GenericStack> slot = inputSlotAlternatives(entry);
            if (!slot.isEmpty()) {
                inputs.add(List.copyOf(slot));
            }
        }

        List<GenericStack> outputs = new ArrayList<>(outputEntries.length);
        for (OIRecipe.OutputEntry<?> entry : outputEntries) {
            GenericStack out = firstOutputStack(entry);
            if (out != null) {
                outputs.add(out);
            }
        }

        return new Converted(List.copyOf(inputs), List.copyOf(outputs));
    }

    /** One input entry &rarr; one pattern slot: every convertible content becomes an alternative. */
    private static List<GenericStack> inputSlotAlternatives(OIRecipe.InputEntry<?> entry) {
        RecipeCapability<?, ?> capability = entry.capability();
        if (capability == BuiltinOIResourceIntegrations.ITEM.recipeCapability()) {
            List<GenericStack> alternatives = new ArrayList<>();
            for (Object content : entry.contents()) {
                appendItemAlternatives(alternatives, (OIItemInput) content);
            }
            return alternatives;
        }
        if (capability == BuiltinOIResourceIntegrations.FLUID.recipeCapability()) {
            List<GenericStack> alternatives = new ArrayList<>();
            for (Object content : entry.contents()) {
                // OIFluidIngredient's constructor rejects empty fluid, so no isEmpty guard needed.
                FluidStack stack = ((OIFluidIngredient) content).fluid();
                alternatives.add(new GenericStack(AEFluidKey.of(stack.getFluid()), stack.getAmount()));
            }
            return alternatives;
        }
        requireRepresentableOrScalar(capability);
        return List.of();
    }

    private static void appendItemAlternatives(List<GenericStack> alternatives, OIItemInput itemInput) {
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
    private static @Nullable GenericStack firstOutputStack(OIRecipe.OutputEntry<?> entry) {
        RecipeCapability<?, ?> capability = entry.capability();
        if (capability == BuiltinOIResourceIntegrations.ITEM.recipeCapability()) {
            for (Object content : entry.contents()) {
                ItemStack stack = ((OIItemOutput) content).stack();
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
        if (capability == BuiltinOIResourceIntegrations.FLUID.recipeCapability()) {
            for (Object content : entry.contents()) {
                FluidStack stack = ((OIFluidIngredient) content).fluid();
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
                "AE2 pattern transfer has no converter for recipe capability " + capability.id() + "; add a dispatch branch in OIRecipeToAe2StackConverter");
    }

    private static void logDroppedExtras(OIRecipe recipe) {
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
