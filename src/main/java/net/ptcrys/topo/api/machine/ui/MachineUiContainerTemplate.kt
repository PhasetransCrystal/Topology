package net.ptcrys.topo.api.machine.ui

import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate.TextLayout
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate.CardStyle.*

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.Icons
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection
import dev.vfyjxf.taffy.style.TaffyPosition
import org.lwjgl.glfw.GLFW

object MachineUiContainerTemplate {
    data class TabPage(val key: String, val title: Component, val content: UIElement)

    /** 控制按钮组容器本身的外观，不影响按钮自身的选中/悬浮/按下样式。 */
    enum class ButtonGroupStyle {
        /** 带底框样式：按钮组外层绘制背景框，并使用 tabStripPadding 作为内边距。 */
        BOXED,

        /** 悬浮样式：按钮组外层不绘制背景框、不额外加内边距，只保留横向排列和按钮间距。 */
        FLOATING,
    }

    /** Controls card border chrome and width behavior. */
    enum class CardStyle {
        /** Draws the shared machine UI box background and padding around the title/content pair. */
        BOXED,

        /** No background frame and no padding; the parent container owns chrome. */
        NO_BORDER,
    }

    fun createBox(needPadding: Boolean = false, needGap: Boolean = false) = UIElement().applyBoxChrome(needPadding, needGap)

    private fun UIElement.applyBoxChrome(needPadding: Boolean = false, needGap: Boolean = false) = apply {
        setId("createBox")
        style {
            it.background(MachineUiComponentStyle.boxTexture)
        }
        layout {
            if (needPadding) {
                it.paddingAll(MachineUiComponentStyle.boxTextureWidth + MachineUiComponentStyle.boxAllPadding)
            } else {
                it.paddingAll(MachineUiComponentStyle.boxTextureWidth)
            }
            if (needGap) {
                it.gapAll(MachineUiComponentStyle.boxAllGap)
            }
        }
    }

    fun createLcdData(orientation: LcdData.Orientation): LcdData = LcdData.create(orientation)

    /**
     * 工具提示内嵌 LCD:大字号、无外层卡片([LcdData.Presentation.TOOLTIP])。
     * 物品 tooltip / 悬浮面板统一走这里,与机器 UI 的 [createLcdData] 区分。
     */
    fun createTooltipLcdData(orientation: LcdData.Orientation): LcdData = LcdData.create(orientation, LcdData.Presentation.TOOLTIP)

    /**
     * 页面列容器：固定宽度、子项水平居中、纵向排列并带行距。机器 UI 页签内容（配方页、结构页、
     * JEI 预览页）统一用它做根容器，页签切换不重排窗口。
     */
    @JvmOverloads
    fun createPageColumn(width: Float, gapRow: Float = MachineUiComponentStyle.pageColumnGap): UIElement = UIElement().apply {
        setId("createPageColumn")
        applyPageColumnLayout(this, width, gapRow)
    }

    /**
     * 把页面列布局施加到一个【特别定制】的容器（自带行为逻辑、无法由工厂直接创建的 UIElement 子类）
     * 上，让定制容器的布局仍然走模板，而不是在调用点手写。
     */
    @JvmOverloads
    fun applyPageColumnLayout(element: UIElement, width: Float, gapRow: Float = MachineUiComponentStyle.pageColumnGap) {
        element.layout {
            it.flexDirection(FlexDirection.COLUMN)
            it.alignItems(AlignItems.CENTER)
            it.width(width)
            it.gapRow(gapRow)
            it.flexShrink(0f)
        }
    }

