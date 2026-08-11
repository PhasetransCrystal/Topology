package net.ptcrys.topo.apiv2.recipe.content;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
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
import java.util.function.Supplier;

/** Concrete item recipe output with a long logical amount and a count-one identity template. */
public final class OIItemOutput {

    private static final Codec<OIItemOutput> LEGACY_CODEC = ItemStackTemplate.CODEC.xmap(OIItemOutput::new, OIItemOutput::legacyTemplate);
    private static final Codec<OIItemOutput> LONG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStackTemplate.CODEC.fieldOf("template").forGetter(OIItemOutput::template),
            Codec.LONG.fieldOf("amount").forGetter(OIItemOutput::count))
            .apply(instance, OIItemOutput::new));

    /** Existing template-only JSON remains canonical while the amount fits its legacy int field. */
    public static final Codec<OIItemOutput> CODEC = Codec.either(LEGACY_CODEC, LONG_CODEC).xmap(
            encoded -> encoded.map(value -> value, value -> value),
            value -> value.count <= Integer.MAX_VALUE ? Either.left(value) : Either.right(value));

    /** Same-version wire format: identity template followed by the complete logical VAR_LONG. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OIItemOutput> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public OIItemOutput decode(RegistryFriendlyByteBuf buf) {
            return new OIItemOutput(ItemStackTemplate.STREAM_CODEC.decode(buf), buf.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OIItemOutput value) {
            ItemStackTemplate.STREAM_CODEC.encode(buf, value.template());
            buf.writeVarLong(value.count());
        }
    };

    private final Supplier<ItemStackTemplate> templateSupplier;
    private final long count;
    private final Object contentKey;
    private volatile @Nullable ItemStackTemplate resolvedTemplate;
    private volatile @Nullable ItemResource resolvedResource;

    public OIItemOutput(ItemStackTemplate template) {
        this(template, Objects.requireNonNull(template, "OIItemOutput.template").count());
    }

    private OIItemOutput(ItemStackTemplate template, long count) {
        validateCount(count);
        ItemStackTemplate identity = identityTemplate(template);
        this.templateSupplier = () -> identity;
        this.count = count;
        this.contentKey = new ItemContentKey(identity, count);
        this.resolvedTemplate = identity;
    }

    private OIItemOutput(Supplier<ItemStackTemplate> template, long count, Object contentKey) {
        this.templateSupplier = Objects.requireNonNull(template, "OIItemOutput.template");
        validateCount(count);
        this.count = count;
        this.contentKey = Objects.requireNonNull(contentKey, "OIItemOutput.contentKey");
    }

    public static OIItemOutput of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("OIItemOutput stack must be non-empty");
        }
        ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(stack);
        return new OIItemOutput(template, template.count());
    }

    public static OIItemOutput of(ItemLike item, long count) {
        validateCount(count);
        return new OIItemOutput(new ItemStackTemplate(Objects.requireNonNull(item, "item").asItem(), 1), count);
    }

    public static OIItemOutput of(Supplier<? extends ItemLike> item, long count) {
        validateCount(count);
        Objects.requireNonNull(item, "item");
        return new OIItemOutput(
                () -> new ItemStackTemplate(item.get().asItem(), 1),
                count,
                new LazyItemKey(item, count));
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

    /** Display-only stack; the authoritative amount remains {@link #count()}. */
    public ItemStack stack() {
        return template().withCount((int) Math.min(count, Integer.MAX_VALUE)).create();
    }

    public OIItemOutput withCount(long count) {
        validateCount(count);
        return this.count == count ? this : new OIItemOutput(template(), count);
    }

    public OIItemOutput withScaledCount(double factor) {
        if (factor == 1.0d) {
            return this;
        }
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("factor must be finite and > 0 (was " + factor + ")");
        }
        BigDecimal scaled = BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        try {
            return withCount(scaled.longValueExact());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Scaled item output exceeds long range: " + scaled, exception);
        }
    }

    public OIItemOutput withParallelCount(long factor) {
        if (factor < 1L) {
            throw new IllegalArgumentException("parallel factor must be >= 1 (was " + factor + ")");
        }
        if (factor == 1L) {
            return this;
        }
        if (count > Long.MAX_VALUE / factor) {
            throw new IllegalArgumentException("Scaled item output exceeds long range: " + count + " x " + factor);
        }
        return withCount(count * factor);
    }

    public Object contentKey() {
        return contentKey;
    }

    private ItemStackTemplate legacyTemplate() {
        return template().withCount((int) count);
    }

    private static ItemStackTemplate identityTemplate(ItemStackTemplate template) {
        ItemStackTemplate value = Objects.requireNonNull(template, "OIItemOutput.template");
        if (value.count() <= 0) {
            throw new IllegalArgumentException(
                    "OIItemOutput.template count must be > 0 (was " + value.count() + ")");
        }
        return value.count() == 1 ? value : value.withCount(1);
    }

    private static void validateCount(long count) {
        if (count <= 0L) {
            throw new IllegalArgumentException("OIItemOutput.count must be > 0 (was " + count + ")");
        }
    }

    private record LazyItemKey(Supplier<? extends ItemLike> item, long count) {

        private LazyItemKey {
            Objects.requireNonNull(item, "item");
        }
    }

    private record ItemContentKey(ItemStackTemplate template, long count) {

        private ItemContentKey {
            Objects.requireNonNull(template, "template");
        }
    }
}
