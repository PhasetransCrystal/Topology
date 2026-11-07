package net.ptcrys.topo.api.api.registration;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A fluid supplied by another mod or by vanilla and reused as a material form target.
 *
 * <p>
 * The target follows the same id/object/supplier timing rules as {@link ExternalItemTarget}.
 * Bucket items are optional: when supplied, the stock fluid form can add the external bucket to the
 * form's creative tab; when omitted, the external owner keeps responsibility for its bucket.
 */
public final class ExternalFluidTarget {

    private final @Nullable Identifier explicitId;
    private final @Nullable Supplier<? extends Fluid> fluid;
    private final @Nullable Supplier<? extends ItemLike> bucket;
    private volatile @Nullable Identifier resolvedId;

    /** Creates a target backed only by a fluid registry id. */
    public ExternalFluidTarget(Identifier id) {
        this(Objects.requireNonNull(id, "id"), null, null);
    }

    private ExternalFluidTarget(
                                @Nullable Identifier explicitId,
                                @Nullable Supplier<? extends Fluid> fluid,
                                @Nullable Supplier<? extends ItemLike> bucket) {
        if (explicitId == null && fluid == null) {
            throw new IllegalArgumentException("external fluid target requires an id or a fluid");
        }
        this.explicitId = explicitId;
        this.fluid = fluid;
        this.bucket = bucket;
    }

    /** Uses an already available fluid; its registry id is resolved lazily. */
    public static ExternalFluidTarget of(Fluid fluid) {
        Objects.requireNonNull(fluid, "fluid");
        return new ExternalFluidTarget(null, () -> fluid, null);
    }

    /** Uses an already available fluid with an explicit stable registry id. */
    public static ExternalFluidTarget of(Identifier id, Fluid fluid) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fluid, "fluid");
        return new ExternalFluidTarget(id, () -> fluid, null);
    }

    /** Uses a fluid supplier; the fluid and its registry id are resolved lazily. */
    public static ExternalFluidTarget ofSupplier(Supplier<? extends Fluid> fluid) {
        Objects.requireNonNull(fluid, "fluid");
        return new ExternalFluidTarget(null, fluid, null);
    }

    /** Uses a fluid supplier with an explicit stable registry id. */
    public static ExternalFluidTarget ofSupplier(Identifier id, Supplier<? extends Fluid> fluid) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fluid, "fluid");
        return new ExternalFluidTarget(id, fluid, null);
    }

    /** Uses a fluid supplier plus the external bucket item used for creative-tab contribution. */
    public static ExternalFluidTarget of(
                                         Identifier id, Supplier<? extends Fluid> fluid, Supplier<? extends ItemLike> bucket) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(bucket, "bucket");
        return new ExternalFluidTarget(id, fluid, bucket);
    }

    /** The fluid id; object/supplier targets must be registered before this is called. */
    public Identifier id() {
        if (explicitId != null) {
            return explicitId;
        }
        Identifier cached = resolvedId;
        if (cached != null) {
            return cached;
        }
        Supplier<? extends Fluid> supplier = Objects.requireNonNull(fluid, "fluid supplier");
        Fluid resolved = Objects.requireNonNull(supplier.get(), "external fluid supplier returned null");
        Identifier key = BuiltInRegistries.FLUID.getKey(resolved);
        if (key == null) {
            throw new IllegalStateException(
                    "external fluid target is not registered yet: " + resolved + "; use ExternalTargets.fluid(id, fluid) for a stable id before binding");
        }
        resolvedId = key;
        return key;
    }

    /** The id when it was declared explicitly; empty for object/supplier targets. */
    public Optional<Identifier> explicitId() {
        return Optional.ofNullable(explicitId);
    }

    /** Supplier suitable for deferred registry-bound consumers. */
    public Supplier<? extends Fluid> fluidSupplier() {
        if (fluid != null) {
            return fluid;
        }
        Identifier id = Objects.requireNonNull(explicitId, "explicit id");
        return () -> {
            Fluid resolved = BuiltInRegistries.FLUID.getValue(id);
            if (resolved == null) {
                throw new IllegalStateException("external fluid target is not registered: " + id);
            }
            return resolved;
        };
    }

    /** Optional external bucket item, used only for creative-tab contribution. */
    public Optional<Supplier<? extends ItemLike>> bucketSupplier() {
        return Optional.ofNullable(bucket);
    }

    @Override
    public String toString() {
        return explicitId != null ? explicitId.toString() : "external fluid target";
    }
}
