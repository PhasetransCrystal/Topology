package net.ptcrys.topo.data.ore.common.display

import net.ptcrys.topo.api.machine.ui.LcdData
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.api.material.Material
import net.ptcrys.topo.api.ore.OrePlacement
import net.ptcrys.topo.api.ore.OreVein
import net.ptcrys.topo.api.ore.shape.OreVeinShape
import net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes
import net.ptcrys.topo.data.ore.BuiltinTopoOreLang
import net.ptcrys.topo.helper.MaterialHelper

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems

import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

/**
 * Ore-vein JEI panel shell. Placement-channel differences use sealed [OrePlacement] matching;
 * dim/host/exposure labels come from declaration contributions (no path-string when).
 */
object OreVeinPanels {
    const val PANEL_WIDTH: Int = 192
    const val PANEL_HEIGHT: Int = 142

    private const val HEADER_HEIGHT = 16f
    private const val ICON_TILE = 16
    private const val PREVIEW_FRAME = 62
    private const val PREVIEW_INNER = 54
    private const val TOP_ROW_GAP = 4f
    private const val SECTION_GAP = 1f
    private const val COMPOSITION_LIST_HEIGHT = 54f
    private const val COMPOSITION_CONTENT_WIDTH = 177f
    private const val COMPOSITION_PADDING_RIGHT = 4f
    private const val ROW_HEIGHT = 18f
    private const val INFO_ROW_HEIGHT = 10f
    private const val INFO_KEY_WIDTH = 38f
    private const val INFO_FONT = 7f
    private const val ENTRY_NAME_WIDTH = 40f
    private const val ENTRY_PERCENT_WIDTH = 26f
    private const val ENTRY_BAR_HEIGHT = 6f
    private const val SLOT_SIZE = 18

    private const val COLOR_TEXT_TITLE = 0xFFF0F0F0.toInt()
    private const val COLOR_TEXT_VALUE = 0xFFDCDCDC.toInt()
    private const val COLOR_TEXT_SECTION = 0xFFA8AEB6.toInt()
    private const val COLOR_BAR_TRACK = 0x66000000
    private const val COLOR_BAR_TRACK_SHADOW = 0x44000000
    private const val COLOR_BAR_LEAD_CAP = 0xFFFFE8A8.toInt()

    fun build(vein: OreVein): UIElement {
        val entries = entryViews(vein)
        val total = max(1, entries.sumOf { it.weight })
        val placement = vein.placement()
        return MachineUiLayout.box(needPadding = true, needGap = false) {
            root.setId("topo_ore_vein_preview")
            root.layout {
                it.width(PANEL_WIDTH.toFloat())
                it.height(PANEL_HEIGHT.toFloat())
                it.paddingVertical(3f)
            }
            add(buildHeader(vein, entries))
            add(buildTopRow(vein, entries, total, placement))
            add(buildComposition(entries))
        }
    }

    private fun buildHeader(vein: OreVein, entries: List<EntryView>): UIElement {
        val icon = entries.firstOrNull()?.stack ?: ItemStack.EMPTY
        return MachineUiLayout.row(
            gap = 0f,
            height = HEADER_HEIGHT,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "topo_ore_vein_header",
        ) {
            if (!icon.isEmpty) {
                add(
                    UIElement().apply {
                        setId("topo_ore_vein_specimen")
                        layout {
                            it.width(ICON_TILE.toFloat())
                            it.height(ICON_TILE.toFloat())
                            it.flexShrink(0f)
                        }
                        style { it.backgroundTexture(ItemStackTexture(icon)) }
                    },
                )
            }
            add(
                Label().apply {
                    setId("topo_ore_vein_title")
                    setText(vein.displayName())
                    textStyle {
                        it.textColor(COLOR_TEXT_TITLE)
                        it.textShadow(false)
                        it.textAlignHorizontal(Horizontal.LEFT)
                        it.adaptiveWidth(false)
                        it.adaptiveHeight(true)
                    }
                    layout {
                        it.height(10f)
                        it.flexGrow(1f)
                        it.marginLeft(if (icon.isEmpty) 0f else 4f)
                    }
                },
            )
        }
    }

