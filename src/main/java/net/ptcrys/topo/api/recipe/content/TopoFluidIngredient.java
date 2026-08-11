package net.ptcrys.topo.api.recipe.content;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.function.Supplier;

/** Exact fluid identity plus a long logical recipe amount. */
public final class TopoFluidIngredient {

    private static final Codec<TopoFluidIngredient> LEGACY_CODEC = FluidStackTemplate.CODEC.xmap(TopoFluidIngredient::new, TopoFluidIngredient::legacyTemplate);
    private static final Codec<TopoFluidIngredient> LONG_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FluidStackTemplate.CODEC.fieldOf("template").forGetter(TopoFluidIngredient::template),
            Codec.LONG.fieldOf("amount").forGetter(TopoFluidIngredient::amount))
            .apply(instance, TopoFluidIngredient::new));

    /** Existing template-only JSON remains canonical for amounts representable by the old schema. */
    public static final Codec<TopoFluidIngredient> CODEC = Codec.either(LEGACY_CODEC, LONG_CODEC).xmap(
            encoded -> encoded.map(value -> value, value -> value),
            value -> value.amount() <= Integer.MAX_VALUE ? Either.left(value) : Either.right(value));

    /** Same-version wire format: identity template followed by the complete logical VAR_LONG. */
    public static final StreamCodec<RegistryFriendlyByteBuf, TopoFluidIngredient> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public TopoFluidIngredient decode(RegistryFriendlyByteBuf buf) {
            return new TopoFluidIngredient(FluidStackTemplate.STREAM_CODEC.decode(buf), buf.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, TopoFluidIngredient value) {
            FluidStackTemplate.STREAM_CODEC.encode(buf, value.template());
            buf.writeVarLong(value.amount());
        }
    };

    private final Supplier<FluidStackTemplate> templateSupplier;
    /** Zero only for the compatibility lazy factory whose amount is obtained on first resolution. */
    private final long declaredAmount;
    private final Object contentKey;
    private volatile @Nullable FluidStackTemplate resolvedTemplate;
    private volatile @Nullable FluidResource resolvedResource;
    private volatile long resolvedLegacyAmount;

    public TopoFluidIngredient(FluidStackTemplate template) {
        this(template, Objects.requireNonNull(template, "TopoFluidIngredient.template").amount());
    }

    public TopoFluidIngredient(FluidStackTemplate template, long amount) {
        validateAmount(amount);
        FluidStackTemplate identity = identityTemplate(template);
        this.templateSupplier = () -> identity;
        this.declaredAmount = amount;
        this.contentKey = new FluidContentKey(identity, amount);
        this.resolvedTemplate = identity;
    }

    /** Runtime convenience: wrap a live stack and retain its amount as a long logical quantity. */
    public TopoFluidIngredient(FluidStack fluid) {
        this(FluidStackTemplate.fromNonEmptyStack(fluid));
    }

    private TopoFluidIngredient(
                                Supplier<FluidStackTemplate> template,
                                long declaredAmount,
                                Object contentKey) {
        this.templateSupplier = Objects.requireNonNull(template, "TopoFluidIngredient.template");
        if (declaredAmount < 0L) {
            throw new IllegalArgumentException("TopoFluidIngredient.amount must not be negative");
        }
        if (declaredAmount > 0L) {
            validateAmount(declaredAmount);
        }
        this.declaredAmount = declaredAmount;
        this.contentKey = Objects.requireNonNull(contentKey, "TopoFluidIngredient.contentKey");
    }

    /**
     * Legacy lazy factory. The amount remains unresolved with the template and is memoized only
     * after the supplier first succeeds.
     */
    public static TopoFluidIngredient lazy(Supplier<FluidStackTemplate> template) {
        return new TopoFluidIngredient(template, 0L, template);
    }

    /** Legacy lazy factory with a caller-owned identity key. */
    public static TopoFluidIngredient lazy(Supplier<FluidStackTemplate> template, Object contentKey) {
        return new TopoFluidIngredient(template, 0L, contentKey);
    }

    /** Preferred lazy factory: the logical amount is known without resolving the fluid holder. */
    public static TopoFluidIngredient lazy(
                                           Supplier<FluidStackTemplate> template,
                                           long amount,
                                           Object contentKey) {
        return new TopoFluidIngredient(template, amount, contentKey);
    }

    public FluidStackTemplate template() {
        FluidStackTemplate cached = resolvedTemplate;
        if (cached != null) {
            return cached;
        }
        FluidStackTemplate supplied = Objects.requireNonNull(templateSupplier.get(), "TopoFluidIngredient.template");
        if (supplied.amount() <= 0) {
            throw new IllegalArgumentException(
                    "TopoFluidIngredient.template amount must be > 0 (was " + supplied.amount() + ")");
        }
        FluidStackTemplate resolved = identityTemplate(supplied);
        if (declaredAmount == 0L) {
            resolvedLegacyAmount = supplied.amount();
        }
        resolvedTemplate = resolved;
        return resolved;
    }

    /** Required amount in millibuckets. */
    public long amount() {
        if (declaredAmount > 0L) {
            return declaredAmount;
        }
        long cached = resolvedLegacyAmount;
        if (cached > 0L) {
            return cached;
        }
        template();
        return resolvedLegacyAmount;
    }

    /** Pure replacement of the logical amount; fluid/components identity is unchanged. */
    public TopoFluidIngredient withAmount(long amount) {
        validateAmount(amount);
        return amount() == amount ? this : new TopoFluidIngredient(template(), amount);
    }

    public TopoFluidIngredient withScaledAmount(double factor) {
        if (factor == 1.0d) {
            return this;
        }
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("factor must be finite and > 0 (was " + factor + ")");
        }
        BigDecimal scaled = BigDecimal.valueOf(amount())
                .multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        try {
            return withAmount(scaled.longValueExact());
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Scaled fluid amount exceeds long range: " + scaled, exception);
        }
    }

    public TopoFluidIngredient withParallelAmount(long factor) {
        if (factor < 1L) {
            throw new IllegalArgumentException("parallel factor must be >= 1 (was " + factor + ")");
        }
        if (factor == 1L) {
            return this;
        }
        long amount = amount();
        if (amount > Long.MAX_VALUE / factor) {
            throw new IllegalArgumentException("Scaled fluid amount exceeds long range: " + amount + " x " + factor);
        }
        return withAmount(amount * factor);
    }

    /** Display-only live stack; the authoritative logical amount remains {@link #amount()}. */
    public FluidStack fluid() {
        return template().withAmount((int) Math.min(amount(), Integer.MAX_VALUE)).create();
    }

    public FluidResource resource() {
        FluidResource cached = resolvedResource;
        if (cached != null) {
            return cached;
        }
        FluidResource resolved = FluidResource.of(template());
        resolvedResource = resolved;
        return resolved;
    }

    public boolean test(FluidStack stack) {
        return !stack.isEmpty() && FluidStack.isSameFluidSameComponents(stack, fluid()) && stack.getAmount() >= amount();
    }

    public Object contentKey() {
        return contentKey;
    }

    private FluidStackTemplate legacyTemplate() {
        return template().withAmount((int) amount());
    }

    private static FluidStackTemplate identityTemplate(FluidStackTemplate template) {
        FluidStackTemplate value = Objects.requireNonNull(template, "TopoFluidIngredient.template");
        if (value.amount() <= 0) {
            throw new IllegalArgumentException(
                    "TopoFluidIngredient.template amount must be > 0 (was " + value.amount() + ")");
        }
        return value.amount() == 1 ? value : value.withAmount(1);
    }

    private static void validateAmount(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("TopoFluidIngredient.amount must be > 0 (was " + amount + ")");
        }
    }

    private record FluidContentKey(FluidStackTemplate template, long amount) {

        private FluidContentKey {
            Objects.requireNonNull(template, "template");
        }
    }
}
