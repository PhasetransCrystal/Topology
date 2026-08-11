package net.ptcrys.topo.integration.jei.ae2;

import net.minecraft.world.item.Item;

import appeng.api.stacks.AEItemKey;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.me.items.PatternEncodingTermMenu;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Reads the client-side ME network repository of an open {@link PatternEncodingTermMenu} and
 * aggregates stored amount / craftability per candidate {@link Item}. Used by the pattern builder
 * popup to color hatch candidates by availability. Returns an empty map when the client repo is
 * not yet synced (e.g. the terminal just opened).
 */
public final class MeStockQuerier {

    private MeStockQuerier() {}

    public record StockEntry(long available, boolean craftable) {}

    public static Map<Item, StockEntry> query(
                                              PatternEncodingTermMenu menu, Set<Item> candidateItems) {
        IClientRepo repo = menu.getClientRepo();
        if (repo == null) {
            return Map.of();
        }
        Map<Item, StockEntry> result = new HashMap<>(candidateItems.size());
        for (GridInventoryEntry entry : repo.getAllEntries()) {
            if (entry.getWhat() instanceof AEItemKey itemKey && candidateItems.contains(itemKey.getItem())) {
                Item item = itemKey.getItem();
                StockEntry existing = result.get(item);
                if (existing != null) {
                    result.put(item, new StockEntry(
                            existing.available + entry.getStoredAmount(),
                            existing.craftable || entry.isCraftable()));
                } else {
                    result.put(item, new StockEntry(
                            entry.getStoredAmount(), entry.isCraftable()));
                }
            }
        }
        return result;
    }
}
