package net.ptcrys.topo.apiv2.machine.multiblock.ui

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockClientDiagnosis
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockController
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Blueprint
import net.ptcrys.topo.apiv2.machine.ui.LcdData
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.apiv2.machine.ui.MachineUiLayout

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.TextColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.texture.Icons
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignItems

/**
 * The controller GUI's structure surfaces, ported from the old GTOdyssey controller pages:
 *
 * - [page]: the interactive 3D structure scene with the full toolbar, an eye toggle flipping
 *   between the required example and the live as-built view (auto-refreshing), and cell selection.
 * - [diagnosticsPanel]: formation state plus a live diagnosis list — every reason the structure is
 *   not formed (wrong/missing/misoriented blocks with icons and local coordinates, count
 *   shortfalls), each cell-level row clickable to highlight that cell in the scene, followed by the
 *   selected cell's requirement details.
 */
object MultiblockStructurePage {
    private const val REFRESH_INTERVAL_TICKS = 10
    private const val REASON_DISPLAY_MAX = 10
    private const val ICON_CYCLE_TICKS = 20

    /** Shared selection channel: scene clicks, reason rows, and the detail dock stay in sync. */
    class Selection {
        private var current: BlockPos? = null
        private val listeners = ArrayList<(BlockPos?) -> Unit>()

        fun addListener(listener: (BlockPos?) -> Unit) {
            listeners.add(listener)
        }

        fun current(): BlockPos? = current

        fun select(offset: BlockPos?) {
            current = offset
            for (listener in listeners) listener(offset)
        }
    }

    @JvmStatic
    fun page(machine: MachineBlockEntity, controller: MultiblockController, blueprint: Blueprint, selection: Selection): UIElement {
        val preview = BlueprintPreview.of(blueprint, machine.definition())
        val requiredBlocks = preview.blocks()
        val builtBlocks = MultiblockClientDiagnosis.detectedBlocks(machine, blueprint)
        val startWithBuilt = builtBlocks.isNotEmpty()
        val sceneBlocks = if (startWithBuilt) builtBlocks else requiredBlocks
        // The rendered core is always the FULL blueprint footprint: the as-built view of a partial
        // structure leaves its empty cells as air, and the required/as-built toggle can then light
        // any cell up — a core limited to the initial block set would pin every later swap to it.
        val scene = MultiblockScenePreview.scene(sceneBlocks, requiredBlocks.keys)

        val panelRef = arrayOfNulls<MultiblockScenePanel.Panel>(1)
        val modeToggle = sceneModeToggle(machine, blueprint, requiredBlocks, startWithBuilt, builtBlocks, panelRef) {
            if (selection.current() != null) selection.select(null)
        }
        val panel = MultiblockScenePanel.of(scene, requiredBlocks.keys)
            .leadingControl(modeToggle)
            .onSelect { offset ->
                if (offset == selection.current()) selection.select(null) else selection.select(offset)
            }
            .build()
        panelRef[0] = panel
        selection.addListener { offset -> panel.highlight(offset) }

        return MachineUiLayout.column(
            gap = 2f,
            width = MachineUiComponentStyle.playerInventoryWidth.toFloat(),
            alignItems = AlignItems.STRETCH,
            id = "oi_multiblock_structure_page",
        ) {
            add(
                panel.sceneArea().apply {
                    layout { it.height(MachineUiComponentStyle.structurePageSceneHeight) }
                },
            )
        }
    }

