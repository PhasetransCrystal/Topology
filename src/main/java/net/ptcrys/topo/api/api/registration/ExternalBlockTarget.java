package net.ptcrys.topo.api.api.registration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A block supplied by another mod or by vanilla and reused as a material form target.
 *
 * <p>
 * A block's item is not assumed to have the same registry id as the block. The target resolves the
 * item independently, and callers that need a stable item id before registry binding can declare
 * one explicitly. See {@link ExternalItemTarget} for the id/object/supplier timing rules.
 */
public final class ExternalBlockTarget {

    private final @Nullable Identifier explicitId;
    private final @Nullable Identifier explicitItemId;
    private final @Nullable Supplier<? extends Block> block;
    private final @Nullable Supplier<? extends ItemLike> item;
    private volatile @Nullable Identifier resolvedBlockId;
    private volatile @Nullable Identifier resolvedItemId;

    /** Creates a target backed only by a registry id; its item id defaults to the same id. */
    public ExternalBlockTarget(Identifier id) {
        this(Objects.requireNonNull(id, "id"), Objects.requireNonNull(id, "id"), null, null);
    }

    private ExternalBlockTarget(
                                @Nullable Identifier explicitId,
                                @Nullable Identifier explicitItemId,
                                @Nullable Supplier<? extends Block> block,
                                @Nullable Supplier<? extends ItemLike> item) {
        if (explicitId == null && block == null) {
            throw new IllegalArgumentException("external block target requires an id or a block");
        }
        this.explicitId = explicitId;
        this.explicitItemId = explicitItemId;
        this.block = block;
        this.item = item;
    }

    /** Uses an already available block; its registry id and item id are resolved lazily. */
    public static ExternalBlockTarget of(Block block) {
        Objects.requireNonNull(block, "block");
        return new ExternalBlockTarget(null, null, () -> block, null);
    }

    /** Uses an already available block with an explicit stable block id. */
    public static ExternalBlockTarget of(Identifier id, Block block) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(block, "block");
        return new ExternalBlockTarget(id, null, () -> block, null);
    }

    /** Uses a block supplier with an explicit stable block id. */
    public static ExternalBlockTarget of(Identifier id, Supplier<? extends Block> block) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(block, "block");
        return new ExternalBlockTarget(id, null, block, null);
    }

    /** Uses a block supplier; its registry id and item id are resolved lazily. */
    public static ExternalBlockTarget ofSupplier(Supplier<? extends Block> block) {
        Objects.requireNonNull(block, "block");
        return new ExternalBlockTarget(null, null, block, null);
    }

    /** Uses a block supplier and a separate item supplier with an explicit stable block id. */
    public static ExternalBlockTarget of(
                                         Identifier id, Supplier<? extends Block> block, Supplier<? extends ItemLike> item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(item, "item");
        return new ExternalBlockTarget(id, null, block, item);
    }

    /** Uses a block supplier and an explicit stable item id. */
    public static ExternalBlockTarget of(Identifier id, Identifier itemId, Supplier<? extends Block> block) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(block, "block");
        return new ExternalBlockTarget(id, itemId, block, null);
    }

    /**
     * The block id. Object/supplier targets must be registered before this is called; use
     * {@link #explicitId()} when a caller needs to know whether the id is already available.
     */
    public Identifier id() {
        if (explicitId != null) {
            return explicitId;
        }
        Identifier cached = resolvedBlockId;
        if (cached != null) {
            return cached;
        }
        Supplier<? extends Block> supplier = Objects.requireNonNull(block, "block supplier");
        Block resolved = Objects.requireNonNull(supplier.get(), "external block supplier returned null");
        Identifier key = BuiltInRegistries.BLOCK.getKey(resolved);
        if (key == null) {
            throw new IllegalStateException(
                    "external block target is not registered yet: " + resolved + "; use ExternalTargets.block(id, block) for a stable id before binding");
        }
        resolvedBlockId = key;
        return key;
    }

    /**
     * The item id backing this block form. It is resolved from the block's item unless an explicit
     * item id or item supplier was declared.
     */
    public Identifier itemId() {
        if (explicitItemId != null) {
            return explicitItemId;
        }
        Identifier cached = resolvedItemId;
        if (cached != null) {
            return cached;
        }
        ItemLike resolvedItem;
        if (item != null) {
            resolvedItem = Objects.requireNonNull(item.get(), "external block item supplier returned null");
        } else if (block != null) {
            resolvedItem = Objects.requireNonNull(block.get(), "external block supplier returned null").asItem();
        } else {
            return id();
        }
        Item itemValue = resolvedItem.asItem();
        if (itemValue == Items.AIR) {
            throw new IllegalStateException("external block target does not have an item: " + id());
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(itemValue);
        if (key == null) {
            throw new IllegalStateException(
                    "external block item is not registered yet: " + itemValue + "; use ExternalTargets.block(id, block, item) for a stable item id before binding");
        }
        resolvedItemId = key;
        return key;
    }

    /** The block id when it was declared explicitly; empty for object/supplier targets. */
    public Optional<Identifier> explicitId() {
        return Optional.ofNullable(explicitId);
    }

    /** The item id when it was declared explicitly; empty when it is derived from the block. */
    public Optional<Identifier> explicitItemId() {
        return Optional.ofNullable(explicitItemId);
    }

    /** The block supplier, when the target was declared as an object/supplier rather than an id. */
    public Optional<Supplier<? extends Block>> blockSupplier() {
        return Optional.ofNullable(block);
    }

    /**
     * Supplier suitable for deferred creative-tab or other registry-bound consumers. Id-only
     * targets resolve the item from the registry when the supplier is invoked.
     */
    public Supplier<? extends ItemLike> itemSupplier() {
        if (item != null) {
            return item;
        }
        if (explicitItemId != null) {
            Identifier id = explicitItemId;
            return () -> {
                Item resolved = BuiltInRegistries.ITEM.getValue(id);
                if (resolved == null) {
                    throw new IllegalStateException("external block item target is not registered: " + id);
                }
                return resolved;
            };
        }
        if (block != null) {
            return () -> {
                Block resolved = block.get();
                if (resolved == null) {
                    throw new IllegalStateException("external block target supplier returned null");
                }
                return resolved.asItem();
            };
        }
        throw new IllegalStateException("external block target has no item");
    }

    @Override
    public String toString() {
        return explicitId != null ? explicitId.toString() : "external block target";
    }
}
