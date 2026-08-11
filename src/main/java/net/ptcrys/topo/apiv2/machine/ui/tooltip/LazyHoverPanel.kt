package net.ptcrys.topo.apiv2.machine.ui.tooltip

import net.ptcrys.topo.apiv2.machine.ui.MachineUiTooltipTemplate
import net.ptcrys.topo.client.debug.UiPerfProbe

import net.minecraft.world.inventory.tooltip.TooltipComponent

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

import java.util.function.Supplier

/**
 * 元素悬浮 UI 面板的懒构建盒:HOVER_TOOLTIPS 事件逐帧触发,而悬浮面板(内含独立 ModularUI)
 * 只该构建一次 —— 首次 [get] 时经 [MachineUiTooltipTemplate.createTooltipComponent] 包装并缓存,
 * 此后每帧复用同一组件;宿主元素 REMOVED 时调 [release] 释放内部 ModularUI(与 [ItemTooltipUis]
 * 缓存逐出时的 dispose 同责)。释放后再悬停会重建,幂等。仅客户端渲染线程使用,无并发语义。
 */
class LazyHoverPanel(private val panel: Supplier<UIElement>) {

    private var component: TooltipComponent? = null

    fun get(): TooltipComponent {
        component?.let { return it }
        val root = panel.get()
        UiPerfProbe.instrumentExternal("tooltip", root)
        return MachineUiTooltipTemplate.createTooltipComponent(root).also { component = it }
    }

    fun release() {
        component?.let(MachineUiTooltipTemplate::dispose)
        component = null
    }
}