    /**
     * Formation state + live diagnosis list + selected-cell expected/current comparison — the
     * right-column component, per the v5 spec
     * ({@code /specs/2026-06-10-structure-panel-redesign-design.md}).
     */
    @JvmStatic
    fun diagnosticsPanel(machine: MachineBlockEntity, controller: MultiblockController, blueprint: Blueprint, selection: Selection): UIElement {
        val preview = BlueprintPreview.of(blueprint, machine.definition())

        // ① Formed LCD strip: "Formed Yes/No" left, "matched/total" flush right. The yes/no flag is
        // server-truth (S2C bound); the match counter is client-side diagnosis, refreshed below.
        val matchCounter = lcdLabel(MachineUiComponentStyle.textMuted, Horizontal.RIGHT).apply {
            setId("oi_multiblock_match_counter")
            textStyle { it.adaptiveWidth(false) }
            layout {
                it.flexGrow(1f)
                it.flexShrink(0f)
            }
        }
        val formationState = MachineUiLayout.row(
            gap = 3f,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "oi_multiblock_formation_state",
        ) {
            root.style { it.backgroundTexture(MachineUiComponentStyle.lcdFrameTexture()) }
            root.layout { it.paddingAll(4f) }
            add(
                lcdLabel(MachineUiComponentStyle.textMuted, Horizontal.LEFT).apply {
                    setText(Component.translatable("ui.topo.multiblock.formed"))
                },
            )
            add(
                lcdLabel(MachineUiComponentStyle.ledText, Horizontal.LEFT).apply {
                    bind(
                        DataBindingBuilder.componentS2C {
                            val formed = controller.formed()
                            val color = if (formed) MachineUiComponentStyle.ledOutput else MachineUiComponentStyle.ledError
                            tinted(
                                Component.translatable(
                                    if (formed) {
                                        "ui.topo.multiblock.yes"
                                    } else {
                                        "ui.topo.multiblock.no"
                                    },
                                ),
                                color,
                            )
                        }.build(),
                    )
                },
            )
            add(matchCounter)
        }

        // ② Capped scroll view: the sampled rows + footer live in a fixed-height viewport so a wall
        // of problems scrolls instead of growing the card past the screen.
        val reasons = MachineUiContainerTemplate.createScrollView(
            MachineUiComponentStyle.diagnosisListContentWidth,
            MachineUiComponentStyle.diagnosisListMaxHeight,
        ).apply {
            setId("oi_multiblock_reasons")
            cushionScrollContent(this)
        }

        // ③+④ Selected-cell section: header row (block + verdict tag) and the expected/current
        // comparison table, both fixed (no inner scrolling — at most a handful of property rows).
        val detailBody = MachineUiLayout.column(
            gap = MachineUiComponentStyle.boxAllGap,
            alignItems = AlignItems.STRETCH,
            id = "oi_multiblock_cell_detail_body",
        ) {
            root.layout { it.widthPercent(100f) }
        }
        val detail = MachineUiLayout.column(
            gap = MachineUiComponentStyle.cardSectionGap,
            alignItems = AlignItems.STRETCH,
            id = "oi_multiblock_cell_detail",
        ) {
            root.layout { it.widthPercent(100f) }
            add(sectionDivider())
            add(detailBody)
            root.setDisplay(false)
        }

        // All-met replacement for the problem list: a short stats LCD instead of a tall empty box.
        val statsPanel = MachineUiLayout.column(
            gap = MachineUiComponentStyle.boxAllGap,
            alignItems = AlignItems.STRETCH,
            id = "oi_multiblock_stats",
        ) {
            root.layout { it.widthPercent(100f) }
            root.setDisplay(false)
        }

        val lastSignature = arrayOfNulls<String>(1)
        fun refresh() {
            val report = MultiblockClientDiagnosis.report(machine, blueprint, REASON_DISPLAY_MAX)
            matchCounter.setText(Component.literal("${report.matchedCells()}/${report.totalCells()}"))
            val signature = signature(report)
            if (signature == lastSignature[0]) return
            lastSignature[0] = signature
            val allMet = report.allMet()
            reasons.setDisplay(!allMet)
            statsPanel.setDisplay(allMet)
            if (allMet) {
                statsPanel.clearAllChildren()
                statsPanel.addChild(
                    hintRow(
                        Component.translatable("ui.topo.multiblock.diagnose.all_met"),
                        MachineUiComponentStyle.ledOutput,
                    ),
                )
                statsPanel.addChild(
                    MachineUiContainerTemplate.createLcdData(LcdData.Orientation.VERTICAL)
                        .addStaticEntry(
                            Component.translatable("ui.topo.multiblock.stats.blocks"),
                            Component.literal("${report.totalCells()}"),
                            MachineUiComponentStyle.ledText,
                        )
                        .addStaticEntry(
                            Component.translatable("ui.topo.multiblock.stats.parts"),
                            Component.literal("${report.partCount()}"),
                            MachineUiComponentStyle.ledText,
                        )
                        .addStaticEntry(
                            Component.translatable("ui.topo.multiblock.stats.capture"),
                            Component.literal(String.format("%.2f ms", report.captureNanos() / 1_000_000.0)),
                            MachineUiComponentStyle.textMuted,
                        )
                        .addStaticEntry(
                            Component.translatable("ui.topo.multiblock.stats.scan"),
                            Component.literal(String.format("%.2f ms", report.scanNanos() / 1_000_000.0)),
                            MachineUiComponentStyle.textMuted,
                        ),
                )
            } else {
                reasons.clearAllScrollViewChildren()
                for (row in reasonRows(report, selection)) reasons.addScrollViewChild(row)
            }
        }

        fun refreshDetail(offset: BlockPos?) {
            detailBody.clearAllChildren()
            val previewState = offset?.let { preview.blocks()[it] }
            if (offset == null || previewState == null) {
                detail.setDisplay(false)
                return
            }
            val diagnosis = MultiblockClientDiagnosis.diagnoseCell(machine, blueprint, offset)
            detailBody.addChild(detailHeaderRow(previewState, diagnosis))
            // Expected vs current, folded to the controller's actual orientation — the facing the
            // table shows is the one to place. Green = goal/met, amber = the value to fix.
            MultiblockClientDiagnosis.expectedStateAt(machine, blueprint, offset)?.let { expected ->
                val actual = MultiblockClientDiagnosis.foundBlockAt(machine, blueprint, offset)
                val comparisons = PropertyDisplays.compare(expected, actual)
                if (comparisons.isNotEmpty()) {
                    detailBody.addChild(comparisonTable(comparisons))
                }
            }
            detail.setDisplay(true)
        }

        selection.addListener(::refreshDetail)
        refresh()
        refreshDetail(selection.current())

        val ticks = intArrayOf(0)
        return MachineUiLayout.column(
            gap = MachineUiComponentStyle.cardSectionGap,
            alignItems = AlignItems.STRETCH,
            id = "oi_multiblock_status_body",
        ) {
            root.layout { it.widthPercent(100f) }
            root.addEventListener(UIEvents.TICK) {
                // Visibility gate: a hidden card (other page active) skips the client re-scan —
                // a refresh of a 100k-cell structure is not free even captured + compiled.
                if (ticks[0]++ % REFRESH_INTERVAL_TICKS == 0 && root.isDisplayed) {
                    refresh()
                }
            }
            add(formationState)
            add(reasons)
            add(statsPanel)
            add(detail)
        }
    }

