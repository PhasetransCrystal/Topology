package net.ptcrys.topo.api.machine.multiblock.ui

import net.ptcrys.topo.api.api.lang.LangKey
import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.MachineBlockEntity
import net.ptcrys.topo.api.machine.multiblock.MultiblockClientDiagnosis
import net.ptcrys.topo.api.machine.multiblock.MultiblockController
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint
import net.ptcrys.topo.api.machine.ui.LcdData
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiLayout

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

/** 控制器的结构场景与诊断面板。场景、问题行和位置详情通过 Selection 联动；诊断每次刷新共用一份朝向与读取上下文。 */
object MultiblockStructurePage {
    private const val REFRESH_INTERVAL_TICKS = 10
    private const val REASON_DISPLAY_MAX = 10
    private const val ICON_CYCLE_TICKS = 20

    /** 场景、问题行与详情共用的位置选择和诊断缓存；同一刷新周期只采集、识别一次。 */
    class Selection {
        private var current: BlockPos? = null
        private val listeners = ArrayList<(BlockPos?) -> Unit>()
        private var frame: MultiblockClientDiagnosis.Frame? = null
        private var frameMachine: MachineBlockEntity? = null
        private var frameBlueprint: Blueprint? = null
        private var frameTick = Long.MIN_VALUE

        fun diagnosis(machine: MachineBlockEntity, blueprint: Blueprint): MultiblockClientDiagnosis.Frame {
            val tick = requireNotNull(machine.level).gameTime
            val cached = frame
            if (cached != null && machine === frameMachine && blueprint === frameBlueprint && tick >= frameTick && tick - frameTick < REFRESH_INTERVAL_TICKS) return cached
            return MultiblockClientDiagnosis.capture(machine, blueprint).also {
                frame = it
                frameMachine = machine
                frameBlueprint = blueprint
                frameTick = tick
            }
        }

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
        val frame = selection.diagnosis(machine, blueprint)
        val requiredBlocks = frame.requiredPreview(machine.definition()).blocks()
        val builtBlocks = frame.detectedBlocks()
        val startWithBuilt = builtBlocks.isNotEmpty()
        val sceneBlocks = if (startWithBuilt) builtBlocks else requiredBlocks
        val renderedPositions = frame.positions()
        val scene = MultiblockScenePreview.scene(sceneBlocks, renderedPositions)

