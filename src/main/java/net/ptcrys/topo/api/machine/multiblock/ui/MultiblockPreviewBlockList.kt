package net.ptcrys.topo.api.machine.multiblock.ui

import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiLayout

import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexWrap

/**
 * Aggregated bill-of-materials dock for the multiblock preview: cells with identical candidate
 * signatures merge into one `N×` row. Single-option rows render as one compact
 * `chip · slot · name` line; multi-option rows put the name on its own line with the option slots
 * wrapping below, each slot's tooltip listing the roles that part can fulfil. The dock scrolls
 * vertically past [maxHeight], and scene cell-clicks highlight the matching row.
 */
object MultiblockPreviewBlockList {
    private const val COUNT_WIDTH = 24f
    private const val COUNT_HEIGHT = 12f
    private const val SLOT_SIZE = 18f
    private const val ROW_GAP = 2f

    // 透明底走样式表的平价配方(单四边形);RectTexture 即使全透明也会按圆角细分出几十个三角形。
    private val TRANSPARENT_BG: IGuiTexture = MachineUiComponentStyle.transparentTexture()

    /**
     * Builds the dock. [onCandidateClick] (when given) runs on left-clicking a candidate slot —
     * the JEI surface wires recipe lookup through it; the in-machine page passes null.
     */
    @JvmStatic
    @JvmOverloads
    fun build(preview: BlueprintPreview, width: Float, maxHeight: Float, onCandidateClick: ((ItemStack) -> Unit)? = null): BlockListView {
        val groups = aggregate(preview)
        val rowsByCell = HashMap<BlockPos, UIElement>(preview.blocks().size)
        val scroller = MachineUiContainerTemplate.createScrollView(width, maxHeight).apply {
            setId("topo_multiblock_preview_block_list")
        }
        for (group in groups) {
            val row = row(group, onCandidateClick)
            for (cell in group.cells) rowsByCell[cell] = row
            scroller.addScrollViewChild(row)
        }
        return BlockListView(scroller, rowsByCell)
    }

    private fun row(group: AggGroup, onCandidateClick: ((ItemStack) -> Unit)?): UIElement = MachineUiLayout.row(
        gap = 4f,
        widthPercent = 100f,
        alignItems = AlignItems.FLEX_START,
        id = "topo_multiblock_preview_block_list_row",
    ) {
        root.layout {
            it.paddingTop(1f)
            it.paddingBottom(1f)
            it.paddingRight(2f)
        }
        root.style { it.backgroundTexture(TRANSPARENT_BG) }
        add(countChip(group.count))
        add(rowBody(group.candidates, onCandidateClick))
    }

    private fun countChip(count: Int): Label = Label().apply {
        setId("topo_multiblock_preview_block_list_count")
        setText(Component.literal("$count×"))
        textStyle {
            it.textColor(MachineUiComponentStyle.ledWaiting)
            it.textShadow(true)
            it.textAlignHorizontal(Horizontal.RIGHT)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        style { it.backgroundTexture(MachineUiComponentStyle.previewCountChipTexture()) }
        layout {
            it.width(COUNT_WIDTH)
            it.height(COUNT_HEIGHT)
            it.paddingRight(3f)
            // Centers the 12px chip against the 18px slot line of the row body.
            it.marginTop((SLOT_SIZE - COUNT_HEIGHT) / 2f)
            it.flexShrink(0f)
        }
    }

    /** Single option: one `slot · name` line. Several options: name line, slots wrapping below. */
    private fun rowBody(candidates: List<BlueprintPreview.Candidate>, onCandidateClick: ((ItemStack) -> Unit)?): UIElement {
        val byItem = LinkedHashMap<Item, MutableList<BlueprintPreview.Candidate>>()
        for (candidate in candidates) {
            byItem.getOrPut(candidate.item().item) { ArrayList() }.add(candidate)
        }
        if (byItem.size == 1) {
            return MachineUiLayout.row(
                gap = 4f,
                alignItems = AlignItems.CENTER,
                id = "topo_multiblock_preview_block_list_row_body",
            ) {
                root.layout {
                    it.flexGrow(1f)
                    it.flexShrink(1f)
                }
                add(candidateSlot(byItem.values.first(), onCandidateClick))
                add(nameLabel(candidates, inRow = true))
            }
        }
        return MachineUiLayout.column(
            gap = ROW_GAP,
            id = "topo_multiblock_preview_block_list_row_body",
        ) {
            root.layout {
                it.flexGrow(1f)
                it.flexShrink(1f)
            }
            add(nameLabel(candidates, inRow = false))
            add(slotsWrap(byItem, onCandidateClick))
        }
    }

    /**
     * Row context takes the line's remaining width (flex-basis 0 + grow); column context spans the
     * parent ({@code width:100%}) — a zero flex-basis in a column resolves the CROSS axis to zero
     * and the text degenerates to one character per line.
     */
    private fun nameLabel(candidates: List<BlueprintPreview.Candidate>, inRow: Boolean): Label = Label().apply {
        setId("topo_multiblock_preview_block_list_name")
        setText(candidates[0].item().hoverName)
        textStyle {
            it.textColor(MachineUiComponentStyle.ledInfo)
            it.textShadow(false)
            it.textWrap(TextWrap.WRAP)
            it.adaptiveHeight(true)
        }
        layout {
            if (inRow) {
                it.width(0f)
                it.flexGrow(1f)
                it.flexShrink(1f)
            } else {
                it.widthPercent(100f)
            }
        }
    }

    private fun slotsWrap(byItem: Map<Item, List<BlueprintPreview.Candidate>>, onCandidateClick: ((ItemStack) -> Unit)?): UIElement = MachineUiLayout.row(
        gap = ROW_GAP,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
        flexWrap = FlexWrap.WRAP,
        id = "topo_multiblock_preview_block_list_alt_group",
    ) {
        for (group in byItem.values) add(candidateSlot(group, onCandidateClick))
    }

    /** One slot for an item, with every role it can fulfil (plus count constraint) in the tooltip. */
    @JvmStatic
    fun candidateSlot(candidatesForItem: List<BlueprintPreview.Candidate>, onCandidateClick: ((ItemStack) -> Unit)?): ItemSlot = ItemSlot().apply {
        setId("topo_multiblock_preview_block_list_slot")
        setItem(candidatesForItem[0].item())
        layout {
            it.width(SLOT_SIZE)
            it.height(SLOT_SIZE)
            it.flexShrink(0f)
        }
        val tip = roleTooltip(candidatesForItem)
        if (tip.isNotEmpty()) style.tooltips(*tip)
        if (onCandidateClick != null) {
            addEventListener(UIEvents.MOUSE_DOWN) { event ->
                if (event.button == 0 && !value.isEmpty) {
                    onCandidateClick(value)
                }
            }
        }
    }

    private fun roleTooltip(candidates: List<BlueprintPreview.Candidate>): Array<Component> {
        val roles = candidates.filter { it.hasRole() }
        if (roles.isEmpty()) return emptyArray()
        return Array(roles.size) { i ->
            val candidate = roles[i]
            val role = Component.literal(candidate.role()!!.id().path.replace('_', ' '))
                .withStyle(ChatFormatting.GOLD)
            Component.translatable("ui.topo.multiblock.preview.as_role", role)
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("  "))
                .append(countText(candidate.min(), candidate.max()).withStyle(ChatFormatting.GRAY))
        }
    }

