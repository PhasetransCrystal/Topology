package net.ptcrys.topo.api.api.util;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.transfer.item.PlayerInventoryWrapper;

/** 末影箱访问；复用有限物品栏的整批转移与事务提交契约。 */
public final class EnderChestStorageAccess extends InventoryStorageAccess {

    public EnderChestStorageAccess(ServerPlayer player) {
        super(PlayerInventoryWrapper.of(player.getEnderChestInventory()));
    }
}