    private fun buildTopRow(vein: OreVein, entries: List<EntryView>, total: Int, placement: OrePlacement): UIElement = MachineUiLayout.row(
        gap = TOP_ROW_GAP,
        widthPercent = 100f,
        alignItems = AlignItems.FLEX_START,
        id = "topo_ore_vein_top_row",
    ) {
        add(buildPreviewTile(vein, entries, total, placement))
        add(buildInfoCard(vein, placement))
    }

    private fun buildPreviewTile(vein: OreVein, entries: List<EntryView>, total: Int, placement: OrePlacement): UIElement = MachineUiContainerTemplate.createBox(needPadding = true, needGap = false).apply {
        setId("topo_ore_vein_preview_tile")
        layout {
            it.width(PREVIEW_FRAME.toFloat())
            it.height(PREVIEW_FRAME.toFloat())
            it.flexShrink(0f)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
        }
        addChild(
            when (placement) {
                is OrePlacement.Grid ->
                    ShapePreviewElement(entries, total, placement.shape(), PREVIEW_INNER)
                is OrePlacement.Feature ->
                    FeaturePreviewElement(entries, total, placement.size(), PREVIEW_INNER)
            },
        )
    }

    private fun buildInfoCard(vein: OreVein, placement: OrePlacement): UIElement = MachineUiLayout.column(gap = 0f, id = "topo_ore_vein_info") {
        root.style { it.background(MachineUiComponentStyle.lcdFrameTexture()) }
        root.layout {
            it.flexGrow(1f)
            it.paddingHorizontal(4f)
            it.paddingVertical(2f)
        }
        add(infoRow(BuiltinTopoOreLang.LABEL_DIM.getComponent(), dimensions(vein), LcdData.LED_TEXT))
        add(
            infoRow(
                BuiltinTopoOreLang.LABEL_HEIGHT.getComponent(),
                Component.literal("${vein.minY()}..${vein.maxY()}"),
                LcdData.LED_RUNNING,
            ),
        )
        add(infoRow(BuiltinTopoOreLang.LABEL_HOST.getComponent(), hostRocks(vein), LcdData.LED_TEXT))
        add(infoRow(BuiltinTopoOreLang.LABEL_SIZE.getComponent(), sizeLabel(placement), LcdData.LED_TEXT))
        add(infoRow(BuiltinTopoOreLang.LABEL_SPACING.getComponent(), spacingLabel(placement), LcdData.LED_RUNNING))
        add(
            infoRow(
                BuiltinTopoOreLang.LABEL_EXPOSED.getComponent(),
                vein.airExposurePolicy().describe(vein.airExposureDiscardChance()),
                exposureLed(vein),
            ),
        )
    }

    private fun infoRow(key: Component, value: Component, valueColor: Int): UIElement = MachineUiLayout.row(
        gap = 0f,
        height = INFO_ROW_HEIGHT,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
    ) {
        add(
            Label().apply {
                setText(key)
                textStyle {
                    it.textColor(COLOR_TEXT_SECTION)
                    it.textShadow(false)
                    it.fontSize(INFO_FONT)
                    it.adaptiveWidth(false)
                    it.textAlignHorizontal(Horizontal.LEFT)
                }
                layout {
                    it.width(INFO_KEY_WIDTH)
                    it.height(INFO_ROW_HEIGHT)
                }
            },
        )
        add(
            Label().apply {
                setText(value)
                textStyle {
                    it.textColor(valueColor)
                    it.textShadow(false)
                    it.fontSize(INFO_FONT)
                    it.adaptiveWidth(false)
                    it.textAlignHorizontal(Horizontal.RIGHT)
                }
                layout {
                    it.flexGrow(1f)
                    it.height(INFO_ROW_HEIGHT)
                }
            },
        )
    }

