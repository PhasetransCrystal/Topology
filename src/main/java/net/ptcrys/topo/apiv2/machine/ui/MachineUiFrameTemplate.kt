package net.ptcrys.topo.apiv2.machine.ui

import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate.ButtonGroupStyle
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate.CardStyle
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate.createBox

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.inventory.InventorySlots
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection
import dev.vfyjxf.taffy.style.FlexWrap
import dev.vfyjxf.taffy.style.TaffyPosition

object MachineUiFrameTemplate {
    private const val DEFAULT_PAGE_KEY = "main"
    private val DEFAULT_PAGE_TITLE: Component = Component.literal("Main")

    private const val MainPageMaxWidth = 270f
    private const val ComponentMaxWidth = 140f

    @JvmStatic
    fun create(title: Component, pages: List<PageCollector.Page>, components: List<ComponentCollector.Entry>): UIElement {
        val content = MachineUiContent.create(pages.ifEmpty { listOf(defaultPage()) }, components)
        val leftColumn = SideColumn(
            ComponentCollector.Side.LEFT,
            content.componentsOn(ComponentCollector.Side.LEFT),
            content.firstPageKey(),
        )
        val rightColumn = SideColumn(
            ComponentCollector.Side.RIGHT,
            content.componentsOn(ComponentCollector.Side.RIGHT),
            content.firstPageKey(),
        )
        val bottomStrip = BottomStrip(
            content.componentsOn(ComponentCollector.Side.BOTTOM),
            content.firstPageKey(),
        )
        val mainColumn = createMainColumn(title, content, leftColumn, rightColumn, bottomStrip)

        // ModularUI centers the window on the root's in-flow size ((screen - root) / 2). The side
        // columns are absolutely positioned off the main column's outer edges, so they never count
        // toward that size: the main column — the block with the player inventory — stays dead
        // center on screen no matter which side panels exist or which page is active.
        leftColumn.layout {
            it.positionType(TaffyPosition.ABSOLUTE)
            it.rightPercent(100f)
            it.marginRight(MachineUiComponentStyle.mainElementGap)
            it.top(0f)
        }
        rightColumn.layout {
            it.positionType(TaffyPosition.ABSOLUTE)
            it.leftPercent(100f)
            it.marginLeft(MachineUiComponentStyle.mainElementGap)
            it.top(0f)
        }

        return UIElement().apply {
            setId("machine_ui")
            layout {
                it.flexDirection(FlexDirection.ROW)
            }
            addChildren(leftColumn, mainColumn, rightColumn)
        }
    }

    private fun defaultPage(): PageCollector.Page {
        val page = UIElement().apply {
            setId("machine_ui_default_page")
        }
        return PageCollector.Page(DEFAULT_PAGE_KEY, DEFAULT_PAGE_TITLE, page)
    }

