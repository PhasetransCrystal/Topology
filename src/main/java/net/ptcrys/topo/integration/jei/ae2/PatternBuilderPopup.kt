package net.ptcrys.topo.integration.jei.ae2

import net.ptcrys.topo.apiv2.machine.MachineDefinition
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockControllerMetadata
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRole
import net.ptcrys.topo.apiv2.machine.multiblock.ui.BlueprintPreview
import net.ptcrys.topo.apiv2.machine.ui.LcdData
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.apiv2.machine.ui.MachineUiIcons
import net.ptcrys.topo.apiv2.machine.ui.MachineUiLayout
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineUiLang

import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

import appeng.api.stacks.AEItemKey
import appeng.api.stacks.GenericStack
import appeng.menu.me.items.PatternEncodingTermMenu
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture
import com.lowdragmc.lowdraglib2.gui.texture.Icons
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexWrap
import org.slf4j.LoggerFactory

/**
 * Multiblock pattern builder popup: data model, state extraction, UI, and AE2 pattern encoding.
 *
 * Opens a modal layer over the AE2 pattern encoding terminal when the user clicks "+" on a
 * multiblock structure page in JEI. The popup lets the user choose which hatches fill the
 * blueprint's anyOf cells before committing an AE2 processing pattern whose inputs are the
 * structure's bill of materials and whose output is a name-tagged sign (the "blueprint item").
 *
 * Design-system compliance: every texture/color/size comes from [MachineUiComponentStyle], all
 * containers/elements are built via [MachineUiContainerTemplate]/[MachineUiComponentTemplate] or
 * raw [UIElement] layout (no LDLib2 stylesheet classes); all text is static or locally bound —
 * the popup is a pure client-side tree, so S2C bindings would never receive values.
 */
object PatternBuilderPopup {

    // ====================================================================== //
    //  Data model
    // ====================================================================== //

    data class StockSnapshot(val available: Long, val craftable: Boolean)

    class MeStockIndex(private val stock: Map<Item, StockSnapshot>) {
        companion object {
            val EMPTY = MeStockIndex(emptyMap())
        }

        fun get(item: Item): StockSnapshot? = stock[item]

        fun status(item: Item, selectedCount: Int): AvailabilityStatus {
            val s = stock[item] ?: return AvailabilityStatus.UNAVAILABLE
            if (selectedCount.toLong() <= s.available) return AvailabilityStatus.IN_STOCK
            if (s.craftable) return AvailabilityStatus.CRAFTABLE
            return AvailabilityStatus.UNAVAILABLE
        }
    }

    enum class AvailabilityStatus(val sortPriority: Int) {
        IN_STOCK(0),
        CRAFTABLE(1),
        UNAVAILABLE(2),
    }

    /**
     * One candidate block that can fill an anyOf cell, identified by its [item] key.
     * [capabilities] is the union of all part capabilities this block provides (a dual-role hatch
     * merges both). [groupIndex] identifies which [AnyOfGroup] this candidate belongs to.
     */
    data class CandidateBlock(val item: Item, val displayStack: ItemStack, val capabilities: Set<PartRole>, val eligiblePositions: Int, val groupIndex: Int)

    /**
     * A group of anyOf cells that share the same candidate signature (identical set of candidate
     * items). Each group tracks its total positions and the display stack for the casing (first
     * role-less candidate).
     */
    data class AnyOfGroup(val groupIndex: Int, val totalPositions: Int, val casingDisplayStack: ItemStack?)

    /**
     * An entry in the fixed-blocks section of the pattern builder UI.
     * Sealed hierarchy: [Structural] for truly fixed blocks (frame, controller),
     * [CasingGroup] for the casing line of an anyOf group whose count is dynamic.
     */
    sealed class FixedEntry {
        abstract val displayStack: ItemStack

        /** Current count of this entry, possibly dynamic based on selections. */
        abstract fun currentCount(state: PatternBuilderState): Int

        /** A truly fixed structural block (frame, controller) with a static count. */
        data class Structural(override val displayStack: ItemStack, val count: Int) : FixedEntry() {
            override fun currentCount(state: PatternBuilderState): Int = count
        }

        /**
         * A casing group entry: count = group total positions - selections consumed by this group.
         * Appears in the fixed section to show remaining casing needed.
         */
        data class CasingGroup(override val displayStack: ItemStack, val group: AnyOfGroup) : FixedEntry() {
            override fun currentCount(state: PatternBuilderState): Int {
                val used = state.selections.entries.sumOf { (item, count) ->
                    if (count <= 0) return@sumOf 0
                    val candidate = state.allCandidates[item] ?: return@sumOf 0
                    if (candidate.groupIndex == group.groupIndex) count else 0
                }
                return (group.totalPositions - used).coerceAtLeast(0)
            }
        }
    }

    /** Blueprint-wide count requirement for a part capability. */
    data class CountConstraint(val capability: PartRole, val min: Int, val max: Int)

