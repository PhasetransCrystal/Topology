package net.ptcrys.topo.data.material.common;

import net.minecraft.world.item.Item;

import java.util.Objects;

public final class ItemAdditiveData {

    private final Item item;
    private final int count;

    private ItemAdditiveData(Item item, int count) {
        this.item = item;
        this.count = count;
    }

    static ItemAdditiveData create(Item item, int count) {
        Objects.requireNonNull(item, "item");
        if (count <= 0) {
            throw new IllegalArgumentException("additive count must be positive: " + count);
        }
        return new ItemAdditiveData(item, count);
    }

    public Item item() {
        return item;
    }

    public int count() {
        return count;
    }
}