    /** A single-line label in the LCD font; color is the fallback for unstyled component parts. */
    private fun lcdLabel(color: Int, align: Horizontal): Label = Label().apply {
        textStyle {
            it.textColor(color)
            it.textShadow(false)
            it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
            it.textWrap(TextWrap.NONE)
            it.textAlignHorizontal(align)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(true)
        }
        layout {
            it.widthAuto()
            it.height(MachineUiComponentStyle.lcdLineHeight.toFloat())
            it.flexShrink(0f)
        }
    }

    private fun sectionDivider(): UIElement = MachineUiLayout.horizontalDivider(height = 1f)

    /**
     * Vertical cushion inside a scroll view's content box, so the first/last visible row never sits
     * glyph-to-edge against the viewport border. Padding lives on the content container (not the
     * viewport), keeping the scroller's extent math untouched.
     */
    private fun cushionScrollContent(scroller: ScrollerView) {
        scroller.viewContainer { container ->
            container.layout {
                it.paddingTop(2f)
                it.paddingBottom(2f)
            }
        }
    }

    private fun reasonRows(report: MultiblockClientDiagnosis.Report, selection: Selection): List<UIElement> {
        if (report.allMet()) {
            return listOf(
                hintRow(
                    Component.translatable("ui.topo.multiblock.diagnose.all_met"),
                    MachineUiComponentStyle.ledOutput,
                ),
            )
        }
        val rows = ArrayList<UIElement>()
        for (reason in report.shown()) rows.add(reasonRow(reason, selection))
        if (report.hiddenProblems() > 0) {
            rows.add(
                hintRow(
                    Component.translatable("ui.topo.multiblock.diagnose.more", report.hiddenProblems()),
                    MachineUiComponentStyle.textMuted,
                ),
            )
        }
        return rows
    }

