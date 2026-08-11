package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.data.DataItemResourceHandler;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import com.google.common.primitives.Ints;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * Small encoded-pattern inventory owned by an AE pattern provider trait. Accepts only encoded
 * AE2 processing patterns; decodes are cached against a content signature.
 */
public final class AePatternInventory extends SnapshotJournal<ItemStack[]>
                                      implements ResourceHandler<ItemResource> {

    private static final String PATTERNS_TAG = "patterns";
    private static final String STACK_TAG = "stack";

    private final ItemStack[] patterns;
    private @Nullable Runnable onChanged;
    private @Nullable Level cachedLevel;
    private int cachedSignature;
    private int contentSignature = 1;
    private boolean contentSignatureDirty = true;
    private @Nullable List<IPatternDetails> cachedDecoded;
    /**
     * Owner-supplied level lookup, set by the trait at resolve time so {@link #isValid} can
     * decode candidate patterns and gate on AEProcessingPattern.
     */
    private @Nullable Supplier<Level> levelGetter;

    public AePatternInventory(int size) {
        this(size, () -> {});
    }

    public AePatternInventory(int size, Runnable onChanged) {
        this.patterns = new ItemStack[size];
        this.onChanged = onChanged == null ? () -> {} : onChanged;
        for (int i = 0; i < size; i++) {
            patterns[i] = ItemStack.EMPTY;
        }
    }

    @Override
    public int size() {
        return patterns.length;
    }

    public ItemStack get(int slot) {
        return patterns[slot].copy();
    }

    public void set(int slot, ItemStack stack) {
        patterns[slot] = stack == null ? ItemStack.EMPTY : stack.copy();
        notifyChanged();
    }

    @Override
    public ItemResource getResource(int index) {
        return index < 0 || index >= patterns.length ? ItemResource.EMPTY : ItemResource.of(patterns[index]);
    }

    @Override
    public long getAmountAsLong(int index) {
        return index < 0 || index >= patterns.length ? 0L : patterns[index].getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        if (index < 0 || index >= patterns.length || resource == null) {
            return 0L;
        }
        if (resource.isEmpty()) {
            // vanilla Slot.getMaxStackSize() probes capacity with EMPTY before knowing the cursor
            // resource; returning 0 here would gate every placement. Per-resource capacity gating
            // still happens via the non-EMPTY branch below and via isValid.
            return Item.ABSOLUTE_MAX_STACK_SIZE;
        }
        ItemStack current = patterns[index];
        if (!current.isEmpty() && !resource.matches(current)) {
            return 0L;
        }
        return resource.getMaxStackSize();
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (index < 0 || index >= patterns.length || resource == null || resource.isEmpty()) {
            return false;
        }
        ItemStack stack = resource.toStack();
        if (!(stack.getItem() instanceof EncodedPatternItem<?>)) {
            return false;
        }
        Level level = resolveLevel();
        if (level == null) {
            // Pre-bind / detached state: refuse rather than accept blindly. The trait sets a
            // level supplier at resolve time, so a real in-world inventory always has one by the
            // time anything can interact with it.
            return false;
        }
        AEItemKey key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(key, level);
        return details instanceof AEProcessingPattern;
    }

    /**
     * Owner registers a level supplier so {@link #isValid} can decode encoded patterns and accept
     * only {@code AEProcessingPattern}s. Pass {@code null} to detach.
     */
    public void setLevelGetter(@Nullable Supplier<Level> levelGetter) {
        this.levelGetter = levelGetter;
    }

    private @Nullable Level resolveLevel() {
        Supplier<Level> getter = levelGetter;
        if (getter != null) {
            Level level = getter.get();
            if (level != null) {
                return level;
            }
        }
        return cachedLevel;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (!isValid(index, resource) || amount <= 0) {
            return 0;
        }
        ItemStack current = patterns[index];
        if (!current.isEmpty() && !resource.matches(current)) {
            return 0;
        }
        int capacity = Ints.saturatedCast(getCapacityAsLong(index, resource));
        int inserted = Math.min(amount, capacity - current.getCount());
        if (inserted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        patterns[index] = resource.toStack(current.getCount() + inserted);
        return inserted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index < 0 || index >= patterns.length || resource == null || resource.isEmpty() || amount <= 0) {
            return 0;
        }
        ItemStack current = patterns[index];
        if (current.isEmpty() || !resource.matches(current)) {
            return 0;
        }
        int extracted = Math.min(amount, current.getCount());
        if (extracted <= 0) {
            return 0;
        }
        updateSnapshots(transaction);
        current.shrink(extracted);
        if (current.isEmpty()) {
            patterns[index] = ItemStack.EMPTY;
        }
        return extracted;
    }

    public List<IPatternDetails> decode(Level level) {
        int signature = contentSignature();
        List<IPatternDetails> cached = cachedDecoded;
        if (cached != null && cachedLevel == level && cachedSignature == signature) {
            return cached;
        }

        ArrayList<IPatternDetails> decoded = new ArrayList<>();
        for (ItemStack stack : patterns) {
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack);
            if (key == null) {
                continue;
            }
            IPatternDetails details = PatternDetailsHelper.decodePattern(key, level);
            if (details != null) {
                decoded.add(details);
            }
        }
        cachedLevel = level;
        cachedSignature = signature;
        cachedDecoded = List.copyOf(decoded);
        return cachedDecoded;
    }

    public void serialize(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList(PATTERNS_TAG);
        for (ItemStack pattern : patterns) {
            list.addChild().store(STACK_TAG, ItemStack.OPTIONAL_CODEC, pattern);
        }
    }

    public void deserialize(ValueInput input) {
        input.childrenList(PATTERNS_TAG).ifPresent(list -> {
            int index = 0;
            for (ValueInput child : list) {
                if (index >= patterns.length) {
                    break;
                }
                patterns[index] = child.read(STACK_TAG, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
                index++;
            }
            notifyChanged();
        });
    }

    public void loadPersistedSlots(List<DataItemResourceHandler.Entry> entries) {
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = ItemStack.EMPTY;
        }
        for (DataItemResourceHandler.Entry entry : entries) {
            int index = entry.index();
            if (index < 0 || index >= patterns.length) {
                continue;
            }
            patterns[index] = entry.resource().toStack(Ints.saturatedCast(entry.amount()));
        }
        notifyChanged();
    }

    @Override
    protected ItemStack[] createSnapshot() {
        return copyPatterns();
    }

    @Override
    protected void revertToSnapshot(ItemStack[] snapshot) {
        for (int i = 0; i < patterns.length; i++) {
            patterns[i] = snapshot[i].copy();
        }
        notifyChanged();
    }

    @Override
    protected void onRootCommit(ItemStack[] originalState) {
        notifyChanged();
    }

    public void setOnChanged(@Nullable Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private ItemStack[] copyPatterns() {
        return Arrays.stream(patterns)
                .map(ItemStack::copy)
                .toArray(ItemStack[]::new);
    }

    private void notifyChanged() {
        cachedDecoded = null;
        cachedLevel = null;
        contentSignatureDirty = true;
        if (onChanged != null) {
            onChanged.run();
        }
    }

    public int contentSignature() {
        if (!contentSignatureDirty) {
            return contentSignature;
        }
        int hash = 1;
        for (ItemStack stack : patterns) {
            if (!stack.isEmpty()) {
                hash = 31 * hash + ItemStack.hashItemAndComponents(stack);
                hash = 31 * hash + stack.getCount();
            } else {
                hash = 31 * hash;
            }
        }
        contentSignature = hash;
        contentSignatureDirty = false;
        return contentSignature;
    }
}
