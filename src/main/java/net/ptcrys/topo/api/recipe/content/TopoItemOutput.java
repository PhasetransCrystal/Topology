package net.ptcrys.topo.api.recipe.content;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.transfer.item.ItemResource;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Concrete item recipe output with a long logical amount and a count-one identity template. */
public final class TopoItemOutput {

    private static final Codec<TopoItemOutput> LEGACY_CODEC = ItemStackTemplate.CODEC.xmap(TopoItemOutput::new, TopoItemOutput::legacyTemplate);
    private static final Codec<TopoItemOutput> LONG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStackTemplate.CODEC.fieldOf("template").forGetter(TopoItemOutput::template),
            Codec.LONG.fieldOf("amount").forGetter(TopoItemOutput::count),
            Codec.LONG.optionalFieldOf("max_amount").forGetter(output -> output.maxCount() == output.count() ? Optional.empty() : Optional.of(output.maxCount())))
            .apply(instance, (template, amount, maxAmount) -> new TopoItemOutput(template, amount, maxAmount.orElse(amount))));

    /** Existing template-only JSON remains canonical while the amount fits its legacy int field. */
    public static final Codec<TopoItemOutput> CODEC = Codec.either(LEGACY_CODEC, LONG_CODEC).xmap(
            encoded -> encoded.map(value -> value, value -> value),
            value -> value.maxCount == value.count && value.count <= Integer.MAX_VALUE ? Either.left(value) : Either.right(value));

    /** Same-version wire format: identity template followed by the complete logical VAR_LONG. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TopoItemOutput> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public TopoItemOutput decode(RegistryFriendlyByteBuf buf) {
            return new TopoItemOutput(ItemStackTemplate.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TopoItemOutput value) {
            ItemStackTemplate.STREAM_CODEC.encode(buf, value.template());
            buf.writeVarLong(value.count());
            buf.writeVarLong(value.maxCount());
        }
    };

    private final Supplier<ItemStackTemplate> templateSupplier;
    private final long count;
    private final long maxCount;
    private final Object contentKey;
    private volatile @Nullable ItemStackTemplate resolvedTemplate;
    private volatile @Nullable ItemResource resolvedResource;

    public TopoItemOutput(ItemStackTemplate template) {
        this(template, Objects.requireNonNull(template, "TopoItemOutput.template").count());
    }

    private TopoItemOutput(ItemStackTemplate template, long count) {
        this(template, count, count);
    }

    private TopoItemOutput(ItemStackTemplate template, long count, long maxCount) {
        validateRange(count, maxCount);
        ItemStackTemplate identity = identityTemplate(template);
        this.templateSupplier = () -> identity;
        this.count = count;
        this.maxCount = maxCount;
        this.contentKey = new ItemContentKey(identity, count, maxCount);
        this.resolvedTemplate = identity;
    }

    private TopoItemOutput(Supplier<ItemStackTemplate> template, long count, long maxCount, Object contentKey) {
        this.templateSupplier = Objects.requireNonNull(template, "TopoItemOutput.template");
        validateRange(count, maxCount);
        this.count = count;
        this.maxCount = maxCount;
        this.contentKey = Objects.requireNonNull(contentKey, "TopoItemOutput.contentKey");
    }

    public static TopoItemOutput of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("TopoItemOutput stack must be non-empty");
        }
        ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(stack);
        return new TopoItemOutput(template, template.count());
    }

    public static TopoItemOutput of(ItemLike item, long count) {
        return ofRange(item, count, count);
    }

    public static TopoItemOutput ofRange(ItemLike item, long minCount, long maxCount) {
        validateRange(minCount, maxCount);
        return new TopoItemOutput(
                new ItemStackTemplate(Objects.requireNonNull(item, "item").asItem(), 1),
                minCount,
                maxCount);
    }

    public static TopoItemOutput of(Supplier<? extends ItemLike> item, long count) {
        return ofRange(item, count, count);
    }

    public static TopoItemOutput ofRange(Supplier<? extends ItemLike> item, long minCount, long maxCount) {
        validateRange(minCount, maxCount);
        Objects.requireNonNull(item, "item");
        return new TopoItemOutput(
                () -> new ItemStackTemplate(item.get().asItem(), 1),
                minCount,
                maxCount,
                new LazyItemKey(item, minCount, maxCount));
    }

    public ItemStackTemplate template() {
        ItemStackTemplate cached = resolvedTemplate;
        if (cached != null) {
            return cached;
        }
        ItemStackTemplate resolved = identityTemplate(templateSupplier.get());
        resolvedTemplate = resolved;
        return resolved;
    }

    public ItemResource resource() {
        ItemResource cached = resolvedResource;
        if (cached != null) {
            return cached;
        }
        ItemResource resolved = ItemResource.of(template());
        resolvedResource = resolved;
        return resolved;
    }

    public long count() {
        return count;
    }

    public long maxCount() {
        return maxCount;
    }

    /** Roll the effective output amount for one emission. */
    public long rollCount(RandomSource random) {
        if (count == maxCount) {
            return count;
        }
        long span = maxCount - count;
        return count + Long.remainderUnsigned(random.nextLong(), span + 1L);
    }

    /** Display-only stack; the authoritative amount remains {@link #count()}. */
    public ItemStack stack() {
        return template().withCount((int) Math.min(count, Integer.MAX_VALUE)).create();
    }

    public TopoItemOutput withCount(long count) {
        return withCounts(count, count);
    }

    public TopoItemOutput withCounts(long minCount, long maxCount) {
        validateRange(minCount, maxCount);
        return this.count == minCount && this.maxCount == maxCount ? this : new TopoItemOutput(template(), minCount, maxCount);
    }

    public TopoItemOutput withScaledCount(double factor) {
        if (factor == 1.0d) {
            return this;
        }
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("factor must be finite and > 0 (was " + factor + ")");
        }
        long scaledMin = scaleCount(count, factor);
        long scaledMax = scaleCount(maxCount, factor);
        return withCounts(scaledMin, Math.max(scaledMin, scaledMax));
    }

    public TopoItemOutput withParallelCount(long factor) {
        if (factor < 1L) {
            throw new IllegalArgumentException("parallel factor must be >= 1 (was " + factor + ")");
        }
        if (factor == 1L) {
            return this;
        }
        if (maxCount > Long.MAX_VALUE / factor) {
            throw new IllegalArgumentException("Scaled item output exceeds long range: " + maxCount + " x " + factor);
        }
        return withCounts(count * factor, maxCount * factor);
    }

    public Object contentKey() {
        return contentKey;
    }

    private ItemStackTemplate legacyTemplate() {
        return template().withCount((int) count);
    }

    private static ItemStackTemplate identityTemplate(ItemStackTemplate template) {
        ItemStackTemplate value = Objects.requireNonNull(template, "TopoItemOutput.template");
        if (value.count() <= 0) {
            throw new IllegalArgumentException(
                    "TopoItemOutput.template count must be > 0 (was " + value.count() + ")");
        }
        return value.count() == 1 ? value : value.withCount(1);
    }

    private static long scaleCount(long count, double factor) {
        BigDecimal scaled = BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        try {
            return scaled.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Scaled item output exceeds long range: " + scaled, exception);
        }
    }

    private static void validateRange(long minCount, long maxCount) {
        if (minCount <= 0L) {
            throw new IllegalArgumentException("TopoItemOutput.count must be > 0 (was " + minCount + ")");
        }
        if (maxCount < minCount) {
            throw new IllegalArgumentException(
                    "TopoItemOutput.maxCount must be >= count (was " + maxCount + " < " + minCount + ")");
        }
    }

    private record LazyItemKey(Supplier<? extends ItemLike> item, long minCount, long maxCount) {

        private LazyItemKey {
            Objects.requireNonNull(item, "item");
        }
    }

    private record ItemContentKey(ItemStackTemplate template, long minCount, long maxCount) {

        private ItemContentKey {
            Objects.requireNonNull(template, "template");
        }
    }
}