    /**
     * One problem, one line: {@code [icon] expected-name …… coords}. The name's color is the
     * category (red = missing/wrong block or part-count, amber = state mismatch); the full sentence
     * lives in the tooltip. Cell-level rows click to select the cell in the scene.
     */
    private fun reasonRow(reason: MultiblockClientDiagnosis.Reason, selection: Selection): UIElement {
        val offset = reason.localPos()
        val content = MachineUiLayout.row(
            gap = 4f,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "oi_multiblock_reason_row_content",
        ) {
            // Always reserve the icon column (transparent placeholder when the reason has no icon)
            // so every row's text starts at the same x. Multi-candidate rows (part-count problems:
            // every machine that could fulfil the role) cycle their icon JEI-style.
            add(
                UIElement().apply {
                    setId("oi_multiblock_reason_icon")
                    val icons = reason.icons().filter { !it.isEmpty }
                    if (icons.isNotEmpty()) {
                        style { it.backgroundTexture(ItemStackTexture(icons[0])) }
                    }
                    if (icons.size > 1) {
                        val tick = intArrayOf(0)
                        addEventListener(UIEvents.TICK) {
                            if (++tick[0] % ICON_CYCLE_TICKS == 0) {
                                val next = icons[(tick[0] / ICON_CYCLE_TICKS) % icons.size]
                                style { it.backgroundTexture(ItemStackTexture(next)) }
                            }
                        }
                    }
                    layout {
                        it.width(14f)
                        it.height(14f)
                        it.flexShrink(0f)
                    }
                },
            )
            add(
                Label().apply {
                    setId("oi_multiblock_reason_title")
                    setText(reason.title())
                    textStyle {
                        it.textColor(kindColor(reason.kind()))
                        it.textShadow(false)
                        // LCD font size: at the default 9px font a name overflows its shrunken box and
                        // PAINTS OVER the coords (labels do not clip). Same size as the coords column,
                        // plus wrap as the overflow valve for extreme names — overlap is impossible.
                        it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
                        it.textWrap(TextWrap.WRAP)
                        it.adaptiveWidth(false)
                        it.textAlignHorizontal(Horizontal.LEFT)
                        it.textAlignVertical(Vertical.CENTER)
                        it.adaptiveHeight(true)
                    }
                    layout {
                        // Flex-basis 0 + grow/shrink: exactly the width left of icon and coords.
                        it.width(0f)
                        it.flexGrow(1f)
                        it.flexShrink(1f)
                    }
                },
            )
            offset?.let { pos ->
                add(
                    Label().apply {
                        setId("oi_multiblock_reason_coords")
                        setText(Component.literal("${pos.x},${pos.y},${pos.z}"))
                        textStyle {
                            it.textColor(MachineUiComponentStyle.textMuted)
                            it.textShadow(false)
                            it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
                            it.textWrap(TextWrap.NONE)
                            it.textAlignHorizontal(Horizontal.RIGHT)
                            it.textAlignVertical(Vertical.CENTER)
                            it.adaptiveWidth(true)
                        }
                        layout {
                            it.widthAuto()
                            it.flexShrink(0f)
                        }
                    },
                )
            }
        }
        val container: UIElement = if (offset != null) {
            Button().apply {
                noText()
                setOnClick { selection.select(offset) }
                style {
                    it.tooltips(
                        reason.message(),
                        tinted(
                            Component.translatable("ui.topo.multiblock.diagnose.locate"),
                            MachineUiComponentStyle.textMuted,
                        ),
                    )
                    it.background(MachineUiComponentStyle.inlineRowButtonBaseTexture())
                }
                buttonStyle {
                    it.baseTexture(MachineUiComponentStyle.inlineRowButtonBaseTexture())
                    it.hoverTexture(MachineUiComponentStyle.inlineRowButtonHoverTexture())
                    it.pressedTexture(MachineUiComponentStyle.inlineRowButtonPressedTexture())
                }
            }
        } else {
            UIElement().apply {
                style { it.tooltips(reason.message()) }
            }
        }
        return container.apply {
            setId("oi_multiblock_reason_row")
            layout {
                it.widthPercent(100f)
                it.paddingHorizontal(3f)
                it.paddingVertical(2f)
                it.heightAuto()
            }
            addChild(content)
        }
    }