    fun createScrollView(contentWidth: Float, visibleHeight: Float): ScrollerView = ScrollerView().apply {
        fun applyDarkScrollButton(button: Button) {
            button.style {
                it.background(MachineUiComponentStyle.scrollThumbBaseTexture())
            }
            button.buttonStyle {
                it.baseTexture(MachineUiComponentStyle.scrollThumbBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.scrollThumbHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.scrollThumbPressedTexture())
            }
        }
        fun applyDarkScrollIconButton(button: Button, icon: IGuiTexture) {
            applyDarkScrollButton(button)
            button.layout {
                it.paddingAll(0f)
            }
            button.addPreIcon(icon)
        }
        fun applyDarkScroller(scroller: Scroller) {
            scroller.style {
                it.background(MachineUiComponentStyle.scrollTrackTexture())
            }
            scroller.scrollContainer {
                it.style { style -> style.background(MachineUiComponentStyle.scrollTrackTexture()) }
            }
            if (scroller is Scroller.Horizontal) {
                scroller.headButton { applyDarkScrollIconButton(it, Icons.LEFT_ARROW_NO_BAR_S_WHITE) }
                scroller.tailButton { applyDarkScrollIconButton(it, Icons.RIGHT_ARROW_NO_BAR_S_WHITE) }
            } else {
                scroller.headButton { applyDarkScrollIconButton(it, Icons.UP_ARROW_NO_BAR_S_WHITE) }
                scroller.tailButton { applyDarkScrollIconButton(it, Icons.DOWN_ARROW_NO_BAR_S_WHITE) }
            }
            scroller.scrollBar(::applyDarkScrollButton)
        }
        setId("createScrollView")
        layout {
            it.width(contentWidth + MachineUiComponentStyle.scrollBarWidth)
            it.height(visibleHeight)
            it.flexShrink(0f)
        }
        scrollerStyle {
            it.mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS)
                .horizontalScrollDisplay(ScrollDisplay.NEVER)
                .adaptiveWidth(true)
        }
        viewPort { viewPort ->
            viewPort.style {
                it.background(MachineUiComponentStyle.scrollFrameTexture())
            }
            viewPort.layout {
                it.paddingAll(0f)
            }
        }
        viewContainer { viewContainer ->
            viewContainer.layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.flexShrink(0f)
            }
        }
        verticalScroller(::applyDarkScroller)
        horizontalScroller(::applyDarkScroller)
    }

    /**
     * Creates a titled content group.
     *
     * Use [CardStyle.NO_BORDER] when the parent already provides the visible frame and spacing, and the
     * card should only attach a title to its content.
     */
    @JvmOverloads
    fun createCard(title: Component, content: UIElement, cardStyle: CardStyle = CardStyle.BOXED): UIElement = createCardWithTitle(MachineUiComponentTemplate.createText(title, TextLayout.AUTO_WIDTH_1_LINE), content, cardStyle)

    /**
     * 元素标题栏变体:标题行由调用方自带(图标行等非纯文本头),chrome 与 [createCard] 完全一致。
     * 经 [MachineUiContribution.leftPanel] 的 titleBar 走到这里(side-IO 配置卡:左 IO 方向箭头 + 右资源图形)。
     */
    @JvmOverloads
    fun createCard(titleBar: UIElement, content: UIElement, cardStyle: CardStyle = CardStyle.BOXED): UIElement = createCardWithTitle(titleBar, content, cardStyle)

    /**
     * 卡片 = **单层** chrome 内「标题 + 正文」。
     *
     * 禁止把标题、正文各自再包一层 [createBox] / [createCardContent]：侧栏 absolute 列一旦
     * 被拉高，两段独立 box 之间会露出空洞（title 与小组件本体分离）。
     * 标题与正文都是本卡直接子节点；间距只用 content 的 marginTop，容器 gap 清零。
     */
    private fun createCardWithTitle(titleContent: UIElement, content: UIElement, cardStyle: CardStyle): UIElement {
        val headerGap = MachineUiComponentStyle.cardHeaderGap
        // 标题/正文都不可被父级纵向拉伸拉开。
        titleContent.layout {
            it.flexGrow(0f)
            it.flexShrink(0f)
        }
        content.layout {
            it.marginTop(headerGap)
            it.flexGrow(0f)
            it.flexShrink(0f)
        }
        return when (cardStyle) {
            BOXED -> createBox(needPadding = true, needGap = false).apply {
                setId("createCard")
                applyCardStackLayout()
                addChild(titleContent)
                addChild(content)
            }

            NO_BORDER -> UIElement().apply {
                setId("createCard")
                layout {
                    it.paddingAll(0f)
                }
                applyCardStackLayout()
                addChild(titleContent)
                addChild(content)
            }
        }
    }

    /** 卡片内纵向堆叠：顶对齐、不伸长、容器 gap 强制为 0（间距走子项 marginTop）。 */
    private fun UIElement.applyCardStackLayout() = layout {
        it.flexDirection(FlexDirection.COLUMN)
        it.alignItems(AlignItems.STRETCH)
        it.justifyContent(AlignContent.FLEX_START)
        it.alignContent(AlignContent.FLEX_START)
        it.flexGrow(0f)
        it.flexShrink(0f)
        it.gapRow(0f)
        it.gapColumn(0f)
    }

    /** 仅正文、无标题的卡片内容区（弹窗/嵌套用）。 */
    @JvmOverloads
    fun createCardContent(content: UIElement, cardStyle: CardStyle = CardStyle.BOXED): UIElement = when (cardStyle) {
        BOXED -> createBox(needPadding = true).apply {
            setId("createCardContent")
            addChild(content)
        }

        NO_BORDER -> UIElement().apply {
            setId("createCardContent")
            layout {
                it.paddingAll(0f)
                it.flexDirection(FlexDirection.COLUMN)
                it.flexShrink(0f)
            }
            addChild(content)
        }
    }

    // 此前这里有 NO_BORDER_MAX_WIDTH / MaxWidthCard(onLayoutChanged 里按测量宽回写 minWidth 的
    // "最宽锁")。在布局回调里改布局属于自馈结构,叠上自适应文本即触发 LDLib2 每帧 10 轮布局
    // 脏循环(Jade 计时面板掉帧根因,UiPerfProbe 取证后整组移除)。悬浮面板的宽度稳定改由
    // LcdDatanColumns 的构建期定宽承担,不要再以任何形式恢复布局回调内的布局写入。

    /**
     * 槽位网格：把 [slotCount] 个格子按 [columns] 列排成行；行数超过 [visibleRows] 时整体包进滚动视图。
     * 格子元素由 [slotFactory] 按全局索引创建（LDLib2 默认槽位皮肤在设计系统豁免清单内）；
     * [cellHeight] 支持复合格（如 ME 配置页 ghost+库存 双槽叠放）。
     */
    @JvmOverloads
    fun createSlotGrid(slotCount: Int, columns: Int = MachineUiComponentStyle.slotGridColumns, visibleRows: Int = MachineUiComponentStyle.slotGridVisibleRows, cellHeight: Float = MachineUiComponentStyle.slotSize.toFloat(), slotFactory: (Int) -> UIElement): UIElement {
        require(slotCount > 0) { "slot grid needs at least one slot" }
        require(columns > 0) { "slot grid needs at least one column" }
        val slotSize = MachineUiComponentStyle.slotSize.toFloat()
        val grid = UIElement().apply {
            setId("createSlotGrid")
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.flexShrink(0f)
            }
        }
        for (rowStart in 0 until slotCount step columns) {
            val row = UIElement().apply {
                setId("slot_grid_row_" + rowStart / columns)
                layout {
                    it.flexDirection(FlexDirection.ROW)
                    it.height(cellHeight)
                    it.flexShrink(0f)
                }
            }
            for (slot in rowStart until minOf(rowStart + columns, slotCount)) {
                row.addChild(slotFactory(slot))
            }
            grid.addChild(row)
        }
        val rows = (slotCount + columns - 1) / columns
        if (rows <= visibleRows) {
            return grid
        }
        return createScrollView(columns * slotSize, visibleRows * cellHeight).apply {
            addScrollViewChild(grid)
        }
    }

    /**
     * 弹窗全屏遮罩：绝对定位铺满窗口、内容居中、压在普通内容之上。点击遮罩本身的关闭语义由调用方
     * 自行挂事件（模板只负责形状与层级）。
     */
    fun createPopupBackdrop(): UIElement = UIElement().apply {
        setId("createPopupBackdrop")
        layout {
            it.positionType(TaffyPosition.ABSOLUTE)
            it.widthPercent(100f)
            it.heightPercent(100f)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
        }
        style {
            it.background(MachineUiComponentStyle.popupBackdropTexture())
            it.zIndex(MachineUiComponentStyle.popupZIndex)
        }
    }

    /** 弹窗面板：盒子底框 + 纵向列布局，宽度由调用方给定（token 见 Style.popupPanelWidth）。 */
    fun createPopupPanel(width: Float): UIElement = createBox(needPadding = true, needGap = true).apply {
        setId("createPopupPanel")
        layout {
            it.flexDirection(FlexDirection.COLUMN)
            it.alignItems(AlignItems.STRETCH)
            it.width(width)
        }
    }

    /**
     * 模态弹窗内容规格：标题之外的面板内容（[body]）与交互回调，供 [openModalPopup] 装配进共享壳。
     * 数字弹窗与物品选取弹窗各供各的内容/回调，壳样板（遮罩/焦点陷阱/键处理/挂载）一处共享。
     *
     * @param onCancel ESC（以及 [dismissOnClickOutside] 时点击遮罩外）触发
     * @param onConfirm ENTER 触发
     * @param onKeyChange 每个按键按下/抬起的钩子（数字弹窗据修饰键刷新步进标签；物品选取传 null）
     * @param focusTarget 挂载后初始焦点（null → 聚焦遮罩本身）
     * @param dismissOnClickOutside 点击面板外是否取消（数字弹窗 true；物品选取 false，暂存流程不因误点关闭）
     */
    data class ModalPopupContent(val body: List<UIElement>, val onCancel: () -> Unit, val onConfirm: () -> Unit = {}, val onKeyChange: (() -> Unit)? = null, val focusTarget: UIElement? = null, val dismissOnClickOutside: Boolean = true)

    /**
     * 装配模态弹窗元素树（纯工厂，不挂载、不聚焦）：遮罩(`${idPrefix}_backdrop`) 含面板
     * (`${idPrefix}_panel`)，面板含标题 + [ModalPopupContent.body]；面板吞 MOUSE_DOWN/WHEEL，遮罩挂
     * 焦点陷阱 + capture KEY_DOWN(ENTER 确认/ESC 取消、先跑 onKeyChange) + 点外取消/吞。
     */
    fun buildModalPopup(idPrefix: String, panelWidth: Float, title: Component, content: ModalPopupContent): UIElement {
        val panel = createPopupPanel(panelWidth).apply {
            setId("${idPrefix}_panel")
            // 吞掉面板内的点击/滚轮，别穿透到遮罩的取消路径与底层屏。
            addEventListener(UIEvents.MOUSE_DOWN) { it.stopPropagation() }
            addEventListener(UIEvents.MOUSE_WHEEL) { it.stopPropagation() }
            addChild(MachineUiComponentTemplate.createStaticText(title))
            content.body.forEach { addChild(it) }
        }
        return createPopupBackdrop().apply {
            setId("${idPrefix}_backdrop")
            // 模态焦点陷阱：子元素失焦无继任者时把焦点拉回，否则 ModularUIWidget 在无焦点时整体丢 KEY_DOWN。
            setEnforceFocus { }
            // capture 相：先于聚焦子元素自己的监听跑，故 ENTER/ESC 无论焦点落在哪个弹窗元素都生效。
            addEventListener(UIEvents.KEY_DOWN, { event ->
                content.onKeyChange?.invoke()
                when (event.keyCode) {
                    GLFW.GLFW_KEY_ENTER,
                    GLFW.GLFW_KEY_KP_ENTER,
                    -> {
                        content.onConfirm()
                        event.stopPropagation()
                    }

                    GLFW.GLFW_KEY_ESCAPE -> {
                        content.onCancel()
                        event.stopPropagation()
                    }
                }
            }, true)
            content.onKeyChange?.let { hook ->
                addEventListener(UIEvents.KEY_UP, { hook() }, true)
            }
            addEventListener(UIEvents.MOUSE_DOWN) { event ->
                if (content.dismissOnClickOutside) {
                    content.onCancel()
                }
                event.stopPropagation()
            }
            addChild(panel)
        }
    }

    /**
     * 在 [host] 的 modular UI 根上挂一个模态 in-UI 弹窗并聚焦。[buildContent] 收 close()（移除弹窗）、
     * 返回内容与回调。无 modular UI 根则不挂、返回 false（守卫先于内容构建短路）。
     */
    fun openModalPopup(host: UIElement, idPrefix: String, panelWidth: Float, title: Component, buildContent: (close: () -> Unit) -> ModalPopupContent): Boolean {
        val root = host.modularUI?.ui?.rootElement ?: return false
        val backdropRef = arrayOfNulls<UIElement>(1)
        val close: () -> Unit = { backdropRef[0]?.let { root.removeChild(it) } }
        val content = buildContent(close)
        val backdrop = buildModalPopup(idPrefix, panelWidth, title, content)
        backdropRef[0] = backdrop
        root.addChild(backdrop)
        // 聚焦初始目标（数字弹窗=文本框，物品选取=遮罩本身），让 capture 焦点陷阱接管后续按键路由。
        (content.focusTarget ?: backdrop).focus()
        return true
    }

    @JvmOverloads
    fun createButtonGroup(buttonGroupStyle: ButtonGroupStyle = ButtonGroupStyle.BOXED): UIElement = UIElement().apply {
        setId("createButtonGroup")
        if (buttonGroupStyle == ButtonGroupStyle.BOXED) {
            style {
                it.background(MachineUiComponentStyle.tabStripTexture())
            }
        }
        layout {
            if (buttonGroupStyle == ButtonGroupStyle.BOXED) {
                it.paddingAll(MachineUiComponentStyle.tabStripPadding)
            }
            it.flexDirection(FlexDirection.ROW)
            it.gapColumn(MachineUiComponentStyle.tabButtonGap)
            it.widthMaxContent()
            it.flexShrink(0f)
        }
    }

    @JvmOverloads
    fun createTabView(pages: List<TabPage>, onPageSelected: (String) -> Unit, buttonGroupStyle: ButtonGroupStyle = ButtonGroupStyle.BOXED): UIElement {
        require(pages.isNotEmpty()) { "tab view pages cannot be empty" }

        val contentByKey = LinkedHashMap<String, UIElement>()
        val buttonByKey = LinkedHashMap<String, MachineUiComponentTemplate.SelectableButton>()
        val buttonGroup = createButtonGroup(buttonGroupStyle).apply {
            setId("machine_ui_page_buttons")
        }
        val contentHost = createBox().apply {
            setId("machine_ui_page_content")
            layout {
                it.marginTop(4f)
            }
        }

        fun selectPage(pageKey: String) {
            for ((key, content) in contentByKey) {
                content.setDisplay(key == pageKey)
            }
            for ((key, button) in buttonByKey) {
                button.selected = key == pageKey
            }
            onPageSelected(pageKey)
        }

        for (page in pages) {
            contentByKey[page.key] = page.content
            contentHost.addChild(page.content)

            val button = MachineUiComponentTemplate.createButton(page.title).apply {
                setId("machine_ui_page_button")
                setOnClick { event ->
                    if (event.button == 0) {
                        selectPage(page.key)
                        event.stopPropagation()
                    }
                }
            }
            buttonByKey[page.key] = button
            buttonGroup.addChild(button)
        }

        return UIElement().apply {
            setId("createTabView")
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.gapRow(MachineUiComponentStyle.tabViewGap)
            }
            addChildren(buttonGroup, contentHost)
            selectPage(pages.first().key)
        }
    }
}
