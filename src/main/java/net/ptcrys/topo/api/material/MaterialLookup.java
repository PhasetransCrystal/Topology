package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.material.form.MaterialContentKey;
import net.ptcrys.topo.api.material.form.MaterialContentType;
import net.ptcrys.topo.api.material.form.MaterialContentTypes;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormPlan;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, content-type-addressable index over material form outputs.
 *
 * <p>
 * The index is built after material freeze and rebuilt once during runtime, when all deferred
 * registries are bound. Lookups are O(1); item lookups use the open {@link MaterialContentTypes#ITEM}
 * type and ignore data components. Use {@link #byContent} for custom content kinds.
 */
public final class MaterialLookup {

    private static volatile MaterialLookup current = new MaterialLookup(Map.of(), Map.of());

    private final Map<MaterialContentType, Map<Identifier, MaterialBinding>> byType;
    private final Map<Identifier, MaterialBinding> items;

    private MaterialLookup(
                           Map<MaterialContentType, Map<Identifier, MaterialBinding>> byType,
                           Map<Identifier, MaterialBinding> items) {
        this.byType = byType;
        this.items = items;
    }

    public static MaterialLookup current() {
        return current;
    }

    public Optional<MaterialBinding> byContent(MaterialContentType type, Identifier id) {
        Objects.requireNonNull(type, "content type");
        Objects.requireNonNull(id, "content id");
        Map<Identifier, MaterialBinding> entries = byType.get(type);
        return entries == null ? Optional.empty() : Optional.ofNullable(entries.get(id));
    }

    public Optional<MaterialBinding> byContent(MaterialContentKey key) {
        Objects.requireNonNull(key, "content key");
        return byContent(key.type(), key.id());
    }

    public Optional<MaterialBinding> byItem(Identifier itemId) {
        Objects.requireNonNull(itemId, "item id");
        return Optional.ofNullable(items.get(itemId));
    }

    public Optional<MaterialBinding> byItem(ItemLike item) {
        Objects.requireNonNull(item, "item");
        Item value = item.asItem();
        if (value == Items.AIR) {
            return Optional.empty();
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(value);
        return id == null ? Optional.empty() : byItem(id);
    }

    /** Item lookup ignores data components; use the form/content API when components matter. */
    public Optional<MaterialBinding> byItem(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return stack.isEmpty() ? Optional.empty() : byItem(stack.getItem());
    }

    @ApiStatus.Internal
    public static void rebuild(Iterable<Material> materials) {
        Map<MaterialContentType, Map<Identifier, MaterialBinding>> mutable = new HashMap<>();
        for (Material material : materials) {
            for (MaterialForm form : material.forms()) {
                MaterialBinding binding = new MaterialBinding(material, form);
                MaterialFormPlan plan = material.formPlan(form);
                for (MaterialContentKey key : plan.outputs()) {
                    add(mutable, key.type(), key.id(), binding);
                }
                addTargets(mutable, plan.targets(), binding);
            }
        }
        Map<MaterialContentType, Map<Identifier, MaterialBinding>> immutable = new HashMap<>(mutable.size());
        mutable.forEach((type, entries) -> immutable.put(type, Map.copyOf(entries)));
        Map<Identifier, MaterialBinding> immutableItems = immutable.getOrDefault(MaterialContentTypes.ITEM, Map.of());
        current = new MaterialLookup(Map.copyOf(immutable), immutableItems);
    }

    private static void addTargets(Map<MaterialContentType, Map<Identifier, MaterialBinding>> targets,
                                   MaterialFormTargets formTargets, MaterialBinding binding) {
        formTargets.item().ifPresent(target -> addResolved(targets, MaterialContentTypes.ITEM, target::id, binding));
        formTargets.block().ifPresent(target -> {
            addResolved(targets, MaterialContentTypes.BLOCK, target::id, binding);
            addResolved(targets, MaterialContentTypes.ITEM, target::itemId, binding);
        });
        formTargets.fluid().ifPresent(target -> {
            addResolved(targets, MaterialContentTypes.FLUID, target::id, binding);
            target.bucketSupplier().ifPresent(bucket -> addResolved(
                    targets, MaterialContentTypes.ITEM, () -> {
                        ItemLike value = bucket.get();
                        return value.asItem() == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(value.asItem());
                    }, binding));
        });
    }

    private static void addResolved(Map<MaterialContentType, Map<Identifier, MaterialBinding>> targets,
                                    MaterialContentType type, IdentifierSupplier id, MaterialBinding binding) {
        try {
            Identifier resolved = id.get();
            if (resolved != null) {
                add(targets, type, resolved, binding);
            }
        } catch (RuntimeException unavailable) {
            // The runtime rebuild resolves object/supplier targets once registries are bound.
        }
    }

    private static void add(Map<MaterialContentType, Map<Identifier, MaterialBinding>> targets,
                            MaterialContentType type, Identifier id, MaterialBinding binding) {
        Map<Identifier, MaterialBinding> entries = targets.computeIfAbsent(type, ignored -> new HashMap<>());
        MaterialBinding previous = entries.putIfAbsent(id, binding);
        if (previous != null && !previous.equals(binding)) {
            throw new IllegalStateException(
                    "material lookup conflict for " + type.id() + " " + id + ": " + previous.material().id() + "/" + previous.form().id() + " and " + binding.material().id() + "/" + binding.form().id());
        }
    }

    @FunctionalInterface
    private interface IdentifierSupplier {

        Identifier get();
    }
}
