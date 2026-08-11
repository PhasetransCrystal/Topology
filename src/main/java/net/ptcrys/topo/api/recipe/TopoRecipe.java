package net.ptcrys.topo.api.recipe;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.api.recipe.capability.RecipeCapability;
import net.ptcrys.topo.api.recipe.capability.RecipeInputUse;
import net.ptcrys.topo.api.recipe.capability.RecipeOutputUse;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Odyssey Industrial machine recipe using capability-based I/O.
 */
public class TopoRecipe implements Recipe<TopoRecipeInput> {

    public static final InputEntry<?>[] EMPTY_INPUTS = new InputEntry<?>[0];
    public static final OutputEntry<?>[] EMPTY_OUTPUTS = new OutputEntry<?>[0];

    private final TopoRecipeType<?> recipeType;
    private final InputEntry<?>[] inputs;
    /** Start inputs grouped by capability so shared storage is planned and committed exactly once. */
    private final InputEntry<?>[] groupedInputs;
    private final OutputEntry<?>[] outputs;
    private final OutputEntry<?>[] groupedOutputs;
    private final InputEntry<?>[] tickInputs;
    private final InputEntry<?>[] groupedTickInputs;
    private final OutputEntry<?>[] tickOutputs;
    private final OutputEntry<?>[] groupedTickOutputs;
    /** Tick input and output contents joined once by capability for pure sequential feasibility checks. */
    private final TickIoEntry<?, ?>[] groupedTickIo;
    private final int duration;
    private final List<ProductionLine> productionLines;
    private final boolean directTickIoEligible;
    /** Lazily resolved after registries bind; zero is safe because every valid content permits 1x. */
    private volatile long parallelScaleLimit;

    public enum TickIoResult {
        SUCCESS,
        INPUT_BLOCKED,
        OUTPUT_BLOCKED
    }

    /**
     * Internal per-capability aggregate of declared contents. Declaration goes through
     * capability-minted {@link RecipeInputUse}s on the builder; entries are what the builder and
     * the serializer aggregate them into, not a public contribution surface.
     */
    public record InputEntry<I>(RecipeCapability<I, ?> capability, List<I> contents) {

        public InputEntry {
            Objects.requireNonNull(capability, "InputEntry.capability");
            Objects.requireNonNull(contents, "InputEntry.contents");
            if (contents.isEmpty()) {
                throw new IllegalArgumentException(
                        "InputEntry.contents is empty for capability " + capability.id());
            }
            for (I content : contents) {
                capability.validateInput(content);
            }
            contents = List.copyOf(contents);
        }

        boolean matches(@Nullable MachineBlockEntity machine) {
            return capability.matchInput(machine, contents);
        }

        long maxParallel(@Nullable MachineBlockEntity machine, long maxParallel) {
            return capability.maxParallelByInputs(machine, contents, maxParallel);
        }

        long maxParallelScale() {
            long limit = Long.MAX_VALUE;
            for (int index = 0; index < contents.size(); index++) {
                limit = Math.min(limit, capability.maxParallelScaleInput(contents.get(index)));
            }
            return limit;
        }

        boolean handleChecked(@Nullable MachineBlockEntity machine, Transaction transaction) {
            return capability.handleInputChecked(machine, contents, transaction);
        }
    }

    public record OutputEntry<O>(RecipeCapability<?, O> capability, List<O> contents) {

        public OutputEntry {
            Objects.requireNonNull(capability, "OutputEntry.capability");
            Objects.requireNonNull(contents, "OutputEntry.contents");
            if (contents.isEmpty()) {
                throw new IllegalArgumentException(
                        "OutputEntry.contents is empty for capability " + capability.id());
            }
            for (O content : contents) {
                capability.validateOutput(content);
            }
            contents = List.copyOf(contents);
        }

        boolean matches(@Nullable MachineBlockEntity machine) {
            return capability.matchOutput(machine, contents);
        }

        boolean handleChecked(@Nullable MachineBlockEntity machine, Transaction transaction) {
            return capability.handleOutputChecked(machine, contents, transaction);
        }

        long maxParallelScale() {
            long limit = Long.MAX_VALUE;
            for (int index = 0; index < contents.size(); index++) {
                limit = Math.min(limit, capability.maxParallelScaleOutput(contents.get(index)));
            }
            return limit;
        }
    }

    public TopoRecipe(
                      TopoRecipeType<?> recipeType,
                      InputEntry<?>[] inputs,
                      OutputEntry<?>[] outputs,
                      int duration) {
        this(recipeType, inputs, outputs, EMPTY_INPUTS, EMPTY_OUTPUTS, duration, List.of());
    }

    public TopoRecipe(
                      TopoRecipeType<?> recipeType,
                      InputEntry<?>[] inputs,
                      OutputEntry<?>[] outputs,
                      InputEntry<?>[] tickInputs,
                      OutputEntry<?>[] tickOutputs,
                      int duration) {
        this(recipeType, inputs, outputs, tickInputs, tickOutputs, duration, List.of());
    }

