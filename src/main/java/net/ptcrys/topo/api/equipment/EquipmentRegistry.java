package net.ptcrys.topo.api.equipment;

import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.api.api.builtin.EquipmentDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.material.Material;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Equipment kind table (query + freeze) and the immutable item records produced by one equipment
 * bootstrap pass. Product writes only via {@link EquipmentDomainRegistration#kind}; generated item
 * records are published once by the framework after every strategy has registered.
 */
public final class EquipmentRegistry {

    private static final FreezableStrategyRegistry<Identifier, Equipment, EquipmentStrategy> REGISTRY = FreezableStrategyRegistry.create("equipment");

    private static volatile List<EquipmentItemRecord> itemRecords = List.of();
    private static volatile Map<Identifier, EquipmentItemRecord> itemsById = Map.of();
    private static volatile Map<Material, List<EquipmentItemRecord>> itemsByMaterial = Map.of();
    private static volatile Map<Equipment, List<EquipmentItemRecord>> itemsByEquipment = Map.of();
    private static volatile boolean itemsPublished;

    private EquipmentRegistry() {}

    /** Single write entry for the equipment domain plugin API. */
    @ApiStatus.Internal
    public static Equipment begin(Identifier id, EquipmentStrategy strategy) {
        Equipment equipment = new Equipment(Objects.requireNonNull(id, "id"), strategy);
        return REGISTRY.register(id, equipment, strategy);
    }

    public static Equipment require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<Equipment> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static boolean isItemsPublished() {
        return itemsPublished;
    }

    @ApiStatus.Internal
    public static void freeze() {
        REGISTRY.freeze();
    }

    /**
     * Publishes the complete equipment item set after all strategies have run. Records are indexed
     * by material and by equipment; duplicate item ids or duplicate (equipment, material) pairs
     * fail before any snapshot is visible.
     */
    @ApiStatus.Internal
    public static void publishItems(List<EquipmentItemRecord> records) {
        Objects.requireNonNull(records, "equipment item records");
        if (!REGISTRY.isFrozen()) {
            throw new IllegalStateException("equipment kinds must freeze before item records are published");
        }
        if (itemsPublished) {
            throw new IllegalStateException("equipment item records are already published");
        }
        List<EquipmentItemRecord> snapshot = List.copyOf(records);
        Map<Identifier, EquipmentItemRecord> byItemId = new LinkedHashMap<>();
        Map<EquipmentPair, EquipmentItemRecord> byPair = new LinkedHashMap<>();
        Map<Material, List<EquipmentItemRecord>> byMaterial = new LinkedHashMap<>();
        Map<Equipment, List<EquipmentItemRecord>> byEquipment = new LinkedHashMap<>();
        for (EquipmentItemRecord record : snapshot) {
            if (REGISTRY.get(record.equipment().id()) != record.equipment()) {
                throw new IllegalStateException(
                        "equipment item record references unregistered kind " + record.equipment().id());
            }
            EquipmentItemRecord previousId = byItemId.putIfAbsent(record.entry().identifier(), record);
            if (previousId != null) {
                throw new IllegalStateException(
                        "equipment item " + record.entry().identifier() + " is produced by both " + describe(previousId) + " and " + describe(record));
            }
            EquipmentPair pair = new EquipmentPair(record.equipment(), record.material());
            if (byPair.putIfAbsent(pair, record) != null) {
                throw new IllegalStateException(
                        "equipment " + record.equipment().id() + " produced more than one item for material " + record.material().id());
            }
            byMaterial.computeIfAbsent(record.material(), ignored -> new ArrayList<>()).add(record);
            byEquipment.computeIfAbsent(record.equipment(), ignored -> new ArrayList<>()).add(record);
        }
        itemsById = Collections.unmodifiableMap(byItemId);
        itemsByMaterial = freezeIndex(byMaterial);
        itemsByEquipment = freezeIndex(byEquipment);
        itemRecords = snapshot;
        itemsPublished = true;
    }

    public static List<EquipmentItemRecord> itemRecords() {
        return itemRecords;
    }

    public static Optional<EquipmentItemRecord> itemFor(Identifier itemId) {
        return Optional.ofNullable(itemsById.get(Objects.requireNonNull(itemId, "item id")));
    }

    public static List<EquipmentItemRecord> itemsFor(Material material) {
        return itemsByMaterial.getOrDefault(Objects.requireNonNull(material, "material"), List.of());
    }

    public static List<EquipmentItemRecord> itemsFor(Equipment equipment) {
        return itemsByEquipment.getOrDefault(Objects.requireNonNull(equipment, "equipment"), List.of());
    }

    private static <K> Map<K, List<EquipmentItemRecord>> freezeIndex(Map<K, List<EquipmentItemRecord>> index) {
        Map<K, List<EquipmentItemRecord>> frozen = new LinkedHashMap<>(index.size());
        index.forEach((key, records) -> frozen.put(key, List.copyOf(records)));
        return Collections.unmodifiableMap(frozen);
    }

    private static String describe(EquipmentItemRecord record) {
        return record.equipment().id() + "/" + record.material().id();
    }

    private record EquipmentPair(Equipment equipment, Material material) {}

    public record EquipmentItemRecord(Equipment equipment, Material material, ItemEntry<Item> entry) {

        public EquipmentItemRecord {
            Objects.requireNonNull(equipment, "equipment");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(entry, "item entry");
        }
    }
}