    private fun buildComposition(entries: List<EntryView>): UIElement {
        val scroller = MachineUiContainerTemplate.createScrollView(COMPOSITION_CONTENT_WIDTH, COMPOSITION_LIST_HEIGHT)
        scroller.setId("topo_ore_vein_composition")
        scroller.layout {
            it.marginTop(SECTION_GAP)
            it.flexShrink(0f)
        }
        scroller.viewContainer { container ->
            container.layout { it.paddingRight(COMPOSITION_PADDING_RIGHT) }
        }
        for (entry in entries) {
            scroller.addScrollViewChild(buildEntryRow(entry))
        }
        return scroller
    }

    private fun buildEntryRow(entry: EntryView): UIElement = MachineUiLayout.row(
        gap = 0f,
        height = ROW_HEIGHT,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
        id = "topo_ore_vein_entry_row",
    ) {
        add(buildRowSlot(entry))
        add(
            Label().apply {
                setId("topo_ore_vein_entry_name")
                setText(entry.name)
                textStyle {
                    it.textColor(COLOR_TEXT_VALUE)
                    it.textShadow(false)
                    it.textAlignHorizontal(Horizontal.LEFT)
                    it.adaptiveWidth(false)
                    it.adaptiveHeight(true)
                }
                layout {
                    it.width(ENTRY_NAME_WIDTH)
                    it.height(9f)
                    it.marginLeft(4f)
                }
            },
        )
        add(CompositionBar(entry.color, entry.ratio))
        add(
            Label().apply {
                setId("topo_ore_vein_entry_percent")
                setText(Component.literal(percent(entry.ratio)))
                textStyle {
                    it.textColor(COLOR_TEXT_SECTION)
                    it.textShadow(false)
                    it.textAlignHorizontal(Horizontal.RIGHT)
                    it.adaptiveWidth(false)
                    it.adaptiveHeight(true)
                }
                layout {
                    it.width(ENTRY_PERCENT_WIDTH)
                    it.height(9f)
                    it.marginLeft(3f)
                }
            },
        )
    }

    private fun buildRowSlot(entry: EntryView): ItemSlot {
        val slot = MachineUiComponentTemplate.createItemSlot()
        slot.setId("topo_ore_vein_entry_slot")
        slot.layout {
            it.width(SLOT_SIZE.toFloat())
            it.height(SLOT_SIZE.toFloat())
            it.flexShrink(0f)
        }
        if (!entry.stack.isEmpty) {
            slot.setItem(entry.stack)
            val outputs = java.util.function.Supplier { java.util.stream.Stream.of(entry.stack) }
            ItemSlot.JEISupport.recipeIngredient(slot, IngredientIO.OUTPUT, outputs)
            ItemSlot.JEISupport.recipeSlot(slot, outputs)
        }
        return slot
    }

    private fun entryViews(vein: OreVein): List<EntryView> {
        val total = max(1, vein.entries().sumOf { it.weight() })
        val displayForm = vein.environment().hostRules().first().generatedForm()
        return vein.entries().map { entry ->
            val material = entry.material()
            val stack = MaterialHelper.item(material, displayForm).map { ItemStack(it) }.orElse(ItemStack.EMPTY)
            EntryView(
                material.displayName(),
                entry.weight(),
                materialColor(material),
                entry.weight().toDouble() / total,
                stack,
            )
        }.sortedByDescending { it.ratio }
    }

    private fun materialColor(material: Material): Int = material.strategy().data(BuiltinTopoMaterialDataTypes.PRIMARY_COLOR).map { it.rgb() }.orElse(0xFFFFFF)

    private fun dimensions(vein: OreVein): Component = join(vein.environment().dimensions().map { it.displayName().getComponent() })

    private fun hostRocks(vein: OreVein): Component = join(vein.environment().hostRules().map { it.displayName().getComponent() })

    private fun sizeLabel(placement: OrePlacement): Component = when (placement) {
        is OrePlacement.Grid -> BuiltinTopoOreLang.SIZE_RADIUS.getComponent(placement.radiusBlocks())
        is OrePlacement.Feature -> BuiltinTopoOreLang.SIZE_CLUSTER.getComponent(placement.size())
    }