    public TopoRecipe(
                      TopoRecipeType<?> recipeType,
                      InputEntry<?>[] inputs,
                      OutputEntry<?>[] outputs,
                      InputEntry<?>[] tickInputs,
                      OutputEntry<?>[] tickOutputs,
                      int duration,
                      List<ProductionLine> productionLines) {
        this.recipeType = Objects.requireNonNull(recipeType, "TopoRecipe.recipeType");
        this.inputs = Objects.requireNonNull(inputs, "TopoRecipe.inputs").clone();
        this.groupedInputs = groupInputsByCapability(this.inputs);
        this.outputs = Objects.requireNonNull(outputs, "TopoRecipe.outputs").clone();
        this.groupedOutputs = groupOutputsByCapability(this.outputs);
        this.tickInputs = Objects.requireNonNull(tickInputs, "TopoRecipe.tickInputs").clone();
        this.groupedTickInputs = groupInputsByCapability(this.tickInputs);
        this.tickOutputs = Objects.requireNonNull(tickOutputs, "TopoRecipe.tickOutputs").clone();
        this.groupedTickOutputs = groupOutputsByCapability(this.tickOutputs);
        this.groupedTickIo = groupTickIoByCapability(groupedTickInputs, groupedTickOutputs);
        if (duration <= 0) {
            throw new IllegalArgumentException("TopoRecipe.duration must be > 0 (was " + duration + ")");
        }
        this.duration = duration;
        this.productionLines = List.copyOf(Objects.requireNonNull(productionLines, "TopoRecipe.productionLines"));
        this.directTickIoEligible = computeDirectTickIoEligible(groupedTickIo);
    }

    /**
     * Per-active-run direct tick kernel. The array and every capability binding are allocated only when
     * the recipe starts (or its routing table is invalidated); steady working ticks reuse them.
     */
    public static final class BoundTickIoPlan {

        private final RecipeCapability.DirectTickIoBinding[] bindings;
        private final long routingRevision;

        private BoundTickIoPlan(
                                RecipeCapability.DirectTickIoBinding[] bindings,
                                long routingRevision) {
            this.bindings = bindings;
            this.routingRevision = routingRevision;
        }

        public long routingRevision() {
            return routingRevision;
        }

        /**
         * Attempts the direct path. {@code null} means a dynamic safety gate no longer holds and
         * the caller must use the original transactional implementation for this tick.
         */
        public @Nullable TickIoResult tryHandleDirect() {
            if (Transaction.getLifecycle() != Transaction.Lifecycle.NONE) {
                return null;
            }
            for (RecipeCapability.DirectTickIoBinding binding : bindings) {
                if (!binding.directReady()) {
                    return null;
                }
                if (!binding.matchesInput()) {
                    return TickIoResult.INPUT_BLOCKED;
                }
            }
            for (RecipeCapability.DirectTickIoBinding binding : bindings) {
                if (!binding.matchesOutputAfterInputs()) {
                    return TickIoResult.OUTPUT_BLOCKED;
                }
            }
            for (RecipeCapability.DirectTickIoBinding binding : bindings) {
                binding.applyInput();
            }
            for (RecipeCapability.DirectTickIoBinding binding : bindings) {
                binding.applyOutput();
            }
            return TickIoResult.SUCCESS;
        }
    }

    private static long computeParallelScaleLimit(
                                                  InputEntry<?>[] inputs,
                                                  OutputEntry<?>[] outputs,
                                                  InputEntry<?>[] tickInputs,
                                                  OutputEntry<?>[] tickOutputs) {
        long limit = Long.MAX_VALUE;
        for (InputEntry<?> entry : inputs) {
            limit = Math.min(limit, entry.maxParallelScale());
        }
        for (OutputEntry<?> entry : outputs) {
            limit = Math.min(limit, entry.maxParallelScale());
        }
        for (InputEntry<?> entry : tickInputs) {
            limit = Math.min(limit, entry.maxParallelScale());
        }
        for (OutputEntry<?> entry : tickOutputs) {
            limit = Math.min(limit, entry.maxParallelScale());
        }
        return limit;
    }

    private static boolean computeDirectTickIoEligible(TickIoEntry<?, ?>[] tickIo) {
        if (tickIo.length == 0) {
            return false;
        }
        for (TickIoEntry<?, ?> entry : tickIo) {
            if (!entry.capability().supportsDirectTickIo()) {
                return false;
            }
        }
        return true;
    }

    public TopoRecipeType<?> recipeType() {
        return recipeType;
    }

    public InputEntry<?>[] inputs() {
        return inputs.clone();
    }

    public OutputEntry<?>[] outputs() {
        return outputs.clone();
    }

    public InputEntry<?>[] tickInputs() {
        return tickInputs.clone();
    }

    public OutputEntry<?>[] tickOutputs() {
        return tickOutputs.clone();
    }

