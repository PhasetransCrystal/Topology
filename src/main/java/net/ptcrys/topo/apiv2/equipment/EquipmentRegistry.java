package net.ptcrys.topo.apiv2.equipment;

import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.apiv2.material.Material;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Equipment kind table (query + freeze + item records). Product writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.EquipmentDomainRegistration#kind}.
 */
public final class EquipmentRegistry {

    private static final FreezableStrategyRegistry<Identifier, Equipment, EquipmentStrategy> REGISTRY = FreezableStrategyRegistry.create("equipment");

    private static final List<EquipmentItemRecord> ITEM_RECORDS = new ArrayList<>();

    private EquipmentRegistry() {}

    /** Single write entry for the equipment domain plugin API. */
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

    public static void freeze() {
        REGISTRY.freeze();
    }

    public static void recordItem(Equipment equipment, Material material, ItemEntry<Item> entry) {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(entry, "entry");
        ITEM_RECORDS.add(new EquipmentItemRecord(equipment, material, entry));
    }

    public static List<EquipmentItemRecord> itemRecords() {
        return Collections.unmodifiableList(ITEM_RECORDS);
    }

    public record EquipmentItemRecord(Equipment equipment, Material material, ItemEntry<Item> entry) {}
}