    private fun spacingLabel(placement: OrePlacement): Component = when (placement) {
        is OrePlacement.Grid -> BuiltinTopoOreLang.SPACING_BLOCKS.getComponent(placement.gridSizeChunks() * 16)
        is OrePlacement.Feature -> BuiltinTopoOreLang.SPACING_ATTEMPTS.getComponent(placement.attemptsPerChunk())
    }

    private fun join(parts: List<Component>): Component {
        if (parts.isEmpty()) {
            return Component.empty()
        }
        val result = parts[0].copy()
        for (i in 1 until parts.size) {
            result.append(Component.literal(", "))
            result.append(parts[i])
        }
        return result
    }

    private fun exposureLed(vein: OreVein): Int {
        val chance = vein.airExposureDiscardChance()
        val policy = vein.airExposurePolicy()
        return when {
            !policy.discardExposed(chance, 0.0) -> LcdData.LED_OUTPUT
            policy.discardExposed(chance, 1.0) && policy.discardExposed(chance, 0.0) -> LcdData.LED_WAITING
            else -> LcdData.LED_TEXT
        }
    }

    private fun percent(value: Double): String = "${round(value * 100.0).toInt()}%"

    private fun chooseEntry(entries: List<EntryView>, total: Int, dx: Int, dy: Int, dz: Int): EntryView {
        val selected = Math.floorMod(dx * 7349 + dy * 9151 + dz * 5279, total)
        var cursor = 0
        for (entry in entries) {
            cursor += entry.weight
            if (selected < cursor) {
                return entry
            }
        }
        return entries.last()
    }

    private fun shade(rgb: Int, factor: Double): Int {
        val r = (round(((rgb ushr 16) and 0xFF) * factor).toInt()).coerceIn(0, 255)
        val g = (round(((rgb ushr 8) and 0xFF) * factor).toInt()).coerceIn(0, 255)
        val b = (round((rgb and 0xFF) * factor).toInt()).coerceIn(0, 255)
        return (r shl 16) or (g shl 8) or b
    }

    private class EntryView(val name: Component, val weight: Int, val color: Int, val ratio: Double, val stack: ItemStack)

    private class CompositionBar(private val color: Int, private val ratio: Double) : UIElement() {
        private val tex = ColorRectTexture(0)

        init {
            setId("topo_ore_vein_composition_bar")
            layout {
                it.flexGrow(1f)
                it.height(ENTRY_BAR_HEIGHT)
                it.marginHorizontal(3f)
            }
        }

        override fun drawBackgroundAdditional(context: IGUIContext) {
            val x = positionX
            val y = positionY
            val w = sizeWidth
            val h = sizeHeight
            if (w <= 0f || h <= 0f) return

            val base = color and 0xFFFFFF
            val fillWidth = (w * ratio).toFloat().coerceAtLeast(2f)

            tex.setColor(COLOR_BAR_TRACK)
            context.drawTexture(tex, x, y, w, h)
            tex.setColor(COLOR_BAR_TRACK_SHADOW)
            context.drawTexture(tex, x, y, w, 1f)

            tex.setColor(0xFF000000.toInt() or shade(base, 0.62))
            context.drawTexture(tex, x, y, fillWidth, h)
            tex.setColor(0xFF000000.toInt() or shade(base, 1.05))
            context.drawTexture(tex, x, y, fillWidth, h - 1f)
            tex.setColor(0xFF000000.toInt() or shade(base, 1.35))
            context.drawTexture(tex, x, y, fillWidth, 1f)

            if (fillWidth < w - 0.5f) {
                tex.setColor(COLOR_BAR_LEAD_CAP)
                context.drawTexture(tex, x + fillWidth - 1f, y, 1f, h)
            }
        }
    }

    private class ShapePreviewElement(private val entries: List<EntryView>, private val total: Int, private val shape: OreVeinShape, pixelSize: Int) : UIElement() {
        private val cellTex = ColorRectTexture(0)
        private val cells: Array<Array<PreviewCell?>> = Array(GRID_SIZE) { arrayOfNulls(GRID_SIZE) }
        private val cellPixels: Float = pixelSize / GRID_SIZE.toFloat()

