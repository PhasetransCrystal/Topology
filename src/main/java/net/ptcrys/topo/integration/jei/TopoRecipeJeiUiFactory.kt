package net.ptcrys.topo.integration.jei

import net.ptcrys.topo.api.machine.resource.RecipeRole
import net.ptcrys.topo.api.machine.ui.LcdData
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiIcons
import net.ptcrys.topo.api.machine.ui.ResourceBar
import net.ptcrys.topo.api.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.api.recipe.RecipePreviewPlan.SlotPlan
import net.ptcrys.topo.api.recipe.TopoRecipe
import net.ptcrys.topo.api.recipe.TopoRecipeType
import net.ptcrys.topo.api.recipe.capability.RecipeCapability
import net.ptcrys.topo.api.recipe.capability.SlottedRecipeCapability
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import com.lowdragmc.lowdraglib2.gui.util.TextFormattingUtil
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection

import java.util.Locale

/** Builds the LDLIB2 tree rendered inside JEI recipe categories. */
object TopoRecipeJeiUiFactory {
    private val MIN_PANEL_WIDTH = MachineUiComponentStyle.jeiPanelMinWidth
    private val MIN_PANEL_HEIGHT = MachineUiComponentStyle.jeiPanelMinHeight
    private const val DIVIDER_BUDGET = 3
    private const val BODY_GAP = 6f

