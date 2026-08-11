package net.ptcrys.topo.apiv2.machine.ui

import net.minecraft.world.inventory.tooltip.TooltipComponent

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties
import com.lowdragmc.lowdraglib2.gui.ui.style.Property
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import com.lowdragmc.lowdraglib2.gui.ui.utils.ModularUITooltipComponent
import dev.vfyjxf.taffy.style.TaffyDimension

import kotlin.math.ceil
import kotlin.math.max

/**
 * tooltip 宿主包装:把一棵纯展示 [UIElement] 树包成 vanilla [TooltipComponent],可直接从
 * `getTooltipImage` / GatherComponents 返回(物品 tooltip),也可塞进 ModularUI 元素的
 * HoverTooltips(LDLib2 渲染端按注册工厂识别 TooltipComponent;懒构建/释放见
 * [net.ptcrys.topo.apiv2.machine.ui.tooltip.LazyHoverPanel])。渲染端零自研——返回的是 LDLib2 原生
 * [ModularUITooltipComponent](其客户端工厂由 LDLib2 自己注册),本对象只负责把它"喂对":
 *
 * 1. **尺寸**:`ModularUI.init` 把上报宽高设为根元素**声明**尺寸(auto 轴在布局后回填实测值,
 *    但 tooltip 盒在原生客户端组件里读的就是这两个值,声明轴必须保真)。这里对**显式声明**的轴
 *    原样保留;只对 auto 轴做内容实测并回写声明,再二次 `init(实测w, 实测h)`——上报尺寸正确,
 *    且声明尺寸 == 虚拟屏幕尺寸,根元素居中偏移归零(对照 Jade 侧 LDLibTooltipElement 的修正)。
 * 2. **tick**:`setTickWhileRending(true)`,动画与 Supplier 绑定在悬停期间保持活动。
 *
 * 缓存与逐出由调用方负责(tooltip 组件收集每帧发生,见 ItemTooltipUis);逐出时必须调 [dispose]
 * 释放内部 ModularUI。LDLib2 具体类是本对象的实现细节,不出现在任何调用方签名里。
 *
 * 注意:auto 轴的实测依赖 LDLib2 样式→taffy 管线(headless 单测环境不完整,实测为 0 时按 1 兜底
 * 不崩;像素级正确性由游戏内截图验收)。
 */
object MachineUiTooltipTemplate {

    /** 包装一棵纯展示树。树内只允许展示型组件——tooltip 无输入,交互组件不会收到任何事件。 */
    fun createTooltipComponent(root: UIElement): TooltipComponent {
        val ui = ModularUI.of(
            UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)),
        )
        // 第一次 init:auto 根元素 layoutWidth=NaN → MAX_CONTENT,得到内容驱动布局。
        ui.init(0, 0)
        val declaredWidth = declaredLength(root, LayoutProperties.WIDTH)
        val declaredHeight = declaredLength(root, LayoutProperties.HEIGHT)
        val width = clampToPixel(declaredWidth ?: root.sizeWidth)
        val height = clampToPixel(declaredHeight ?: root.sizeHeight)
        // 只把 auto 轴的实测值回写为声明尺寸;显式声明的轴保持调用方原值。
        if (declaredWidth == null || declaredHeight == null) {
            root.layout {
                if (declaredWidth == null) it.width(width.toFloat())
                if (declaredHeight == null) it.height(height.toFloat())
            }
        }
        ui.init(width, height)
        ui.isTickWhileRending = true
        return ModularUITooltipComponent(ui)
    }

    /** 缓存逐出钩子:释放包装内的 ModularUI。幂等;非本包装产物一律忽略。 */
    fun dispose(component: TooltipComponent) {
        if (component is ModularUITooltipComponent) {
            component.modularUI.onRemoved()
        }
    }

    /** 根元素该轴的显式声明长度;auto/percent(对 0 尺寸虚拟屏无意义)按未声明处理。 */
    private fun declaredLength(root: UIElement, property: Property<TaffyDimension>): Float? {
        val dimension = root.styleBag.computeCandidate(property) ?: return null
        return if (!dimension.isAuto && !dimension.isPercent) dimension.value else null
    }

    private fun clampToPixel(value: Float): Int = max(1, ceil(value).toInt())
}