        val panelRef = arrayOfNulls<MultiblockScenePanel.Panel>(1)
        val modeToggle = sceneModeToggle(machine, blueprint, selection, startWithBuilt, panelRef) {
            if (selection.current() != null) selection.select(null)
        }
        val panel = MultiblockScenePanel.of(scene, renderedPositions)
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
            id = "topo_multiblock_structure_page",
        ) {
            add(
                panel.sceneArea().apply {
                    layout { it.height(MachineUiComponentStyle.structurePageSceneHeight) }
                },
            )
        }
    }

    /** 成型状态、定期刷新的诊断列表与选中位置的预期／实际状态对照。 */
    @JvmStatic
    fun diagnosticsPanel(machine: MachineBlockEntity, controller: MultiblockController, blueprint: Blueprint, selection: Selection): UIElement {
        // ① Formed LCD strip: "Formed Yes/No" left, "matched/total" flush right. The yes/no flag is
        // server-truth (S2C bound); the match counter is client-side diagnosis, refreshed below.
        val matchCounter = lcdLabel(MachineUiComponentStyle.textMuted, Horizontal.RIGHT).apply {
            setId("topo_multiblock_match_counter")
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
            id = "topo_multiblock_formation_state",
        ) {
            root.style { it.backgroundTexture(MachineUiComponentStyle.lcdFrameTexture()) }
            root.layout { it.paddingAll(4f) }
            add(
                lcdLabel(MachineUiComponentStyle.textMuted, Horizontal.LEFT).apply {
                    setText(TopoApiLang.UI_MULTIBLOCK_STRUCTURE_FORMED.getComponent())
                },
            )
            add(
                lcdLabel(MachineUiComponentStyle.ledText, Horizontal.LEFT).apply {
                    bind(
                        DataBindingBuilder.componentS2C {
                            val formed = controller.formed()
                            if (formed) {
                                TopoApiLang.UI_MULTIBLOCK_STRUCTURE_YES.getComponent().withColor(MachineUiComponentStyle.formedYes)
                            } else {
                                TopoApiLang.UI_MULTIBLOCK_STRUCTURE_NO.getComponent().withColor(MachineUiComponentStyle.formedNo)
                            }
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
            setId("topo_multiblock_reasons")
            cushionScrollContent(this)
        }

        // ③+④ Selected-cell section: header row (block + verdict tag) and the expected/current
        // comparison table, both fixed (no inner scrolling — at most a handful of property rows).
        val detailBody = MachineUiLayout.column(
            gap = MachineUiComponentStyle.boxAllGap,
            alignItems = AlignItems.STRETCH,
            id = "topo_multiblock_cell_detail_body",
        ) {
            root.layout { it.widthPercent(100f) }
        }
        val detail = MachineUiLayout.column(
            gap = MachineUiComponentStyle.cardSectionGap,
            alignItems = AlignItems.STRETCH,
            id = "topo_multiblock_cell_detail",
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
            id = "topo_multiblock_stats",
        ) {
            root.layout { it.widthPercent(100f) }
            root.setDisplay(false)
        }

        var diagnosisFrame = selection.diagnosis(machine, blueprint)
        var previewBlocks = diagnosisFrame.requiredPreview(machine.definition()).blocks()
        val lastSignature = arrayOfNulls<String>(1)
        fun refresh() {
            val nextFrame = selection.diagnosis(machine, blueprint)
            if (nextFrame !== diagnosisFrame) {
                diagnosisFrame = nextFrame
                previewBlocks = diagnosisFrame.requiredPreview(machine.definition()).blocks()
            }
            val report = diagnosisFrame.report(REASON_DISPLAY_MAX)
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
                        TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_ALL_MET.getComponent(),
                        MachineUiComponentStyle.ledOutput,
                    ),
                )
                statsPanel.addChild(
                    MachineUiContainerTemplate.createLcdData(LcdData.Orientation.VERTICAL)
                        .addStaticEntry(
                            TopoApiLang.UI_MULTIBLOCK_STATS_BLOCKS.getComponent(),
                            Component.literal("${report.totalCells()}"),
                            MachineUiComponentStyle.ledText,
                        )
                        .addStaticEntry(
                            TopoApiLang.UI_MULTIBLOCK_STATS_PARTS.getComponent(),
                            Component.literal("${report.partCount()}"),
                            MachineUiComponentStyle.ledText,
                        )
                        .addStaticEntry(
                            TopoApiLang.UI_MULTIBLOCK_STATS_CAPTURE.getComponent(),
                            Component.literal(String.format("%.2f ms", report.captureNanos() / 1_000_000.0)),
                            MachineUiComponentStyle.textMuted,
                        )
                        .addStaticEntry(
                            TopoApiLang.UI_MULTIBLOCK_STATS_SCAN.getComponent(),
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
            val previewState = offset?.let { previewBlocks[it] }
            if (offset == null || previewState == null || !diagnosisFrame.containsCell(offset)) {
                detail.setDisplay(false)
                return
            }
            val diagnosis = diagnosisFrame.diagnoseCell(offset)
            detailBody.addChild(detailHeaderRow(previewState, diagnosis))
            // Expected vs current, folded to the controller's actual orientation — the facing the
            // table shows is the one to place. Green = goal/met, amber = the value to fix.
            diagnosisFrame.expectedStateAt(offset)?.let { expected ->
                val actual = diagnosisFrame.foundBlockAt(offset)
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
            id = "topo_multiblock_status_body",
        ) {
            root.layout { it.widthPercent(100f) }
            root.addEventListener(UIEvents.TICK) {
                // Visibility gate: a hidden card (other page active) skips the client re-scan —
                // a refresh of a 100k-cell structure is not free even captured + compiled.
                if (ticks[0]++ % REFRESH_INTERVAL_TICKS == 0 && root.isDisplayed) {
                    refresh()
                    refreshDetail(selection.current())
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
                    TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_ALL_MET.getComponent(),
                    MachineUiComponentStyle.ledOutput,
                ),
            )
        }
        val rows = ArrayList<UIElement>()
        for (reason in report.shown()) rows.add(reasonRow(reason, selection))
        if (report.hiddenProblems() > 0) {
            rows.add(
                hintRow(
                    TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_MORE.getComponent(report.hiddenProblems()),
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
            id = "topo_multiblock_reason_row_content",
        ) {
            // Always reserve the icon column (transparent placeholder when the reason has no icon)
            // so every row's text starts at the same x. Multi-candidate rows (part-count problems:
            // every machine that could fulfil the role) cycle their icon JEI-style.
            add(
                UIElement().apply {
                    setId("topo_multiblock_reason_icon")
                    val icons = reason.iconBlocks().map { ItemStack(it) }.filter { !it.isEmpty }
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
                    setId("topo_multiblock_reason_title")
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
                        setId("topo_multiblock_reason_coords")
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
                            TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_LOCATE.getComponent(),
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
            setId("topo_multiblock_reason_row")
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
        id = "topo_multiblock_reason_hint",
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

    private fun kindColor(kind: MultiblockClientDiagnosis.Kind): Int = if (kind.warning()) MachineUiComponentStyle.ledWaiting else MachineUiComponentStyle.ledError

    /**
     * Selected-cell header: {@code [expected-block icon] name …… verdict tag}. The tag is the
     * high-level category only (Empty cell / Wrong block / State mismatch / OK) — blockstate
     * specifics live in the comparison table below.
     */
    private fun detailHeaderRow(previewState: BlockState, diagnosis: MultiblockClientDiagnosis.Reason?): UIElement = MachineUiLayout.row(
        gap = 4f,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
        id = "topo_multiblock_cell_header",
    ) {
        root.layout {
            it.paddingHorizontal(3f)
            it.paddingVertical(2f)
        }
        val icon = ItemStack(diagnosis?.iconBlocks()?.firstOrNull() ?: previewState.block)
        if (!icon.isEmpty) {
            add(
                UIElement().apply {
                    setId("topo_multiblock_cell_header_icon")
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
                setId("topo_multiblock_cell_header_name")
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
                setId("topo_multiblock_cell_header_tag")
                setText(verdictKey(diagnosis).getComponent())
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

    private fun verdictKey(diagnosis: MultiblockClientDiagnosis.Reason?): LangKey = diagnosis?.kind()?.headlineKey() ?: TopoApiLang.UI_MULTIBLOCK_DIAGNOSE_CELL_OK

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

    private fun sceneModeToggle(machine: MachineBlockEntity, blueprint: Blueprint, selection: Selection, startWithBuilt: Boolean, panelRef: Array<MultiblockScenePanel.Panel?>, beforeModeChange: () -> Unit): UIElement {
        var showingBuilt = startWithBuilt
        var previousFrame = selection.diagnosis(machine, blueprint)
        var lastBlocks = if (showingBuilt) previousFrame.detectedBlocks() else previousFrame.requiredPreview(machine.definition()).blocks()
        var lastPositions = previousFrame.positions()
        fun refreshScene(force: Boolean) {
            val frame = selection.diagnosis(machine, blueprint)
            if (!force && frame === previousFrame) return
            previousFrame = frame
            val blocks = if (showingBuilt) frame.detectedBlocks() else frame.requiredPreview(machine.definition()).blocks()
            val positions = frame.positions()
            if (force || blocks != lastBlocks || positions != lastPositions) {
                panelRef[0]?.swapBlocks(blocks, positions, !showingBuilt)
                if (selection.current()?.let { it !in positions } == true) selection.select(null)
                lastBlocks = blocks
                lastPositions = positions
            }
        }
        val buttonRef = arrayOfNulls<MachineUiComponentTemplate.SelectableButton>(1)
        val button = MachineUiComponentTemplate.createIconButton(Icons.EYE, modeTooltipKey(showingBuilt)) {
            beforeModeChange()
            showingBuilt = !showingBuilt
            refreshScene(true)
            buttonRef[0]?.style { it.tooltips(modeTooltipKey(showingBuilt).getComponent()) }
        }
        buttonRef[0] = button
        button.setId("topo_multiblock_scene_mode_button")
        val ticks = intArrayOf(0)
        button.addEventListener(UIEvents.TICK) {
            if (ticks[0]++ % REFRESH_INTERVAL_TICKS == 0 && button.isDisplayed) refreshScene(false)
        }
        return button
    }

    private fun modeTooltipKey(showingBuilt: Boolean): LangKey = if (showingBuilt) {
        TopoApiLang.UI_MULTIBLOCK_SCENE_SWITCH_TO_REQUIRED
    } else {
        TopoApiLang.UI_MULTIBLOCK_SCENE_SWITCH_TO_DETECTED
    }

    private fun signature(report: MultiblockClientDiagnosis.Report): String {
        val sb = StringBuilder()
        sb.append(report.totalProblems()).append('|').append(report.matchedCells()).append('|')
            .append(report.totalCells()).append('|').append(report.partCount()).append('|').append(report.allMet()).append('\n')
        for (reason in report.shown()) {
            sb.append(reason.localPos()?.asLong() ?: -1L).append(':')
                .append(reason.kind().ordinal).append(':')
                .append(reason.message().string).append('\n')
        }
        return sb.toString()
    }
}