    /** Live tally of one capability against its constraint. */
    data class ConstraintStatus(val capability: PartRole, val min: Int, val max: Int, val current: Int) {
        val satisfied: Boolean
            get() = current >= min && (max == Int.MAX_VALUE || current <= max)
    }

    /**
     * Mutable selection state for the pattern builder. Created once per popup opening via
     * [extractState]; the UI mutates [selections] and [fixedEnabled], then reads computed
     * properties to update constraint indicators and the write button.
     */
    class PatternBuilderState(val fixedEntries: List<FixedEntry>, val anyOfGroups: List<AnyOfGroup>, val candidatesByCapability: Map<PartRole, List<CandidateBlock>>, val allCandidates: Map<Item, CandidateBlock>, val constraints: List<CountConstraint>, val totalSlots: Int, val stockIndex: MeStockIndex = MeStockIndex.EMPTY) {
        /** Item -> count for each candidate the user has selected. */
        val selections: MutableMap<Item, Int> = LinkedHashMap()

        /** Index into [fixedEntries] -> enabled/disabled (defaults to true). */
        val fixedEnabled: MutableMap<Int, Boolean> = HashMap()

        private val listeners = mutableListOf<() -> Unit>()

        fun onChanged(l: () -> Unit) {
            listeners.add(l)
        }

        fun notifyChanged() {
            for (l in listeners) l()
        }

        /**
         * Constraints as surfaced in the UI — selection cards and checklist tally rows share this
         * list and its order. Required part types (min > 0) come first, optional ones (min == 0)
         * sort to the back; a constraint whose capability has no selectable candidate is skipped
         * rather than shown as an unsatisfiable empty card.
         */
        fun displayConstraints(): List<CountConstraint> = constraints
            .filter { candidatesByCapability.containsKey(it.capability) }
            .sortedBy { if (it.min > 0) 0 else 1 }

        /** Tally each capability from current selections, producing live constraint statuses. */
        fun constraintStatuses(): List<ConstraintStatus> = constraints.map { constraint ->
            val tally = selections.entries.sumOf { (item, count) ->
                if (count <= 0) return@sumOf 0
                val candidate = allCandidates[item] ?: return@sumOf 0
                if (constraint.capability in candidate.capabilities) count else 0
            }
            ConstraintStatus(constraint.capability, constraint.min, constraint.max, tally)
        }

        /** Total anyOf positions consumed by positive selections (across all groups). */
        fun usedSlots(): Int = selections.values.sumOf { maxOf(it, 0) }

        /**
         * Count of distinct input types that will be written to the AE2 pattern: enabled fixed
         * entries (structural + casing groups with positive count) plus candidates with positive
         * selection count.
         */
        fun distinctInputCount(): Int {
            var count = 0
            for (i in fixedEntries.indices) {
                if (!fixedEnabled.getOrDefault(i, true)) continue
                if (fixedEntries[i].currentCount(this) > 0) count++
            }
            for ((_, qty) in selections) {
                if (qty > 0) count++
            }
            return count
        }

        /**
         * Whether the current configuration can be written to an AE2 processing pattern: at least
         * one input, selections within the structure's anyOf positions, and distinct inputs within
         * AE2's 81-slot limit.
         */
        fun canWrite(): Boolean = distinctInputCount() > 0 &&
            usedSlots() <= totalSlots &&
            distinctInputCount() <= AE2_MAX_INPUTS
    }

    // ====================================================================== //
    //  Constants
    // ====================================================================== //

    private const val AE2_MAX_INPUTS = 81

    /** Popup-local layout numbers (cross-file tokens live in [MachineUiComponentStyle]). */
    private const val CHIP_WIDTH = 24f
    private const val CHIP_HEIGHT = 12f
    private const val ROW_GAP = 2f
    private const val ENTRY_PADDING = 2f

    /** Conservative per-candidate-entry width estimate for the scroller height heuristic. */
    private const val ENTRY_WIDTH_ESTIMATE = 96f
    private const val MIN_SCROLLER_HEIGHT = 36f

    private val logger = LoggerFactory.getLogger(PatternBuilderPopup::class.java)

    // Pattern-builder labels come from BuiltinOIMachineUiLang handles — no key concatenation.

    // ====================================================================== //
    //  JEI transfer entry point
    // ====================================================================== //

