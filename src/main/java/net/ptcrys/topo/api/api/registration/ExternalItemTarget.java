package net.ptcrys.topo.api.api.registration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * An item supplied by another mod or by vanilla and reused as a material form target.
 *
 * <p>
 * The target can be declared by registry id, by an already available {@link ItemLike}, or by a
 * supplier. Id-only and explicit-id targets are always safe to inspect before the item registry is
 * bound. Object/supplier targets resolve their id lazily, so they also work when the declaring mod
 * is loaded but its item has not been bound yet. Tags and creative-tab contributions made by the
 * stock form strategies are deferred to a phase where that resolution is safe.
 */
public final class ExternalItemTarget {

    private final @Nullable Identifier explicitId;
    private final @Nullable Supplier<? extends ItemLike> item;
    private volatile @Nullable Identifier resolvedId;

    /** Creates a target backed only by a registry id. */
    public ExternalItemTarget(Identifier id) {
        this(Objects.requireNonNull(id, "id"), null);
    }

    private ExternalItemTarget(@Nullable Identifier explicitId, @Nullable Supplier<? extends ItemLike> item) {
        if (explicitId == null && item == null) {
            throw new IllegalArgumentException("external item target requires an id or an item");
        }
        this.explicitId = explicitId;
        this.item = item;
    }

    /** Uses an already available item; its registry id is resolved lazily. */
    public static ExternalItemTarget of(ItemLike item) {
        Objects.requireNonNull(item, "item");
        return new ExternalItemTarget(null, () -> item);
    }

    /** Uses an already available item with an explicit stable registry id. */
    public static ExternalItemTarget of(Identifier id, ItemLike item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        return new ExternalItemTarget(id, () -> item);
    }

    /** Uses a supplier; the item and its registry id are resolved lazily. */
    public static ExternalItemTarget ofSupplier(Supplier<? extends ItemLike> item) {
        Objects.requireNonNull(item, "item");
        return new ExternalItemTarget(null, item);
    }

    /** Uses a supplier with an explicit stable registry id. */
    public static ExternalItemTarget ofSupplier(Identifier id, Supplier<? extends ItemLike> item) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(item, "item");
        return new ExternalItemTarget(id, item);
    }

    /**
     * The target id. Object/supplier targets must be registered before this is called; use
     * {@link #explicitId()} when a caller needs to know whether the id is already available.
     */
    public Identifier id() {
        if (explicitId != null) {
            return explicitId;
        }
        Identifier cached = resolvedId;
        if (cached != null) {
            return cached;
        }
        Supplier<? extends ItemLike> supplier = Objects.requireNonNull(item, "item supplier");
        ItemLike value = Objects.requireNonNull(supplier.get(), "external item supplier returned null");
        Item resolved = value.asItem();
        if (resolved == Items.AIR) {
            throw new IllegalStateException("external item target does not resolve to an item: " + value);
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(resolved);
        if (key == null) {
            throw new IllegalStateException(
                    "external item target is not registered yet: " + resolved + "; use ExternalTargets.item(id, item) for a stable id before binding");
        }
        resolvedId = key;
        return key;
    }

    /** The id when it was declared explicitly; empty for object/supplier targets. */
    public Optional<Identifier> explicitId() {
        return Optional.ofNullable(explicitId);
    }

    /**
     * Supplier suitable for deferred creative-tab or other registry-bound consumers. Id-only
     * targets resolve the item from the registry when the supplier is invoked.
     */
    public Supplier<? extends ItemLike> itemSupplier() {
        if (item != null) {
            return item;
        }
        Identifier id = Objects.requireNonNull(explicitId, "explicit id");
        return () -> {
            Item resolved = BuiltInRegistries.ITEM.getValue(id);
            if (resolved == null) {
                throw new IllegalStateException("external item target is not registered: " + id);
            }
            return resolved;
        };
    }

    @Override
    public String toString() {
        return explicitId != null ? explicitId.toString() : "external item target";
    }
}