    private fun createMainColumn(title: Component, content: MachineUiContent, leftColumn: SideColumn, rightColumn: SideColumn, bottomStrip: BottomStrip): UIElement {
        val pages = createPages(content.pages()) { pageKey ->
            leftColumn.showPage(pageKey)
            rightColumn.showPage(pageKey)
            bottomStrip.showPage(pageKey)
        }
        val pageCard = MachineUiContainerTemplate.createCard(title, pages, CardStyle.NO_BORDER).apply {
            setId("machine_ui_main_card")
        }
        val inventory = createInventory()

        return createBox(needPadding = true, needGap = true).apply {
            setId("machine_ui_main")
            addChild(pageCard)
            if (bottomStrip.hasEntries()) {
                addChild(bottomStrip)
            }
            addChild(inventory)
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.alignItems(AlignItems.STRETCH)
            }
        }
    }

    private fun createPages(pages: List<PageCollector.Page>, onPageSelected: (String) -> Unit): UIElement {
        if (pages.size == 1) {
            return createPagePane(pages.first().element())
        }
        return createTabbedPages(pages, onPageSelected)
    }

    private fun createInventory(): UIElement {
        val inventory: UIElement = InventorySlots()
        inventory.apply {
            setId("player_inventory")
        }
        return UIElement().apply {
            layout {
                setId("player_inventory_wrapper")
                it.widthAuto()
                it.flexDirection(FlexDirection.COLUMN)
                it.alignItems(AlignItems.CENTER)
            }
            addChild(inventory)
        }
    }

    private fun createTabbedPages(pages: List<PageCollector.Page>, onPageSelected: (String) -> Unit): UIElement {
        val tabPages = pages.map { page ->
            MachineUiContainerTemplate.TabPage(page.key(), page.title(), createPagePane(page.element()))
        }
        return MachineUiContainerTemplate.createTabView(tabPages, onPageSelected, ButtonGroupStyle.FLOATING)
    }

    private fun createPagePane(page: UIElement): UIElement = UIElement().apply {
        setId("machine_ui_page")
        addChild(page)
        layout {
            it.maxWidth(MainPageMaxWidth)
        }
    }

    /**
     * 玩家物品栏上方的全宽条带:元素裸排(无卡片/标题),与物品栏同宽居中,按页可见性切换。
     * 标量资源条([ResourceBar])经 trait 的 [ComponentCollector.Side.BOTTOM] 贡献自动落位到这里。
     */
    private class BottomStrip(components: List<ComponentCollector.Entry>, activePageKey: String) : UIElement() {
        private val rows = ArrayList<Pair<UIElement, ComponentCollector.Entry>>()

        init {
            setId(ComponentCollector.Side.BOTTOM.id())
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.alignItems(AlignItems.CENTER)
                it.gapRow(MachineUiComponentStyle.resourceBarGap)
            }
            components.forEach { component ->
                rows.add(component.element() to component)
                addChild(component.element())
            }
            showPage(activePageKey)
        }

        fun hasEntries(): Boolean = rows.isNotEmpty()

        fun showPage(pageKey: String) {
            for ((element, component) in rows) {
                element.setDisplay(component.visibleOn(pageKey))
            }
        }
    }

    /**
     * Side card slot. [liveVisible] is server-authoritative for entries with a live predicate.
     *
     * Per LDLib2 data_bindings.html, S2C for non-[IBindable] chrome uses a [BindableValue] child
     * with [DataBindingBuilder.boolS2C] + remoteSetter. The sidebar tree is built once so SyncValue
     * and RPC ids stay aligned between the client and logical server; visibility only uses setDisplay.
     * Taffy wraps the fixed card sequence into outward columns, so a tall sidebar never needs to
     * reparent synchronized card subtrees at runtime.
     */
    private class SideCardSlot(val card: UIElement, val component: ComponentCollector.Entry, var liveVisible: Boolean)

    private class SideColumn(side: ComponentCollector.Side, components: List<ComponentCollector.Entry>, activePageKey: String) : UIElement() {
        private val cards = ArrayList<SideCardSlot>()
        private val liveVisibilityAnchors = ArrayList<UIElement>()
        private var currentPageKey: String = activePageKey
        private var appliedHeight = Float.NaN
        private var appliedTop = Float.NaN

        init {
            setId(side.id())
            layout {
                it.flexDirection(
                    if (side == ComponentCollector.Side.LEFT) {
                        FlexDirection.ROW_REVERSE
                    } else {
                        FlexDirection.ROW
                    },
                )
                it.alignItems(AlignItems.FLEX_START)
                it.gapColumn(MachineUiComponentStyle.mainElementGap)
                // Absolute sidebars do not contribute to the root size. Pin their available height
                // to the in-flow main column so the fixed inner stack has a definite wrap boundary.
                it.heightPercent(100f)
            }
            components.forEach { component ->
                val titleBar = component.titleBar()
                val card = (
                    if (titleBar != null) {
                        MachineUiContainerTemplate.createCard(titleBar, component.element())
                    } else {
                        MachineUiContainerTemplate.createCard(component.title(), component.element())
                    }
                    ).apply {
                    setId("machine_ui_component")
                    layout {
                        it.maxWidth(ComponentMaxWidth)
                        // 侧栏卡高度只跟内容，禁止被列容器纵向拉高后把 title/正文撑开。
                        it.flexGrow(0f)
                        it.flexShrink(0f)
                        it.alignSelf(AlignItems.FLEX_START)
                    }
                }
                val slot = SideCardSlot(card, component, true)
                cards.add(slot)
                if (component.hasLiveVisibility()) {
                    // Stable, layout-hidden anchor kept in the final tree on both sides.
                    liveVisibilityAnchors.add(
                        BindableValue(true).apply {
                            setId("oi_side_card_live_${component.key()}")
                            setDisplay(false)
                            bind(
                                DataBindingBuilder.boolS2C { component.liveVisible() }
                                    .remoteSetter { show ->
                                        slot.liveVisible = show ?: true
                                        applySlotDisplay(slot)
                                    }
                                    .build(),
                            )
                        },
                    )
                }
            }
            for (anchor in liveVisibilityAnchors) {
                addChild(anchor)
            }
            val gap = MachineUiComponentStyle.mainElementGap
            addChild(
                UIElement().apply {
                    setId("machine_ui_side_stack")
                    layout {
                        it.flexDirection(FlexDirection.COLUMN)
                        it.flexWrap(
                            if (side == ComponentCollector.Side.LEFT) {
                                FlexWrap.WRAP_REVERSE
                            } else {
                                FlexWrap.WRAP
                            },
                        )
                        it.alignItems(AlignItems.FLEX_START)
                        it.gapRow(gap)
                        it.gapColumn(gap)
                        it.heightPercent(100f)
                        it.flexShrink(0f)
                        it.flexGrow(0f)
                    }
                    cards.forEach { slot ->
                        slot.card.layout {
                            it.flexGrow(0f)
                            it.flexShrink(0f)
                            it.alignSelf(AlignItems.FLEX_START)
                        }
                        addChild(slot.card)
                    }
                },
            )
            showPage(activePageKey)
        }

        override fun onLayoutChanged() {
            super.onLayoutChanged()
            fitWrappedColumnsToViewport()
        }

        fun showPage(pageKey: String) {
            currentPageKey = pageKey
            refreshVisibility(pageKey)
        }

        /** setDisplay only — never reparent (keeps LDLib SyncValue/RPC ids stable). */
        private fun applySlotDisplay(slot: SideCardSlot) {
            val want = slot.component.pageVisibleOn(currentPageKey) && slot.liveVisible
            if (slot.card.isDisplayed != want) {
                slot.card.setDisplay(want)
            }
        }

        private fun refreshVisibility(pageKey: String) {
            currentPageKey = pageKey
            for (slot in cards) {
                applySlotDisplay(slot)
            }
        }

        /**
         * Keep the old half-card overflow allowance without moving card subtrees. A little extra
         * vertical capacity usually removes an entire outward column; the whole sidebar is then
         * centered beside the main window and clamped to the GUI viewport.
         */
        private fun fitWrappedColumnsToViewport() {
            val mainHeight = parent?.sizeHeight ?: return
            val viewportHeight = modularUI?.screenHeight?.toFloat() ?: return
            if (mainHeight <= 0f || viewportHeight <= 0f) {
                return
            }

            val gap = MachineUiComponentStyle.mainElementGap
            var columnHeight = 0f
            var tallestColumn = 0f
            for (slot in cards) {
                val card = slot.card
                if (!card.isDisplayed) {
                    continue
                }
                val cardHeight = card.sizeHeight
                if (cardHeight <= 0f) {
                    return
                }
                val needed = if (columnHeight == 0f) cardHeight else columnHeight + gap + cardHeight
                if (columnHeight > 0f && shouldStartNewColumn(needed, mainHeight, cardHeight)) {
                    tallestColumn = maxOf(tallestColumn, columnHeight)
                    columnHeight = cardHeight
                } else {
                    columnHeight = needed
                }
            }
            tallestColumn = maxOf(tallestColumn, columnHeight)

            val targetHeight = maxOf(mainHeight, tallestColumn).coerceAtMost(viewportHeight)
            val rootTop = parent?.positionY ?: return
            val idealGlobalTop = rootTop + (mainHeight - targetHeight) * 0.5f
            val maxGlobalTop = (viewportHeight - targetHeight).coerceAtLeast(0f)
            val targetTop = idealGlobalTop.coerceIn(0f, maxGlobalTop) - rootTop
            if (kotlin.math.abs(appliedHeight - targetHeight) < 0.01f &&
                kotlin.math.abs(appliedTop - targetTop) < 0.01f
            ) {
                return
            }
            appliedHeight = targetHeight
            appliedTop = targetTop
            layout {
                it.height(targetHeight)
                it.top(targetTop)
            }
        }

        private fun shouldStartNewColumn(columnBottom: Float, mainHeight: Float, cardHeight: Float): Boolean = columnBottom > mainHeight && columnBottom - mainHeight > cardHeight * 0.5f
    }
}