    /**
     * Called by [MultiblockPatternTransferHandler] when the user clicks the "+" transfer button on
     * a multiblock structure page while the AE2 pattern encoding terminal is open.
     *
     * **Timing note:** JEI's `RecipeTransferButtonController` calls `recipesGui.onClose()`
     * immediately after our `transferRecipe` returns null (no error). `RecipesGui.onClose()`
     * replaces the entire screen — destroying any GUI layer pushed during the handler call. To
     * avoid this race the actual popup opening is deferred to the next client tick via
     * [Minecraft.execute]; by then JEI has restored the AE2 `PatternEncodingTermScreen`, so
     * `pushGuiLayer` stacks correctly on top of it.
     */
    @JvmStatic
    fun openFromTransfer(definition: MachineDefinition, menu: PatternEncodingTermMenu) {
        val mc = Minecraft.getInstance()

        val metadata = definition.metadata(MultiblockControllerMetadata.TYPE).firstOrNull()
        if (metadata == null) {
            logger.warn("Pattern builder invoked for non-controller definition {}", definition.id())
            return
        }

        // Capture state eagerly (the menu must be open for the stock query).
        val preview = BlueprintPreview.of(metadata.blueprint(), definition)
        val baseState = extractState(preview)
        val title: Component = definition.registeredBlock().get().name
        val stockIndex = buildStockIndex(menu, baseState.allCandidates.keys)
        val state = PatternBuilderState(
            fixedEntries = baseState.fixedEntries,
            anyOfGroups = baseState.anyOfGroups,
            candidatesByCapability = sortCandidates(baseState.candidatesByCapability, stockIndex),
            allCandidates = baseState.allCandidates,
            constraints = baseState.constraints,
            totalSlots = baseState.totalSlots,
            stockIndex = stockIndex,
        )

        // Defer to next tick — JEI's onClose() runs synchronously after this returns, replacing
        // the screen. We must wait for that to finish before layering on top of the terminal.
        mc.execute {
            if (mc.screen == null) {
                logger.warn("Cannot open pattern builder popup: no active screen after JEI close")
                return@execute
            }

            val close: () -> Unit = { mc.popGuiLayer() }
            val commit: () -> Unit = {
                try {
                    if (state.canWrite()) {
                        DefaultAe2PatternEncoder.INSTANCE.encode(menu, buildAe2Inputs(state), buildAe2Output(title))
                    }
                } catch (e: Exception) {
                    logger.error("Failed to encode AE2 pattern", e)
                } finally {
                    close()
                }
            }

            val backdrop = buildPopup(state, title, commit, close)
            val ui = ModularUI.of(UI.of(backdrop, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)))
            mc.pushGuiLayer(object : ModularUIScreen(ui, title) {
                // Vanilla Screen consumes ESC before LDLib2 widgets see it and routes it here;
                // pop only our layer so the AE2 terminal underneath stays open.
                override fun onClose() {
                    mc.popGuiLayer()
                }
            })
        }
    }

    // ====================================================================== //
    //  Popup structure
    // ====================================================================== //

    private fun buildPopup(state: PatternBuilderState, title: Component, onCommit: () -> Unit, onClose: () -> Unit): UIElement {
        val panel = MachineUiContainerTemplate.createPopupPanel(MachineUiComponentStyle.patternBuilderPanelWidth).apply {
            setId("oi_pattern_builder_panel")
            // Stop clicks on the panel from reaching the backdrop's cancel path, and wheel events
            // from reaching whatever sits underneath.
            addEventListener(UIEvents.MOUSE_DOWN) { it.stopPropagation() }
            addEventListener(UIEvents.MOUSE_WHEEL) { it.stopPropagation() }

            addChild(
                Label().apply {
                    setId("oi_pattern_builder_title")
                    setText(title)
                    textStyle {
                        it.textColor(MachineUiComponentStyle.textSelected)
                        it.textShadow(true)
                        it.adaptiveWidth(true)
                        it.adaptiveHeight(true)
                    }
                    layout { it.flexShrink(0f) }
                },
            )
            if (state.fixedEntries.isNotEmpty()) {
                addChild(buildFixedSection(state))
                addChild(
                    MachineUiLayout.horizontalDivider(
                        height = MachineUiComponentStyle.boxTextureWidth,
                        id = "oi_pattern_builder_section_divider",
                    ),
                )
            }
            buildCapabilitySection(state)?.let { addChild(it) }
            addChild(buildChecklist(state))
            addChild(buildActionRow(state, onCommit, onClose))
        }

        return MachineUiContainerTemplate.createPopupBackdrop().apply {
            setId("oi_pattern_builder_backdrop")
            // ESC is handled by the layered Screen (see openFromTransfer); only backdrop clicks
            // need an explicit cancel here.
            addEventListener(UIEvents.MOUSE_DOWN) { event ->
                onClose()
                event.stopPropagation()
            }
            addChild(panel)
        }
    }

    // ====================================================================== //
    //  Fixed block section
    // ====================================================================== //

    private fun buildFixedSection(state: PatternBuilderState): UIElement = MachineUiLayout.column(gap = ROW_GAP, id = "oi_pattern_builder_fixed_section") {
        root.layout { it.widthPercent(100f) }
        add(
            Label().apply {
                setId("oi_pattern_builder_fixed_header")
                setText(BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_FIXED_BLOCKS.getComponent())
                textStyle {
                    it.textColor(MachineUiComponentStyle.textMuted)
                    it.textShadow(false)
                    it.adaptiveWidth(true)
                    it.adaptiveHeight(true)
                }
                layout { it.flexShrink(0f) }
            },
        )
        state.fixedEntries.forEachIndexed { index, entry ->
            add(buildFixedRow(state, index, entry))
        }
    }

    private fun buildFixedRow(state: PatternBuilderState, index: Int, entry: FixedEntry): UIElement {
        val isCasingGroup = entry is FixedEntry.CasingGroup

        val toggleButton = MachineUiComponentTemplate.createIconButton(
            DynamicTexture.of {
                if (state.fixedEnabled.getOrDefault(index, true)) Icons.CHECKBOX_MARKED else Icons.CHECKBOX_BLANK
            },
            BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_TOGGLE_FIXED.key(),
        ) {
            val current = state.fixedEnabled.getOrDefault(index, true)
            state.fixedEnabled[index] = !current
            state.notifyChanged()
        }.apply {
            setId("oi_pattern_builder_fixed_toggle_$index")
        }

        val slot = MachineUiComponentTemplate.createItemSlot().apply {
            setId("oi_pattern_builder_fixed_slot_$index")
            setItem(entry.displayStack)
            layout {
                it.width(MachineUiComponentStyle.slotSize.toFloat())
                it.height(MachineUiComponentStyle.slotSize.toFloat())
                it.flexShrink(0f)
            }
        }

        val nameLabel = Label().apply {
            setId("oi_pattern_builder_fixed_name_$index")
            setText(entry.displayStack.hoverName)
            textStyle {
                it.textColor(MachineUiComponentStyle.textNormal)
                it.textShadow(false)
                it.adaptiveWidth(true)
            }
            layout { it.flexGrow(1f) }
        }

        val countChip = Label().apply {
            setId("oi_pattern_builder_fixed_count_$index")
            setText(Component.literal("×${entry.currentCount(state)}"))
            textStyle {
                it.textColor(if (isCasingGroup) MachineUiComponentStyle.ledInfo else MachineUiComponentStyle.ledWaiting)
                it.textShadow(true)
                it.textAlignHorizontal(Horizontal.RIGHT)
                it.textAlignVertical(Vertical.CENTER)
                it.adaptiveWidth(false)
                it.adaptiveHeight(false)
            }
            style {
                it.background(
                    if (isCasingGroup) {
                        MachineUiComponentStyle.patternBuilderCasingChipTexture()
                    } else {
                        MachineUiComponentStyle.previewCountChipTexture()
                    },
                )
            }
            layout {
                it.width(CHIP_WIDTH)
                it.height(CHIP_HEIGHT)
                it.paddingRight(3f)
                it.flexShrink(0f)
            }
        }

        // Casing group counts shrink as their group's selections grow.
        if (isCasingGroup) {
            state.onChanged {
                countChip.setText(Component.literal("×${entry.currentCount(state)}"))
            }
        }

        return MachineUiLayout.row(
            gap = MachineUiComponentStyle.boxAllGap,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "oi_pattern_builder_fixed_row_$index",
        ) {
            add(toggleButton)
            add(slot)
            add(nameLabel)
            add(countChip)
        }
    }

    // ====================================================================== //
    //  Capability section
    // ====================================================================== //

    /** Inner content width available to the capability scroller (panel minus chrome and bar). */
    private fun capabilityContentWidth(): Float {
        val panelPadding = MachineUiComponentStyle.boxTextureWidth + MachineUiComponentStyle.boxAllPadding
        return MachineUiComponentStyle.patternBuilderPanelWidth - 2f * panelPadding -
            MachineUiComponentStyle.scrollBarWidth
    }

    private fun buildCapabilitySection(state: PatternBuilderState): UIElement? {
        val constraintList = state.displayConstraints()
        if (constraintList.isEmpty()) return null
        val scroller = MachineUiContainerTemplate.createScrollView(
            capabilityContentWidth(),
            estimateScrollerHeight(state, constraintList),
        ).apply {
            setId("oi_pattern_builder_capability_scroller")
        }
        for ((index, constraint) in constraintList.withIndex()) {
            val candidates = state.candidatesByCapability[constraint.capability] ?: continue
            val card = buildCapabilityCard(state, constraint, candidates)
            if (index < constraintList.size - 1) {
                card.layout { it.marginBottom(MachineUiComponentStyle.boxAllGap) }
            }
            scroller.addScrollViewChild(card)
        }
        return scroller
    }

    /**
     * Fixed-height approximation of the card stack (the template scroller has no adaptive-height
     * mode): per card a header line plus wrap rows of candidate entries. Only aesthetics depend on
     * this — the scroller clamps to [MachineUiComponentStyle.patternBuilderScrollerMaxHeight] and
     * scrolls past it either way.
     */
    private fun estimateScrollerHeight(state: PatternBuilderState, constraints: List<CountConstraint>): Float {
        val perRow = maxOf(1, (capabilityContentWidth() / ENTRY_WIDTH_ESTIMATE).toInt())
        var total = 0f
        for ((index, constraint) in constraints.withIndex()) {
            val candidateCount = state.candidatesByCapability[constraint.capability]?.size ?: continue
            val rows = (candidateCount + perRow - 1) / perRow
            val entryHeight = MachineUiComponentStyle.slotSize + 2f * ENTRY_PADDING
            total += 2f * MachineUiComponentStyle.boxAllPadding + MachineUiComponentStyle.buttonHeight +
                ROW_GAP + rows * (entryHeight + ROW_GAP)
            if (index < constraints.size - 1) total += MachineUiComponentStyle.boxAllGap
        }
        return total.coerceIn(MIN_SCROLLER_HEIGHT, MachineUiComponentStyle.patternBuilderScrollerMaxHeight)
    }

    private fun buildCapabilityCard(state: PatternBuilderState, constraint: CountConstraint, candidates: List<CandidateBlock>): UIElement {
        val capability = constraint.capability
        val idPath = capability.id().path

        val header = MachineUiLayout.row(
            gap = 0f,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "oi_pattern_builder_capability_header_$idPath",
        ) {
            add(
                Label().apply {
                    setId("oi_pattern_builder_capability_name_$idPath")
                    setText(capability.displayName())
                    textStyle {
                        it.textColor(MachineUiComponentStyle.textNormal)
                        it.textShadow(true)
                        it.adaptiveWidth(true)
                        it.adaptiveHeight(true)
                    }
                    layout { it.flexGrow(1f) }
                },
            )
            add(
                Label().apply {
                    setId("oi_pattern_builder_capability_min_$idPath")
                    setText(Component.literal("≥${constraint.min}"))
                    textStyle {
                        it.textColor(MachineUiComponentStyle.ledInfo)
                        it.textShadow(true)
                        it.textAlignHorizontal(Horizontal.CENTER)
                        it.textAlignVertical(Vertical.CENTER)
                        it.adaptiveWidth(false)
                        it.adaptiveHeight(false)
                    }
                    style { it.background(MachineUiComponentStyle.patternBuilderCasingChipTexture()) }
                    layout {
                        it.width(CHIP_WIDTH)
                        it.height(CHIP_HEIGHT)
                        it.flexShrink(0f)
                    }
                },
            )
        }

        val body = MachineUiLayout.row(
            gap = ROW_GAP,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            flexWrap = FlexWrap.WRAP,
            id = "oi_pattern_builder_capability_body_$idPath",
        ) {
            for (candidate in candidates) {
                add(buildCandidateEntry(state, candidate))
            }
        }

        val card = MachineUiLayout.column(
            gap = ROW_GAP,
            id = "oi_pattern_builder_capability_card_$idPath",
        ) {
            root.layout {
                it.paddingAll(MachineUiComponentStyle.boxAllPadding)
                it.widthPercent(100f)
            }
            add(header)
            add(body)
        }

        fun updateCardBorder() {
            val satisfied = state.constraintStatuses()
                .find { it.capability == capability }?.satisfied == true
            card.style { it.background(MachineUiComponentStyle.patternBuilderCardTexture(satisfied)) }
        }
        updateCardBorder()
        state.onChanged { updateCardBorder() }

        return card
    }

    private fun buildCandidateEntry(state: PatternBuilderState, candidate: CandidateBlock): UIElement {
        val item = candidate.item

        val slot = MachineUiComponentTemplate.createItemSlot().apply {
            setId("oi_pattern_builder_candidate_slot")
            setItem(candidate.displayStack)
            layout {
                it.width(MachineUiComponentStyle.slotSize.toFloat())
                it.height(MachineUiComponentStyle.slotSize.toFloat())
                it.flexShrink(0f)
            }
            val tips = candidate.capabilities.map { it.displayName() }.toTypedArray()
            if (tips.isNotEmpty()) style.tooltips(*tips)
        }

        val countLabel = Label().apply {
            setId("oi_pattern_builder_candidate_count")
            setText(Component.literal("${state.selections.getOrDefault(item, 0)}"))
            textStyle {
                it.textColor(candidateCountColor(state.stockIndex, item, state.selections.getOrDefault(item, 0)))
                it.textShadow(true)
                it.adaptiveWidth(false)
                it.textAlignHorizontal(Horizontal.CENTER)
                it.textAlignVertical(Vertical.CENTER)
            }
            layout {
                it.width(MachineUiComponentStyle.patternBuilderCountWidth)
                it.height(MachineUiComponentStyle.buttonHeight)
                it.flexShrink(0f)
            }
        }

        val minusButton = MachineUiComponentTemplate.createIconButton(
            MachineUiIcons.minus(),
            BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_REMOVE.key(),
        ) {
            val count = state.selections.getOrDefault(item, 0)
            if (count > 0) {
                state.selections[item] = count - 1
                state.notifyChanged()
            }
        }.apply { setId("oi_pattern_builder_candidate_minus") }

        val plusButton = MachineUiComponentTemplate.createIconButton(
            MachineUiIcons.plus(),
            BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_ADD.key(),
        ) {
            val count = state.selections.getOrDefault(item, 0)
            if (count < candidate.eligiblePositions && groupHasCapacity(state, candidate)) {
                state.selections[item] = count + 1
                state.notifyChanged()
            }
        }.apply { setId("oi_pattern_builder_candidate_plus") }

        // Update count display and availability color on every state change.
        state.onChanged {
            val count = state.selections.getOrDefault(item, 0)
            countLabel.setText(Component.literal("$count"))
            countLabel.textStyle { it.textColor(candidateCountColor(state.stockIndex, item, count)) }
        }

        return MachineUiLayout.row(
            gap = ROW_GAP,
            alignItems = AlignItems.CENTER,
            id = "oi_pattern_builder_candidate_entry",
        ) {
            root.style { it.background(MachineUiComponentStyle.patternBuilderEntryTexture()) }
            root.layout { it.paddingAll(ENTRY_PADDING) }
            add(slot)
            add(minusButton)
            add(countLabel)
            add(plusButton)
        }
    }

    /** Whether [candidate]'s anyOf group still has unselected positions left. */
    private fun groupHasCapacity(state: PatternBuilderState, candidate: CandidateBlock): Boolean {
        val group = state.anyOfGroups.getOrNull(candidate.groupIndex) ?: return true
        val groupUsed = state.selections.entries.sumOf { (item, count) ->
            if (count <= 0) return@sumOf 0
            val c = state.allCandidates[item] ?: return@sumOf 0
            if (c.groupIndex == candidate.groupIndex) count else 0
        }
        return groupUsed < group.totalPositions
    }

    // ====================================================================== //
    //  Checklist section
    // ====================================================================== //

    private fun buildChecklist(state: PatternBuilderState): UIElement {
        // Left panel: live tallies for the constraints shown above, in the same card order.
        // Local bindings — the popup tree is client-only.
        val displayConstraints = state.displayConstraints()
        val leftPanel = MachineUiContainerTemplate.createLcdData(LcdData.Orientation.VERTICAL)
        for (constraint in displayConstraints) {
            leftPanel.addLocalBoundEntry(
                constraint.capability.displayName(),
                {
                    val cs = state.constraintStatuses().find { it.capability == constraint.capability }
                    Component.literal("${cs?.current ?: 0}/${constraint.min}")
                },
                {
                    val cs = state.constraintStatuses().find { it.capability == constraint.capability }
                    if (cs?.satisfied == true) LcdData.LED_OUTPUT else MachineUiComponentStyle.ledError
                },
            )
        }

        // Right panel: AE2 pattern input slot usage.
        val rightPanel = MachineUiContainerTemplate.createLcdData(LcdData.Orientation.VERTICAL)
        rightPanel.addLocalBoundEntry(
            BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_INPUTS_SHORT.getComponent(),
            { Component.literal("${state.distinctInputCount()}/$AE2_MAX_INPUTS") },
            {
                if (state.distinctInputCount() <= AE2_MAX_INPUTS) {
                    MachineUiComponentStyle.ledInfo
                } else {
                    MachineUiComponentStyle.ledError
                }
            },
        )

        return MachineUiLayout.row(
            gap = MachineUiComponentStyle.boxAllGap,
            widthPercent = 100f,
            alignItems = AlignItems.STRETCH,
            id = "oi_pattern_builder_checklist",
        ) {
            if (displayConstraints.isNotEmpty()) {
                add(
                    leftPanel.apply {
                        layout {
                            it.flexGrow(1f)
                            it.flexBasis(0f)
                        }
                    },
                )
            }
            add(
                rightPanel.apply {
                    layout {
                        it.flexGrow(1f)
                        it.flexBasis(0f)
                    }
                },
            )
        }
    }

    // ====================================================================== //
    //  Action row
    // ====================================================================== //

    private fun buildActionRow(state: PatternBuilderState, onCommit: () -> Unit, onClose: () -> Unit): UIElement {
        val writeButton = MachineUiComponentTemplate.createButton(
            BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_WRITE.getComponent(),
        ).apply {
            setId("oi_pattern_builder_commit")
            layout {
                it.width(MachineUiComponentStyle.popupActionButtonWidth)
            }
            setOnClick { _ ->
                if (state.canWrite()) onCommit()
            }
        }

        val updateWrite: () -> Unit = {
            if (state.canWrite()) {
                // Re-assigning the selection state replays the full enabled button chrome.
                writeButton.selected = false
            } else {
                val disabled = MachineUiComponentStyle.patternBuilderDisabledButtonTexture()
                writeButton.style { it.background(disabled) }
                writeButton.buttonStyle {
                    it.baseTexture(disabled)
                    it.hoverTexture(disabled)
                    it.pressedTexture(disabled)
                }
                writeButton.textStyle { it.textColor(MachineUiComponentStyle.textMuted) }
            }
        }
        updateWrite()
        state.onChanged(updateWrite)

        return MachineUiLayout.row(
            gap = 0f,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            justifyContent = AlignContent.SPACE_BETWEEN,
            id = "oi_pattern_builder_action_row",
        ) {
            add(
                MachineUiComponentTemplate.createButton(
                    BuiltinOIMachineUiLang.UI_PATTERN_BUILDER_CANCEL.getComponent(),
                ).apply {
                    setId("oi_pattern_builder_cancel")
                    layout {
                        it.width(MachineUiComponentStyle.popupActionButtonWidth)
                    }
                    setOnClick { _ -> onClose() }
                },
            )
            add(writeButton)
        }
    }

    // ====================================================================== //
    //  State extraction from the blueprint preview
    // ====================================================================== //

    /**
     * Build a [PatternBuilderState] from a [BlueprintPreview].
     *
     * Walks [BlueprintPreview.candidatesByCell] to classify each cell:
     * - Single-candidate cells with no role -> fixed structural blocks (controller included)
     * - Multi-candidate (anyOf) cells -> grouped by candidate signature; role-bearing candidates
     *   feed the capability section, the first role-less candidate becomes the group's casing
     *
     * AnyOf cells with the same set of candidate items merge into one [AnyOfGroup]; each group's
     * casing appears as a [FixedEntry.CasingGroup] with a dynamic count (total positions minus
     * selections in that group). Candidates are de-duplicated by [Item]; dual-role hatches merge
     * their capabilities.
     */
    fun extractState(preview: BlueprintPreview): PatternBuilderState {
        // Aggregate fixed blocks by item (insertion ordered).
        val fixedAggregator = LinkedHashMap<Item, FixedAggregation>()
        // Constraints keyed by capability (first-seen wins; the blueprint-wide requirement is the
        // same on every candidate of one capability).
        val constraintMap = LinkedHashMap<PartRole, CountConstraint>()

        // AnyOf grouping: candidate signature (set of items) -> group index.
        val signatureToGroupIndex = LinkedHashMap<Set<Item>, Int>()
        val groupBuilders = mutableListOf<AnyOfGroupBuilder>()
        val candidateMap = LinkedHashMap<Item, MutableCandidateBuilder>()
        var totalAnyOfCount = 0

        for ((_, cellCandidates) in preview.candidatesByCell()) {
            if (cellCandidates.isEmpty()) continue

            if (isFixedCell(cellCandidates)) {
                val item = cellCandidates[0].item().item
                val existing = fixedAggregator[item]
                if (existing != null) {
                    existing.count++
                } else {
                    fixedAggregator[item] = FixedAggregation(cellCandidates[0].item().copy(), 1)
                }
            } else {
                totalAnyOfCount++

                // This cell's candidate signature plus its casing face (first role-less item).
                val cellSignature = LinkedHashSet<Item>()
                var cellCasingStack: ItemStack? = null
                for (candidate in cellCandidates) {
                    if (candidate.item().isEmpty) continue
                    cellSignature.add(candidate.item().item)
                    if (!candidate.hasRole() && cellCasingStack == null) {
                        cellCasingStack = candidate.item().copy()
                    }
                }

                val groupIndex = signatureToGroupIndex.getOrPut(cellSignature) {
                    val idx = groupBuilders.size
                    groupBuilders.add(AnyOfGroupBuilder(idx, 0, cellCasingStack))
                    idx
                }
                groupBuilders[groupIndex].totalPositions++

                val countedInCell = HashSet<Item>()
                for (candidate in cellCandidates) {
                    if (candidate.item().isEmpty) continue
                    val capability = candidate.role() ?: continue
                    val item = candidate.item().item
                    val builder = candidateMap.getOrPut(item) {
                        MutableCandidateBuilder(candidate.item().copy(), mutableSetOf(), groupIndex = groupIndex)
                    }
                    builder.capabilities.add(capability)
                    if (countedInCell.add(item)) {
                        builder.eligiblePositions++
                    }
                    if (capability !in constraintMap) {
                        constraintMap[capability] = CountConstraint(capability, candidate.min(), candidate.max())
                    }
                }
            }
        }

        val anyOfGroups = groupBuilders.map { gb ->
            AnyOfGroup(gb.groupIndex, gb.totalPositions, gb.casingDisplayStack)
        }

        // Fixed entries = structural blocks, then one casing line per group that has a casing face.
        val fixedEntries = mutableListOf<FixedEntry>()
        for (agg in fixedAggregator.values) {
            fixedEntries.add(FixedEntry.Structural(agg.stack, agg.count))
        }
        for (group in anyOfGroups) {
            if (group.casingDisplayStack != null) {
                fixedEntries.add(FixedEntry.CasingGroup(group.casingDisplayStack, group))
            }
        }

        val allCandidates = candidateMap.map { (item, builder) ->
            item to CandidateBlock(
                item,
                builder.displayStack,
                builder.capabilities.toSet(),
                builder.eligiblePositions,
                builder.groupIndex,
            )
        }.toMap(LinkedHashMap())

        val candidatesByCapability = LinkedHashMap<PartRole, MutableList<CandidateBlock>>()
        for (candidate in allCandidates.values) {
            for (capability in candidate.capabilities) {
                candidatesByCapability.getOrPut(capability) { mutableListOf() }.add(candidate)
            }
        }

        return PatternBuilderState(
            fixedEntries = fixedEntries,
            anyOfGroups = anyOfGroups,
            candidatesByCapability = candidatesByCapability,
            allCandidates = allCandidates,
            constraints = constraintMap.values.toList(),
            totalSlots = totalAnyOfCount,
        )
    }

    /** A cell is "fixed" when it has exactly one candidate with no role (no part capability). */
    private fun isFixedCell(candidates: List<BlueprintPreview.Candidate>): Boolean = candidates.size == 1 && !candidates[0].hasRole()

    private class FixedAggregation(val stack: ItemStack, var count: Int)

    private class AnyOfGroupBuilder(val groupIndex: Int, var totalPositions: Int, val casingDisplayStack: ItemStack?)

    private class MutableCandidateBuilder(val displayStack: ItemStack, val capabilities: MutableSet<PartRole>, var eligiblePositions: Int = 0, val groupIndex: Int = 0)

    // ====================================================================== //
    //  Stock helpers
    // ====================================================================== //

    private fun buildStockIndex(menu: PatternEncodingTermMenu, items: Set<Item>): MeStockIndex {
        val raw = MeStockQuerier.query(menu, items)
        return MeStockIndex(raw.mapValues { (_, v) -> StockSnapshot(v.available(), v.craftable()) })
    }

    /** In-stock first, then craftable, then unavailable; ties broken by stored amount, descending. */
    private fun sortCandidates(candidatesByCapability: Map<PartRole, List<CandidateBlock>>, stockIndex: MeStockIndex): Map<PartRole, List<CandidateBlock>> = candidatesByCapability.mapValues { (_, candidates) ->
        candidates.sortedWith(
            compareBy<CandidateBlock> { stockIndex.status(it.item, 1).sortPriority }
                .thenByDescending { stockIndex.get(it.item)?.available ?: 0L },
        )
    }

    /** Zero count still hints at availability (green=stocked, amber=craftable, muted=absent). */
    private fun candidateCountColor(stockIndex: MeStockIndex, item: Item, count: Int): Int = when (stockIndex.status(item, maxOf(count, 1))) {
        AvailabilityStatus.IN_STOCK -> MachineUiComponentStyle.ledOutput
        AvailabilityStatus.CRAFTABLE -> MachineUiComponentStyle.ledWaiting
        AvailabilityStatus.UNAVAILABLE -> MachineUiComponentStyle.textMuted
    }

    // ====================================================================== //
    //  AE2 pattern writing
    // ====================================================================== //

    /**
     * Build the AE2 processing-pattern input list from the current state: enabled fixed entries
     * (structural blocks + casing groups with their dynamic remainder counts), then positive
     * selections. Each entry is a single-element list (processing patterns use
     * `List<List<GenericStack>>` where each inner list is one slot's alternatives). Truncated to
     * [AE2_MAX_INPUTS] entries.
     */
    fun buildAe2Inputs(state: PatternBuilderState): List<List<GenericStack>> {
        val inputs = mutableListOf<List<GenericStack>>()

        for (i in state.fixedEntries.indices) {
            if (!state.fixedEnabled.getOrDefault(i, true)) continue
            val entry = state.fixedEntries[i]
            val count = entry.currentCount(state)
            if (count <= 0) continue
            val key = AEItemKey.of(entry.displayStack) ?: continue
            inputs.add(listOf(GenericStack(key, count.toLong())))
        }

        for ((item, count) in state.selections) {
            if (count <= 0) continue
            val candidate = state.allCandidates[item] ?: continue
            val key = AEItemKey.of(candidate.displayStack) ?: continue
            inputs.add(listOf(GenericStack(key, count.toLong())))
        }

        return if (inputs.size > AE2_MAX_INPUTS) inputs.take(AE2_MAX_INPUTS) else inputs
    }

    /**
     * Build the AE2 processing-pattern output: a named oak sign carrying the multiblock title —
     * a craftable "blueprint item" the ME autocrafting tree can target.
     */
    fun buildAe2Output(title: Component): List<GenericStack> {
        val signStack = Items.OAK_SIGN.defaultInstance.copy()
        signStack.set(DataComponents.CUSTOM_NAME, title)
        val key = AEItemKey.of(signStack) ?: return emptyList()
        return listOf(GenericStack(key, 1L))
    }
}