    public int duration() {
        return duration;
    }

    public List<ProductionLine> productionLines() {
        return productionLines;
    }

    /**
     * Scales only recipe duration. Pure; identity when {@code durationFactor == 1}. Used so
     * multi-scalar machines can fold several capability-specific amount modifiers without multiplying
     * duration more than once (only one modifier should call this).
     */
    public TopoRecipe withScaledDuration(double durationFactor) {
        if (durationFactor == 1.0d) {
            return this;
        }
        if (!Double.isFinite(durationFactor) || durationFactor <= 0.0d) {
            throw new IllegalArgumentException(
                    "durationFactor must be finite and > 0 (was " + durationFactor + ")");
        }
        double scaled = duration * durationFactor;
        if (!Double.isFinite(scaled) || scaled > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Scaled recipe duration exceeds int ticks: " + scaled);
        }
        int scaledDuration = Math.max(1, (int) Math.round(scaled));
        return new TopoRecipe(
                recipeType, inputs, outputs, tickInputs, tickOutputs, scaledDuration, productionLines);
    }

    /**
     * Scales contents on entries whose {@link InputEntry#capability()}/{@link OutputEntry#capability()} is
     * {@code capability} only, via {@link RecipeCapability#scaleInput}/{@link RecipeCapability#scaleOutput}. Other
     * capabilities are untouched. Duration is unchanged — pair with {@link #withScaledDuration} when a
     * full performance step is needed.
     */
    public TopoRecipe withScaledCapabilityAmounts(RecipeCapability<?, ?> capability, double inputFactor, double outputFactor) {
        Objects.requireNonNull(capability, "capability");
        if (inputFactor == 1.0d && outputFactor == 1.0d) {
            return this;
        }
        if (!Double.isFinite(inputFactor) || !Double.isFinite(outputFactor) || inputFactor <= 0.0d || outputFactor <= 0.0d) {
            throw new IllegalArgumentException(
                    "inputFactor and outputFactor must be finite and > 0 (was " + inputFactor + ", " + outputFactor + ")");
        }
        return new TopoRecipe(
                recipeType,
                scaleCapabilityInputEntries(inputs, capability, inputFactor),
                scaleCapabilityOutputEntries(outputs, capability, outputFactor),
                scaleCapabilityInputEntries(tickInputs, capability, inputFactor),
                scaleCapabilityOutputEntries(tickOutputs, capability, outputFactor),
                duration,
                productionLines);
    }

    /** {@link #withScaledCapabilityAmounts} with the same factor on that capability's inputs and outputs. */
    public TopoRecipe withScaledCapabilityAmounts(RecipeCapability<?, ?> capability, double amountFactor) {
        return withScaledCapabilityAmounts(capability, amountFactor, amountFactor);
    }

    /**
     * Duration × {@code durationFactor}, then {@code capability} amounts × {@code amountFactor} on both
     * I/O. Preferred single-capability consumer/producer step.
     */
    public TopoRecipe withScaledCapabilityPerformance(
                                                      RecipeCapability<?, ?> capability, double durationFactor, double amountFactor) {
        return withScaledDuration(durationFactor).withScaledCapabilityAmounts(capability, amountFactor);
    }

    /**
     * Conversion on two capabilities: duration, input-capability amounts × {@code inputFactor}, output-capability
     * amounts × {@code outputFactor}. Other capabilities unchanged.
     */
    public TopoRecipe withScaledCapabilityConversion(
                                                     RecipeCapability<?, ?> inputCapability,
                                                     RecipeCapability<?, ?> outputCapability,
                                                     double durationFactor,
                                                     double inputFactor,
                                                     double outputFactor) {
        Objects.requireNonNull(inputCapability, "inputCapability");
        Objects.requireNonNull(outputCapability, "outputCapability");
        return withScaledDuration(durationFactor)
                .withScaledCapabilityAmounts(inputCapability, inputFactor, 1.0d)
                .withScaledCapabilityAmounts(outputCapability, 1.0d, outputFactor);
    }

    /**
     * Parallel batch: every capability scales consumable contents × {@code parallels} via
     * {@link RecipeCapability#scaleInputForParallel}/{@link RecipeCapability#scaleOutputForParallel}; duration
     * unchanged.
     */
    public TopoRecipe withParallel(long parallels) {
        if (parallels == 1) {
            return this;
        }
        if (parallels < 1) {
            throw new IllegalArgumentException("parallels must be >= 1 (was " + parallels + ")");
        }
        return new TopoRecipe(
                recipeType,
                scaleAllInputEntriesForParallel(inputs, parallels),
                scaleAllOutputEntriesForParallel(outputs, parallels),
                scaleAllInputEntriesForParallel(tickInputs, parallels),
                scaleAllOutputEntriesForParallel(tickOutputs, parallels),
                duration,
                productionLines);
    }

