package net.ptcrys.topo.apiv2.machine.ui

import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

import java.util.function.Consumer
import java.util.function.Predicate

/**
 * 把一个 UI 元素登记为 XEI(JEI)幽灵投放目标的插座接口。api 包不依赖 JEI——实现住
 * integration/jei,经 [ItemGhostDrop.install] 在运行时装上(与 [net.ptcrys.topo.apiv2.machine.ui.recipe]
 * 的 XeiRecipeLookup 同款范式)。投放谓词/回调只吞 [ItemStack],签名里没有任何 JEI 类型。
 */
fun interface ItemGhostDropBridge {
    /**
     * 把 [slot] 元素登记为物品幽灵投放目标:JEI 拖入物品时,先用 [mayPlace] 判定该格是否高亮可放,
     * 放下后把物品交给 [onPlace]。元素被移除(弹窗关闭)时监听随元素一并失效,无需手动注销。
     */
    fun register(slot: UIElement, mayPlace: Predicate<ItemStack>, onPlace: Consumer<ItemStack>)
}

/**
 * [ItemGhostDropBridge] 的全局插座:JEI 在场时由 integration/jei 装上实现,否则 [register] 静默
 * 无操作——物品选择弹窗的「点击背包」「手动输入 id/#tag」两条路径不依赖本插座,无 JEI 也照常工作。
 */
object ItemGhostDrop {
    @Volatile
    private var bridge: ItemGhostDropBridge? = null

    @JvmStatic
    fun install(bridge: ItemGhostDropBridge) {
        this.bridge = bridge
    }

    @JvmStatic
    fun uninstall() {
        this.bridge = null
    }

    @JvmStatic
    fun isAvailable(): Boolean = bridge != null

    /** 把 [slot] 登记为物品幽灵投放目标;无桥接(无 JEI)时无操作。 */
    @JvmStatic
    fun register(slot: UIElement, mayPlace: Predicate<ItemStack>, onPlace: Consumer<ItemStack>) {
        bridge?.register(slot, mayPlace, onPlace)
    }
}
