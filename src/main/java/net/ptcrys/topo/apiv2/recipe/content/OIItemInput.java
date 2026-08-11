package net.ptcrys.topo.apiv2.recipe.content;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.transfer.item.ItemResource;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Item recipe input content.
 *
 * <p>
 * Recipe quantities are {@code long}. Vanilla {@link ItemStackTemplate} instances carry only
 * resource identity and are normalized to count one; transfer calls are chunked at the capability
 * boundary where NeoForge still accepts {@code int} amounts.
 */
public sealed interface OIItemInput
                                    permits OIItemInput.Resource, OIItemInput.Tag, OIItemInput.AnyOf, OIItemInput.Unconsumed {

    Codec<OIItemInput> CODEC = Codec.STRING.dispatch("type", OIItemInput::type, OIItemInput::codecFor);

    StreamCodec<RegistryFriendlyByteBuf, OIItemInput> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public OIItemInput decode(RegistryFriendlyByteBuf buf) {
            int kind = buf.readVarInt();
            return switch (kind) {
                case 0 -> new Resource(ItemStackTemplate.STREAM_CODEC.decode(buf), buf.readVarLong());
                case 1 -> new Tag(TagKey.streamCodec(Registries.ITEM).decode(buf), buf.readVarLong());
                case 2 -> new AnyOf(readTemplates(buf), buf.readVarLong());
                case 3 -> new Unconsumed(ItemStackTemplate.STREAM_CODEC.decode(buf), buf.readVarLong());
                default -> throw new IllegalArgumentException("Unknown item input kind: " + kind);
            };
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, OIItemInput value) {
            switch (value) {
                case Resource resource -> {
                    buf.writeVarInt(0);
                    ItemStackTemplate.STREAM_CODEC.encode(buf, resource.template());
                    buf.writeVarLong(resource.count());
                }
                case Tag tag -> {
                    buf.writeVarInt(1);
                    TagKey.streamCodec(Registries.ITEM).encode(buf, tag.tag());
                    buf.writeVarLong(tag.count());
                }
                case AnyOf anyOf -> {
                    buf.writeVarInt(2);
                    writeTemplates(buf, anyOf.templates());
                    buf.writeVarLong(anyOf.count());
                }
                case Unconsumed unconsumed -> {
                    buf.writeVarInt(3);
                    ItemStackTemplate.STREAM_CODEC.encode(buf, unconsumed.template());
                    buf.writeVarLong(unconsumed.count());
                }
            }
        }
    };

    long count();

    String type();

    boolean matchesResource(ItemResource resource);

    default boolean matches(ItemResource resource, long amount) {
        return amount >= count() && matchesResource(resource);
    }

    default boolean consumesOnMatch() {
        return true;
    }

    /** Pure replacement of the logical amount; the identity template remains count one. */
    OIItemInput withCount(long count);

    /** Decimal performance scaling. Dynamic parallel uses the exact integer path below. */
    default OIItemInput withScaledCount(double factor) {
        if (factor == 1.0d || !consumesOnMatch()) {
            return this;
        }
        return withCount(scaleAmount(count(), factor, "item input"));
    }

    /** Exact, overflow-checked integer scaling for parallel batches. */
    default OIItemInput withParallelCount(long factor) {
        if (factor < 1L) {
            throw new IllegalArgumentException("parallel factor must be >= 1 (was " + factor + ")");
        }
        if (factor == 1L || !consumesOnMatch()) {
            return this;
        }
        return withCount(multiplyAmount(count(), factor, "item input"));
    }

    List<ItemStack> displayStacks();

    static OIItemInput of(ItemLike item, long count) {
        validateCount(count, "OIItemInput.Resource");
        return new Resource(new ItemStackTemplate(Objects.requireNonNull(item, "item").asItem(), 1), count);
    }

    static OIItemInput of(Supplier<? extends ItemLike> item, long count) {
        validateCount(count, "OIItemInput.Resource");
        Objects.requireNonNull(item, "item");
        return Resource.lazy(
                () -> new ItemStackTemplate(item.get().asItem(), 1),
                count,
                new LazyItemKey(item, count));
    }

    static OIItemInput of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("OIItemInput stack must be non-empty");
        }
        ItemStackTemplate template = ItemStackTemplate.fromNonEmptyStack(stack);
        return new Resource(template, template.count());
    }

    static OIItemInput tag(TagKey<Item> tag, long count) {
        return new Tag(tag, count);
    }

    /**
     * Match-only input: must be present for the recipe, but is not extracted on commit.
     * Registry entries which also implement {@link Supplier} stay unresolved until runtime.
     */
    static OIItemInput unconsumed(ItemLike item) {
        Objects.requireNonNull(item, "item");
        if (item instanceof Supplier<?> supplier) {
            return unconsumed(() -> {
                Object value = supplier.get();
                if (value instanceof ItemLike itemLike) {
                    return itemLike;
                }
                throw new IllegalStateException(
                        "Supplier ItemLike produced non-ItemLike: " + (value == null ? "null" : value.getClass()));
            });
        }
        return new Unconsumed(new ItemStackTemplate(item.asItem(), 1), 1L);
    }

    static OIItemInput unconsumed(Supplier<? extends ItemLike> item) {
        Objects.requireNonNull(item, "item");
        return Unconsumed.lazy(
                () -> new ItemStackTemplate(item.get().asItem(), 1),
                1L,
                new LazyItemKey(item, 1L));
    }

    /** @deprecated Use {@link #unconsumed(ItemLike)}. */
    @Deprecated(forRemoval = false)
    static OIItemInput catalyst(ItemLike item) {
        return unconsumed(item);
    }

    /** @deprecated Use {@link #unconsumed(Supplier)}. */
    @Deprecated(forRemoval = false)
    static OIItemInput catalyst(Supplier<? extends ItemLike> item) {
        return unconsumed(item);
    }

    static OIItemInput fromIngredient(Ingredient ingredient, long count) {
        Objects.requireNonNull(ingredient, "ingredient");
        validateCount(count, "OIItemInput.AnyOf");
        LinkedHashSet<ItemStackTemplate> templates = new LinkedHashSet<>();
        ingredient.getValues().forEach(holder -> {
            if (holder.value() != Items.AIR) {
                templates.add(new ItemStackTemplate(holder, 1, DataComponentPatch.EMPTY));
            }
        });
        if (templates.size() == 1) {
            return new Resource(templates.iterator().next(), count);
        }
        return new AnyOf(List.copyOf(templates), count);
    }

    private static MapCodec<? extends OIItemInput> codecFor(String type) {
        return switch (type) {
            case "resource" -> Resource.MAP_CODEC;
            case "tag" -> Tag.MAP_CODEC;
            case "any_of" -> AnyOf.MAP_CODEC;
            case "unconsumed", "catalyst" -> Unconsumed.MAP_CODEC;
            default -> throw new IllegalArgumentException("Unknown item input type: " + type);
        };
    }

    private static List<ItemStackTemplate> readTemplates(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        if (size < 1) {
            throw new IllegalArgumentException("Item input alternative count must be positive: " + size);
        }
        List<ItemStackTemplate> templates = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            templates.add(ItemStackTemplate.STREAM_CODEC.decode(buf));
        }
        return templates;
    }

    private static void writeTemplates(RegistryFriendlyByteBuf buf, List<ItemStackTemplate> templates) {
        buf.writeVarInt(templates.size());
        for (ItemStackTemplate template : templates) {
            ItemStackTemplate.STREAM_CODEC.encode(buf, template);
        }
    }

    private static ItemStackTemplate identityTemplate(ItemStackTemplate template, String owner) {
        ItemStackTemplate value = Objects.requireNonNull(template, owner + ".template");
        if (value.count() <= 0) {
            throw new IllegalArgumentException(owner + ".template count must be > 0 (was " + value.count() + ")");
        }
        return value.count() == 1 ? value : value.withCount(1);
    }

    private static ItemStackTemplate legacyTemplate(ItemStackTemplate identity, long count) {
        return count <= Integer.MAX_VALUE ? identity.withCount((int) count) : identity;
    }

    private static long decodedAmount(ItemStackTemplate template, long amount, String owner) {
        long decoded = amount == 0L ? template.count() : amount;
        validateCount(decoded, owner);
        return decoded;
    }

    private static long encodedAmount(long count) {
        return count <= Integer.MAX_VALUE ? 0L : count;
    }

    private static int displayCount(long count) {
        return (int) Math.min(count, Integer.MAX_VALUE);
    }

    private static void validateCount(long count, String owner) {
        if (count <= 0L) {
            throw new IllegalArgumentException(owner + ".count must be > 0 (was " + count + ")");
        }
    }

    private static long multiplyAmount(long amount, long factor, String owner) {
        if (amount > Long.MAX_VALUE / factor) {
            throw new IllegalArgumentException(owner + " amount exceeds long range: " + amount + " x " + factor);
        }
        return amount * factor;
    }

    private static long scaleAmount(long amount, double factor, String owner) {
        if (!Double.isFinite(factor) || factor <= 0.0d) {
            throw new IllegalArgumentException("factor must be finite and > 0 (was " + factor + ")");
        }
        BigDecimal scaled = BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(factor))
                .setScale(0, RoundingMode.HALF_UP)
                .max(BigDecimal.ONE);
        try {
            return scaled.longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(owner + " amount exceeds long range: " + scaled, exception);
        }
    }

    final class Resource implements OIItemInput {

        static final MapCodec<Resource> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStackTemplate.CODEC.fieldOf("template").forGetter(Resource::encodedTemplate),
                Codec.LONG.optionalFieldOf("amount", 0L).forGetter(Resource::encodedAmount))
                .apply(instance, Resource::decode));

        private final Supplier<ItemStackTemplate> templateSupplier;
        private final long count;
        private final Object contentKey;
        private volatile @Nullable ItemStackTemplate resolvedTemplate;
        private volatile @Nullable ItemResource resolvedResource;

        public Resource(ItemStackTemplate template) {
            this(template, Objects.requireNonNull(template, "OIItemInput.Resource.template").count());
        }

        private Resource(ItemStackTemplate template, long count) {
            validateCount(count, "OIItemInput.Resource");
            ItemStackTemplate identity = identityTemplate(template, "OIItemInput.Resource");
            this.templateSupplier = () -> identity;
            this.count = count;
            this.contentKey = new ItemContentKey(identity, count);
            this.resolvedTemplate = identity;
        }

        private Resource(Supplier<ItemStackTemplate> template, long count, Object contentKey) {
            this.templateSupplier = Objects.requireNonNull(template, "OIItemInput.Resource.template");
            validateCount(count, "OIItemInput.Resource");
            this.count = count;
            this.contentKey = Objects.requireNonNull(contentKey, "OIItemInput.Resource.contentKey");
        }

        private static Resource decode(ItemStackTemplate template, long amount) {
            return new Resource(template, decodedAmount(template, amount, "OIItemInput.Resource"));
        }

        static Resource lazy(Supplier<ItemStackTemplate> template, long count, Object contentKey) {
            return new Resource(template, count, contentKey);
        }

        public ItemStackTemplate template() {
            ItemStackTemplate cached = resolvedTemplate;
            if (cached != null) {
                return cached;
            }
            ItemStackTemplate resolved = identityTemplate(templateSupplier.get(), "OIItemInput.Resource");
            resolvedTemplate = resolved;
            return resolved;
        }

        private ItemStackTemplate encodedTemplate() {
            return legacyTemplate(template(), count);
        }

        private long encodedAmount() {
            return OIItemInput.encodedAmount(count);
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public Resource withCount(long count) {
            validateCount(count, "OIItemInput.Resource");
            return this.count == count ? this : new Resource(template(), count);
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

        public Object contentKey() {
            return contentKey;
        }

        @Override
        public String type() {
            return "resource";
        }

        @Override
        public boolean matchesResource(ItemResource resource) {
            return resource.matches(template());
        }

        @Override
        public List<ItemStack> displayStacks() {
            return List.of(template().withCount(displayCount(count)).create());
        }
    }

    final class Unconsumed implements OIItemInput {

        static final MapCodec<Unconsumed> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStackTemplate.CODEC.fieldOf("template").forGetter(Unconsumed::encodedTemplate),
                Codec.LONG.optionalFieldOf("amount", 0L).forGetter(Unconsumed::encodedAmount))
                .apply(instance, Unconsumed::decode));

        private final Supplier<ItemStackTemplate> templateSupplier;
        private final long count;
        private final Object contentKey;
        private volatile @Nullable ItemStackTemplate resolvedTemplate;
        private volatile @Nullable ItemResource resolvedResource;

        public Unconsumed(ItemStackTemplate template) {
            this(template, Objects.requireNonNull(template, "OIItemInput.Unconsumed.template").count());
        }

        private Unconsumed(ItemStackTemplate template, long count) {
            validateCount(count, "OIItemInput.Unconsumed");
            ItemStackTemplate identity = identityTemplate(template, "OIItemInput.Unconsumed");
            this.templateSupplier = () -> identity;
            this.count = count;
            this.contentKey = new ItemContentKey(identity, count);
            this.resolvedTemplate = identity;
        }

        private Unconsumed(Supplier<ItemStackTemplate> template, long count, Object contentKey) {
            this.templateSupplier = Objects.requireNonNull(template, "OIItemInput.Unconsumed.template");
            validateCount(count, "OIItemInput.Unconsumed");
            this.count = count;
            this.contentKey = Objects.requireNonNull(contentKey, "OIItemInput.Unconsumed.contentKey");
        }

        private static Unconsumed decode(ItemStackTemplate template, long amount) {
            return new Unconsumed(template, decodedAmount(template, amount, "OIItemInput.Unconsumed"));
        }

        static Unconsumed lazy(Supplier<ItemStackTemplate> template, long count, Object contentKey) {
            return new Unconsumed(template, count, contentKey);
        }

        public ItemStackTemplate template() {
            ItemStackTemplate cached = resolvedTemplate;
            if (cached != null) {
                return cached;
            }
            ItemStackTemplate resolved = identityTemplate(templateSupplier.get(), "OIItemInput.Unconsumed");
            resolvedTemplate = resolved;
            return resolved;
        }

        private ItemStackTemplate encodedTemplate() {
            return legacyTemplate(template(), count);
        }

        private long encodedAmount() {
            return OIItemInput.encodedAmount(count);
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

        public Object contentKey() {
            return contentKey;
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public Unconsumed withCount(long count) {
            validateCount(count, "OIItemInput.Unconsumed");
            return this.count == count ? this : new Unconsumed(template(), count);
        }

        @Override
        public String type() {
            return "unconsumed";
        }

        @Override
        public boolean matchesResource(ItemResource resource) {
            return resource.matches(template());
        }

        @Override
        public boolean consumesOnMatch() {
            return false;
        }

        @Override
        public List<ItemStack> displayStacks() {
            return List.of(template().withCount(displayCount(count)).create());
        }
    }

    record Tag(TagKey<Item> tag, long count) implements OIItemInput {

        static final MapCodec<Tag> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(Tag::tag),
                Codec.LONG.optionalFieldOf("count", 1L).forGetter(Tag::count))
                .apply(instance, Tag::new));

        public Tag {
            Objects.requireNonNull(tag, "OIItemInput.Tag.tag");
            validateCount(count, "OIItemInput.Tag");
        }

        @Override
        public Tag withCount(long count) {
            validateCount(count, "OIItemInput.Tag");
            return this.count == count ? this : new Tag(tag, count);
        }

        @Override
        public String type() {
            return "tag";
        }

        @Override
        public boolean matchesResource(ItemResource resource) {
            return resource.typeHolder().is(tag);
        }

        @Override
        public List<ItemStack> displayStacks() {
            int shown = displayCount(count);
            return java.util.stream.StreamSupport.stream(
                    BuiltInRegistries.ITEM.getTagOrEmpty(tag).spliterator(), false)
                    .map(holder -> new ItemStack(holder.value(), shown))
                    .toList();
        }
    }

    record AnyOf(List<ItemStackTemplate> templates, long count) implements OIItemInput {

        static final MapCodec<AnyOf> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                ItemStackTemplate.CODEC.listOf().fieldOf("templates").forGetter(AnyOf::encodedTemplates),
                Codec.LONG.optionalFieldOf("amount", 0L).forGetter(AnyOf::encodedAmount))
                .apply(instance, AnyOf::decode));

        public AnyOf(List<ItemStackTemplate> templates) {
            this(templates, commonLegacyCount(templates));
        }

        public AnyOf {
            Objects.requireNonNull(templates, "OIItemInput.AnyOf.templates");
            validateCount(count, "OIItemInput.AnyOf");
            LinkedHashSet<ItemStackTemplate> deduped = new LinkedHashSet<>();
            for (ItemStackTemplate template : templates) {
                deduped.add(identityTemplate(template, "OIItemInput.AnyOf"));
            }
            if (deduped.isEmpty()) {
                throw new IllegalArgumentException("OIItemInput.AnyOf.templates must not be empty");
            }
            templates = List.copyOf(deduped);
        }

        private static AnyOf decode(List<ItemStackTemplate> templates, long amount) {
            long decoded = amount == 0L ? commonLegacyCount(templates) : amount;
            return new AnyOf(templates, decoded);
        }

        private List<ItemStackTemplate> encodedTemplates() {
            if (count > Integer.MAX_VALUE) {
                return templates;
            }
            int legacyCount = (int) count;
            return templates.stream().map(template -> template.withCount(legacyCount)).toList();
        }

        private long encodedAmount() {
            return OIItemInput.encodedAmount(count);
        }

        @Override
        public AnyOf withCount(long count) {
            validateCount(count, "OIItemInput.AnyOf");
            return this.count == count ? this : new AnyOf(templates, count);
        }

        @Override
        public String type() {
            return "any_of";
        }

        @Override
        public boolean matchesResource(ItemResource resource) {
            for (int index = 0; index < templates.size(); index++) {
                if (resource.matches(templates.get(index))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public List<ItemStack> displayStacks() {
            int shown = displayCount(count);
            return templates.stream().map(template -> template.withCount(shown).create()).toList();
        }

        private static long commonLegacyCount(List<ItemStackTemplate> templates) {
            Objects.requireNonNull(templates, "OIItemInput.AnyOf.templates");
            long count = -1L;
            for (ItemStackTemplate template : templates) {
                ItemStackTemplate value = Objects.requireNonNull(
                        template, "OIItemInput.AnyOf.templates must not contain null");
                validateCount(value.count(), "OIItemInput.AnyOf");
                if (count < 0L) {
                    count = value.count();
                } else if (count != value.count()) {
                    throw new IllegalArgumentException("OIItemInput.AnyOf templates must share one count");
                }
            }
            if (count < 0L) {
                throw new IllegalArgumentException("OIItemInput.AnyOf.templates must not be empty");
            }
            return count;
        }
    }

    record LazyItemKey(Supplier<? extends ItemLike> item, long count) {

        public LazyItemKey {
            Objects.requireNonNull(item, "item");
        }
    }

    record ItemContentKey(ItemStackTemplate template, long count) {

        public ItemContentKey {
            Objects.requireNonNull(template, "template");
        }
    }
}