    /**
     * Largest input-supported factor in {@code [0, maxParallel]}.
     *
     * <p>
     * Only one-time start inputs limit maximum parallel. Every capability receives all of its start
     * contents in one call and computes a side-effect-free quantity bound from one storage
     * snapshot. Tick inputs and all outputs are multiplied only after the factor is selected; the
     * normal start gates validate their scaled one-tick/output capacity.
     */
    public long maxParallelByInputs(@Nullable MachineBlockEntity machine, long maxParallel) {
        if (maxParallel < 1) {
            throw new IllegalArgumentException("maxParallel must be >= 1 (was " + maxParallel + ")");
        }
        long parallel = Math.min(maxParallel, parallelScaleLimit());
        for (InputEntry<?> entry : groupedInputs) {
            parallel = entry.maxParallel(machine, parallel);
            if (parallel <= 0) {
                return 0;
            }
        }
        return parallel;
    }

    private long parallelScaleLimit() {
        long cached = parallelScaleLimit;
        if (cached != 0) {
            return cached;
        }
        long resolved = computeParallelScaleLimit(inputs, outputs, tickInputs, tickOutputs);
        parallelScaleLimit = resolved;
        return resolved;
    }

    /**
     * Content units per second at the given parallel: {@code baseCount * parallel * 20 / durationTicks}.
     * Duration is in game ticks (20 tps).
     */
    public static double contentPerSecond(long baseCount, long parallel, int durationTicks) {
        if (baseCount < 0 || parallel < 1 || durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "baseCount>=0 parallel>=1 durationTicks>0 (was " + baseCount + "," + parallel + "," + durationTicks + ")");
        }
        return baseCount * (double) parallel * 20.0d / (double) durationTicks;
    }

    /**
     * Scales every entry's contents through its owning capability. Duration is unchanged. Scaling is
     * exhaustive per capability (new capabilities must implement {@link RecipeCapability#scaleInput}/
     * {@link RecipeCapability#scaleOutput} or fail to compile); there is no silent skip of unknown types.
     */
    public TopoRecipe withScaledAllContents(double factor) {
        if (factor == 1.0d) {
            return this;
        }
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("factor must be finite and > 0 (was " + factor + ")");
        }
        return new TopoRecipe(
                recipeType,
                scaleAllInputEntries(inputs, factor),
                scaleAllOutputEntries(outputs, factor),
                scaleAllInputEntries(tickInputs, factor),
                scaleAllOutputEntries(tickOutputs, factor),
                duration,
                productionLines);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static InputEntry<?>[] scaleCapabilityInputEntries(
                                                               InputEntry<?>[] entries, RecipeCapability<?, ?> capability, double factor) {
        if (factor == 1.0d || entries.length == 0) {
            return entries.clone();
        }
        InputEntry<?>[] scaled = new InputEntry<?>[entries.length];
        for (int i = 0; i < entries.length; i++) {
            InputEntry<?> entry = entries[i];
            if (entry.capability() != capability) {
                scaled[i] = entry;
                continue;
            }
            scaled[i] = scaleInputEntry(entry, factor);
        }
        return scaled;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OutputEntry<?>[] scaleCapabilityOutputEntries(
                                                                 OutputEntry<?>[] entries, RecipeCapability<?, ?> capability, double factor) {
        if (factor == 1.0d || entries.length == 0) {
            return entries.clone();
        }
        OutputEntry<?>[] scaled = new OutputEntry<?>[entries.length];
        for (int i = 0; i < entries.length; i++) {
            OutputEntry<?> entry = entries[i];
            if (entry.capability() != capability) {
                scaled[i] = entry;
                continue;
            }
            scaled[i] = scaleOutputEntry(entry, factor);
        }
        return scaled;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static InputEntry<?>[] scaleAllInputEntries(InputEntry<?>[] entries, double factor) {
        if (entries.length == 0) {
            return entries;
        }
        InputEntry<?>[] scaled = new InputEntry<?>[entries.length];
        for (int i = 0; i < entries.length; i++) {
            scaled[i] = scaleInputEntry(entries[i], factor);
        }
        return scaled;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OutputEntry<?>[] scaleAllOutputEntries(OutputEntry<?>[] entries, double factor) {
        if (entries.length == 0) {
            return entries;
        }
        OutputEntry<?>[] scaled = new OutputEntry<?>[entries.length];
        for (int i = 0; i < entries.length; i++) {
            scaled[i] = scaleOutputEntry(entries[i], factor);
        }
        return scaled;
    }

    private static InputEntry<?>[] scaleAllInputEntriesForParallel(InputEntry<?>[] entries, long factor) {
        if (entries.length == 0) {
            return entries;
        }
        InputEntry<?>[] scaled = new InputEntry<?>[entries.length];
        for (int index = 0; index < entries.length; index++) {
            scaled[index] = scaleInputEntryForParallel(entries[index], factor);
        }
        return scaled;
    }

    private static OutputEntry<?>[] scaleAllOutputEntriesForParallel(OutputEntry<?>[] entries, long factor) {
        if (entries.length == 0) {
            return entries;
        }
        OutputEntry<?>[] scaled = new OutputEntry<?>[entries.length];
        for (int index = 0; index < entries.length; index++) {
            scaled[index] = scaleOutputEntryForParallel(entries[index], factor);
        }
        return scaled;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static InputEntry<?> scaleInputEntry(InputEntry<?> entry, double factor) {
        RecipeCapability capability = entry.capability();
        List<?> contents = entry.contents();
        List<Object> next = new ArrayList<>(contents.size());
        boolean changed = false;
        for (Object content : contents) {
            Object scaledContent = capability.scaleInput(content, factor);
            next.add(scaledContent);
            changed |= scaledContent != content;
        }
        return changed ? new InputEntry(capability, next) : entry;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OutputEntry<?> scaleOutputEntry(OutputEntry<?> entry, double factor) {
        RecipeCapability capability = entry.capability();
        List<?> contents = entry.contents();
        List<Object> next = new ArrayList<>(contents.size());
        boolean changed = false;
        for (Object content : contents) {
            Object scaledContent = capability.scaleOutput(content, factor);
            next.add(scaledContent);
            changed |= scaledContent != content;
        }
        return changed ? new OutputEntry(capability, next) : entry;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static InputEntry<?> scaleInputEntryForParallel(InputEntry<?> entry, long factor) {
        RecipeCapability capability = entry.capability();
        List<?> contents = entry.contents();
        List<Object> next = new ArrayList<>(contents.size());
        boolean changed = false;
        for (Object content : contents) {
            Object scaledContent = capability.scaleInputForParallel(content, factor);
            next.add(scaledContent);
            changed |= scaledContent != content;
        }
        return changed ? new InputEntry(capability, next) : entry;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static OutputEntry<?> scaleOutputEntryForParallel(OutputEntry<?> entry, long factor) {
        RecipeCapability capability = entry.capability();
        List<?> contents = entry.contents();
        List<Object> next = new ArrayList<>(contents.size());
        boolean changed = false;
        for (Object content : contents) {
            Object scaledContent = capability.scaleOutputForParallel(content, factor);
            next.add(scaledContent);
            changed |= scaledContent != content;
        }
        return changed ? new OutputEntry(capability, next) : entry;
    }

    public boolean hasTickIo() {
        return tickInputs.length > 0 || tickOutputs.length > 0;
    }

    /**
     * Whether this recipe's per-tick IO is eligible for the direct scalar capability (no root transaction
     * per tick). Requires tick entries to exist and every tick capability to support exact direct
     * apply after its pure sequential snapshot check.
     */
    public boolean hasDirectTickIo() {
        return directTickIoEligible;
    }

    public boolean startInputsResourceVersionStable() {
        return resourceVersionStable(inputs);
    }

    public boolean startRetryResourceVersionStable() {
        return resourceVersionStable(inputs) && resourceVersionStable(tickInputs) && resourceVersionStable(tickOutputs) && resourceVersionStable(outputs);
    }

    public boolean tickIoResourceVersionStable() {
        return resourceVersionStable(tickInputs) && resourceVersionStable(tickOutputs);
    }

    public boolean outputsResourceVersionStable() {
        return resourceVersionStable(outputs);
    }

    public boolean matchInputs(@Nullable MachineBlockEntity machine) {
        for (InputEntry<?> entry : groupedInputs) {
            if (!entry.matches(machine)) {
                return false;
            }
        }
        return true;
    }

    public boolean consumeInputs(@Nullable MachineBlockEntity machine) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (InputEntry<?> entry : groupedInputs) {
                if (!entry.handleChecked(machine, transaction)) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    private record TickIoEntry<I, O>(RecipeCapability<I, O> capability, List<I> inputs, List<O> outputs) {

        private TickIoEntry {
            Objects.requireNonNull(capability, "TickIoEntry.capability");
            inputs = List.copyOf(Objects.requireNonNull(inputs, "TickIoEntry.inputs"));
            outputs = List.copyOf(Objects.requireNonNull(outputs, "TickIoEntry.outputs"));
            if (inputs.isEmpty() && outputs.isEmpty()) {
                throw new IllegalArgumentException("TickIoEntry has no contents for capability " + capability.id());
            }
        }

        boolean matchesInput(@Nullable MachineBlockEntity machine) {
            return inputs.isEmpty() || capability.matchInput(machine, inputs);
        }

        boolean matchesOutputAfterInputs(@Nullable MachineBlockEntity machine) {
            return outputs.isEmpty() || capability.matchOutputAfterInputs(machine, inputs, outputs);
        }

        RecipeCapability.@Nullable DirectTickIoBinding bindDirect(
                                                                  @Nullable MachineBlockEntity machine,
                                                                  RecipeSearchPoolId poolId) {
            return capability.bindDirectTickIo(machine, poolId, inputs, outputs);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static InputEntry<?>[] groupInputsByCapability(InputEntry<?>[] entries) {
        boolean duplicateCapability = false;
        for (int index = 0; index < entries.length && !duplicateCapability; index++) {
            for (int later = index + 1; later < entries.length; later++) {
                if (entries[index].capability() == entries[later].capability()) {
                    duplicateCapability = true;
                    break;
                }
            }
        }
        if (!duplicateCapability) {
            return entries;
        }

        Map<RecipeCapability<?, ?>, List<Object>> contentsByCapability = new IdentityHashMap<>();
        List<RecipeCapability<?, ?>> capabilityOrder = new ArrayList<>();
        for (InputEntry<?> entry : entries) {
            List<Object> contents = contentsByCapability.get(entry.capability());
            if (contents == null) {
                contents = new ArrayList<>();
                contentsByCapability.put(entry.capability(), contents);
                capabilityOrder.add(entry.capability());
            }
            contents.addAll((List) entry.contents());
        }

        InputEntry<?>[] grouped = new InputEntry<?>[capabilityOrder.size()];
        for (int index = 0; index < grouped.length; index++) {
            RecipeCapability capability = capabilityOrder.get(index);
            grouped[index] = new InputEntry(capability, contentsByCapability.get(capability));
        }
        return grouped;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static OutputEntry<?>[] groupOutputsByCapability(OutputEntry<?>[] entries) {
        boolean duplicateCapability = false;
        for (int index = 0; index < entries.length && !duplicateCapability; index++) {
            for (int later = index + 1; later < entries.length; later++) {
                if (entries[index].capability() == entries[later].capability()) {
                    duplicateCapability = true;
                    break;
                }
            }
        }
        if (!duplicateCapability) {
            return entries;
        }

        Map<RecipeCapability<?, ?>, List<Object>> contentsByCapability = new IdentityHashMap<>();
        List<RecipeCapability<?, ?>> capabilityOrder = new ArrayList<>();
        for (OutputEntry<?> entry : entries) {
            List<Object> contents = contentsByCapability.get(entry.capability());
            if (contents == null) {
                contents = new ArrayList<>();
                contentsByCapability.put(entry.capability(), contents);
                capabilityOrder.add(entry.capability());
            }
            contents.addAll((List) entry.contents());
        }

        OutputEntry<?>[] grouped = new OutputEntry<?>[capabilityOrder.size()];
        for (int index = 0; index < grouped.length; index++) {
            RecipeCapability capability = capabilityOrder.get(index);
            grouped[index] = new OutputEntry(capability, contentsByCapability.get(capability));
        }
        return grouped;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static TickIoEntry<?, ?>[] groupTickIoByCapability(
                                                               InputEntry<?>[] inputs,
                                                               OutputEntry<?>[] outputs) {
        Map<RecipeCapability<?, ?>, List<?>> inputsByCapability = new IdentityHashMap<>();
        Map<RecipeCapability<?, ?>, List<?>> outputsByCapability = new IdentityHashMap<>();
        List<RecipeCapability<?, ?>> capabilityOrder = new ArrayList<>(inputs.length + outputs.length);
        for (InputEntry<?> input : inputs) {
            inputsByCapability.put(input.capability(), input.contents());
            capabilityOrder.add(input.capability());
        }
        for (OutputEntry<?> output : outputs) {
            outputsByCapability.put(output.capability(), output.contents());
            if (!inputsByCapability.containsKey(output.capability())) {
                capabilityOrder.add(output.capability());
            }
        }

        TickIoEntry<?, ?>[] grouped = new TickIoEntry<?, ?>[capabilityOrder.size()];
        for (int index = 0; index < grouped.length; index++) {
            RecipeCapability capability = capabilityOrder.get(index);
            List<?> capabilityInputs = inputsByCapability.get(capability);
            List<?> capabilityOutputs = outputsByCapability.get(capability);
            grouped[index] = new TickIoEntry(
                    capability,
                    capabilityInputs == null ? List.of() : capabilityInputs,
                    capabilityOutputs == null ? List.of() : capabilityOutputs);
        }
        return grouped;
    }

    public boolean canEmitOutputs(@Nullable MachineBlockEntity machine) {
        for (OutputEntry<?> entry : groupedOutputs) {
            if (!entry.matches(machine)) {
                return false;
            }
        }
        return true;
    }

    public boolean emitOutputs(@Nullable MachineBlockEntity machine) {
        try (Transaction transaction = Transaction.openRoot()) {
            for (OutputEntry<?> entry : groupedOutputs) {
                if (!entry.handleChecked(machine, transaction)) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    public boolean matchTickInputs(@Nullable MachineBlockEntity machine) {
        for (InputEntry<?> entry : groupedTickInputs) {
            if (!entry.matches(machine)) {
                return false;
            }
        }
        return true;
    }

    public boolean consumeTickInputs(@Nullable MachineBlockEntity machine) {
        if (tickInputs.length == 0) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (InputEntry<?> entry : groupedTickInputs) {
                if (!entry.handleChecked(machine, transaction)) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    public boolean canEmitTickOutputs(@Nullable MachineBlockEntity machine) {
        for (OutputEntry<?> entry : groupedTickOutputs) {
            if (!entry.matches(machine)) {
                return false;
            }
        }
        return true;
    }

    public boolean emitTickOutputs(@Nullable MachineBlockEntity machine) {
        if (tickOutputs.length == 0) {
            return true;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (OutputEntry<?> entry : groupedTickOutputs) {
                if (!entry.handleChecked(machine, transaction)) {
                    return false;
                }
            }
            transaction.commit();
            return true;
        }
    }

    public TickIoResult checkTickIo(@Nullable MachineBlockEntity machine) {
        if (tickInputs.length == 0 && tickOutputs.length == 0) {
            return TickIoResult.SUCCESS;
        }
        return checkGroupedTickIo(machine);
    }

    private TickIoResult checkGroupedTickIo(@Nullable MachineBlockEntity machine) {
        for (TickIoEntry<?, ?> entry : groupedTickIo) {
            if (!entry.matchesInput(machine)) {
                return TickIoResult.INPUT_BLOCKED;
            }
        }
        for (TickIoEntry<?, ?> entry : groupedTickIo) {
            if (!entry.matchesOutputAfterInputs(machine)) {
                return TickIoResult.OUTPUT_BLOCKED;
            }
        }
        return TickIoResult.SUCCESS;
    }

    public TickIoResult handleTickIo(@Nullable MachineBlockEntity machine) {
        return handleTickIoTransactional(machine);
    }

    /** Full generic fallback used when a machine-bound direct plan cannot be proven safe. */
    public TickIoResult handleTickIoTransactional(@Nullable MachineBlockEntity machine) {
        if (tickInputs.length == 0 && tickOutputs.length == 0) {
            return TickIoResult.SUCCESS;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            for (InputEntry<?> entry : groupedTickInputs) {
                if (!entry.handleChecked(machine, transaction)) {
                    return TickIoResult.INPUT_BLOCKED;
                }
            }
            for (OutputEntry<?> entry : groupedTickOutputs) {
                if (!entry.handleChecked(machine, transaction)) {
                    return TickIoResult.OUTPUT_BLOCKED;
                }
            }
            transaction.commit();
            return TickIoResult.SUCCESS;
        }
    }

    /**
     * Resolves and freezes a direct tick-I/O plan for one active run. Unsupported capabilities and any
     * third-party/non-direct handler return {@code null}; callers retain the full transactional
     * path as a compatibility fallback.
     */
    public @Nullable BoundTickIoPlan bindDirectTickIo(
                                                      @Nullable MachineBlockEntity machine,
                                                      RecipeSearchPoolId poolId,
                                                      long routingRevision) {
        Objects.requireNonNull(poolId, "recipe pool id");
        if (machine == null || !directTickIoEligible) {
            return null;
        }
        RecipeCapability.DirectTickIoBinding[] bindings = new RecipeCapability.DirectTickIoBinding[groupedTickIo.length];
        IdentityHashMap<Object, RecipeCapability.DirectTickIoBinding> bindingOwners = new IdentityHashMap<>();
        for (int index = 0; index < groupedTickIo.length; index++) {
            RecipeCapability.DirectTickIoBinding binding = groupedTickIo[index].bindDirect(machine, poolId);
            if (binding == null || !binding.directReady() || aliasesAnotherBinding(bindingOwners, binding, binding.inputIdentity()) || aliasesAnotherBinding(bindingOwners, binding, binding.outputIdentity())) {
                return null;
            }
            bindings[index] = binding;
        }
        return new BoundTickIoPlan(bindings, routingRevision);
    }

    private static boolean aliasesAnotherBinding(
                                                 IdentityHashMap<Object, RecipeCapability.DirectTickIoBinding> owners,
                                                 RecipeCapability.DirectTickIoBinding binding,
                                                 @Nullable Object identity) {
        if (identity == null) {
            return false;
        }
        RecipeCapability.DirectTickIoBinding previous = owners.putIfAbsent(identity, binding);
        return previous != null && previous != binding;
    }

    private static boolean resourceVersionStable(InputEntry<?>[] entries) {
        for (InputEntry<?> entry : entries) {
            if (!entry.capability().isStableForResourceContentVersion()) {
                return false;
            }
        }
        return true;
    }

    private static boolean resourceVersionStable(OutputEntry<?>[] entries) {
        for (OutputEntry<?> entry : entries) {
            if (!entry.capability().isStableForResourceContentVersion()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean matches(TopoRecipeInput input, @NonNull Level level) {
        return matchInputs(input.machine());
    }

    @Override
    public ItemStack assemble(TopoRecipeInput input) {
        throw new UnsupportedOperationException(
                "TopoRecipe does not support vanilla item assembly; use capability outputs instead.");
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return java.util.List.of();
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return recipeType.recipeBookCategory();
    }

    @Override
    public RecipeSerializer<? extends Recipe<TopoRecipeInput>> getSerializer() {
        return recipeType.serializer();
    }

    @Override
    public RecipeType<? extends Recipe<TopoRecipeInput>> getType() {
        return recipeType.vanillaType();
    }

    /**
     * Fluent builder for base and subclassed Topo recipes.
     */
    public static class Builder<R extends TopoRecipe> {

        protected final TopoRecipeType<R> recipeType;
        protected final String recipeName;
        private final Map<RecipeCapability<?, ?>, List<Object>> inputs = new LinkedHashMap<>();
        private final Map<RecipeCapability<?, ?>, List<Object>> outputs = new LinkedHashMap<>();
        private final Map<RecipeCapability<?, ?>, List<Object>> tickInputs = new LinkedHashMap<>();
        private final Map<RecipeCapability<?, ?>, List<Object>> tickOutputs = new LinkedHashMap<>();
        private final LinkedHashSet<ProductionLine> productionLines = new LinkedHashSet<>();
        private int duration = 100;
        private boolean saved;
        /** 与导入配方冲突时剔除导入项（GTCEu 无同名 API；Topo 显式冲突纪律）。 */
        private boolean replacesImported;

        protected Builder(TopoRecipeType<R> recipeType, String recipeName) {
            this.recipeType = Objects.requireNonNull(recipeType, "recipe type");
            this.recipeName = Objects.requireNonNull(recipeName, "recipe name");
        }

        /**
         * 声明本配方替换冲突的<strong>导入</strong>配方（{@link TopoRecipeType#importRecipesFrom}）。
         * 索引构建时剔除物品起始输入冲突的导入项；未标记却冲突则失败。
         */
        public Builder<R> replacesImported() {
            this.replacesImported = true;
            return this;
        }

        public Builder<R> input(RecipeInputUse<?> use) {
            appendContent(inputs, use.capability(), use.content());
            return this;
        }

        public Builder<R> output(RecipeOutputUse<?> use) {
            appendContent(outputs, use.capability(), use.content());
            return this;
        }

        public Builder<R> tickInput(RecipeInputUse<?> use) {
            appendContent(tickInputs, use.capability(), use.content());
            return this;
        }

        public Builder<R> tickOutput(RecipeOutputUse<?> use) {
            appendContent(tickOutputs, use.capability(), use.content());
            return this;
        }

        public Builder<R> duration(int duration) {
            if (duration <= 0) {
                throw new IllegalArgumentException("Recipe duration must be > 0 (was " + duration + ")");
            }
            this.duration = duration;
            return this;
        }

        public Builder<R> productionLine(ProductionLine line) {
            productionLines.add(Objects.requireNonNull(line, "production line"));
            return this;
        }

        public Builder<R> productionLines(ProductionLine... lines) {
            Objects.requireNonNull(lines, "production lines");
            for (ProductionLine line : lines) {
                productionLine(line);
            }
            return this;
        }

        public R buildRecipe() {
            return recipeType.createRecipe(
                    toInputEntries(inputs),
                    toOutputEntries(outputs),
                    toInputEntries(tickInputs),
                    toOutputEntries(tickOutputs),
                    duration,
                    List.copyOf(productionLines));
        }

        public R save() {
            if (saved) {
                throw new IllegalStateException("Recipe " + recipeType.id() + " / " + recipeName + " was already saved");
            }
            saved = true;
            R recipe = buildRecipe();
            recipeType.addRecipe(recipeName, recipe);
            if (replacesImported) {
                recipeType.markReplacesImported(recipeName);
            }
            return recipe;
        }

        private static void appendContent(
                                          Map<RecipeCapability<?, ?>, List<Object>> target,
                                          RecipeCapability<?, ?> capability,
                                          Object content) {
            Objects.requireNonNull(capability, "capability");
            Objects.requireNonNull(content, "content");
            target.computeIfAbsent(capability, unused -> new ArrayList<>()).add(content);
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static InputEntry<?>[] toInputEntries(Map<RecipeCapability<?, ?>, List<Object>> map) {
            InputEntry<?>[] entries = new InputEntry<?>[map.size()];
            int index = 0;
            for (Map.Entry<RecipeCapability<?, ?>, List<Object>> entry : map.entrySet()) {
                entries[index++] = new InputEntry(entry.getKey(), entry.getValue());
            }
            return entries;
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private static OutputEntry<?>[] toOutputEntries(Map<RecipeCapability<?, ?>, List<Object>> map) {
            OutputEntry<?>[] entries = new OutputEntry<?>[map.size()];
            int index = 0;
            for (Map.Entry<RecipeCapability<?, ?>, List<Object>> entry : map.entrySet()) {
                entries[index++] = new OutputEntry(entry.getKey(), entry.getValue());
            }
            return entries;
        }
    }
}