        init {
            setId("topo_ore_vein_shape_preview")
            layout {
                it.width(pixelSize.toFloat())
                it.height(pixelSize.toFloat())
            }
            sample()
        }

        override fun drawBackgroundAdditional(context: IGUIContext) {
            val x = positionX
            val y = positionY
            val cs = cellPixels
            for (gz in 0 until GRID_SIZE) {
                for (gx in 0 until GRID_SIZE) {
                    val cell = cells[gz][gx] ?: continue
                    val cx = x + gx * cs
                    val cy = y + gz * cs
                    val base = cell.color and 0xFFFFFF
                    cellTex.setColor(0xFF000000.toInt() or shade(base, cell.light))
                    context.drawTexture(cellTex, cx, cy, cs, cs)
                    cellTex.setColor(0xFF000000.toInt() or shade(base, cell.light + 0.24))
                    context.drawTexture(cellTex, cx, cy, cs, 1f)
                    cellTex.setColor(0xFF000000.toInt() or shade(base, cell.light + 0.12))
                    context.drawTexture(cellTex, cx, cy, 1f, cs)
                }
            }
        }

        private fun sample() {
            for (gridZ in 0 until GRID_SIZE) {
                val dz = scaleGrid(gridZ)
                for (gridX in 0 until GRID_SIZE) {
                    val dx = scaleGrid(gridX)
                    val topY = topY(dx, dz)
                    if (topY == Int.MIN_VALUE) continue
                    val entry = chooseEntry(entries, total, dx, topY, dz)
                    val heightShade = (topY + SAMPLE_RADIUS).toDouble() / (SAMPLE_RADIUS * 2)
                    cells[gridZ][gridX] = PreviewCell(entry.color, 0.76 + heightShade * 0.38)
                }
            }
        }

        private fun topY(dx: Int, dz: Int): Int {
            var dy = SAMPLE_RADIUS
            while (dy >= -SAMPLE_RADIUS) {
                if (shape.contains(dx, dy, dz, SAMPLE_RADIUS)) return dy
                dy -= 2
            }
            return Int.MIN_VALUE
        }

        private class PreviewCell(val color: Int, val light: Double)

        companion object {
            const val SAMPLE_RADIUS = 32
            const val GRID_SIZE = 21

            fun scaleGrid(index: Int): Int = Math.round(-SAMPLE_RADIUS + index.toFloat() * (SAMPLE_RADIUS * 2) / (GRID_SIZE - 1))
        }
    }

    private class FeaturePreviewElement(private val entries: List<EntryView>, private val total: Int, private val size: Int, private val pixelSize: Int) : UIElement() {
        private val cellTex = ColorRectTexture(0)

        init {
            setId("topo_ore_vein_feature_preview")
            layout {
                it.width(pixelSize.toFloat())
                it.height(pixelSize.toFloat())
            }
        }

        override fun drawBackgroundAdditional(context: IGUIContext) {
            val count = min(18, max(8, size * 3))
            val center = pixelSize / 2f
            val pixel = max(2f, pixelSize / 22f)
            val spread = pixelSize * 0.32f
            for (i in 0 until count) {
                val dx = (Math.floorMod(i * 13 + 7, 39) - 19) / 19f * spread
                val dy = (Math.floorMod(i * 17 + 5, 31) - 15) / 15f * spread
                if (dx * dx + dy * dy > spread * spread) continue
                val entry = chooseEntry(entries, total, dx.toInt(), dy.toInt(), i)
                val cx = positionX + center + dx
                val cy = positionY + center + dy
                val base = entry.color and 0xFFFFFF
                cellTex.setColor(0xFF000000.toInt() or shade(base, 0.85))
                context.drawTexture(cellTex, cx - pixel / 2f, cy - pixel / 2f, pixel, pixel)
                cellTex.setColor(0xFF000000.toInt() or shade(base, 1.28))
                context.drawTexture(cellTex, cx - pixel / 2f, cy - pixel / 2f, pixel, 1f)
            }
        }
    }
}
