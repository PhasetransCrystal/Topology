package net.ptcrys.topo.api.pipe.ui

import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.apiv2.machine.ui.MachineUiLayout

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems

import java.util.function.Consumer

/**
 * 管道端口配置屏的行收集器 + 样式化控件工厂。
 * 策略通过 [addRow] 挂配置行；行结构走 [MachineUiLayout]，策略代码不写 flex。
 */
class PipePortUiCollector {
    private val rows = ArrayList<UIElement>()

    fun addRow(row: UIElement) {
        rows.add(row)
    }

    fun rows(): List<UIElement> = rows.toList()

    /** 设置行左侧标签：定宽列、左对齐 muted。 */
    fun caption(id: String, text: Component): Label {
        val label = Label()
        label.setId(id)
        label.setText(text)
        label.textStyle { style ->
            style.textColor(MachineUiComponentStyle.textMuted)
            style.textShadow(false)
            style.textAlignHorizontal(Horizontal.LEFT)
            style.textAlignVertical(Vertical.CENTER)
            style.adaptiveWidth(false)
            style.adaptiveHeight(false)
        }
        label.layout { layout ->
            layout.width(MachineUiComponentStyle.controlLabelColumnWidth)
            layout.height(CONTROL_HEIGHT)
            layout.flexShrink(0f)
        }
        return label
    }

    /** 横向控件行，标准间距。 */
    fun row(id: String, vararg children: UIElement): UIElement = MachineUiLayout.row(
        gap = MachineUiComponentStyle.boxAllGap,
        alignItems = AlignItems.CENTER,
        id = id,
    ) {
        children.forEach { add(it) }
    }

    /**
     * 行式设置行：muted 标签靠左、控件组靠右，撑满整行。
     */
    fun labeledRow(id: String, caption: Component, vararg controls: UIElement): UIElement = labeledRow(id, caption(id + "_caption", caption), *controls)

    fun labeledRow(id: String, captionElement: UIElement, vararg controls: UIElement): UIElement = MachineUiLayout.row(
        gap = MachineUiComponentStyle.boxAllGap,
        height = CONTROL_HEIGHT,
        alignItems = AlignItems.CENTER,
        id = id,
    ) {
        root.layout {
            it.widthPercent(100f)
            it.flexShrink(0f)
        }
        add(captionElement)
        add(
            MachineUiLayout.row(
                gap = MachineUiComponentStyle.boxAllGap,
                alignItems = AlignItems.CENTER,
                id = id + "_controls",
            ) {
                root.layout {
                    it.width(0f)
                    it.flexGrow(1f)
                    it.flexShrink(1f)
                }
                controls.forEach { add(it) }
            },
        )
    }

    /** 样式化整数选项按钮；点击写入 [selectedValue]，高亮只由权威回显更新。 */
    fun intChoiceButton(id: String, text: Component, selectedValue: BindableValue<Int>, value: Int, labelSink: Consumer<Label>): Button {
        val button = Button()
        button.setId(id)
        button.noText()
        button.buttonStyle { style ->
            style.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
            style.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
            style.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
        }
        button.layout { layout ->
            layout.width(0f)
            layout.flexGrow(1f)
            layout.flexShrink(1f)
            layout.height(CONTROL_HEIGHT)
            layout.paddingAll(0f)
            layout.justifyContent(AlignContent.CENTER)
            layout.alignItems(AlignItems.CENTER)
        }
        val label = Label()
        label.setId(id + "_label")
        label.setText(text)
        label.textStyle { style ->
            style.textColor(MachineUiComponentStyle.textMuted)
            style.textShadow(false)
            style.textAlignHorizontal(Horizontal.CENTER)
            style.textAlignVertical(Vertical.CENTER)
            style.adaptiveWidth(false)
            style.adaptiveHeight(false)
        }
        label.layout { layout ->
            layout.widthPercent(100f)
            layout.heightPercent(100f)
            layout.marginHorizontal(0f)
            layout.flexShrink(1f)
        }
        button.addChild(label)
        labelSink.accept(label)
        button.setOnClick { event ->
            if (event.button == 0) {
                selectedValue.setValue(value)
                event.stopPropagation()
            }
        }
        return button
    }

    private companion object {
        private val CONTROL_HEIGHT = MachineUiComponentStyle.controlRowHeight
    }
}
