package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.api.tick.MachineTicker;
import net.ptcrys.topo.api.tick.TickHandle;
import net.ptcrys.topo.apiv2.machine.component.ComponentContext;
import net.ptcrys.topo.apiv2.machine.component.ComponentKey;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.MachineComponents;
import net.ptcrys.topo.apiv2.machine.data.DataBoolean;
import net.ptcrys.topo.apiv2.machine.data.DataItemResourceHandler;
import net.ptcrys.topo.apiv2.machine.data.DataString;
import net.ptcrys.topo.apiv2.machine.data.DataValueIoField;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType;
import net.ptcrys.topo.apiv2.machine.resource.RecipeInputPortControl;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.SearchPoolUiControl;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternContainer;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Internal AE2 crafting provider for multiblock parts: pushes processing-pattern inputs into local
 * OI buffers (shared ports or per-pattern slot buffers). Does not push to adjacent world inventories
 * like AE2's {@code PatternProviderLogic}.
 *
 * <p>
 * Also implements {@link PatternContainer} (via the {@link AeGridNode} delegate) so the machine
 * shows up in AE2's Pattern Access Terminal.
 */
public final class AePatternProvider extends MachineTicker
                                     implements ICraftingProvider, PatternContainer, SearchPoolUiControl, RecipeInputPortControl {

    public static final ComponentKey<AePatternProvider> AE_PATTERN_PROVIDER = ComponentKey.oi("ae_pattern_provider", AePatternProvider.class)
            .service(SearchPoolUiControl.KEY, (trait, unused) -> trait)
            .service(RecipeInputPortControl.KEY, (trait, unused) -> trait);

    private static final String CUSTOM_NAME_TAG = "custom_name";
    private static final String SLOT_BUFFERS_TAG = "slot_buffers";
    private static final int SLOT_POOL_ID_WIDTH = 6;
    /** Hard cap server-side; over-length input is truncated rather than rejected. */
    public static final int MAX_CUSTOM_NAME_LENGTH = 32;

    private final @Nullable ComponentKey<ItemResourcePort> itemInputPortKey;
    private final @Nullable ComponentKey<FluidResourcePort> fluidInputPortKey;
    private final AePatternInventory patterns;
    private final AePatternInternalInventoryAdapter terminalInventory;
    private final DataItemResourceHandler patternData;
    private final DataString customName;
    private final DataBoolean blocking;
    private final DataBoolean separated;
    private final AePatternSlotBuffer[] slotBuffers;
    private final DataValueIoField slotBufferData;
    private @Nullable AeGridNode grid;
    private @Nullable ResourceHandler<ItemResource> itemHandler;
    private @Nullable ResourceHandler<FluidResource> fluidHandler;
    private int lastPatternSignature;
    /** pattern details identity → slot index while patterns are stable. */
    private final Map<IPatternDetails, Integer> patternSlotIndex = new HashMap<>();

    private AePatternProvider(
                              ComponentContext<AePatternProvider> context,
                              int patternSlots,
                              @Nullable ComponentKey<ItemResourcePort> itemInputPortKey,
                              @Nullable ComponentKey<FluidResourcePort> fluidInputPortKey) {
        super(context);
        this.itemInputPortKey = itemInputPortKey;
        this.fluidInputPortKey = fluidInputPortKey;
        this.patterns = new AePatternInventory(patternSlots, this::onPatternsChanged);
        this.terminalInventory = new AePatternInternalInventoryAdapter(this.patterns);
        this.slotBuffers = new AePatternSlotBuffer[patternSlots];
        Set<RecipeSearchPoolId> used = new LinkedHashSet<>();
        for (int i = 0; i < patternSlots; i++) {
            RecipeSearchPoolId id = RecipeSearchPoolId.generateUnique(used);
            used.add(id);
            slotBuffers[i] = new AePatternSlotBuffer(id, this::onSlotBufferContentsChanged);
        }
        this.patternData = data().itemResourceHandler(
                "ae_pattern_provider",
                patterns,
                patterns::loadPersistedSlots,
                () -> {})
                .persisted()
                .done();
        // UI-facing config is server-persist only (syncNone). Open-menu chrome uses LDLib2 UI
        // S2C/RPC — never machine-data client sync. See SyncableFieldBuilder#syncToClientAtEndOfDirtyTick.
        this.customName = data().stringField(CUSTOM_NAME_TAG, "")
                .persisted()
                .syncNone()
                .done();
        this.blocking = data().booleanField("blocking", true)
                .persisted()
                .syncNone()
                .done();
        this.separated = data().booleanField("separated", false)
                .persisted()
                .syncNone()
                .done();
        this.slotBufferData = data().valueIoField(
                SLOT_BUFFERS_TAG,
                this::writeSlotBuffers,
                this::readSlotBuffers,
                () -> {})
                .persisted()
                .done();
    }

    public static ComponentMount<AePatternProvider> mount(
                                                          int patternSlots,
                                                          @Nullable ComponentKey<ItemResourcePort> itemInputPortKey,
                                                          @Nullable ComponentKey<FluidResourcePort> fluidInputPortKey) {
        return AE_PATTERN_PROVIDER.mount(context -> new AePatternProvider(context, patternSlots, itemInputPortKey, fluidInputPortKey));
    }

    public AePatternInventory patterns() {
        return patterns;
    }

    public boolean blocking() {
        return blocking.valueOrElse(true);
    }

    public void setBlocking(boolean value) {
        if (!data().domain().isBusinessReady()) {
            return;
        }
        if (blocking.valueOrElse(true) == value) {
            return;
        }
        blocking.set(value);
        data().markPersistedStateChanged();
    }

    public boolean separated() {
        return separated.valueOrElse(false);
    }

    @Override
    public boolean suppressPortSearchPoolConfig() {
        // Per-slot pools (each slot = one pool spanning that slot's items + fluids) replace the
        // machine-level shared search-pool config chrome.
        return separated();
    }

    @Override
    public boolean suppressIsolatablePortInputs() {
        return separated();
    }

    /** Applies a requested mode and returns the authoritative state (the request may be rejected). */
    public boolean trySetSeparated(boolean value) {
        if (!data().domain().isBusinessReady()) {
            return separated();
        }
        if (separated.valueOrElse(false) == value) {
            return value;
        }
        // Disabling would hide committed per-slot inputs from shared-mode recipe routing. Enabling
        // is always safe: legacy shared inputs stay intact and resume when shared mode is restored.
        if (!value && hasAnySlotBufferContent(slotBuffers)) {
            return true;
        }
        separated.set(value);
        if (value) {
            ensureUniqueSlotPoolIds();
        }
        machine().machineComponents().invalidateRecipeHandlers();
        data().markPersistedStateChanged();
        AeGridNode gridTrait = grid;
        if (gridTrait != null) {
            gridTrait.requestCraftingProviderUpdate();
        }
        return value;
    }

    public void setSeparated(boolean value) {
        trySetSeparated(value);
    }

    /**
     * Pool id for pattern slot {@code index}; always present. Server-authoritative (buffers are not
     * machine-data S2C'd). Open-menu tooltips mirror ids via LDLib2 UI string S2C of
     * {@link #encodeSlotPoolIdsForUi()}.
     */
    public RecipeSearchPoolId slotPoolId(int index) {
        return slotBuffers[index].poolId();
    }

    /** Comma-joined six-char pool tokens for every pattern slot (LDLib2 UI S2C source). */
    public String encodeSlotPoolIdsForUi() {
        return encodeSlotPoolIds(slotBuffers);
    }

    @Override
    public void resolveDependencies(MachineComponents traits) {
        AeGridNode gridTrait = traits.require(AeGridNode.AE_GRID);
        gridTrait.addService(ICraftingProvider.class, this);
        gridTrait.setPatternDelegate(this);
        grid = gridTrait;
        if (itemInputPortKey != null) {
            itemHandler = traits.require(itemInputPortKey).handler();
        }
        if (fluidInputPortKey != null) {
            fluidHandler = traits.require(fluidInputPortKey).handler();
        }
        patterns.setLevelGetter(() -> machine().getLevel());
        lastPatternSignature = patterns.contentSignature();
        rebuildPatternSlotIndex();
    }

    @Override
    protected <R extends Resource> void collectRecipeResourceHandlers(
                                                                      MachineResourceType<R> resourceType,
                                                                      RecipeRole recipeIo,
                                                                      Consumer<RecipeResourceContribution<R>> out) {
        if (!separated.value() || recipeIo == RecipeRole.OUTPUT || recipeIo == RecipeRole.NONE) {
            return;
        }
        MachineResourceType<ItemResource> itemType = BuiltinOIResourceIntegrations.ITEM.resourceType();
        MachineResourceType<FluidResource> fluidType = BuiltinOIResourceIntegrations.FLUID.resourceType();
        for (AePatternSlotBuffer buffer : slotBuffers) {
            if (resourceType == itemType && recipeIo.allows(RecipeRole.INPUT)) {
                @SuppressWarnings("unchecked")
                ResourceHandler<R> handler = (ResourceHandler<R>) buffer.items();
                out.accept(new RecipeResourceContribution<>(buffer.poolId(), true, handler));
            } else if (resourceType == fluidType && recipeIo.allows(RecipeRole.INPUT)) {
                @SuppressWarnings("unchecked")
                ResourceHandler<R> handler = (ResourceHandler<R>) buffer.fluids();
                out.accept(new RecipeResourceContribution<>(buffer.poolId(), true, handler));
            }
        }
        // Inputs already committed before separation cannot be assigned back to a pattern slot.
        // Keep the legacy shared buffers globally visible to every per-slot pool so they can drain;
        // new AE pushes still target only the matching slot buffer.
        if (resourceType == itemType && itemHandler != null) {
            @SuppressWarnings("unchecked")
            ResourceHandler<R> handler = (ResourceHandler<R>) itemHandler;
            out.accept(new RecipeResourceContribution<>(RecipeSearchPoolId.DEFAULT, false, handler));
        } else if (resourceType == fluidType && fluidHandler != null) {
            @SuppressWarnings("unchecked")
            ResourceHandler<R> handler = (ResourceHandler<R>) fluidHandler;
            out.accept(new RecipeResourceContribution<>(RecipeSearchPoolId.DEFAULT, false, handler));
        }
    }

    // ---- ICraftingProvider ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        Level level = machine().getLevel();
        if (level == null) {
            return List.of();
        }
        return patterns.decode(level);
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (patternDetails == null || !getAvailablePatterns().contains(patternDetails)) {
            return false;
        }
        if (separated.value()) {
            Integer slot = patternSlotIndex.get(patternDetails);
            if (slot == null) {
                rebuildPatternSlotIndex();
                slot = patternSlotIndex.get(patternDetails);
            }
            if (slot == null) {
                return false;
            }
            AePatternSlotBuffer buffer = slotBuffers[slot];
            if (blocking.value() && buffer.hasContent()) {
                return false;
            }
            return pushPatternInputsToBuffers(
                    patternDetails, inputHolder, buffer.items(), buffer.fluids());
        }
        if (isSharedBusy()) {
            return false;
        }
        return pushPatternInputsToBuffers(patternDetails, inputHolder, itemHandler, fluidHandler);
    }

    @Override
    public boolean isBusy() {
        if (itemInputPortKey != null && itemHandler == null) {
            return true;
        }
        if (fluidInputPortKey != null && fluidHandler == null) {
            return true;
        }
        if (separated.value()) {
            // Per-slot busy is enforced in pushPattern; global busy only when every slot is blocked.
            if (!blocking.value()) {
                return false;
            }
            for (AePatternSlotBuffer buffer : slotBuffers) {
                if (!buffer.hasContent()) {
                    return false;
                }
            }
            return slotBuffers.length > 0;
        }
        return isSharedBusy();
    }

    private boolean isSharedBusy() {
        if (!blocking.value()) {
            return false;
        }
        return hasSharedContent();
    }

    private boolean hasSharedContent() {
        return hasAnyContent(itemHandler) || hasAnyContent(fluidHandler);
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    public void tick(long gameTime, TickHandle handle) {
        int signature = patterns.contentSignature();
        if (signature != lastPatternSignature) {
            lastPatternSignature = signature;
            onPatternsChanged();
        }
    }

    /**
     * Pushes pattern inputs through {@code IPatternDetails.pushInputsToExternalInventory} into
     * the local buffers atomically. AE input selection runs against a copy of the holder; the
     * selected state is published only after the complete, read-only-derived buffer plan commits.
     */
    static boolean pushPatternInputsToBuffers(
                                              IPatternDetails patternDetails,
                                              KeyCounter[] inputs,
                                              @Nullable ResourceHandler<ItemResource> itemHandler,
                                              @Nullable ResourceHandler<FluidResource> fluidHandler) {
        if (patternDetails == null || !patternDetails.supportsPushInputsToExternalInventory()) {
            return false;
        }
        AePatternInputPlan.Collector inputsCollector = AePatternInputPlan.collector();
        KeyCounter[] stagedInputs = copyInputs(inputs);
        patternDetails.pushInputsToExternalInventory(stagedInputs, inputsCollector::add);
        AePatternInputPlan plan = inputsCollector.plan(itemHandler, fluidHandler);
        if (plan == null || !plan.commit()) {
            return false;
        }
        replaceInputs(inputs, stagedInputs);
        return true;
    }

    /** Direct KeyCounter→buffer push used by unit-style tests; all-or-nothing per call. */
    public static boolean pushInputsToBuffers(
                                              KeyCounter[] inputs,
                                              @Nullable ResourceHandler<ItemResource> itemHandler,
                                              @Nullable ResourceHandler<FluidResource> fluidHandler) {
        if (inputs == null) {
            return true;
        }
        AePatternInputPlan.Collector inputsCollector = AePatternInputPlan.collector();
        for (KeyCounter counter : inputs) {
            if (counter == null || counter.isEmpty()) {
                continue;
            }
            for (Object2LongMap.Entry<AEKey> entry : counter) {
                inputsCollector.add(entry.getKey(), entry.getLongValue());
            }
        }
        AePatternInputPlan plan = inputsCollector.plan(itemHandler, fluidHandler);
        return plan != null && plan.commit();
    }

    static boolean hasAnyContent(@Nullable ResourceHandler<?> handler) {
        if (handler == null) {
            return false;
        }
        for (int i = 0, n = handler.size(); i < n; i++) {
            if (handler.getAmountAsLong(i) > 0L) {
                return true;
            }
        }
        return false;
    }

    static boolean hasAnySlotBufferContent(AePatternSlotBuffer[] buffers) {
        for (AePatternSlotBuffer buffer : buffers) {
            if (buffer.hasContent()) {
                return true;
            }
        }
        return false;
    }

    static String encodeSlotPoolIds(AePatternSlotBuffer[] buffers) {
        StringBuilder encoded = new StringBuilder(buffers.length * (SLOT_POOL_ID_WIDTH + 1));
        for (int index = 0; index < buffers.length; index++) {
            if (index > 0) {
                encoded.append(',');
            }
            encoded.append(buffers[index].poolId().value());
        }
        return encoded.toString();
    }

    /**
     * Decode one slot token from a UI-mirrored {@link #encodeSlotPoolIdsForUi()} string. Used by
     * open-menu chrome on the client after LDLib2 string S2C (not machine-data sync).
     */
    public static RecipeSearchPoolId decodeSlotPoolId(
                                                      String encoded,
                                                      int index,
                                                      RecipeSearchPoolId fallback) {
        if (index < 0) {
            return fallback;
        }
        int start = index * (SLOT_POOL_ID_WIDTH + 1);
        int end = start + SLOT_POOL_ID_WIDTH;
        if (end > encoded.length() || (start > 0 && encoded.charAt(start - 1) != ',') || (end < encoded.length() && encoded.charAt(end) != ',')) {
            return fallback;
        }
        RecipeSearchPoolId parsed = RecipeSearchPoolId.parse(encoded.substring(start, end));
        return AePatternSlotBuffer.isValidSlotPoolId(parsed) ? parsed : fallback;
    }

    // ---- PatternContainer ----

    @Override
    public @Nullable IGrid getGrid() {
        AeGridNode gridTrait = grid;
        return gridTrait == null ? null : gridTrait.getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return terminalInventory;
    }

    @Override
    public long getTerminalSortOrder() {
        return machine().getBlockPos().asLong();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        Component name = resolveDisplayName();
        AEItemKey icon = resolveIcon();
        return new PatternContainerGroup(icon, name, List.of());
    }

    private Component resolveDisplayName() {
        String custom = customName.value();
        if (!custom.isEmpty()) {
            return Component.literal(custom);
        }
        return machine().getBlockState().getBlock().getName();
    }

    private AEItemKey resolveIcon() {
        ItemStack selfStack = new ItemStack(machine().getBlockState().getBlock());
        AEItemKey key = AEItemKey.of(selfStack);
        return key != null ? key : AEItemKey.of(Items.AIR);
    }

    // ---- customName API ----

    public @Nullable String customName() {
        String name = customName.valueOrElse("");
        return name.isEmpty() ? null : name;
    }

    public void setCustomName(@Nullable String name) {
        if (!data().domain().isBusinessReady()) {
            return;
        }
        String normalized = normalizeCustomName(name);
        if (customName.valueOrElse("").equals(normalized)) {
            return;
        }
        customName.set(normalized);
        data().markPersistedStateChanged();
        AeGridNode gridTrait = grid;
        if (gridTrait != null) {
            gridTrait.requestCraftingProviderUpdate();
        }
    }

    private static String normalizeCustomName(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > MAX_CUSTOM_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CUSTOM_NAME_LENGTH);
        }
        return trimmed;
    }

    private void onPatternsChanged() {
        var domain = data().domain();
        if (domain.isMutationSuppressed() || !domain.isBusinessReady()) {
            return;
        }
        patternData.markDirty();
        rebuildPatternSlotIndex();
        data().markPersistedStateChanged();
        AeGridNode gridTrait = grid;
        if (gridTrait != null) {
            gridTrait.requestCraftingProviderUpdate();
        }
    }

    private void onSlotBufferContentsChanged() {
        var domain = data().domain();
        if (domain.isMutationSuppressed() || domain.isClientSide() || !domain.isBusinessReady()) {
            return;
        }
        machine().machineComponents().noteResourceContentChanged();
        slotBufferData.markDirty();
        data().markPersistedStateChanged();
    }

    private void rebuildPatternSlotIndex() {
        patternSlotIndex.clear();
        Level level = machine().getLevel();
        if (level == null) {
            return;
        }
        for (int i = 0; i < patterns.size(); i++) {
            ItemStack stack = patterns.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            IPatternDetails details = appeng.api.crafting.PatternDetailsHelper.decodePattern(key, level);
            if (details != null) {
                patternSlotIndex.put(details, i);
            }
        }
    }

    private void ensureUniqueSlotPoolIds() {
        ensureUniqueSlotPoolIds(slotBuffers);
    }

    static void ensureUniqueSlotPoolIds(AePatternSlotBuffer[] buffers) {
        Set<RecipeSearchPoolId> used = new LinkedHashSet<>();
        for (AePatternSlotBuffer buffer : buffers) {
            RecipeSearchPoolId id = buffer.poolId();
            if (!AePatternSlotBuffer.isValidSlotPoolId(id) || used.contains(id)) {
                id = RecipeSearchPoolId.generateUnique(used);
                buffer.setPoolId(id);
            }
            used.add(id);
        }
    }

    private void writeSlotBuffers(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList("slots");
        for (AePatternSlotBuffer buffer : slotBuffers) {
            buffer.write(list.addChild());
        }
    }

    private void readSlotBuffers(ValueInput input) {
        Set<RecipeSearchPoolId> used = new LinkedHashSet<>();
        input.childrenList("slots").ifPresent(list -> {
            int index = 0;
            for (ValueInput child : list) {
                if (index >= slotBuffers.length) {
                    break;
                }
                slotBuffers[index].read(child, used);
                index++;
            }
        });
        // Validate the complete array, including constructor-backed trailing slots. Use a fresh
        // set so ids already recorded while reading are not mistaken for duplicates of themselves.
        ensureUniqueSlotPoolIds();
    }

    private static KeyCounter @Nullable [] copyInputs(KeyCounter @Nullable [] inputs) {
        if (inputs == null) {
            return null;
        }
        KeyCounter[] snapshot = new KeyCounter[inputs.length];
        for (int i = 0; i < inputs.length; i++) {
            KeyCounter input = inputs[i];
            if (input != null) {
                KeyCounter copy = new KeyCounter();
                copy.addAll(input);
                snapshot[i] = copy;
            }
        }
        return snapshot;
    }

    private static void replaceInputs(KeyCounter @Nullable [] inputs, KeyCounter @Nullable [] replacement) {
        if (inputs == null || replacement == null) {
            return;
        }
        for (int i = 0; i < inputs.length; i++) {
            KeyCounter staged = i < replacement.length ? replacement[i] : null;
            if (staged == null) {
                if (inputs[i] != null) {
                    inputs[i].clear();
                }
            } else {
                if (inputs[i] == null) {
                    inputs[i] = new KeyCounter();
                } else {
                    inputs[i].clear();
                }
                inputs[i].addAll(staged);
            }
        }
    }
}