    @JvmStatic
    fun buildPreview(recipeType: TopoRecipeType<*>, recipe: TopoRecipe): ModularUI {
        val slotCounts = recipeType.previewPlan().slotted()
        // 与历史 Java 完全同构：只设 width/height + COLUMN + center，不加 min/max
        // （min/max 会让 ModularUI 在 MAX_CONTENT 布局下把内容挤扁，JEI 导航钮漂到机器侧栏）。
        val root = UIElement().apply {
            setId("topo_recipe_jei_preview")
            layout {
                it.width(jeiPanelWidth(recipeType).toFloat())
                it.height(jeiPanelHeight(recipeType).toFloat())
                it.flexDirection(FlexDirection.COLUMN)
                it.alignItems(AlignItems.CENTER)
                it.justifyContent(AlignContent.CENTER)
            }
        }
        val ioRow = RecipeUiLayout.buildRecipeIoRow(
            recipeType,
            slotCounts,
            { io, index -> buildJeiSlot(slotCounts, recipe, io, index) },
            { bar -> bindWallClockProgress(bar, recipe.duration()) },
        )
        val scalarRows = collectScalarContents(recipe)
        root.addChild(attachScalarBars(scalarRows, recipe.duration(), ioRow))
        root.addChild(buildLcd(recipe, scalarRows))
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)))
    }

    private fun buildLcd(recipe: TopoRecipe, scalarRows: List<ScalarContentRow>): LcdData {
        val lcd = MachineUiContainerTemplate.createLcdData(LcdData.Orientation.HORIZONTAL)
            .addStaticIconEntry(
                MachineUiIcons.clock(),
                Component.literal(formatDurationSeconds(recipe.duration()) + "s"),
                LcdData.LED_RUNNING,
                BuiltinTopoMachineUiLang.UI_RECIPE_TIME.getComponent(),
            )
        for (row in scalarRows) {
            val color = row.capability.resource().color()
            lcd.addStaticIconEntry(
                MachineUiIcons.iconOr(row.capability.resourceType(), ColorRectTexture(color)),
                Component.literal(formatScalarDelta(row)),
                color,
            ) {
                ResourceBar.recipeContentTooltipPanel(
                    row.capability.resource().displayName(),
                    color,
                    row.amount,
                    row.perTick,
                    if (row.consumed) ResourceBar.Flow.CONSUMED else ResourceBar.Flow.PRODUCED,
                    recipe.duration(),
                )
            }
        }
        lcd.setId("topo_recipe_jei_lcd")
        lcd.layout {
            it.height(24f)
            it.marginTop(4f)
        }
        return lcd
    }

    private fun formatScalarDelta(row: ScalarContentRow): String {
        val sign = if (row.consumed) "-" else "+"
        val body = TextFormattingUtil.formatLongToCompactString(row.amount, 3)
        return if (row.perTick) "$sign$body/t" else "$sign$body"
    }

    @JvmStatic
    fun jeiPanelWidth(recipeType: TopoRecipeType<*>): Int {
        var content = RecipeUiLayout.PLAYER_INVENTORY_WIDTH + 4
        val scalarCapabilities = recipeType.previewPlan().implicitCapabilities()
        if (scalarCapabilities > 0) {
            val barWidth = MachineUiComponentStyle.resourceBarVerticalWidth.toInt()
            val barGap = MachineUiComponentStyle.resourceBarGap.toInt()
            content += scalarCapabilities * barWidth +
                maxOf(0, scalarCapabilities - 1) * barGap +
                (if (scalarCapabilities > 1) DIVIDER_BUDGET else 0) +
                6
        }
        return maxOf(content, MIN_PANEL_WIDTH)
    }

    private data class ScalarContentRow(val capability: ScalarRecipeCapability, val amount: Long, val perTick: Boolean, val consumed: Boolean)

    private fun collectScalarContents(recipe: TopoRecipe): List<ScalarContentRow> {
        val rows = ArrayList<ScalarContentRow>()
        collectScalarInputs(rows, recipe.inputs(), false)
        collectScalarInputs(rows, recipe.tickInputs(), true)
        collectScalarOutputs(rows, recipe.outputs(), false)
        collectScalarOutputs(rows, recipe.tickOutputs(), true)
        return rows
    }

    private fun collectScalarInputs(rows: MutableList<ScalarContentRow>, entries: Array<out TopoRecipe.InputEntry<*>>, perTick: Boolean) {
        for (entry in entries) {
            val scalar = entry.capability() as? ScalarRecipeCapability ?: continue
            for (content in entry.contents()) {
                rows.add(ScalarContentRow(scalar, content as Long, perTick, consumed = true))
            }
        }
    }

    private fun collectScalarOutputs(rows: MutableList<ScalarContentRow>, entries: Array<out TopoRecipe.OutputEntry<*>>, perTick: Boolean) {
        for (entry in entries) {
            val scalar = entry.capability() as? ScalarRecipeCapability ?: continue
            for (content in entry.contents()) {
                rows.add(ScalarContentRow(scalar, content as Long, perTick, consumed = false))
            }
        }
    }

    private fun attachScalarBars(rows: List<ScalarContentRow>, durationTicks: Int, ioRow: UIElement): UIElement {
        val consumed = ArrayList<UIElement>()
        val produced = ArrayList<UIElement>()
        for (row in rows) {
            val bar = scalarBar(
                row.capability,
                row.amount,
                row.perTick,
                if (row.consumed) ResourceBar.Flow.CONSUMED else ResourceBar.Flow.PRODUCED,
                durationTicks,
            )
            if (row.consumed) consumed.add(bar) else produced.add(bar)
        }
        if (consumed.isEmpty() && produced.isEmpty()) {
            return ioRow
        }
        val barsRow = UIElement().apply {
            setId("topo_recipe_scalar_bars")
            layout {
                it.flexDirection(FlexDirection.ROW)
                it.alignItems(AlignItems.CENTER)
                it.gapColumn(MachineUiComponentStyle.resourceBarGap)
            }
            consumed.forEach { addChild(it) }
            if (consumed.isNotEmpty() && produced.isNotEmpty()) {
                addChild(
                    UIElement().apply {
                        setId("topo_recipe_scalar_bar_divider")
                        layout {
                            it.width(1f)
                            it.height(RecipeUiLayout.IO_ROW_HEIGHT * 0.6f)
                            it.flexShrink(0f)
                        }
                        style {
                            it.backgroundTexture(MachineUiComponentStyle.previewDividerTexture())
                        }
                    },
                )
            }
            produced.forEach { addChild(it) }
        }
        return UIElement().apply {
            setId("topo_recipe_body_with_scalars")
            layout {
                it.flexDirection(FlexDirection.ROW)
                it.alignItems(AlignItems.CENTER)
                it.justifyContent(AlignContent.CENTER)
                it.gapColumn(BODY_GAP)
            }
            addChild(barsRow)
            addChild(ioRow)
        }
    }

    private fun scalarBar(capability: ScalarRecipeCapability, amount: Long, perTick: Boolean, flow: ResourceBar.Flow, durationTicks: Int): UIElement {
        val bar = MachineUiComponentTemplate.createResourceBar(
            capability.resource().displayName(),
            capability.resource().color(),
            ResourceBar.Orientation.VERTICAL,
        )
        bar.setId("topo_recipe_scalar_bar")
        bar.setStaticContent(amount, perTick, flow, durationTicks)
        return bar
    }

    @JvmStatic
    fun jeiPanelHeight(recipeType: TopoRecipeType<*>): Int = maxOf(RecipeUiLayout.IO_ROW_HEIGHT + 34, MIN_PANEL_HEIGHT)

    private fun buildJeiSlot(slotCounts: Map<SlottedRecipeCapability<*, *, *>, SlotPlan>, recipe: TopoRecipe, io: RecipeRole, globalIndex: Int): UIElement {
        var remaining = globalIndex
        for ((capability, plan) in slotCounts) {
            val count = plan.count(io)
            if (remaining < count) {
                return buildTyped(capability, recipe, io, remaining, count)
            }
            remaining -= count
        }
        throw IllegalArgumentException("JEI slot index $globalIndex is out of range for $io")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I, O> buildTyped(capability: SlottedRecipeCapability<I, O, *>, recipe: TopoRecipe, io: RecipeRole, localIndex: Int, slotCount: Int): UIElement {
        if (io == RecipeRole.INPUT) {
            val start = inputContents(recipe.inputs(), capability)
            val tick = inputContents(recipe.tickInputs(), capability)
            val contentIndex = capability.previewInputSlotAssignment(slotCount, start, tick)[localIndex]
            if (contentIndex < 0) {
                return capability.createPreviewInputSlotWidget(null, false)
            }
            if (contentIndex < start.size) {
                return capability.createPreviewInputSlotWidget(start[contentIndex], false)
            }
            return capability.createPreviewInputSlotWidget(tick[contentIndex - start.size], true)
        }
        val start = outputContents(recipe.outputs(), capability)
        if (localIndex < start.size) {
            return capability.createPreviewOutputSlotWidget(start[localIndex], false)
        }
        val tick = outputContents(recipe.tickOutputs(), capability)
        val tickIndex = localIndex - start.size
        val output = if (tickIndex < tick.size) tick[tickIndex] else null
        return capability.createPreviewOutputSlotWidget(output, output != null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I> inputContents(entries: Array<out TopoRecipe.InputEntry<*>>, capability: RecipeCapability<I, *>): List<I> {
        for (entry in entries) {
            if (entry.capability() === capability) {
                return entry.contents() as List<I>
            }
        }
        return emptyList()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <O> outputContents(entries: Array<out TopoRecipe.OutputEntry<*>>, capability: RecipeCapability<*, O>): List<O> {
        for (entry in entries) {
            if (entry.capability() === capability) {
                return entry.contents() as List<O>
            }
        }
        return emptyList()
    }

    private fun bindWallClockProgress(bar: ProgressBar, durationTicks: Int) {
        val durationMs = maxOf(1, durationTicks) * 50L
        bar.bindDataSource(
            SupplierDataSource.of {
                (System.currentTimeMillis() % durationMs) / durationMs.toFloat()
            },
        )
    }

    @JvmStatic
    fun formatDurationSeconds(ticks: Int): String {
        val t = maxOf(1, ticks)
        return if (t % 20 == 0) {
            (t / 20).toString()
        } else {
            String.format(Locale.ROOT, "%.1f", t / 20.0)
        }
    }
}
