package net.ptcrys.topo.integration.ae2;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import appeng.api.inventories.InternalInventory;
import org.jspecify.annotations.NonNull;

/**
 * Bridges the Topo-native {@link AePatternInventory} (a {@code ResourceHandler<ItemResource>})
 * into AE2's {@link InternalInventory} so it can be returned from
 * {@link appeng.helpers.patternprovider.PatternContainer#getTerminalPatternInventory()}.
 *
 * <p>
 * Only the four non-default {@code InternalInventory} methods need wiring — the inherited
 * {@code ItemTransfer} surface (addItems / removeItems / fuzzy variants) is satisfied by the
 * default implementations in {@code InternalInventory}.
 */
final class AePatternInternalInventoryAdapter implements InternalInventory {

    private final AePatternInventory backing;

    AePatternInternalInventoryAdapter(AePatternInventory backing) {
        this.backing = backing;
    }

    @Override
    public int size() {
        return backing.size();
    }

    @Override
    public ResourceHandler<ItemResource> toResourceHandler() {
        return backing;
    }

    @Override
    public @NonNull ItemStack getStackInSlot(int slotIndex) {
        return backing.get(slotIndex);
    }

    @Override
    public void setItemDirect(int slotIndex, @NonNull ItemStack stack) {
        backing.set(slotIndex, stack);
    }
}
