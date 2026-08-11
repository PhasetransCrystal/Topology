package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.world.item.Item;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Owner of per-item machine interactions: one behavior per item, registered after items bind
 * (common setup), frozen before play. Keyed by item instance like {@code ItemTooltipUis} — the
 * lookup runs on every machine right-click, so it must be one frozen-map read.
 */
public final class MachineItemBehaviors {

    private static final FreezableStrategyRegistry<Item, MachineItemBehavior, MachineItemBehavior> REGISTRY = FreezableStrategyRegistry.create("machine item behaviors");

    private MachineItemBehaviors() {}

    public static void register(Item item, MachineItemBehavior behavior) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(behavior, "behavior");
        REGISTRY.register(item, behavior, behavior);
    }

    public static @Nullable MachineItemBehavior find(Item item) {
        return REGISTRY.get(item);
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
