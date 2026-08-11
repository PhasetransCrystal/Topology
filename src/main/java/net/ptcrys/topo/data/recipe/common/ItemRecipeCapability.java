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
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.recipe.capability.RecipeInputUse;
import net.ptcrys.topo.api.recipe.capability.RecipeOutputUse;
import net.ptcrys.topo.api.recipe.capability.SlottedRecipeCapability;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topo.api.recipe.search.RecipeSearchKeyRegistry;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import com.lowdragmc.lowdraglib2.gui.slot.ItemResourceHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ItemRecipeCapability extends SlottedRecipeCapability<TopoItemInput, TopoItemOutput, ItemResource> {

    private static final String RECIPE_ITEM_SLOT_ID = "topo_recipe_item_slot";
    // Reject pathological or corrupt handlers before they can retain unbounded per-thread scratch.
    private static final int MAX_HANDLER_SLOTS = 1 << 16;
    private static final int MAX_PLANNER_INPUTS = 1 << 12;
    private static final int MAX_PLANNER_RESOURCES = 1 << 14;
    private static final int MAX_MATCH_CELLS = 1 << 18;
    private static final int MAX_JOINT_IO_CELLS = 1 << 18;
    private static final int MAX_FLOW_NODES = MAX_PLANNER_INPUTS + MAX_PLANNER_RESOURCES + 2;
    private static final int MAX_FLOW_DIRECTED_EDGES = MAX_PLANNER_INPUTS + MAX_MATCH_CELLS + MAX_PLANNER_RESOURCES;
    private static final int MAX_FLOW_STORED_EDGES = MAX_FLOW_DIRECTED_EDGES * 2;
    private static final int MAX_PLANNER_REENTRANCY = 8;
    private static final int SMALL_RESOURCE_LOOKUP_LIMIT = 16;
    private static final int MAX_EXACT_CUT_DIMENSION = 12;
    private static final int MAX_EXACT_CUT_WORK = 1 << 13;
    private static final ThreadLocal<ItemInputPlannerPool> INPUT_PLANNERS = ThreadLocal.withInitial(ItemInputPlannerPool::new);

    private final MachineResourceType<ItemResource> resourceType;

    public ItemRecipeCapability(MachineResourceType<ItemResource> resourceType) {
        super(
                Objects.requireNonNull(resourceType, "resource type").id(),
                TopoItemInput.class,
                TopoItemOutput.class,
                TopoItemInput.CODEC,
                TopoItemOutput.CODEC,
                TopoItemInput.STREAM_CODEC,
                TopoItemOutput.STREAM_CODEC);
        this.resourceType = resourceType;
    }

    @Override
    public MachineResourceType<ItemResource> resourceType() {
        return resourceType;
    }

    /** 铸造：精确物品输入。 */
    public RecipeInputUse<TopoItemInput> in(ItemLike item, long count) {
        return inputUse(TopoItemInput.of(item, count));
    }

    /** 铸造：材料 Form 物品输入（供材料后处理器产配方）。 */
    public RecipeInputUse<TopoItemInput> in(Material material, MaterialForm form, long count) {
        return inputUse(TopoItemInput.of(MaterialHelper.materialItemSupplier(material, form), count));
    }

    /** 铸造：延迟物品输入（供注册期尚未绑定的物品句柄，如组件 ItemEntry::get）。 */
    public RecipeInputUse<TopoItemInput> in(Supplier<? extends ItemLike> item, long count) {
        return inputUse(TopoItemInput.of(item, count));
    }

    /** 铸造：物品 tag 输入。 */
    public RecipeInputUse<TopoItemInput> inTag(TagKey<Item> tag, long count) {
        return inputUse(TopoItemInput.tag(tag, count));
    }

    /** 铸造：催化剂输入（在场不消耗，如工艺模具）。 */
    public RecipeInputUse<TopoItemInput> catalyst(ItemLike item) {
        return unconsumedInput(item);
    }

    /** 铸造：催化剂输入的延迟形态，供注册期尚未绑定的物品句柄。 */
    public RecipeInputUse<TopoItemInput> catalyst(Supplier<? extends ItemLike> item) {
        return unconsumedInput(item);
    }

    /**
     * Unconsumed presence input. Prefer {@link #unconsumedInput(Supplier)} for registry entries:
     * {@code ItemEntry} implements both {@link ItemLike} and {@link Supplier}, and the ItemLike path
     * historically resolved first and bound items too early.
     */
    public RecipeInputUse<TopoItemInput> unconsumedInput(ItemLike item) {
        return inputUse(TopoItemInput.unconsumed(item));
    }

    /** Deferred unconsumed input for registration-time item suppliers / registry entries. */
    public RecipeInputUse<TopoItemInput> unconsumedInput(Supplier<? extends ItemLike> item) {
        return inputUse(TopoItemInput.unconsumed(item));
    }

    /** 铸造：精确物品输出。 */
    public RecipeOutputUse<TopoItemOutput> out(ItemLike item, long count) {
        return outputUse(TopoItemOutput.of(item, count));
    }

    /** 铸造：材料 Form 物品输出。 */
    public RecipeOutputUse<TopoItemOutput> out(Material material, MaterialForm form, long count) {
        return outputUse(TopoItemOutput.of(MaterialHelper.materialItemSupplier(material, form), count));
    }

    /** 铸造：延迟物品输出（供注册期尚未绑定的物品句柄，如组件 ItemEntry::get）。 */
    public RecipeOutputUse<TopoItemOutput> out(Supplier<? extends ItemLike> item, long count) {
        return outputUse(TopoItemOutput.of(item, count));
    }

    @Override
    public boolean matchInput(@Nullable MachineBlockEntity machine, List<TopoItemInput> contents) {
        return maxParallelByInputs(itemHandler(machine, RecipeRole.INPUT), contents, 1) > 0;
    }

    @Override
    public long maxParallelByInputs(
                                    @Nullable MachineBlockEntity machine,
                                    List<TopoItemInput> contents,
                                    long maxParallel) {
        return maxParallelByInputs(
                itemHandler(machine, RecipeRole.INPUT), contents, maxParallel);
    }

    @Override
    public boolean matchOutput(@Nullable MachineBlockEntity machine, List<TopoItemOutput> contents) {
        ResourceHandler<ItemResource> handler = itemHandler(machine, RecipeRole.OUTPUT);
        return handler == null ? contents.isEmpty() : canInsertOutputs(handler, contents);
    }

    @Override
    public boolean matchOutputAfterInputs(
                                          @Nullable MachineBlockEntity machine,
                                          List<TopoItemInput> inputs,
                                          List<TopoItemOutput> outputs) {
        if (outputs.isEmpty()) {
            return true;
        }
        ResourceHandler<ItemResource> outputHandler = itemHandler(machine, RecipeRole.OUTPUT);
        if (outputHandler == null) {
            return false;
        }
        if (inputs.isEmpty()) {
            return canInsertOutputs(outputHandler, outputs);
        }
        ResourceHandler<ItemResource> inputHandler = itemHandler(machine, RecipeRole.INPUT);
        return inputHandler != null && matchOutputAfterInputs(inputHandler, inputs, outputHandler, outputs);
    }

    /** Pure handler-level joint snapshot for routers that already resolved both recipe roles. */
    public static boolean matchOutputAfterInputs(
                                                 ResourceHandler<ItemResource> inputHandler,
                                                 List<TopoItemInput> inputs,
                                                 ResourceHandler<ItemResource> outputHandler,
                                                 List<TopoItemOutput> outputs) {
        Objects.requireNonNull(inputHandler, "item input handler");
        Objects.requireNonNull(inputs, "item tick inputs");
        Objects.requireNonNull(outputHandler, "item output handler");
        Objects.requireNonNull(outputs, "item tick outputs");
        ItemInputPlannerPool planners = INPUT_PLANNERS.get();
        ItemInputPlannerScratch planner = planners.acquire();
        if (planner == null) {
            return false;
        }
        try {
            return planner.planExtraction(inputHandler, inputs) && planner.canInsertAfterPlannedExtraction(inputHandler, outputHandler, outputs);
        } finally {
            planners.release(planner);
        }
    }

    @Override
    public boolean handleInputChecked(
                                      @Nullable MachineBlockEntity machine,
                                      List<TopoItemInput> contents,
                                      Transaction transaction) {
        return consumeFromMachine(machine, contents, transaction);
    }

    @Override
    public boolean handleOutputChecked(
                                       @Nullable MachineBlockEntity machine,
                                       List<TopoItemOutput> contents,
                                       Transaction transaction) {
        ResourceHandler<ItemResource> handler = itemHandler(machine, RecipeRole.OUTPUT);
        return handler != null && insertIntoHandler(handler, contents, transaction);
    }

    @Override
    public boolean hasAnyContent(@Nullable MachineBlockEntity machine) {
        ResourceHandler<ItemResource> handler = itemHandler(machine, RecipeRole.INPUT);
        if (handler == null) {
            return false;
        }
        for (int index = 0; index < handler.size(); index++) {
            ItemResource resource = handler.getResource(index);
            if (!resource.isEmpty() && handler.getAmountAsLong(index) > 0L) {
                return true;
            }
        }
        return false;
    }

    @Override
    public TopoItemInput scaleInput(TopoItemInput content, double factor) {
        Objects.requireNonNull(content, "item input");
        if (factor == 1.0d) {
            return content;
        }
        requirePositiveScaleFactor(factor);
        // Sealed exhaustiveness: new TopoItemInput subtype must be handled here or fail compile.
        return switch (content) {
            case TopoItemInput.Resource resource -> resource.withScaledCount(factor);
            case TopoItemInput.Tag tag -> tag.withScaledCount(factor);
            case TopoItemInput.AnyOf anyOf -> anyOf.withScaledCount(factor);
            case TopoItemInput.Unconsumed unconsumed -> unconsumed; // presence-only; never parallel-multiply
        };
    }

    @Override
    public TopoItemInput scaleInputForParallel(TopoItemInput content, long factor) {
        return Objects.requireNonNull(content, "item input").withParallelCount(factor);
    }

    @Override
    public long maxParallelScaleInput(TopoItemInput content) {
        Objects.requireNonNull(content, "item input");
        return content.consumesOnMatch() ? Long.MAX_VALUE / content.count() : Long.MAX_VALUE;
    }

    @Override
    public TopoItemOutput scaleOutput(TopoItemOutput content, double factor) {
        Objects.requireNonNull(content, "item output");
        return content.withScaledCount(factor);
    }

    @Override
    public TopoItemOutput scaleOutputForParallel(TopoItemOutput content, long factor) {
        return Objects.requireNonNull(content, "item output").withParallelCount(factor);
    }

    @Override
    public long maxParallelScaleOutput(TopoItemOutput content) {
        return Long.MAX_VALUE / Objects.requireNonNull(content, "item output").count();
    }

    @Override
    public boolean isStableForResourceContentVersion() {
        // Optimization contract: item recipe checks read and mutate only item resource handlers.
        // ResourcePort bumps the machine resource version for every committed item change,
        // so failed-search and retry caches can safely key item predicates on that version.
        return true;
    }

    @Override
    public @NonNull UIElement createPreviewInputSlotWidget(@Nullable TopoItemInput input) {
        ItemSlot slot = previewSlot();
        if (input != null && !input.consumesOnMatch()) {
            // 催化剂(不消耗)预览槽:强调框区分;"不消耗"文案由模具物品自身 tooltip 携带。
            slot.getStyle().backgroundTexture(MachineUiComponentStyle.INSTANCE.catalystSlotTexture());
        }
        List<ItemStack> stacks = input == null ? List.of() : input.displayStacks();
        if (stacks.isEmpty()) {
            return slot;
        }
        slot.setItem(stacks.getFirst());
        Supplier<Stream<ItemStack>> allItems = stacks::stream;
        ItemSlot.JEISupport.recipeIngredient(slot, IngredientIO.INPUT, allItems);
        ItemSlot.JEISupport.recipeSlot(slot, allItems);
        return slot;
    }

    @Override
    public @NonNull UIElement createPreviewOutputSlotWidget(@Nullable TopoItemOutput output) {
        ItemSlot slot = previewSlot();
        if (output == null) {
            return slot;
        }
        ItemStack stack = output.stack();
        slot.setItem(stack);
        Supplier<Stream<ItemStack>> allItems = () -> Stream.of(stack);
        ItemSlot.JEISupport.recipeIngredient(slot, IngredientIO.OUTPUT, allItems);
        ItemSlot.JEISupport.recipeSlot(slot, allItems);
        return slot;
    }

    private static ItemSlot previewSlot() {
        ItemSlot slot = new ItemSlot();
        slot.setId(RECIPE_ITEM_SLOT_ID);
        slot.getStyle().backgroundTexture(MachineUiComponentStyle.INSTANCE.realSlotTexture());
        return slot;
    }

    @Override
    public @NonNull UIElement createLiveSlotWidget(ResourcePort<?, ItemResource> storage, int slot) {
        PlayerAccess access = storage.playerSlotAccess();
        ItemResourceHandlerSlot backing = new ItemResourceHandlerSlot(storage.handler(), slot)
                .setCanPlace(stack -> access.canPlace() && mayPlace(storage, stack))
                .setCanTake(player -> access.canTake());
        ItemSlot itemSlot = MachineUiComponentTemplate.INSTANCE.createItemSlot(backing);
        itemSlot.setId("topo_live_item_slot_" + slot);
        // side-IO 卡标题悬浮时按端口归属高亮本槽。
        PortUiHighlight.tag(itemSlot, storage.id());
        if (storage.metadata().resourceFilter() != null) {
            itemSlot.slotStyle(style -> style
                    .slotOverlay(MachineUiComponentStyle.INSTANCE.dieSlotOverlayTexture())
                    .showSlotOverlayOnlyEmpty(true));
        }
        return itemSlot;
    }

    private static boolean mayPlace(ResourcePort<?, ItemResource> storage, ItemStack stack) {
        return !stack.isEmpty() && storage.metadata().accepts(ItemResource.of(stack));
    }

    @Override
    public int countPreviewSlots(ResourcePortMetadata port) {
        return port instanceof ItemResourcePortMetadata itemPort ? itemPort.slots() : 0;
    }

    /**
     * 与机器侧"match-only 输入端口殿后"的声明约定配对：不消耗输入内容钉到尾部槽、
     * 普通内容自头部按声明序铺，配方 JSON 的内容声明序不再泄漏到展示层。
     * 溢出守卫：普通内容绝不侵入不消耗输入尾区，装不下的内容不指派。
     */
    @Override
    public int[] previewInputSlotAssignment(
                                            int slotCount,
                                            List<TopoItemInput> startContents,
                                            List<TopoItemInput> tickContents) {
        int total = startContents.size() + tickContents.size();
        boolean[] unconsumed = new boolean[total];
        int unconsumedCount = 0;
        for (int i = 0; i < total; i++) {
            TopoItemInput content = i < startContents.size() ? startContents.get(i) : tickContents.get(i - startContents.size());
            if (content != null && !content.consumesOnMatch()) {
                unconsumed[i] = true;
                unconsumedCount++;
            }
        }
        int[] assignment = new int[slotCount];
        Arrays.fill(assignment, -1);
        int tailStart = Math.max(0, slotCount - unconsumedCount);
        int head = 0;
        int tail = tailStart;
        for (int i = 0; i < total; i++) {
            if (unconsumed[i]) {
                if (tail < slotCount) {
                    assignment[tail++] = i;
                }
            } else if (head < tailStart) {
                assignment[head++] = i;
            }
        }
        return assignment;
    }

    private @Nullable ResourceHandler<ItemResource> itemHandler(
                                                                @Nullable MachineBlockEntity machine,
                                                                RecipeRole io) {
        return machine == null ? null : machine.machineComponents().resources().recipeSide().handler(resourceType, io);
    }

    public static long maxParallelByInputs(
                                           @Nullable ResourceHandler<ItemResource> handler,
                                           List<TopoItemInput> contents,
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
        ItemInputPlannerPool planners = INPUT_PLANNERS.get();
        ItemInputPlannerScratch planner = planners.acquire();
        if (planner == null) {
            return 0;
        }
        try {
            return planner.planMaximum(handler, contents, maxParallel);
        } finally {
            planners.release(planner);
        }
    }

    private boolean consumeFromMachine(
                                       @Nullable MachineBlockEntity machine,
                                       List<TopoItemInput> contents,
                                       Transaction transaction) {
        if (contents.isEmpty()) {
            return true;
        }
        if (machine == null) {
            return false;
        }
        ResourceHandler<ItemResource> inputs = itemHandler(machine, RecipeRole.INPUT);
        if (inputs == null) {
            return false;
        }
        return consumePlannedInputs(inputs, contents, transaction);
    }

    static boolean consumePlannedInputs(
                                        ResourceHandler<ItemResource> inputs,
                                        List<TopoItemInput> contents,
                                        Transaction transaction) {
        ItemInputPlannerPool planners = INPUT_PLANNERS.get();
        ItemInputPlannerScratch planner = planners.acquire();
        if (planner == null) {
            return false;
        }
        try {
            return planner.planExtraction(inputs, contents) && planner.extract(inputs, transaction);
        } finally {
            planners.release(planner);
        }
    }

    /**
     * A handler callback may recursively start another recipe check. Keep the outer extraction plan
     * leased until consumption finishes and lazily dedicate one scratch per nested depth.
     */
    private static final class ItemInputPlannerPool {

        private final ItemInputPlannerScratch primary = new ItemInputPlannerScratch();
        private @Nullable ItemInputPlannerScratch @Nullable [] nested;
        private int depth;

        @Nullable
        ItemInputPlannerScratch acquire() {
            int leaseDepth = depth;
            if (leaseDepth >= MAX_PLANNER_REENTRANCY) {
                return null;
            }
            @Nullable
            ItemInputPlannerScratch planner;
            if (leaseDepth == 0) {
                planner = primary;
            } else {
                int nestedIndex = leaseDepth - 1;
                if (nested == null) {
                    nested = new ItemInputPlannerScratch[2];
                } else if (nestedIndex >= nested.length) {
                    nested = Arrays.copyOf(
                            nested,
                            grownSize(nested.length, nestedIndex + 1, MAX_PLANNER_REENTRANCY - 1));
                }
                planner = nested[nestedIndex];
                if (planner == null) {
                    planner = new ItemInputPlannerScratch();
                    nested[nestedIndex] = planner;
                }
            }
            depth = leaseDepth + 1;
            return planner;
        }

        void release(ItemInputPlannerScratch planner) {
            int leaseDepth = depth - 1;
            if (leaseDepth < 0) {
                throw new IllegalStateException("Item input planner released without an active lease");
            }
            @Nullable
            ItemInputPlannerScratch expected = leaseDepth == 0 ? primary : nested == null ? null : nested[leaseDepth - 1];
            if (planner != expected) {
                throw new IllegalStateException("Item input planner leases must be released in LIFO order");
            }
            depth = leaseDepth;
            planner.onLeaseReleased();
        }
    }

    /** Reused per server thread; warm planning performs no heap allocation. */
    private static final class ItemInputPlannerScratch {

        private static final int INITIAL_RESOURCE_CAPACITY = 16;
        private static final int INITIAL_INPUT_CAPACITY = 8;
        private static final int INITIAL_MATCH_CAPACITY = 128;
        private static final int RETAINED_RESOURCE_CAPACITY = 1 << 10;
        private static final int RETAINED_INPUT_CAPACITY = 1 << 8;
        private static final int RETAINED_MATCH_CAPACITY = 1 << 14;
        private static final int SMALL_RELEASES_BEFORE_SHRINK = 64;

        private FlowScratch flow = new FlowScratch();
        private ItemResource[] resources = new ItemResource[INITIAL_RESOURCE_CAPACITY];
        private long[] supplies = new long[INITIAL_RESOURCE_CAPACITY];
        private long[] reservations = new long[INITIAL_RESOURCE_CAPACITY];
        private long[] extraction = new long[INITIAL_RESOURCE_CAPACITY];
        private long[] resourceDemand = new long[INITIAL_RESOURCE_CAPACITY];
        private int[] inputContentIndexes = new int[INITIAL_INPUT_CAPACITY];
        private long[] inputDemands = new long[INITIAL_INPUT_CAPACITY];
        private long[] inputCandidateMasks = new long[INITIAL_INPUT_CAPACITY];
        private int[] candidateCounts = new int[INITIAL_INPUT_CAPACITY];
        private int[] resourceDegrees = new int[INITIAL_RESOURCE_CAPACITY];
        private byte[] matches = new byte[INITIAL_MATCH_CAPACITY];
        private @Nullable Object2IntOpenHashMap<ItemResource> resourceIndexes;
        private int resourceCount;
        private int inputCount;
        private int matchCellCount;
        private int smallReleaseCount;
        private boolean flowUsed;
        private boolean resourcesIndexed;

        long planMaximum(
                         ResourceHandler<ItemResource> handler,
                         List<TopoItemInput> contents,
                         long maxParallel) {
            return plan(handler, contents, maxParallel, false);
        }

        boolean planExtraction(
                               ResourceHandler<ItemResource> handler,
                               List<TopoItemInput> contents) {
            return plan(handler, contents, 1L, true) == 1L;
        }

        private long plan(
                          ResourceHandler<ItemResource> handler,
                          List<TopoItemInput> contents,
                          long maxParallel,
                          boolean prepareExtraction) {
            inputCount = 0;
            matchCellCount = 0;
            flowUsed = false;
            int contentCount = contents.size();
            if (maxParallel < 1 || contentCount < 0 || contentCount > MAX_PLANNER_INPUTS) {
                return 0;
            }
            if (!snapshot(handler)) {
                return 0;
            }
            Arrays.fill(extraction, 0, resourceCount, 0L);
            Arrays.fill(reservations, 0, resourceCount, 0L);
            if (!reserveUnconsumed(contents, contentCount)) {
                return 0;
            }
            for (int resource = 0; resource < resourceCount; resource++) {
                supplies[resource] -= reservations[resource];
            }

            long parallel = collectConsumableInputs(contents, contentCount, maxParallel);
            if (parallel == 0) {
                return 0;
            }
            if (inputCount == 0) {
                return maxParallel;
            }
            if (resourceCount == 0) {
                return 0;
            }

            int matchCount = buildMatches(contents);
            if (matchCount < 0) {
                return 0;
            }
            parallel = Math.min(parallel, upperBoundByIndividualInputs());
            if (parallel == 0) {
                return 0;
            }

            boolean everyInputHasOneResource = true;
            for (int input = 0; input < inputCount; input++) {
                everyInputHasOneResource &= candidateCounts[input] == 1;
            }
            if (everyInputHasOneResource) {
                return planSingleResourceInputs(parallel, prepareExtraction);
            }

            boolean resourcesAreUnshared = true;
            for (int resource = 0; resource < resourceCount; resource++) {
                resourcesAreUnshared &= resourceDegrees[resource] <= 1;
            }
            if (resourcesAreUnshared) {
                if (!prepareExtraction) {
                    return parallel;
                }
                return allocateDisjointInputs(parallel) ? parallel : 0;
            }
            if (canEnumerateExactCuts()) {
                parallel = maxParallelByExactCuts(parallel);
                if (parallel == 0L || !prepareExtraction) {
                    return parallel;
                }
                return planOverlappingInputs(parallel, matchCount, false);
            }
            return planOverlappingInputs(parallel, matchCount, true);
        }

        void onLeaseReleased() {
            boolean oversized = resources.length > RETAINED_RESOURCE_CAPACITY || inputContentIndexes.length > RETAINED_INPUT_CAPACITY || matches.length > RETAINED_MATCH_CAPACITY || flow.isOversized();
            if (!oversized) {
                smallReleaseCount = 0;
                return;
            }
            boolean lastPlanWasSmall = resourceCount <= RETAINED_RESOURCE_CAPACITY && inputCount <= RETAINED_INPUT_CAPACITY && matchCellCount <= RETAINED_MATCH_CAPACITY && (!flowUsed || flow.lastUseWasSmall());
            if (!lastPlanWasSmall) {
                smallReleaseCount = 0;
                return;
            }
            if (++smallReleaseCount < SMALL_RELEASES_BEFORE_SHRINK) {
                return;
            }
            flow = new FlowScratch();
            resources = new ItemResource[INITIAL_RESOURCE_CAPACITY];
            supplies = new long[INITIAL_RESOURCE_CAPACITY];
            reservations = new long[INITIAL_RESOURCE_CAPACITY];
            extraction = new long[INITIAL_RESOURCE_CAPACITY];
            resourceDemand = new long[INITIAL_RESOURCE_CAPACITY];
            inputContentIndexes = new int[INITIAL_INPUT_CAPACITY];
            inputDemands = new long[INITIAL_INPUT_CAPACITY];
            inputCandidateMasks = new long[INITIAL_INPUT_CAPACITY];
            candidateCounts = new int[INITIAL_INPUT_CAPACITY];
            resourceDegrees = new int[INITIAL_RESOURCE_CAPACITY];
            matches = new byte[INITIAL_MATCH_CAPACITY];
            resourceIndexes = null;
            resourceCount = 0;
            inputCount = 0;
            matchCellCount = 0;
            smallReleaseCount = 0;
            flowUsed = false;
            resourcesIndexed = false;
        }

        boolean extract(ResourceHandler<ItemResource> handler, Transaction transaction) {
            for (int resource = 0; resource < resourceCount; resource++) {
                long requested = extraction[resource];
                if (requested > 0L && ResourceHandlerLongOps.extract(
                        handler, resources[resource], requested, transaction) != requested) {
                    return false;
                }
            }
            return true;
        }

        boolean canInsertAfterPlannedExtraction(
                                                ResourceHandler<ItemResource> inputHandler,
                                                ResourceHandler<ItemResource> outputHandler,
                                                List<TopoItemOutput> outputs) {
            int inputSlots = inputHandler.size();
            int outputSlots = outputHandler.size();
            if (inputSlots < 0 || inputSlots > MAX_HANDLER_SLOTS || outputSlots < 0 || outputSlots > MAX_HANDLER_SLOTS || (long) resourceCount * inputSlots > MAX_JOINT_IO_CELLS) {
                return false;
            }

            ItemResource[] inputResources = new ItemResource[inputSlots];
            long[] inputAmounts = new long[inputSlots];
            long[] extractedBySlot = new long[inputSlots];
            for (int slot = 0; slot < inputSlots; slot++) {
                ItemResource resource = inputHandler.getResource(slot);
                long amount = inputHandler.getAmountAsLong(slot);
                if (resource == null || amount < 0L) {
                    return false;
                }
                inputResources[slot] = resource;
                inputAmounts[slot] = amount;
            }

            for (int resourceIndex = 0; resourceIndex < resourceCount; resourceIndex++) {
                long remaining = extraction[resourceIndex];
                ItemResource resource = resources[resourceIndex];
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

            ItemResource[] outputResources = new ItemResource[outputSlots];
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
                        outputResources[outputSlot] = ItemResource.EMPTY;
                    }
                }
            }
            return canInsertOutputs(outputHandler, outputs, outputResources, outputAmounts);
        }

        private boolean snapshot(ResourceHandler<ItemResource> handler) {
            int previousResourceCount = resourceCount;
            resourceCount = 0;
            resourcesIndexed = false;
            boolean completed = false;
            try {
                int slotCount = handler.size();
                if (slotCount < 0 || slotCount > MAX_HANDLER_SLOTS) {
                    return false;
                }
                for (int slot = 0; slot < slotCount; slot++) {
                    ItemResource resource = handler.getResource(slot);
                    long amount = handler.getAmountAsLong(slot);
                    if (resource == null || resource.isEmpty() || amount <= 0L) {
                        continue;
                    }
                    int index = resourceIndex(resource);
                    if (index < 0) {
                        if (appendResource(resource, amount) < 0) {
                            return false;
                        }
                    } else {
                        long room = Long.MAX_VALUE - supplies[index];
                        long merged = Math.min(room, amount);
                        supplies[index] += merged;
                        long spill = amount - merged;
                        if (spill > 0L && appendResource(resource, spill) < 0) {
                            return false;
                        }
                    }
                }
                completed = true;
                return true;
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

        private int appendResource(ItemResource resource, long amount) {
            if (resourceCount >= MAX_PLANNER_RESOURCES) {
                return -1;
            }
            ensureResourceCapacity(resourceCount + 1);
            int index = resourceCount++;
            resources[index] = resource;
            supplies[index] = amount;
            if (resourcesIndexed) {
                Object2IntOpenHashMap<ItemResource> indexes = Objects.requireNonNull(resourceIndexes);
                if (indexes.getInt(resource) < 0) {
                    indexes.put(resource, index);
                }
            }
            return index;
        }

        private int resourceIndex(ItemResource resource) {
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
            Object2IntOpenHashMap<ItemResource> indexes = resourceIndexes;
            if (indexes == null) {
                indexes = new Object2IntOpenHashMap<>(resourceCount);
                indexes.defaultReturnValue(-1);
                resourceIndexes = indexes;
            } else {
                indexes.clear();
            }
            for (int index = 0; index < resourceCount; index++) {
                ItemResource resource = resources[index];
                if (indexes.getInt(resource) < 0) {
                    indexes.put(resource, index);
                }
            }
            resourcesIndexed = true;
        }

        private boolean reserveUnconsumed(List<TopoItemInput> contents, int contentCount) {
            for (int index = 0; index < contentCount; index++) {
                TopoItemInput input = contents.get(index);
                if (input == null || input.count() <= 0) {
                    return false;
                }
                if (input.consumesOnMatch()) {
                    continue;
                }
                int selected = -1;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if (input.matches(resources[resource], supplies[resource])) {
                        selected = resource;
                        break;
                    }
                }
                if (selected < 0) {
                    return false;
                }
                reservations[selected] = Math.max(reservations[selected], input.count());
            }
            for (int resource = 0; resource < resourceCount; resource++) {
                if (reservations[resource] > supplies[resource]) {
                    return false;
                }
            }
            return true;
        }

        private long collectConsumableInputs(
                                             List<TopoItemInput> contents,
                                             int contentCount,
                                             long maxParallel) {
            ensureInputCapacity(contentCount);
            inputCount = 0;
            long parallel = maxParallel;
            for (int index = 0; index < contentCount; index++) {
                TopoItemInput input = contents.get(index);
                if (input == null) {
                    return 0;
                }
                if (!input.consumesOnMatch()) {
                    continue;
                }
                long count = input.count();
                if (count <= 0 || inputCount >= MAX_PLANNER_INPUTS) {
                    return 0;
                }
                inputContentIndexes[inputCount] = index;
                inputDemands[inputCount] = count;
                parallel = Math.min(parallel, Long.MAX_VALUE / count);
                inputCount++;
            }
            return parallel;
        }

        private int buildMatches(List<TopoItemInput> contents) {
            long matchCells = (long) inputCount * resourceCount;
            if (matchCells <= 0L || matchCells > MAX_MATCH_CELLS) {
                return -1;
            }
            matchCellCount = (int) matchCells;
            ensureMatchCapacity(matchCellCount);
            Arrays.fill(matches, 0, matchCellCount, (byte) 0);
            Arrays.fill(candidateCounts, 0, inputCount, 0);
            Arrays.fill(inputCandidateMasks, 0, inputCount, 0L);
            Arrays.fill(resourceDegrees, 0, resourceCount, 0);
            int matchCount = 0;
            for (int input = 0; input < inputCount; input++) {
                TopoItemInput ingredient = contents.get(inputContentIndexes[input]);
                int row = input * resourceCount;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if (supplies[resource] <= 0L || !ingredient.matchesResource(resources[resource])) {
                        continue;
                    }
                    matches[row + resource] = 1;
                    candidateCounts[input]++;
                    if (resourceCount <= Long.SIZE) {
                        inputCandidateMasks[input] |= 1L << resource;
                    }
                    resourceDegrees[resource]++;
                    matchCount++;
                }
                if (candidateCounts[input] == 0) {
                    return -1;
                }
            }
            return matchCount;
        }

        private long upperBoundByIndividualInputs() {
            long parallel = Long.MAX_VALUE;
            for (int input = 0; input < inputCount; input++) {
                long available = 0L;
                boolean overflow = false;
                int row = input * resourceCount;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if (matches[row + resource] != 0) {
                        long amount = supplies[resource];
                        if (amount > Long.MAX_VALUE - available) {
                            overflow = true;
                            break;
                        }
                        available += amount;
                    }
                }
                long bound = overflow ? exactInputAvailableBound(row, inputDemands[input], parallel) : available / inputDemands[input];
                parallel = Math.min(parallel, bound);
            }
            return parallel;
        }

        private long exactInputAvailableBound(int row, long demand, long currentBound) {
            BigInteger available = BigInteger.ZERO;
            for (int resource = 0; resource < resourceCount; resource++) {
                if (matches[row + resource] != 0) {
                    available = available.add(BigInteger.valueOf(supplies[resource]));
                }
            }
            return exactCutBound(available, BigInteger.valueOf(demand), currentBound);
        }

        private long planSingleResourceInputs(long maxParallel, boolean prepareExtraction) {
            Arrays.fill(resourceDemand, 0, resourceCount, 0L);
            for (int input = 0; input < inputCount; input++) {
                int row = input * resourceCount;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if (matches[row + resource] != 0) {
                        long demand = inputDemands[input];
                        if (demand > Long.MAX_VALUE - resourceDemand[resource]) {
                            return 0L;
                        }
                        resourceDemand[resource] += demand;
                        break;
                    }
                }
            }

            long parallel = maxParallel;
            for (int resource = 0; resource < resourceCount; resource++) {
                long demand = resourceDemand[resource];
                if (demand > 0L) {
                    parallel = Math.min(parallel, supplies[resource] / demand);
                }
            }
            if (parallel == 0) {
                return 0;
            }
            if (prepareExtraction) {
                for (int resource = 0; resource < resourceCount; resource++) {
                    extraction[resource] = saturatedMultiply(resourceDemand[resource], parallel);
                }
            }
            return parallel;
        }

        private boolean allocateDisjointInputs(long parallel) {
            for (int input = 0; input < inputCount; input++) {
                long remaining = saturatedMultiply(inputDemands[input], parallel);
                int row = input * resourceCount;
                for (int resource = 0; resource < resourceCount && remaining > 0L; resource++) {
                    if (matches[row + resource] == 0) {
                        continue;
                    }
                    long taken = Math.min(supplies[resource], remaining);
                    extraction[resource] = taken;
                    remaining -= taken;
                }
                if (remaining != 0L) {
                    return false;
                }
            }
            return true;
        }

        private boolean canEnumerateExactCuts() {
            int dimension = Math.min(inputCount, resourceCount);
            if (resourceCount > Long.SIZE || dimension > MAX_EXACT_CUT_DIMENSION) {
                return false;
            }
            long subsets = (1L << dimension) - 1L;
            return subsets * (inputCount + (long) resourceCount) <= MAX_EXACT_CUT_WORK;
        }

        private long maxParallelByExactCuts(long maxParallel) {
            return resourceCount <= inputCount ? maxParallelByResourceCuts(maxParallel) : maxParallelByInputCuts(maxParallel);
        }

        private long maxParallelByResourceCuts(long maxParallel) {
            long subsetLimit = 1L << resourceCount;
            for (long resourceMask = 1L; resourceMask < subsetLimit && maxParallel > 0L; resourceMask++) {
                long demand = 0L;
                boolean demandOverflow = false;
                for (int input = 0; input < inputCount; input++) {
                    if ((inputCandidateMasks[input] & ~resourceMask) != 0L) {
                        continue;
                    }
                    long amount = inputDemands[input];
                    if (amount > Long.MAX_VALUE - demand) {
                        demandOverflow = true;
                        break;
                    }
                    demand += amount;
                }
                if (demand == 0L && !demandOverflow) {
                    continue;
                }

                long supply = 0L;
                boolean supplyOverflow = false;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if ((resourceMask & (1L << resource)) == 0L) {
                        continue;
                    }
                    long amount = supplies[resource];
                    if (amount > Long.MAX_VALUE - supply) {
                        supplyOverflow = true;
                        break;
                    }
                    supply += amount;
                }
                long bound = demandOverflow || supplyOverflow ? exactResourceCutBound(resourceMask, maxParallel) : supply / demand;
                maxParallel = Math.min(maxParallel, bound);
            }
            return maxParallel;
        }

        private long maxParallelByInputCuts(long maxParallel) {
            int subsetLimit = 1 << inputCount;
            for (int inputMask = 1; inputMask < subsetLimit && maxParallel > 0L; inputMask++) {
                long resourceMask = 0L;
                long demand = 0L;
                boolean demandOverflow = false;
                for (int input = 0; input < inputCount; input++) {
                    if ((inputMask & (1 << input)) == 0) {
                        continue;
                    }
                    resourceMask |= inputCandidateMasks[input];
                    long amount = inputDemands[input];
                    if (amount > Long.MAX_VALUE - demand) {
                        demandOverflow = true;
                        break;
                    }
                    demand += amount;
                }

                long supply = 0L;
                boolean supplyOverflow = false;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if ((resourceMask & (1L << resource)) == 0L) {
                        continue;
                    }
                    long amount = supplies[resource];
                    if (amount > Long.MAX_VALUE - supply) {
                        supplyOverflow = true;
                        break;
                    }
                    supply += amount;
                }
                long bound = demandOverflow || supplyOverflow ? exactInputCutBound(inputMask, maxParallel) : supply / demand;
                maxParallel = Math.min(maxParallel, bound);
            }
            return maxParallel;
        }

        private long exactResourceCutBound(long resourceMask, long currentBound) {
            BigInteger demand = BigInteger.ZERO;
            BigInteger supply = BigInteger.ZERO;
            for (int input = 0; input < inputCount; input++) {
                if ((inputCandidateMasks[input] & ~resourceMask) == 0L) {
                    demand = demand.add(BigInteger.valueOf(inputDemands[input]));
                }
            }
            for (int resource = 0; resource < resourceCount; resource++) {
                if ((resourceMask & (1L << resource)) != 0L) {
                    supply = supply.add(BigInteger.valueOf(supplies[resource]));
                }
            }
            return exactCutBound(supply, demand, currentBound);
        }

        private long exactInputCutBound(int inputMask, long currentBound) {
            BigInteger demand = BigInteger.ZERO;
            BigInteger supply = BigInteger.ZERO;
            long resourceMask = 0L;
            for (int input = 0; input < inputCount; input++) {
                if ((inputMask & (1 << input)) != 0) {
                    resourceMask |= inputCandidateMasks[input];
                    demand = demand.add(BigInteger.valueOf(inputDemands[input]));
                }
            }
            for (int resource = 0; resource < resourceCount; resource++) {
                if ((resourceMask & (1L << resource)) != 0L) {
                    supply = supply.add(BigInteger.valueOf(supplies[resource]));
                }
            }
            return exactCutBound(supply, demand, currentBound);
        }

        private static long exactCutBound(BigInteger supply, BigInteger demand, long currentBound) {
            BigInteger quotient = supply.divide(demand);
            return quotient.compareTo(BigInteger.valueOf(currentBound)) >= 0 ? currentBound : quotient.longValueExact();
        }

        private long planOverlappingInputs(
                                           long maxParallel,
                                           int matchCount,
                                           boolean refineFailedUpperBound) {
            long nodeCount = 2L + inputCount + resourceCount;
            long directedEdgeCount = (long) inputCount + matchCount + resourceCount;
            if (nodeCount > MAX_FLOW_NODES || directedEdgeCount > MAX_FLOW_DIRECTED_EDGES) {
                return 0;
            }
            int source = 0;
            int firstInput = 1;
            int firstResource = firstInput + inputCount;
            int sink = firstResource + resourceCount;
            flowUsed = true;
            if (!flow.begin((int) nodeCount, (int) directedEdgeCount, inputCount, resourceCount)) {
                return 0;
            }
            for (int input = 0; input < inputCount; input++) {
                int sourceEdge = flow.addEdge(source, firstInput + input, 0L);
                if (sourceEdge < 0) {
                    return 0;
                }
                flow.sourceEdges[input] = sourceEdge;
                int row = input * resourceCount;
                for (int resource = 0; resource < resourceCount; resource++) {
                    if (matches[row + resource] != 0 && flow.addEdge(
                            firstInput + input,
                            firstResource + resource,
                            Long.MAX_VALUE) < 0) {
                        return 0;
                    }
                }
            }
            for (int resource = 0; resource < resourceCount; resource++) {
                int sinkEdge = flow.addEdge(firstResource + resource, sink, supplies[resource]);
                if (sinkEdge < 0) {
                    return 0;
                }
                flow.resourceSinkEdges[resource] = sinkEdge;
            }

            long parallel = maxParallel;
            while (parallel > 0) {
                for (int input = 0; input < inputCount; input++) {
                    flow.setInitialCapacity(
                            flow.sourceEdges[input],
                            saturatedMultiply(inputDemands[input], parallel));
                }
                flow.reset();
                while (!allSourceDemandSatisfied() && flow.maxFlow(source, sink, Long.MAX_VALUE) > 0L) {
                    // Aggregate demand may exceed long; each pass safely moves at most Long.MAX_VALUE.
                }
                if (allSourceDemandSatisfied()) {
                    for (int resource = 0; resource < resourceCount; resource++) {
                        int edge = flow.resourceSinkEdges[resource];
                        extraction[resource] = flow.initialCapacity(edge) - flow.residualCapacity(edge);
                    }
                    return parallel;
                }

                if (!refineFailedUpperBound) {
                    return 0L;
                }

                flow.markReachable(source);
                long cutDemand = 0L;
                long cutSupply = 0L;
                boolean cutDemandOverflow = false;
                boolean cutSupplyOverflow = false;
                for (int input = 0; input < inputCount; input++) {
                    if (flow.isReachable(firstInput + input)) {
                        long amount = inputDemands[input];
                        if (amount > Long.MAX_VALUE - cutDemand) {
                            cutDemandOverflow = true;
                        } else {
                            cutDemand += amount;
                        }
                    }
                }
                for (int resource = 0; resource < resourceCount; resource++) {
                    if (flow.isReachable(firstResource + resource)) {
                        long amount = supplies[resource];
                        if (amount > Long.MAX_VALUE - cutSupply) {
                            cutSupplyOverflow = true;
                        } else {
                            cutSupply += amount;
                        }
                    }
                }
                if (cutDemand == 0L && !cutDemandOverflow) {
                    return 0;
                }
                long cutBound = cutDemandOverflow || cutSupplyOverflow ? exactReachableCutBound(firstInput, firstResource, parallel - 1L) : cutSupply / cutDemand;
                parallel = Math.min(parallel - 1L, cutBound);
            }
            return 0;
        }

        private boolean allSourceDemandSatisfied() {
            for (int input = 0; input < inputCount; input++) {
                if (flow.residualCapacity(flow.sourceEdges[input]) != 0L) {
                    return false;
                }
            }
            return true;
        }

        private long exactReachableCutBound(
                                            int firstInput,
                                            int firstResource,
                                            long currentBound) {
            BigInteger demand = BigInteger.ZERO;
            BigInteger supply = BigInteger.ZERO;
            for (int input = 0; input < inputCount; input++) {
                if (flow.isReachable(firstInput + input)) {
                    demand = demand.add(BigInteger.valueOf(inputDemands[input]));
                }
            }
            for (int resource = 0; resource < resourceCount; resource++) {
                if (flow.isReachable(firstResource + resource)) {
                    supply = supply.add(BigInteger.valueOf(supplies[resource]));
                }
            }
            return exactCutBound(supply, demand, currentBound);
        }

        private void ensureResourceCapacity(int required) {
            if (required <= resources.length) {
                return;
            }
            int size = grownSize(resources.length, required, MAX_PLANNER_RESOURCES);
            resources = Arrays.copyOf(resources, size);
            supplies = Arrays.copyOf(supplies, size);
            reservations = Arrays.copyOf(reservations, size);
            extraction = Arrays.copyOf(extraction, size);
            resourceDemand = Arrays.copyOf(resourceDemand, size);
            resourceDegrees = Arrays.copyOf(resourceDegrees, size);
        }

        private void ensureInputCapacity(int required) {
            if (required <= inputContentIndexes.length) {
                return;
            }
            int size = grownSize(inputContentIndexes.length, required, MAX_PLANNER_INPUTS);
            inputContentIndexes = Arrays.copyOf(inputContentIndexes, size);
            inputDemands = Arrays.copyOf(inputDemands, size);
            inputCandidateMasks = Arrays.copyOf(inputCandidateMasks, size);
            candidateCounts = Arrays.copyOf(candidateCounts, size);
        }

        private void ensureMatchCapacity(int required) {
            if (required > matches.length) {
                matches = Arrays.copyOf(
                        matches, grownSize(matches.length, required, MAX_MATCH_CELLS));
            }
        }
    }

    private static final class FlowScratch {

        private static final int RETAINED_NODE_CAPACITY = 1 << 11;
        private static final int RETAINED_EDGE_CAPACITY = 1 << 15;
        private static final int RETAINED_INPUT_CAPACITY = 1 << 8;
        private static final int RETAINED_RESOURCE_CAPACITY = 1 << 10;

        private int[] head = new int[32];
        private int[] to = new int[128];
        private int[] next = new int[128];
        private long[] initial = new long[128];
        private long[] residual = new long[128];
        private int[] level = new int[32];
        private int[] current = new int[32];
        private int[] queue = new int[32];
        private boolean[] reachable = new boolean[32];
        private int[] sourceEdges = new int[8];
        private int[] resourceSinkEdges = new int[16];
        private int nodes;
        private int edges;
        private int edgeLimit;

        boolean begin(int nodeCount, int directedEdges, int inputCount, int resourceCount) {
            long storedEdgeCount = (long) directedEdges * 2L;
            if (nodeCount < 2 || nodeCount > MAX_FLOW_NODES || directedEdges < 0 || directedEdges > MAX_FLOW_DIRECTED_EDGES || storedEdgeCount > MAX_FLOW_STORED_EDGES || inputCount < 0 || inputCount > MAX_PLANNER_INPUTS || resourceCount < 0 || resourceCount > MAX_PLANNER_RESOURCES) {
                return false;
            }
            ensureNodeCapacity(nodeCount);
            ensureEdgeCapacity((int) storedEdgeCount);
            if (sourceEdges.length < inputCount) {
                sourceEdges = Arrays.copyOf(
                        sourceEdges,
                        grownSize(sourceEdges.length, inputCount, MAX_PLANNER_INPUTS));
            }
            if (resourceSinkEdges.length < resourceCount) {
                resourceSinkEdges = Arrays.copyOf(
                        resourceSinkEdges,
                        grownSize(resourceSinkEdges.length, resourceCount, MAX_PLANNER_RESOURCES));
            }
            nodes = nodeCount;
            edges = 0;
            edgeLimit = (int) storedEdgeCount;
            Arrays.fill(head, 0, nodes, -1);
            return true;
        }

        int addEdge(int from, int target, long capacity) {
            if (from < 0 || from >= nodes || target < 0 || target >= nodes || capacity < 0L || edges > edgeLimit - 2) {
                return -1;
            }
            int forward = edges;
            to[edges] = target;
            initial[edges] = capacity;
            next[edges] = head[from];
            head[from] = edges++;
            to[edges] = from;
            initial[edges] = 0L;
            next[edges] = head[target];
            head[target] = edges++;
            return forward;
        }

        boolean isOversized() {
            return head.length > RETAINED_NODE_CAPACITY || to.length > RETAINED_EDGE_CAPACITY || sourceEdges.length > RETAINED_INPUT_CAPACITY || resourceSinkEdges.length > RETAINED_RESOURCE_CAPACITY;
        }

        boolean lastUseWasSmall() {
            return nodes <= RETAINED_NODE_CAPACITY && edges <= RETAINED_EDGE_CAPACITY;
        }

        void setInitialCapacity(int edge, long capacity) {
            initial[edge] = capacity;
        }

        long initialCapacity(int edge) {
            return initial[edge];
        }

        long residualCapacity(int edge) {
            return residual[edge];
        }

        void reset() {
            System.arraycopy(initial, 0, residual, 0, edges);
        }

        long maxFlow(int source, int sink, long limit) {
            long total = 0L;
            while (total < limit && buildLevels(source, sink)) {
                System.arraycopy(head, 0, current, 0, nodes);
                long pushed;
                while (total < limit && (pushed = push(source, sink, limit - total)) > 0L) {
                    total += pushed;
                }
            }
            return total;
        }

        void markReachable(int source) {
            Arrays.fill(reachable, 0, nodes, false);
            int read = 0;
            int write = 0;
            queue[write++] = source;
            reachable[source] = true;
            while (read < write) {
                int node = queue[read++];
                for (int edge = head[node]; edge != -1; edge = next[edge]) {
                    int target = to[edge];
                    if (residual[edge] > 0L && !reachable[target]) {
                        reachable[target] = true;
                        queue[write++] = target;
                    }
                }
            }
        }

        boolean isReachable(int node) {
            return reachable[node];
        }

        private boolean buildLevels(int source, int sink) {
            Arrays.fill(level, 0, nodes, -1);
            int read = 0;
            int write = 0;
            queue[write++] = source;
            level[source] = 0;
            while (read < write) {
                int node = queue[read++];
                for (int edge = head[node]; edge != -1; edge = next[edge]) {
                    int target = to[edge];
                    if (residual[edge] > 0L && level[target] < 0) {
                        level[target] = level[node] + 1;
                        queue[write++] = target;
                    }
                }
            }
            return level[sink] >= 0;
        }

        private long push(int node, int sink, long amount) {
            if (node == sink || amount == 0L) {
                return amount;
            }
            for (int edge = current[node]; edge != -1; edge = next[edge]) {
                current[node] = edge;
                int target = to[edge];
                if (residual[edge] <= 0L || level[target] != level[node] + 1) {
                    continue;
                }
                long pushed = push(target, sink, Math.min(amount, residual[edge]));
                if (pushed > 0L) {
                    residual[edge] -= pushed;
                    residual[edge ^ 1] += pushed;
                    return pushed;
                }
            }
            current[node] = -1;
            return 0L;
        }

        private void ensureNodeCapacity(int required) {
            if (required <= head.length) {
                return;
            }
            int size = grownSize(head.length, required, MAX_FLOW_NODES);
            head = Arrays.copyOf(head, size);
            level = Arrays.copyOf(level, size);
            current = Arrays.copyOf(current, size);
            queue = Arrays.copyOf(queue, size);
            reachable = Arrays.copyOf(reachable, size);
        }

        private void ensureEdgeCapacity(int required) {
            if (required <= to.length) {
                return;
            }
            int size = grownSize(to.length, required, MAX_FLOW_STORED_EDGES);
            to = Arrays.copyOf(to, size);
            next = Arrays.copyOf(next, size);
            initial = Arrays.copyOf(initial, size);
            residual = Arrays.copyOf(residual, size);
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

    private static long saturatedMultiply(long value, long multiplier) {
        return value == 0L || multiplier == 0 ? 0L : value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    @Override
    public Set<?> indexKeys(TopoItemInput content) {
        // Optimization: exact item resources and item tags become recipe-search keys.
        // Principle: the machine fingerprint emits the same exact resource plus all item tags
        // present in the input handler, so both exact recipes and tag recipes prune to a small
        // candidate set before transactional matching.
        return switch (content) {
            case TopoItemInput.Resource resource -> Set.of(resource.resource());
            case TopoItemInput.Tag tag -> Set.of(tag.tag());
            case TopoItemInput.AnyOf ignored -> Set.of();
            // 催化剂在场是必要起始条件,可安全充当搜索剪枝键:没插模具的机器在索引层即被剪掉。
            case TopoItemInput.Unconsumed unconsumed -> Set.of(unconsumed.resource());
        };
    }

    @Override
    public long inputAmount(TopoItemInput content) {
        return content.count();
    }

    @Override
    public InputIndexAmountMode inputIndexAmountMode(TopoItemInput content) {
        return content.consumesOnMatch() ? InputIndexAmountMode.ADDITIVE : InputIndexAmountMode.MAX_RESERVATION;
    }

    @Override
    public void extractMachineKeys(
                                   @Nullable MachineBlockEntity machine,
                                   RecipeSearchKeyRegistry registry,
                                   Int2LongMap out) {
        ResourceHandler<ItemResource> handler = itemHandler(machine, RecipeRole.INPUT);
        if (handler == null) {
            return;
        }
        int size = handler.size();
        for (int index = 0; index < size; index++) {
            ItemResource resource = handler.getResource(index);
            if (resource.isEmpty()) {
                continue;
            }
            long amount = handler.getAmountAsLong(index);
            if (amount <= 0L) {
                continue;
            }
            int resourceId = registry.idOf(this, resource);
            if (resourceId >= 0) {
                out.mergeLong(resourceId, amount, ItemRecipeCapability::saturatedAdd);
            }
            resource.typeHolder().tags().forEach(tag -> {
                int tagId = registry.idOf(this, tag);
                if (tagId >= 0) {
                    out.mergeLong(tagId, amount, ItemRecipeCapability::saturatedAdd);
                }
            });
        }
    }

    @Override
    public boolean contributesIndexKeys() {
        return true;
    }

    private static boolean insertIntoHandler(
                                             ResourceHandler<ItemResource> handler,
                                             List<TopoItemOutput> contents,
                                             Transaction transaction) {
        for (TopoItemOutput output : contents) {
            ItemResource resource = output.resource();
            long requested = output.count();
            if (ResourceHandlerLongOps.insert(handler, resource, requested, transaction) != requested) {
                return false;
            }
        }
        return true;
    }

    /** Pure independent-output feasibility in declaration and handler-slot order. */
    public static boolean canInsertOutputs(
                                           ResourceHandler<ItemResource> handler,
                                           List<TopoItemOutput> contents) {
        if (contents.isEmpty()) {
            return true;
        }
        int slotCount = handler.size();
        if (slotCount < 0 || slotCount > MAX_HANDLER_SLOTS) {
            return false;
        }
        ItemResource[] slotResources = new ItemResource[slotCount];
        long[] slotAmounts = new long[slotCount];
        if (!snapshotOutputSlots(handler, slotResources, slotAmounts)) {
            return false;
        }
        return canInsertOutputs(handler, contents, slotResources, slotAmounts);
    }

    private static boolean snapshotOutputSlots(
                                               ResourceHandler<ItemResource> handler,
                                               ItemResource[] slotResources,
                                               long[] slotAmounts) {
        int slotCount = slotResources.length;
        for (int slot = 0; slot < slotCount; slot++) {
            ItemResource resource = handler.getResource(slot);
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
                                            ResourceHandler<ItemResource> handler,
                                            List<TopoItemOutput> contents,
                                            ItemResource[] slotResources,
                                            long[] slotAmounts) {
        int slotCount = slotResources.length;
        for (TopoItemOutput output : contents) {
            ItemResource resource = output.resource();
            long remaining = output.count();
            if (resource == null || resource.isEmpty() || remaining <= 0L) {
                return false;
            }
            for (int slot = 0; slot < slotCount && remaining > 0L; slot++) {
                ItemResource stored = slotResources[slot];
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
                                          ResourceHandler<ItemResource> handler,
                                          ItemResource[] slotResources,
                                          long[] slotAmounts,
                                          int slot,
                                          ItemResource resource,
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
}
