package net.ptcrys.topo.apiv2.machine.ui

import net.ptcrys.topo.apiv2.machine.ui.tooltip.LazyHoverPanel

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.TextColor

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection

import java.util.function.Supplier

/**
 * Small LCD-like data panel shared by machine UI, JEI recipe previews, and item tooltips.
 * Colors come from [MachineUiComponentStyle]; font metrics and chrome depend on [Presentation].
 */
class LcdData private constructor(private val orientation: Orientation, private val presentation: Presentation) : UIElement() {
    private val keyColumn = UIElement()
    private val valueColumn = UIElement()
    private var hasHorizontalEntry = false
    private var pinnedKeyWidth = Float.NaN
    private var pinnedValueWidth = Float.NaN
    private val fontSize = presentation.fontSize
    private val lineHeight = presentation.lineHeight
    private val rowGap = presentation.rowGap
    private val keyValueGap = presentation.keyValueGap
    private val valueAlign = presentation.valueAlign
    private val valueShadow = presentation.valueShadow

    init {
        setId("oi_lcd_data")
        style { it.background(presentation.background()) }
        layout {
            it.paddingAll(presentation.padding)
            if (orientation == Orientation.VERTICAL) {
                it.flexDirection(FlexDirection.ROW)
                it.alignItems(AlignItems.FLEX_START)
                it.gapColumn(keyValueGap)
                it.widthAuto()
                it.flexShrink(0f)
            } else {
                it.gapAll(3f)
                it.flexDirection(FlexDirection.ROW)
                it.alignItems(AlignItems.STRETCH)
                it.widthMaxContent()
                it.flexShrink(0f)
            }
        }
        if (orientation == Orientation.VERTICAL) {
            configureVerticalColumn(keyColumn, "oi_lcd_data_keys", rowGap)
            configureVerticalColumn(valueColumn, "oi_lcd_data_values", rowGap)
            valueColumn.layout {
                it.flexGrow(1f)
                // PANEL 右对齐像仪表读数;TOOLTIP 左对齐贴近原版 tooltip 行文。
                it.alignItems(
                    if (valueAlign == Horizontal.RIGHT) AlignItems.FLEX_END else AlignItems.FLEX_START,
                )
            }
            addChildren(keyColumn, valueColumn)
        }
    }

    fun orientation(): Orientation = orientation

    fun presentation(): Presentation = presentation

    fun pinColumns(keyWidth: Float, valueWidth: Float): LcdData {
        check(orientation == Orientation.VERTICAL) {
            "pinColumns is only meaningful for VERTICAL LCD panels"
        }
        pinnedKeyWidth = keyWidth
        pinnedValueWidth = valueWidth
        keyColumn.layout { it.width(keyWidth) }
        valueColumn.layout {
            it.width(valueWidth)
            it.flexGrow(0f)
        }
        return this
    }

    fun minValueWidth(width: Float): LcdData {
        check(orientation == Orientation.VERTICAL) {
            "minValueWidth is only meaningful for VERTICAL LCD panels"
        }
        valueColumn.layout { it.minWidth(width) }
        return this
    }