    /** Footer/empty-state line of the problem list (muted "…and N more", green "all met"). */
    private fun hintRow(text: Component, color: Int): UIElement = MachineUiLayout.row(
        gap = 0f,
        widthPercent = 100f,
        id = "oi_multiblock_reason_hint",
    ) {
        root.layout {
            it.paddingHorizontal(3f)
            it.paddingVertical(2f)
        }
        add(
            Label().apply {
                setText(text)
                textStyle {
                    it.textColor(color)
                    it.textShadow(false)
                    it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
                    it.textWrap(TextWrap.NONE)
                    it.adaptiveWidth(true)
                }
                layout { it.widthAuto() }
            },
        )
    }

    private fun kindColor(kind: MultiblockClientDiagnosis.Kind): Int = if (kind.propertyLevel()) MachineUiComponentStyle.ledWaiting else MachineUiComponentStyle.ledError

    /**
     * Selected-cell header: {@code [expected-block icon] name …… verdict tag}. The tag is the
     * high-level category only (Empty cell / Wrong block / State mismatch / OK) — blockstate
     * specifics live in the comparison table below.
     */
    private fun detailHeaderRow(previewState: BlockState, diagnosis: MultiblockClientDiagnosis.Reason?): UIElement = MachineUiLayout.row(
        gap = 4f,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
        id = "oi_multiblock_cell_header",
    ) {
        root.layout {
            it.paddingHorizontal(3f)
            it.paddingVertical(2f)
        }
        val icon = diagnosis?.icons()?.firstOrNull() ?: ItemStack(previewState.block)
        if (!icon.isEmpty) {
            add(
                UIElement().apply {
                    setId("oi_multiblock_cell_header_icon")
                    style { it.backgroundTexture(ItemStackTexture(icon)) }
                    layout {
                        it.width(14f)
                        it.height(14f)
                        it.flexShrink(0f)
                    }
                },
            )
        }
        add(
            Label().apply {
                setId("oi_multiblock_cell_header_name")
                setText(diagnosis?.title() ?: previewState.block.name)
                textStyle {
                    it.textColor(MachineUiComponentStyle.ledInfo)
                    it.textShadow(false)
                    // Same overflow rule as the reason rows: LCD size + wrap, never paint over the
                    // verdict tag.
                    it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
                    it.textWrap(TextWrap.WRAP)
                    it.adaptiveWidth(false)
                    it.textAlignHorizontal(Horizontal.LEFT)
                    it.textAlignVertical(Vertical.CENTER)
                    it.adaptiveHeight(true)
                }
                layout {
                    it.width(0f)
                    it.flexGrow(1f)
                    it.flexShrink(1f)
                }
            },
        )
        add(
            Label().apply {
                setId("oi_multiblock_cell_header_tag")
                setText(Component.translatable(verdictKey(diagnosis)))
                textStyle {
                    it.textColor(verdictColor(diagnosis))
                    it.textShadow(false)
                    it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
                    it.textWrap(TextWrap.NONE)
                    it.textAlignHorizontal(Horizontal.RIGHT)
                    it.textAlignVertical(Vertical.CENTER)
                    it.adaptiveWidth(true)
                }
                layout {
                    it.widthAuto()
                    it.flexShrink(0f)
                }
            },
        )
    }

    private fun verdictKey(diagnosis: MultiblockClientDiagnosis.Reason?): String = diagnosis?.kind()?.headlineKey() ?: "ui.topo.multiblock.diagnose.cell_ok"

    private fun verdictColor(diagnosis: MultiblockClientDiagnosis.Reason?): Int = if (diagnosis == null) MachineUiComponentStyle.ledOutput else kindColor(diagnosis.kind())

