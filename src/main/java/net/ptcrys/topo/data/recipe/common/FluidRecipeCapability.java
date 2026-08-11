package net.ptcrys.topo.data.recipe.common;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;
import net.ptcrys.topo.api.machine.resource.PlayerAccess;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topo.api.machine.resource.ResourceHandlerLongOps;
import net.ptcrys.topo.api.machine.resource.ResourcePort;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.machine.ui.PortUiHighlight;
import net.ptcrys.topo.api.recipe.capability.RecipeInputUse;
import net.ptcrys.topo.api.recipe.capability.RecipeOutputUse;
import net.ptcrys.topo.api.recipe.capability.SlottedRecipeCapability;
import net.ptcrys.topo.api.recipe.content.TopoFluidIngredient;
import net.ptcrys.topo.api.recipe.search.RecipeSearchKeyRegistry;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePortMetadata;

import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.RangedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class FluidRecipeCapability
                                         extends SlottedRecipeCapability<TopoFluidIngredient, TopoFluidIngredient, FluidResource> {

    private static final String RECIPE_FLUID_SLOT_ID = "topo_recipe_fluid_slot";
    private static final int MAX_HANDLER_SLOTS = 1 << 16;
    private static final int MAX_PLANNER_INPUTS = 1 << 12;
    private static final int MAX_PLANNER_RESOURCES = 1 << 14;
    private static final int MAX_JOINT_IO_CELLS = 1 << 18;
    private static final int MAX_PLANNER_REENTRANCY = 8;
    private static final int SMALL_RESOURCE_LOOKUP_LIMIT = 16;
    private static final ThreadLocal<FluidInputPlannerPool> INPUT_PLANNERS = ThreadLocal.withInitial(FluidInputPlannerPool::new);

    private final MachineResourceType<FluidResource> resourceType;

    public FluidRecipeCapability(MachineResourceType<FluidResource> resourceType) {
        super(
                java.util.Objects.requireNonNull(resourceType, "resource type").id(),
                TopoFluidIngredient.class,
                TopoFluidIngredient.class,
                TopoFluidIngredient.CODEC,
                TopoFluidIngredient.CODEC,
                TopoFluidIngredient.STREAM_CODEC,
                TopoFluidIngredient.STREAM_CODEC);
        this.resourceType = resourceType;
    }

    @Override
    public MachineResourceType<FluidResource> resourceType() {
        return resourceType;
    }

    /**
     * 铸造：注册期安全的流体输入 —— {@link FluidStackTemplate} 不要求 holder 数据组件已绑定，
     * 配方可在 mod 构造期 / datagen 期注册。
     */
    public RecipeInputUse<TopoFluidIngredient> in(Fluid fluid, long amount) {
        validateAmount(amount);
        return inputUse(new TopoFluidIngredient(new FluidStackTemplate(fluid, 1), amount));
    }

    /** Delayed fluid input, for material fluids that are registered by RegistryLib later in bootstrap. */
    public RecipeInputUse<TopoFluidIngredient> in(Supplier<? extends Fluid> fluid, long amount) {
        Objects.requireNonNull(fluid, "fluid");
        validateAmount(amount);
        return inputUse(TopoFluidIngredient.lazy(
                () -> new FluidStackTemplate(fluid.get(), 1),
                amount,
                new LazyFluidKey(fluid, amount)));
    }

    /** 铸造：实时 {@link FluidStack} 输入（仅运行期上下文使用）。 */
    public RecipeInputUse<TopoFluidIngredient> in(FluidStack stack) {
        return inputUse(new TopoFluidIngredient(stack));
    }

    /** 铸造：注册期安全的流体输出；见 {@link #in(Fluid, int)}。 */
    public RecipeOutputUse<TopoFluidIngredient> out(Fluid fluid, long amount) {
        validateAmount(amount);
        return outputUse(new TopoFluidIngredient(new FluidStackTemplate(fluid, 1), amount));
    }

    /** Delayed fluid output, for material fluids that are registered by RegistryLib later in bootstrap. */
    public RecipeOutputUse<TopoFluidIngredient> out(Supplier<? extends Fluid> fluid, long amount) {
        Objects.requireNonNull(fluid, "fluid");
        validateAmount(amount);
        return outputUse(TopoFluidIngredient.lazy(
                () -> new FluidStackTemplate(fluid.get(), 1),
                amount,
                new LazyFluidKey(fluid, amount)));
    }

    /** 铸造：实时 {@link FluidStack} 输出。 */
    public RecipeOutputUse<TopoFluidIngredient> out(FluidStack stack) {
        return outputUse(new TopoFluidIngredient(stack));
    }

    private static void validateAmount(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("Fluid recipe amount must be a positive long mB value (was " + amount + ")");
        }
    }

    @Override
    public boolean matchInput(@Nullable MachineBlockEntity machine, List<TopoFluidIngredient> contents) {
        ResourceHandler<FluidResource> handler = fluidHandler(machine, RecipeRole.INPUT);
        return maxParallelByInputs(handler, contents, 1) > 0;
    }

    @Override
    public long maxParallelByInputs(
                                    @Nullable MachineBlockEntity machine,
                                    List<TopoFluidIngredient> contents,
                                    long maxParallel) {
        return maxParallelByInputs(fluidHandler(machine, RecipeRole.INPUT), contents, maxParallel);
    }

    @Override
    public boolean matchOutput(@Nullable MachineBlockEntity machine, List<TopoFluidIngredient> contents) {
        ResourceHandler<FluidResource> handler = fluidHandler(machine, RecipeRole.OUTPUT);
        return handler == null ? contents.isEmpty() : canInsertOutputs(handler, contents);
    }

    @Override
    public boolean matchOutputAfterInputs(
                                          @Nullable MachineBlockEntity machine,
                                          List<TopoFluidIngredient> inputs,
                                          List<TopoFluidIngredient> outputs) {
        if (outputs.isEmpty()) {
            return true;
        }
        ResourceHandler<FluidResource> outputHandler = fluidHandler(machine, RecipeRole.OUTPUT);
        if (outputHandler == null) {
            return false;
        }
        if (inputs.isEmpty()) {
            return canInsertOutputs(outputHandler, outputs);
        }
        ResourceHandler<FluidResource> inputHandler = fluidHandler(machine, RecipeRole.INPUT);
        return inputHandler != null && matchOutputAfterInputs(inputHandler, inputs, outputHandler, outputs);
    }

    /** Pure handler-level joint snapshot for routers that already resolved both recipe roles. */
    public static boolean matchOutputAfterInputs(
                                                 ResourceHandler<FluidResource> inputHandler,
                                                 List<TopoFluidIngredient> inputs,
                                                 ResourceHandler<FluidResource> outputHandler,
                                                 List<TopoFluidIngredient> outputs) {
        Objects.requireNonNull(inputHandler, "fluid input handler");
        Objects.requireNonNull(inputs, "fluid tick inputs");
        Objects.requireNonNull(outputHandler, "fluid output handler");
        Objects.requireNonNull(outputs, "fluid tick outputs");
        int inputSlots = inputHandler.size();
        int outputSlots = outputHandler.size();
        if (inputSlots < 0 || inputSlots > MAX_HANDLER_SLOTS || outputSlots < 0 || outputSlots > MAX_HANDLER_SLOTS || (long) inputs.size() * inputSlots > MAX_JOINT_IO_CELLS) {
            return false;
        }

        FluidResource[] inputResources = new FluidResource[inputSlots];
        long[] inputAmounts = new long[inputSlots];
        long[] extractedBySlot = new long[inputSlots];
        for (int slot = 0; slot < inputSlots; slot++) {
            FluidResource resource = inputHandler.getResource(slot);
            long amount = inputHandler.getAmountAsLong(slot);
            if (resource == null || amount < 0L) {
                return false;
            }
            inputResources[slot] = resource;
            inputAmounts[slot] = amount;
        }
        for (TopoFluidIngredient input : inputs) {
            if (input == null || input.amount() <= 0L) {
                return false;
            }
            FluidResource resource = input.resource();
            long remaining = input.amount();
            for (int slot = 0; slot < inputSlots && remaining > 0L; slot++) {
                if (!resource.equals(inputResources[slot])) {
                    continue;
                }
                long available = inputAmounts[slot] - extractedBySlot[slot];
                long extracted = Math.min(available, remaining);
                extractedBySlot[slot] += extracted;
                remaining -= extracted;
            }
            if (remaining != 0L) {
                return false;
            }
        }

        int extractedSlots = 0;
        for (long extracted : extractedBySlot) {
            if (extracted > 0L) {
                extractedSlots++;
            }
        }
        if ((long) extractedSlots * outputSlots > MAX_JOINT_IO_CELLS) {
            return false;
        }

        FluidResource[] outputResources = new FluidResource[outputSlots];
        long[] outputAmounts = new long[outputSlots];
        if (!snapshotOutputSlots(outputHandler, outputResources, outputAmounts)) {
            return false;
        }
        for (int outputSlot = 0; outputSlot < outputSlots; outputSlot++) {
            long released = 0L;
            for (int inputSlot = 0; inputSlot < inputSlots; inputSlot++) {
                long extracted = extractedBySlot[inputSlot];
                if (extracted == 0L || !ResourceHandlerLongOps.sameStorageSlot(
                        inputHandler, inputSlot, outputHandler, outputSlot)) {
                    continue;
                }
                if (!inputResources[inputSlot].equals(outputResources[outputSlot]) || extracted > outputAmounts[outputSlot] - released) {
                    return false;
                }
                released += extracted;
            }
            if (released > 0L) {
                outputAmounts[outputSlot] -= released;
                if (outputAmounts[outputSlot] == 0L) {
                    outputResources[outputSlot] = FluidResource.EMPTY;
                }
            }
        }
        return canInsertOutputs(outputHandler, outputs, outputResources, outputAmounts);
    }

    @Override
    public boolean handleInputChecked(
                                      @Nullable MachineBlockEntity machine,
                                      List<TopoFluidIngredient> contents,
                                      Transaction transaction) {
        ResourceHandler<FluidResource> handler = fluidHandler(machine, RecipeRole.INPUT);
        return handler != null && consumeFromHandler(handler, contents, transaction);
    }

    @Override
    public boolean handleOutputChecked(
                                       @Nullable MachineBlockEntity machine,
                                       List<TopoFluidIngredient> contents,
                                       Transaction transaction) {
        ResourceHandler<FluidResource> handler = fluidHandler(machine, RecipeRole.OUTPUT);
        return handler != null && insertIntoHandler(handler, contents, transaction);
    }

    @Override
    public TopoFluidIngredient scaleInput(TopoFluidIngredient content, double factor) {
        return scaleFluid(content, factor);
    }

    @Override
    public TopoFluidIngredient scaleOutput(TopoFluidIngredient content, double factor) {
        return scaleFluid(content, factor);
    }

    private static TopoFluidIngredient scaleFluid(TopoFluidIngredient content, double factor) {
        Objects.requireNonNull(content, "fluid content");
        return content.withScaledAmount(factor);
    }

    @Override
    public TopoFluidIngredient scaleInputForParallel(TopoFluidIngredient content, long factor) {
        return Objects.requireNonNull(content, "fluid input").withParallelAmount(factor);
    }

    @Override
    public TopoFluidIngredient scaleOutputForParallel(TopoFluidIngredient content, long factor) {
        return Objects.requireNonNull(content, "fluid output").withParallelAmount(factor);
    }

    @Override
    public long maxParallelScaleInput(TopoFluidIngredient content) {
        return Long.MAX_VALUE / Objects.requireNonNull(content, "fluid input").amount();
    }

    @Override
    public long maxParallelScaleOutput(TopoFluidIngredient content) {
        return Long.MAX_VALUE / Objects.requireNonNull(content, "fluid output").amount();
    }

    @Override
    public boolean hasAnyContent(@Nullable MachineBlockEntity machine) {
        ResourceHandler<FluidResource> handler = fluidHandler(machine, RecipeRole.INPUT);
        if (handler == null) {
            return false;
        }
        for (int index = 0; index < handler.size(); index++) {
            FluidResource resource = handler.getResource(index);
            if (!resource.isEmpty() && handler.getAmountAsLong(index) > 0L) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isStableForResourceContentVersion() {
        // Optimization contract: fluid recipe checks read and mutate only fluid resource handlers.
        // ResourcePort bumps the machine resource version for every committed fluid change,
        // so failed-search and retry caches can safely key fluid predicates on that version.
        return true;
    }

    @Override
    public Set<?> indexKeys(TopoFluidIngredient content) {
        // Optimization: fluid inputs use the exact FluidResource as the search key.
        // Principle: Topo fluid matching is component-aware, so the index should prune by the same
        // fluid+component identity and leave only amount/transaction details to precise matching.
        return Set.of(content.resource());
    }

    @Override
    public long inputAmount(TopoFluidIngredient content) {
        return content.amount();
    }

    @Override
    public void extractMachineKeys(
                                   @Nullable MachineBlockEntity machine,
                                   RecipeSearchKeyRegistry registry,
                                   Int2LongMap out) {
        ResourceHandler<FluidResource> handler = fluidHandler(machine, RecipeRole.INPUT);
        if (handler == null) {
            return;
        }
        int size = handler.size();
        for (int index = 0; index < size; index++) {
            FluidResource resource = handler.getResource(index);
            if (resource.isEmpty()) {
                continue;
            }
            long amount = handler.getAmountAsLong(index);
            if (amount <= 0L) {
                continue;
            }
            int keyId = registry.idOf(this, resource);
            if (keyId >= 0) {
                out.mergeLong(keyId, amount, FluidRecipeCapability::saturatedAdd);
            }
        }
    }

    @Override
    public boolean contributesIndexKeys() {
        return true;
    }

    @Override
    public @NonNull UIElement createPreviewInputSlotWidget(@Nullable TopoFluidIngredient input) {
        return previewFluid(IngredientIO.INPUT, input);
    }

    @Override
    public @NonNull UIElement createPreviewOutputSlotWidget(@Nullable TopoFluidIngredient output) {
        return previewFluid(IngredientIO.OUTPUT, output);
    }

    private static UIElement previewFluid(IngredientIO io, @Nullable TopoFluidIngredient ingredient) {
        FluidSlot slot = previewSlot();
        if (ingredient == null) {
            return slot;
        }
        FluidStack fluid = ingredient.fluid();
        if (fluid.isEmpty()) {
            return slot;
        }
        slot.setFluid(fluid);
        Supplier<Stream<FluidStack>> allFluids = () -> Stream.of(fluid);
        FluidSlot.JEISupport.recipeIngredient(slot, io, allFluids);
        FluidSlot.JEISupport.recipeSlot(slot, allFluids);
        return slot;
    }

    private static FluidSlot previewSlot() {
        FluidSlot slot = MachineUiComponentTemplate.INSTANCE.createFluidSlot();
        slot.setId(RECIPE_FLUID_SLOT_ID);
        slot.slotStyle(style -> style
                .slotOverlay(MachineUiComponentStyle.INSTANCE.fluidSlotOverlayTexture())
                .showSlotOverlayOnlyEmpty(true));
        return slot;
    }

    @Override
    public @NonNull UIElement createLiveSlotWidget(ResourcePort<?, FluidResource> storage, int slot) {
        PlayerAccess access = storage.playerSlotAccess();
        // LDLib2 26.1.2.13 的 FluidSlot.tryClickContainer 用绑定的 tank 下标去读手持物品的单槽流体
        // handler（FluidSlot.java:390），直接绑下标 >= 1 的槽位会让每次点击都越界吞掉；绑单槽
        // ranged 视图把它眼中的下标钉在 0。
        FluidSlot fluidSlot = MachineUiComponentTemplate.INSTANCE.createFluidSlot();
        fluidSlot
                .bind(
                        RangedResourceHandler.ofSingleIndex(
                                (ResourceHandler<FluidResource>) storage.handler(), slot),
                        0)
                .setAllowClickDrained(access.canPlace())
                .setAllowClickFilled(access.canTake());
        fluidSlot.setId("topo_live_fluid_slot_" + slot);
        // side-IO 卡标题悬浮时按端口归属高亮本槽。
        PortUiHighlight.tag(fluidSlot, storage.id());
        // 空罐显示流体剪影提示该槽用途。
        fluidSlot.slotStyle(style -> style
                .slotOverlay(MachineUiComponentStyle.INSTANCE.fluidSlotOverlayTexture())
                .showSlotOverlayOnlyEmpty(true));
        return fluidSlot;
    }

    @Override
    public int countPreviewSlots(ResourcePortMetadata port) {
        return port instanceof FluidResourcePortMetadata fluidPort ? fluidPort.tanks() : 0;
    }

    private @Nullable ResourceHandler<FluidResource> fluidHandler(
                                                                  @Nullable MachineBlockEntity machine,
                                                                  RecipeRole io) {
        return machine == null ? null : machine.machineComponents().resources().recipeSide().handler(resourceType, io);
    }

    public static long maxParallelByInputs(
                                           @Nullable ResourceHandler<FluidResource> handler,
                                           List<TopoFluidIngredient> contents,
                                           long maxParallel) {
        if (maxParallel < 1) {
            throw new IllegalArgumentException("maxParallel must be >= 1 (was " + maxParallel + ")");
        }
        if (contents.isEmpty()) {
            return maxParallel;
        }
        if (handler == null) {
            return 0;
        }
        FluidInputPlannerPool planners = INPUT_PLANNERS.get();
        FluidInputPlannerScratch planner = planners.acquire();
        if (planner == null) {
            return 0L;
        }
        try {
            return planner.plan(handler, contents, maxParallel);
        } finally {
            planners.release(planner);
        }
    }

    /**
     * Handler callbacks may re-enter recipe planning. A bounded per-thread pool keeps normal calls
     * allocation-free without allowing corrupt recursion to retain unbounded scratch state.
     */
    private static final class FluidInputPlannerPool {

        private final FluidInputPlannerScratch primary = new FluidInputPlannerScratch();
        private @Nullable FluidInputPlannerScratch @Nullable [] nested;
        private int depth;

        @Nullable
        FluidInputPlannerScratch acquire() {
            int leaseDepth = depth;
            if (leaseDepth >= MAX_PLANNER_REENTRANCY) {
                return null;
            }
            @Nullable
            FluidInputPlannerScratch planner;
            if (leaseDepth == 0) {
                planner = primary;
            } else {
                int nestedIndex = leaseDepth - 1;
                if (nested == null) {
                    nested = new FluidInputPlannerScratch[2];
                } else if (nestedIndex >= nested.length) {
                    nested = Arrays.copyOf(
                            nested,
                            grownSize(nested.length, nestedIndex + 1, MAX_PLANNER_REENTRANCY - 1));
                }
                planner = nested[nestedIndex];
                if (planner == null) {
                    planner = new FluidInputPlannerScratch();
                    nested[nestedIndex] = planner;
                }
            }
            depth = leaseDepth + 1;
            return planner;
        }

        void release(FluidInputPlannerScratch planner) {
            int leaseDepth = depth - 1;
            if (leaseDepth < 0) {
                throw new IllegalStateException("Fluid input planner released without an active lease");
            }
            @Nullable
            FluidInputPlannerScratch expected = leaseDepth == 0 ? primary : nested == null ? null : nested[leaseDepth - 1];
            if (planner != expected) {
                throw new IllegalStateException("Fluid input planner leases must be released in LIFO order");
            }
            depth = leaseDepth;
            planner.onLeaseReleased();
        }
    }

    /** Exact-fluid planner: one handler snapshot plus one demand pass, with no transactions. */
    private static final class FluidInputPlannerScratch {

        private static final int INITIAL_RESOURCE_CAPACITY = 16;
        private static final int RETAINED_RESOURCE_CAPACITY = 1 << 10;
        private static final int SMALL_RELEASES_BEFORE_SHRINK = 64;

        private FluidResource[] resources = new FluidResource[INITIAL_RESOURCE_CAPACITY];
        private long[] supplies = new long[INITIAL_RESOURCE_CAPACITY];
        private long[] demands = new long[INITIAL_RESOURCE_CAPACITY];
        private @Nullable Object2IntOpenHashMap<FluidResource> resourceIndexes;
        private int resourceCount;
        private int smallReleaseCount;
        private boolean resourcesIndexed;

        long plan(
                  ResourceHandler<FluidResource> handler,
                  List<TopoFluidIngredient> contents,
                  long maxParallel) {
            int contentCount = contents.size();
            if (contentCount > MAX_PLANNER_INPUTS) {
                return 0L;
            }
            int slotCount = handler.size();
            if (slotCount < 0 || slotCount > MAX_HANDLER_SLOTS) {
                return 0L;
            }

            int previousResourceCount = resourceCount;
            resourceCount = 0;
            resourcesIndexed = false;
            boolean completed = false;
            try {
                if (!snapshot(handler, slotCount) || !collectDemands(contents, contentCount)) {
                    return 0L;
                }
                long parallel = maxParallel;
                for (int index = 0; index < resourceCount; index++) {
                    long required = demands[index];
                    if (required == 0L) {
                        continue;
                    }
                    parallel = Math.min(parallel, supplies[index] / required);
                    if (parallel == 0L) {
                        return 0L;
                    }
                }
                completed = true;
                return parallel;
            } finally {
                if (completed) {
                    if (resourceCount < previousResourceCount) {
                        Arrays.fill(resources, resourceCount, previousResourceCount, null);
                    }
                } else {
                    Arrays.fill(resources, 0, Math.max(resourceCount, previousResourceCount), null);
                    resourceCount = 0;
                }
            }
        }

        void onLeaseReleased() {
            if (resources.length <= RETAINED_RESOURCE_CAPACITY) {
                smallReleaseCount = 0;
                return;
            }
            if (resourceCount > RETAINED_RESOURCE_CAPACITY) {
                smallReleaseCount = 0;
                return;
            }
            if (++smallReleaseCount < SMALL_RELEASES_BEFORE_SHRINK) {
                return;
            }
            resources = new FluidResource[INITIAL_RESOURCE_CAPACITY];
            supplies = new long[INITIAL_RESOURCE_CAPACITY];
            demands = new long[INITIAL_RESOURCE_CAPACITY];
            resourceIndexes = null;
            resourceCount = 0;
            smallReleaseCount = 0;
            resourcesIndexed = false;
        }

        private boolean snapshot(ResourceHandler<FluidResource> handler, int slotCount) {
            for (int slot = 0; slot < slotCount; slot++) {
                FluidResource resource = handler.getResource(slot);
                long amount = handler.getAmountAsLong(slot);
                if (resource == null || amount < 0L) {
                    return false;
                }
                if (resource.isEmpty() || amount == 0L) {
                    continue;
                }
                int index = resourceIndex(resource);
                if (index < 0) {
                    index = appendResource(resource);
                    if (index < 0) {
                        return false;
                    }
                }
                supplies[index] = saturatedAdd(supplies[index], amount);
            }
            return true;
        }

        private boolean collectDemands(List<TopoFluidIngredient> contents, int contentCount) {
            for (int content = 0; content < contentCount; content++) {
                TopoFluidIngredient ingredient = contents.get(content);
                if (ingredient == null) {
                    return false;
                }
                long amount = ingredient.amount();
                FluidResource resource = ingredient.resource();
                if (amount <= 0L || resource == null || resource.isEmpty()) {
                    return false;
                }
                int index = resourceIndex(resource);
                if (index < 0) {
                    index = appendResource(resource);
                    if (index < 0) {
                        return false;
                    }
                }
                if (demands[index] > Long.MAX_VALUE - amount) {
                    return false;
                }
                demands[index] += amount;
            }
            return true;
        }

        private int appendResource(FluidResource resource) {
            if (resourceCount >= MAX_PLANNER_RESOURCES) {
                return -1;
            }
            ensureResourceCapacity(resourceCount + 1);
            int index = resourceCount++;
            resources[index] = resource;
            supplies[index] = 0L;
            demands[index] = 0L;
            if (resourcesIndexed) {
                Objects.requireNonNull(resourceIndexes).put(resource, index);
            }
            return index;
        }

        private int resourceIndex(FluidResource resource) {
            if (!resourcesIndexed) {
                if (resourceCount <= SMALL_RESOURCE_LOOKUP_LIMIT) {
                    for (int index = 0; index < resourceCount; index++) {
                        if (resource.equals(resources[index])) {
                            return index;
                        }
                    }
                    return -1;
                }
                indexResources();
            }
            return Objects.requireNonNull(resourceIndexes).getInt(resource);
        }

        private void indexResources() {
            Object2IntOpenHashMap<FluidResource> indexes = resourceIndexes;
            if (indexes == null) {
                indexes = new Object2IntOpenHashMap<>(resourceCount);
                indexes.defaultReturnValue(-1);
                resourceIndexes = indexes;
            } else {
                indexes.clear();
            }
            for (int index = 0; index < resourceCount; index++) {
                indexes.put(resources[index], index);
            }
            resourcesIndexed = true;
        }

        private void ensureResourceCapacity(int required) {
            if (required <= resources.length) {
                return;
            }
            int size = grownSize(resources.length, required, MAX_PLANNER_RESOURCES);
            resources = Arrays.copyOf(resources, size);
            supplies = Arrays.copyOf(supplies, size);
            demands = Arrays.copyOf(demands, size);
        }
    }

    private static int grownSize(int current, int required, int maximum) {
        if (required < 0 || required > maximum) {
            throw new IllegalArgumentException(
                    "Required scratch capacity exceeds hard limit: " + required + " > " + maximum);
        }
        int size = Math.max(1, current);
        while (size < required) {
            size = size > maximum / 2 ? maximum : size << 1;
        }
        return size;
    }

    private static long saturatedAdd(long left, long right) {
        return right > Long.MAX_VALUE - left ? Long.MAX_VALUE : left + right;
    }

    private static boolean consumeFromHandler(
                                              ResourceHandler<FluidResource> handler,
                                              List<TopoFluidIngredient> contents,
                                              Transaction transaction) {
        for (TopoFluidIngredient ingredient : contents) {
            if (!consumeOne(handler, ingredient, transaction)) {
                return false;
            }
        }
        return true;
    }

    private static boolean consumeOne(
                                      ResourceHandler<FluidResource> handler,
                                      TopoFluidIngredient ingredient,
                                      Transaction transaction) {
        long requested = ingredient.amount();
        return ResourceHandlerLongOps.extract(
                handler, ingredient.resource(), requested, transaction) == requested;
    }

    private static boolean insertIntoHandler(
                                             ResourceHandler<FluidResource> handler,
                                             List<TopoFluidIngredient> contents,
                                             Transaction transaction) {
        for (TopoFluidIngredient ingredient : contents) {
            long requested = ingredient.amount();
            if (ResourceHandlerLongOps.insert(
                    handler, ingredient.resource(), requested, transaction) != requested) {
                return false;
            }
        }
        return true;
    }

    public static boolean canInsertOutputs(
                                           ResourceHandler<FluidResource> handler,
                                           List<TopoFluidIngredient> contents) {
        int slotCount = handler.size();
        if (slotCount < 0 || slotCount > MAX_HANDLER_SLOTS) {
            return false;
        }
        FluidResource[] slotResources = new FluidResource[slotCount];
        long[] slotAmounts = new long[slotCount];
        if (!snapshotOutputSlots(handler, slotResources, slotAmounts)) {
            return false;
        }
        return canInsertOutputs(handler, contents, slotResources, slotAmounts);
    }

    private static boolean snapshotOutputSlots(
                                               ResourceHandler<FluidResource> handler,
                                               FluidResource[] slotResources,
                                               long[] slotAmounts) {
        int slotCount = slotResources.length;
        for (int slot = 0; slot < slotCount; slot++) {
            FluidResource resource = handler.getResource(slot);
            long amount = handler.getAmountAsLong(slot);
            if (resource == null || amount < 0L) {
                return false;
            }
            slotResources[slot] = resource;
            slotAmounts[slot] = amount;
        }
        return true;
    }

    private static boolean canInsertOutputs(
                                            ResourceHandler<FluidResource> handler,
                                            List<TopoFluidIngredient> contents,
                                            FluidResource[] slotResources,
                                            long[] slotAmounts) {
        int slotCount = slotResources.length;
        for (TopoFluidIngredient output : contents) {
            FluidResource resource = output.resource();
            long remaining = output.amount();
            if (resource == null || resource.isEmpty() || remaining <= 0L) {
                return false;
            }
            for (int slot = 0; slot < slotCount && remaining > 0L; slot++) {
                FluidResource stored = slotResources[slot];
                if ((resource.equals(stored) || stored.isEmpty()) && handler.isValid(slot, resource)) {
                    remaining -= reserveOutputSlot(handler, slotResources, slotAmounts, slot, resource, remaining);
                }
            }
            if (remaining != 0L) {
                return false;
            }
        }
        return true;
    }

    private static long reserveOutputSlot(
                                          ResourceHandler<FluidResource> handler,
                                          FluidResource[] slotResources,
                                          long[] slotAmounts,
                                          int slot,
                                          FluidResource resource,
                                          long requested) {
        long capacity = Math.max(0L, handler.getCapacityAsLong(slot, resource));
        long room = Math.max(0L, capacity - slotAmounts[slot]);
        long inserted = Math.min(room, requested);
        if (inserted > 0L) {
            slotResources[slot] = resource;
            slotAmounts[slot] += inserted;
        }
        return inserted;
    }

    private record LazyFluidKey(Supplier<? extends Fluid> fluid, long amount) {

        private LazyFluidKey {
            Objects.requireNonNull(fluid, "fluid");
        }
    }
}