    private fun countText(min: Int, max: Int): MutableComponent = when {
        max == Int.MAX_VALUE -> Component.literal("≥ $min")
        min == max -> Component.literal("$min")
        else -> Component.literal("$min–$max")
    }

    private fun aggregate(preview: BlueprintPreview): List<AggGroup> {
        val acc = LinkedHashMap<List<Item>, Acc>()
        for ((pos, candidates) in preview.candidatesByCell()) {
            if (candidates.isEmpty()) continue
            val key = candidates.map { it.item().item }
            val group = acc.getOrPut(key) { Acc(candidates) }
            group.count++
            group.cells.add(pos)
        }
        return acc.values.map { AggGroup(it.candidates, it.count, it.cells) }
    }

    private data class AggGroup(val candidates: List<BlueprintPreview.Candidate>, val count: Int, val cells: List<BlockPos>)

    private class Acc(val candidates: List<BlueprintPreview.Candidate>, var count: Int = 0, val cells: MutableList<BlockPos> = ArrayList())

    /** The dock scroller plus the per-cell row index; scene clicks toggle row highlights here. */
    class BlockListView internal constructor(val element: UIElement, private val rowsByCell: Map<BlockPos, UIElement>) {
        private var selectedCell: BlockPos? = null

        /** Toggles the highlighted row for [cell]; returns the now-selected cell (null = cleared). */
        fun toggleSelect(cell: BlockPos): BlockPos? {
            val targetRow = rowsByCell[cell] ?: return selectedCell
            clearCurrentHighlight()
            val newSelected = if (selectedCell == cell) null else cell
            selectedCell = newSelected
            if (newSelected != null) {
                targetRow.style { it.backgroundTexture(MachineUiComponentStyle.previewHighlightTexture()) }
                scrollIntoView(targetRow)
            }
            return newSelected
        }

        private fun clearCurrentHighlight() {
            val cell = selectedCell ?: return
            rowsByCell[cell]?.style { it.backgroundTexture(TRANSPARENT_BG) }
        }

        private fun scrollIntoView(row: UIElement) {
            val scroller = element as? ScrollerView ?: return
            val viewportHeight = scroller.viewPort.sizeHeight
            val containerHeight = scroller.viewContainer.contentHeight
            val scrollable = containerHeight - viewportHeight
            if (scrollable <= 0f) return
            val rowY = row.layoutY
            val rowHeight = row.sizeHeight
            val currentNorm = scroller.verticalScroller.normalizedValue
            val currentScroll = (if (currentNorm.isNaN()) 0f else currentNorm) * scrollable
            val target = when {
                rowY < currentScroll -> rowY
                rowY + rowHeight > currentScroll + viewportHeight -> rowY + rowHeight - viewportHeight
                else -> return
            }
            scroller.verticalScroller.setNormalizedValue((target / scrollable).coerceIn(0f, 1f))
        }
    }
}