    /**
     * Expected/current table in an LCD frame: labels muted left, values flush right (LcdData's
     * stretched-vertical form). A mismatched aspect renders {@code current(amber) → expected(green)},
     * a satisfied or incomparable one just the goal in green — all green means nothing left to fix.
     */
    private fun comparisonTable(comparisons: List<PropertyDisplays.Comparison>): UIElement {
        val lcd = MachineUiContainerTemplate.createLcdData(LcdData.Orientation.VERTICAL)
        for (comparison in comparisons) {
            val expected = tinted(comparison.expected(), MachineUiComponentStyle.ledOutput)
            val value = if (comparison.matches()) {
                expected
            } else {
                tinted(comparison.actual()!!, MachineUiComponentStyle.ledWaiting)
                    .copy()
                    .append(tinted(Component.literal(" → "), MachineUiComponentStyle.textMuted))
                    .append(expected)
            }
            lcd.addStaticEntry(comparison.label(), value, MachineUiComponentStyle.ledText)
        }
        return lcd
    }

    private fun tinted(text: Component, color: Int): MutableComponent = text.copy().withStyle { it.withColor(TextColor.fromRgb(color and 0xFFFFFF)) }

    private fun sceneModeToggle(machine: MachineBlockEntity, blueprint: Blueprint, requiredBlocks: Map<BlockPos, BlockState>, startWithBuilt: Boolean, initialBuiltBlocks: Map<BlockPos, BlockState>, panelRef: Array<MultiblockScenePanel.Panel?>, beforeModeChange: () -> Unit): UIElement {
        val showingBuilt = booleanArrayOf(startWithBuilt)
        val lastBuilt = arrayOfNulls<Map<BlockPos, BlockState>>(1)
        if (startWithBuilt) lastBuilt[0] = initialBuiltBlocks

        // Closure trick: the click action needs the button itself to refresh its tooltip, but the
        // factory takes the action at construction — populate the back-reference right after.
        val buttonRef = arrayOfNulls<MachineUiComponentTemplate.SelectableButton>(1)
        val button = MachineUiComponentTemplate.createIconButton(
            Icons.EYE,
            modeTooltipKey(showingBuilt[0]),
        ) {
            beforeModeChange()
            showingBuilt[0] = !showingBuilt[0]
            if (showingBuilt[0]) {
                val built = MultiblockClientDiagnosis.detectedBlocks(machine, blueprint)
                lastBuilt[0] = built
                panelRef[0]?.swapBlocks(built, requiredBlocks.keys, false)
            } else {
                lastBuilt[0] = null
                panelRef[0]?.swapBlocks(requiredBlocks, requiredBlocks.keys, true)
            }
            buttonRef[0]?.style {
                it.tooltips(Component.translatable(modeTooltipKey(showingBuilt[0])))
            }
        }
        buttonRef[0] = button
        button.setId("oi_multiblock_scene_mode_button")
        // Live as-built refresh: while showing the built structure, re-read it on a short interval
        // and swap only when it actually changed.
        val ticks = intArrayOf(0)
        button.addEventListener(UIEvents.TICK) {
            if (!showingBuilt[0]) return@addEventListener
            if (ticks[0]++ % REFRESH_INTERVAL_TICKS != 0) return@addEventListener
            val built = MultiblockClientDiagnosis.detectedBlocks(machine, blueprint)
            if (built != lastBuilt[0]) {
                lastBuilt[0] = built
                panelRef[0]?.swapBlocks(built, requiredBlocks.keys, false)
            }
        }
        return button
    }

    private fun modeTooltipKey(showingBuilt: Boolean): String = if (showingBuilt) {
        "ui.topo.multiblock.scene.switch_to_required"
    } else {
        "ui.topo.multiblock.scene.switch_to_detected"
    }

    private fun signature(report: MultiblockClientDiagnosis.Report): String {
        val sb = StringBuilder()
        sb.append(report.totalProblems()).append('|').append(report.matchedCells()).append('\n')
        for (reason in report.shown()) {
            sb.append(reason.localPos()?.asLong() ?: -1L).append(':')
                .append(reason.kind().ordinal).append(':')
                .append(reason.message().string).append('\n')
        }
        return sb.toString()
    }
}
