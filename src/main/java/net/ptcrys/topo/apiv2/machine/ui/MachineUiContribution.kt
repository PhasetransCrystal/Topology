package net.ptcrys.topo.apiv2.machine.ui

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

import java.util.function.BooleanSupplier

/**
 * 机器界面**贡献**建造入口：领域 trait 只描述主区分页 / 左栏 / 右栏 / 底条长什么样。
 *
 * 底层写入 [PageCollector] 与 [ComponentCollector]（[MachineUiFrameTemplate] 装卡与侧栏
 * 逻辑不变）；内容一律 [UIElement]。左右栏条目由 Frame 自动包
 * [MachineUiContainerTemplate.createCard]；底条不包卡片。
 *
 * ```
 * override fun collectMachineUi(contribution: MachineUiContribution) {
 *   contribution.mainPage("recipe", TITLE) { /* body */ }
 *   contribution.rightPanel("status", STATUS) { lcdPanel { … } }
 *   contribution.rightPanel("mods", MODS) { column { … } }
 * }
 * ```
 *
 * 命名与 [MachineUiFrameTemplate] / [MachineUiLayout] / [MachineUiComponentTemplate]
 * 同属 `MachineUi*` 族；[pages] / [components] 与旧 collector API 共享实例以便渐进迁移。
 */
class MachineUiContribution(val pages: PageCollector = PageCollector(), val components: ComponentCollector = ComponentCollector()) {
    /** 主区分页；[build] 内用 [MachineUiLayoutScope] 堆子节点。 */
    fun mainPage(key: String, title: Component, build: MachineUiLayoutScope.() -> Unit) {
        pages.sink(key, title, MachineUiLayout.column(block = build))
    }

    /** 主区分页；已有整页 [UIElement]（门控根等）时用。 */
    fun mainPage(key: String, title: Component, element: UIElement) {
        pages.sink(key, title, element)
    }

    /**
     * 主区分页，内容为固定宽居中列（配方页 / 结构页根），
     * 对齐 [MachineUiLayout.pageColumn]。
     */
    fun mainPageColumn(key: String, title: Component, width: Float, build: MachineUiLayoutScope.() -> Unit) {
        pages.sink(key, title, MachineUiLayout.pageColumn(width, block = build))
    }

    @JvmOverloads
    fun leftPanel(key: String, title: Component, pageKey: String? = null, titleBar: UIElement? = null, gap: Float = MachineUiComponentStyle.boxAllGap, maxWidth: Float = Float.NaN, build: MachineUiLayoutScope.() -> Unit) {
        sidePanel(ComponentCollector.Side.LEFT, key, title, pageKey, titleBar, gap, maxWidth, build)
    }

    /** 左栏：直接挂已有内容（可带图标标题栏，如 side-IO 卡）。 */
    @JvmOverloads
    fun leftPanel(key: String, title: Component, pageKey: String? = null, titleBar: UIElement? = null, element: UIElement) {
        components.sink(ComponentCollector.Side.LEFT, pageKey, key, title, titleBar, element)
    }

    @JvmOverloads
    fun rightPanel(key: String, title: Component, pageKey: String? = null, titleBar: UIElement? = null, gap: Float = MachineUiComponentStyle.boxAllGap, maxWidth: Float = Float.NaN, build: MachineUiLayoutScope.() -> Unit) {
        sidePanel(ComponentCollector.Side.RIGHT, key, title, pageKey, titleBar, gap, maxWidth, build)
    }

    /** 右栏：直接挂已有内容。可选 [visibleWhen] 动态隐藏整张侧卡（布局流移除）。 */
    @JvmOverloads
    fun rightPanel(key: String, title: Component, pageKey: String? = null, titleBar: UIElement? = null, element: UIElement, visibleWhen: BooleanSupplier? = null) {
        components.sink(ComponentCollector.Side.RIGHT, pageKey, key, title, titleBar, element, visibleWhen)
    }

    /**
     * 底条贡献（资源条等）：Frame 不包卡片。
     * 对应 [ComponentCollector.Side.BOTTOM]。
     */
    @JvmOverloads
    fun bottomStrip(key: String, title: Component = Component.empty(), pageKey: String? = null, gap: Float = MachineUiComponentStyle.boxAllGap, build: MachineUiLayoutScope.() -> Unit) {
        sidePanel(ComponentCollector.Side.BOTTOM, key, title, pageKey, null, gap, Float.NaN, build)
    }

    /** 底条：直接挂已有 [UIElement]。 */
    @JvmOverloads
    fun bottomStrip(key: String, title: Component = Component.empty(), pageKey: String? = null, element: UIElement) {
        components.sink(ComponentCollector.Side.BOTTOM, pageKey, key, title, null, element)
    }

    private fun sidePanel(side: ComponentCollector.Side, key: String, title: Component, pageKey: String?, titleBar: UIElement?, gap: Float, maxWidth: Float, build: MachineUiLayoutScope.() -> Unit) {
        components.sink(
            side,
            pageKey,
            key,
            title,
            titleBar,
            // 侧栏/卡片正文是纵向列表：用 verticalList 保证项间距（gapRow 不可靠）。
            MachineUiLayout.verticalList(gap = gap, maxWidth = maxWidth, block = build),
        )
    }
}
