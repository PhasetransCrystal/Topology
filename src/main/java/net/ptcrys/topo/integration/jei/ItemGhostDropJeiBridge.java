package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.apiv2.machine.ui.ItemGhostDropBridge;

import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import mezz.jei.api.constants.VanillaTypes;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link ItemGhostDropBridge} 的 JEI 实现:经 LDLib2 自带的幽灵投放管线
 * ({@link LDLibJEIPlugin#ghostIngredient}) 把元素登记为 ITEM_STACK 投放目标。LDLib2 的
 * {@code ModularUIJEIHandlers.GHOST_INGREDIENT_HANDLER} 会在拖拽开始时向 UI 树广播事件,
 * 这里注册的监听据 {@code mayPlace} 报告投放区、{@code onPlace} 接收落下的物品。
 *
 * <p>
 * 由 {@link OIJeiPlugin} 在 JEI runtime 可用/不可用时 install/uninstall(与 XeiRecipeLookup
 * 同款生命周期)。边界:JEI 类型只在 integration 包出现,api 侧只见纯 {@link ItemStack} 谓词/回调。
 */
final class ItemGhostDropJeiBridge implements ItemGhostDropBridge {

    @Override
    public void register(UIElement slot, Predicate<ItemStack> mayPlace, Consumer<ItemStack> onPlace) {
        LDLibJEIPlugin.ghostIngredient(
                slot,
                VanillaTypes.ITEM_STACK,
                typed -> mayPlace.test(typed.getIngredient()),
                onPlace);
    }
}
