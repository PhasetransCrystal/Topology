package net.ptcrys.topo.apiv2.machine.ui

import net.minecraft.resources.Identifier

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry

import java.util.Collections
import java.util.WeakHashMap

/**
 * side-IO 卡标题悬浮 → 端口槽位/资源条高亮的配对机制。
 *
 * 两端解耦:创建端口 UI 元素的代码(物品/流体槽工厂、标量资源条)用 [tag] 登记"该元素属于
 * 哪个端口";side-IO 卡标题用 [attachHoverHighlight] 驱动——客户端逐 tick 检查
 * [UIElement.isSelfOrChildHover](绕开 MOUSE_ENTER/LEAVE 会随后代边界反复触发的语义),
 * 悬浮态翻转时对同一棵元素树里该端口的全部登记元素套/还原 [BasicStyle.overlayTexture]
 * 高亮层(LDLib2 在物品与子元素之后绘制 overlay,故高亮盖在物品之上)。
 *
 * 登记表是弱键全局表:元素树随屏幕关闭被丢弃即自动回收。服务端元素树同样登记/挂监听,
 * 但服务端无鼠标(lastHoveredElement 恒空),悬浮检查恒 false——零翻转零成本;TICK 事件
 * 本身不走网络。
 */
object PortUiHighlight {
    private val taggedPorts: MutableMap<UIElement, Identifier> =
        Collections.synchronizedMap(WeakHashMap())

    /** 登记一个元素属于端口 [portId](trait id);同一元素重复登记取最后一次。 */
    @JvmStatic
    fun tag(element: UIElement, portId: Identifier) {
        taggedPorts[element] = portId
    }

    /**
     * 给 side-IO 卡标题挂悬浮驱动:悬浮(含其子图标)期间,同一元素树里属于 [portId] 的
     * 登记元素全部套高亮,移开即还原。每次进入实时重查目标——翻页/重建后的新槽位照样命中。
     */
    @JvmStatic
    fun attachHoverHighlight(title: UIElement, portId: Identifier) {
        val saved = HashMap<UIElement, IGuiTexture>()
        var hovering = false
        title.addEventListener(UIEvents.TICK) {
            val now = title.isSelfOrChildHover
            if (now != hovering) {
                hovering = now
                if (now) {
                    saved.putAll(applyHighlight(findTargets(rootOf(title), portId)))
                } else {
                    restoreHighlight(saved)
                    saved.clear()
                }
            }
        }
    }

    /** [element] 所在元素树的根(getParent 链顶端;无父时即自身)。 */
    @JvmStatic
    fun rootOf(element: UIElement): UIElement {
        var current = element
        while (true) {
            current = current.parent ?: return current
        }
    }

    /** [root] 子树里登记为 [portId] 的全部元素,深度优先序。 */
    @JvmStatic
    fun findTargets(root: UIElement, portId: Identifier): List<UIElement> = root.selfAndAllChildren().filter { taggedPorts[it] == portId }.toList()

    /**
     * 给 [targets] 套高亮覆盖层,返回各元素被替换前的 overlay 供 [restoreHighlight] 写回。
     *
     * 捕获/写回都对 INLINE 候选层操作([inlineOverlay]),不读已计算层:计算层由 ModularUI
     * 的 StyleEngine 逐帧物化,set 后同帧读回是旧值;INLINE 候选即声明真相,同帧读写自洽
     * (无头单测同样成立)。绝对没有 inline overlay 时归一为 [IGuiTexture.EMPTY]——渲染层
     * 对 null/EMPTY 同样跳过,写回 EMPTY 与移除候选视觉等价。
     */
    @JvmStatic
    fun applyHighlight(targets: List<UIElement>): Map<UIElement, IGuiTexture> {
        val saved = HashMap<UIElement, IGuiTexture>()
        for (target in targets) {
            saved[target] = inlineOverlay(target)
            target.style.overlayTexture(MachineUiComponentStyle.portHighlightOverlayTexture)
        }
        return saved
    }

    /** 写回 [applyHighlight] 捕获的原 overlay。 */
    @JvmStatic
    fun restoreHighlight(saved: Map<UIElement, IGuiTexture>) {
        for ((target, original) in saved) {
            target.style.overlayTexture(original)
        }
    }

    /** [element] 当前声明(INLINE 层)的 overlay;未声明时为 [IGuiTexture.EMPTY]。 */
    @JvmStatic
    fun inlineOverlay(element: UIElement): IGuiTexture = element.style.getInline(PropertyRegistry.OVERLAY) ?: IGuiTexture.EMPTY
}