    private fun applyPin(label: Label, pinnedWidth: Float) {
        if (pinnedWidth.isNaN()) {
            return
        }
        label.textStyle {
            it.adaptiveWidth(false)
            it.textWrap(TextWrap.NONE)
            it.textAlignHorizontal(Horizontal.LEFT)
        }
        label.layout { it.width(pinnedWidth) }
        label.setOverflowVisible(false)
        label.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            event.hoverTooltips = HoverTooltips.create(label.text)
        }
    }

    fun addStaticEntry(label: Component, value: Component, ledColor: Int): LcdData = addEntry(label, { value }, { ledColor }, ValueBinding.STATIC)

    fun addStaticSubEntry(label: Component, value: Component, ledColor: Int): LcdData {
        check(orientation == Orientation.VERTICAL) {
            "sub entries are only meaningful for VERTICAL LCD panels"
        }
        val title = headerLabel()
        applyPin(title, if (pinnedKeyWidth.isNaN()) Float.NaN else pinnedKeyWidth - SUB_KEY_INDENT)
        title.layout { it.marginLeft(SUB_KEY_INDENT) }
        title.setText(label)
        val data = valueLabel()
        applyPin(data, pinnedValueWidth)
        data.setText(tint(value, ledColor))
        keyColumn.addChild(title)
        valueColumn.addChild(data)
        return this
    }

    fun addBoundEntry(label: Component, value: Supplier<Component>, ledColor: Int): LcdData = addBoundEntry(label, value, { ledColor })

    fun addBoundEntry(label: Component, value: Supplier<Component>, ledColor: Supplier<Int>): LcdData = addEntry(label, value, ledColor, ValueBinding.SERVER_SYNC)

    fun addPinnedBoundEntry(label: Component, value: Supplier<Component>, valueWidth: Float, ledColor: Int): LcdData = addEntry(label, value, { ledColor }, ValueBinding.SERVER_SYNC, valueWidth)

    fun addLocalBoundEntry(label: Component, value: Supplier<Component>, ledColor: Int): LcdData = addLocalBoundEntry(label, value, { ledColor })

    fun addLocalBoundEntry(label: Component, value: Supplier<Component>, ledColor: Supplier<Int>): LcdData = addEntry(label, value, ledColor, ValueBinding.LOCAL)

    fun addLine(value: Component): LcdData {
        val label = valueLabel()
        applyPin(label, pinnedValueWidth)
        label.setText(value)
        if (orientation == Orientation.VERTICAL) {
            val spacer = headerLabel()
            applyPin(spacer, pinnedKeyWidth)
            spacer.setText(Component.empty())
            keyColumn.addChild(spacer)
            valueColumn.addChild(label)
        } else {
            addChild(label)
        }
        return this
    }

    fun addStaticIconEntry(icon: IGuiTexture, value: Component, ledColor: Int, vararg hoverLines: Component): LcdData {
        require(hoverLines.isNotEmpty()) { "Icon entry needs at least one hover line (the name)" }
        val hoverHost = appendIconEntry(icon, value, ledColor)
        hoverHost.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            event.hoverTooltips = HoverTooltips.create(*hoverLines as Array<Any>)
        }
        return this
    }

    fun addStaticIconEntry(icon: IGuiTexture, value: Component, ledColor: Int, hoverPanel: Supplier<UIElement>): LcdData {
        val hover = LazyHoverPanel(hoverPanel)
        val hoverHost = appendIconEntry(icon, value, ledColor)
        hoverHost.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            event.hoverTooltips = HoverTooltips.create(hover.get())
        }
        hoverHost.addEventListener(UIEvents.REMOVED) { hover.release() }
        return this
    }

    private fun appendIconEntry(icon: IGuiTexture, value: Component, ledColor: Int): UIElement {
        val iconElement = UIElement().apply {
            setId("oi_lcd_data_icon")
            layout {
                it.width(lineHeight.toFloat())
                it.height(lineHeight.toFloat())
                it.flexShrink(0f)
            }
            style { it.backgroundTexture(icon) }
        }
        val data = valueLabel()
        applyPin(data, pinnedValueWidth)
        data.setText(tint(value, ledColor))

        if (orientation == Orientation.VERTICAL) {
            keyColumn.addChild(iconElement)
            valueColumn.addChild(data)
            return iconElement
        }
        val entry = UIElement().apply {
            setId("oi_lcd_data_entry")
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.gapRow(1f)
                it.widthMaxContent()
                it.flexShrink(0f)
                it.alignItems(AlignItems.CENTER)
            }
            addChildren(iconElement, data)
        }
        appendHorizontalEntry(entry)
        return entry
    }

    private fun addEntry(label: Component, value: Supplier<Component>, ledColor: Supplier<Int>, binding: ValueBinding, valueWidth: Float = pinnedValueWidth): LcdData {
        val title = headerLabel()
        applyPin(title, pinnedKeyWidth)
        title.setText(label)
        val data = valueLabel()
        applyPin(data, valueWidth)
        when (binding) {
            ValueBinding.STATIC -> data.setText(tint(value.get(), ledColor.get()))
            ValueBinding.SERVER_SYNC ->
                data.bind(DataBindingBuilder.componentS2C { tint(value.get(), ledColor.get()) }.build())
            ValueBinding.LOCAL ->
                data.bindDataSource(SupplierDataSource.of { tint(value.get(), ledColor.get()) })
        }
        if (orientation == Orientation.VERTICAL) {
            keyColumn.addChild(title)
            valueColumn.addChild(data)
            return this
        }
        val entry = UIElement().apply {
            setId("oi_lcd_data_entry")
            layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.gapRow(1f)
                it.widthMaxContent()
                it.flexShrink(0f)
            }
            addChildren(title, data)
        }
        appendHorizontalEntry(entry)
        return this
    }

    private fun appendHorizontalEntry(entry: UIElement) {
        if (hasHorizontalEntry) {
            addChild(horizontalDivider())
        }
        addChild(entry)
        hasHorizontalEntry = true
    }

    private fun headerLabel(): Label = Label().apply {
        setId("oi_lcd_data_header")
        textStyle {
            it.textColor(MachineUiComponentStyle.textMuted)
            it.textShadow(false)
            it.fontSize(fontSize.toFloat())
            it.textWrap(TextWrap.NONE)
            it.textAlignHorizontal(Horizontal.LEFT)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(true)
        }
        layout {
            it.widthAuto()
            it.height(lineHeight.toFloat())
            it.flexShrink(0f)
        }
    }

    private fun valueLabel(): Label = Label().apply {
        setId("oi_lcd_data_value")
        textStyle {
            it.textColor(LED_TEXT)
            it.textShadow(valueShadow)
            it.fontSize(fontSize.toFloat())
            it.textWrap(TextWrap.NONE)
            it.adaptiveWidth(true)
            it.textAlignHorizontal(valueAlign)
            it.textAlignVertical(Vertical.CENTER)
        }
        layout {
            it.widthAuto()
            it.height(lineHeight.toFloat())
            it.flexShrink(0f)
        }
    }

    enum class Orientation {
        VERTICAL,
        HORIZONTAL,
    }

    /**
     * 渲染档位:
     * - [PANEL] 机器 UI / JEI / Jade:带 LCD 底框、7px 密排字、值列右对齐 + 发光;
     * - [TOOLTIP] 物品/悬浮工具提示:无外层卡片、字号对齐原版、值列左对齐、无发光,
     *   键值/行距更松,读成简约的 tooltip 属性行而不是无框仪表盘。
     */
    enum class Presentation {
        PANEL,
        TOOLTIP,
        ;

        val fontSize: Int
            get() = when (this) {
                PANEL -> MachineUiComponentStyle.lcdFontSize
                TOOLTIP -> MachineUiComponentStyle.lcdTooltipFontSize
            }

        val lineHeight: Int
            get() = when (this) {
                PANEL -> MachineUiComponentStyle.lcdLineHeight
                TOOLTIP -> MachineUiComponentStyle.lcdTooltipLineHeight
            }

        val padding: Float
            get() = when (this) {
                PANEL -> 4f
                TOOLTIP -> 0f
            }

        val keyValueGap: Float
            get() = when (this) {
                PANEL -> 3f
                TOOLTIP -> MachineUiComponentStyle.lcdTooltipKeyValueGap
            }

        val rowGap: Float
            get() = when (this) {
                PANEL -> 1f
                TOOLTIP -> MachineUiComponentStyle.lcdTooltipRowGap
            }

        val valueAlign: Horizontal
            get() = when (this) {
                PANEL -> Horizontal.RIGHT
                TOOLTIP -> Horizontal.LEFT
            }

        val valueShadow: Boolean
            get() = this == PANEL

        fun background(): IGuiTexture = when (this) {
            PANEL -> MachineUiComponentStyle.lcdFrameTexture()
            TOOLTIP -> MachineUiComponentStyle.transparentTexture()
        }
    }

    private enum class ValueBinding {
        STATIC,
        SERVER_SYNC,
        LOCAL,
    }

    companion object {
        @JvmField
        val LED_IDLE: Int = MachineUiComponentStyle.ledIdle

        @JvmField
        val LED_RUNNING: Int = MachineUiComponentStyle.ledRunning

        @JvmField
        val LED_WAITING: Int = MachineUiComponentStyle.ledWaiting

        @JvmField
        val LED_OUTPUT: Int = MachineUiComponentStyle.ledOutput

        @JvmField
        val LED_TEXT: Int = MachineUiComponentStyle.ledText

        @JvmField
        val SUB_KEY_INDENT = 7f

        @JvmStatic
        @JvmOverloads
        fun create(orientation: Orientation, presentation: Presentation = Presentation.PANEL): LcdData = LcdData(orientation, presentation)

        private fun horizontalDivider(): UIElement = UIElement().apply {
            setId("oi_lcd_data_divider")
            layout {
                it.width(1f)
                it.marginVertical(MachineUiComponentStyle.lcdDividerInset)
                it.flexShrink(0f)
            }
            style { it.backgroundTexture(MachineUiComponentStyle.previewDividerTexture()) }
        }

        private fun configureVerticalColumn(column: UIElement, id: String, rowGap: Float) {
            column.setId(id)
            column.layout {
                it.flexDirection(FlexDirection.COLUMN)
                it.gapRow(rowGap)
                it.widthMaxContent()
                it.flexShrink(0f)
            }
        }

        private fun tint(value: Component, ledColor: Int): Component = value.copy().withStyle { style -> style.withColor(TextColor.fromRgb(ledColor and 0xFFFFFF)) }
    }
}
