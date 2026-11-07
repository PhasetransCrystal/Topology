package net.ptcrys.topo.api.machine.ui

import net.ptcrys.topo.api.api.lang.TopoApiLang

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems

/**
 * 通用模态数量编辑弹窗(设计系统组件,自 ME 配置页的数量编辑器泛化):挂在 modular UI 根上的
 * 全窗遮罩,拦截底层点击,移除自身即关闭。调用方给定 标题/初值/[min max] 域/提交回调——
 * ME 配置页(目标量 1..∞)与管道端口屏(批量 0..cap×interval、周期 窗口域)共用同一实现。
 *
 * 交互模型:步进钮标签显示"生效步长"并随修饰键实时刷新(修饰键三档统一口径:Shift ×10、
 * Ctrl ×100、Shift+Ctrl ×1000,与屏上步进钮一致);文本框是唯一事实源;ENTER 确认,ESC 或
 * 点遮罩取消。按键经遮罩上的 capture 监听 + 焦点陷阱([UIElement.setEnforceFocus],对齐
 * LDLib2 Dialog)处理——KEY_DOWN 只从 focusedElement 分发,弹窗存续期间焦点绝不能逃逸或
 * 置空。(原版 Screen 会先吃 ESC 关掉整屏;输入横竖会被丢弃,取消语义不变。)
 */
object AmountEditorPopup {

    /** 屏上步进钮与弹窗共用的修饰键倍率口径。 */
    @JvmStatic
    fun modifierMultiplier(): Long {
        val mc = Minecraft.getInstance()
        val shift = mc.hasShiftDown()
        val ctrl = mc.hasControlDown()
        return when {
            shift && ctrl -> 1000L
            ctrl -> 100L
            shift -> 10L
            else -> 1L
        }
    }

    @JvmStatic
    fun open(host: UIElement, title: Component, initialAmount: Long, min: Long, max: Long, onCommit: (Long) -> Unit) {
        require(min <= max) { "amount popup needs min <= max, got $min..$max" }
        val state = AmountState(initialAmount, min, max)
        val stepButtonRefs = mutableListOf<Pair<Button, Long>>()
        MachineUiContainerTemplate.openModalPopup(
            host,
            "topo_amount_popup",
            MachineUiComponentStyle.popupPanelWidth,
            title,
        ) { close ->
            val amountField = buildAmountField(state)
            val buttonRow = buildButtonRow(state, amountField, stepButtonRefs)
            val confirm: () -> Unit = {
                // 提交前以文本框当前内容为准(用户可能只改了字没点步进)。
                state.syncFromField(amountField.value)
                onCommit(state.value)
                close()
            }
            val actionRow = buildActionRow(confirm, close)
            MachineUiContainerTemplate.ModalPopupContent(
                body = listOf(buttonRow, amountField, actionRow),
                onCancel = close,
                onConfirm = confirm,
                // 步进钮标签显示"生效步长",随修饰键(Shift/Ctrl)实时刷新——每个按键按下/抬起都重算。
                onKeyChange = { refreshStepLabels(stepButtonRefs) },
                // 立即聚焦文本框可直接打字;壳的焦点陷阱 + capture 键监听保证 ENTER/ESC 之后落焦何处都生效。
                focusTarget = amountField,
                dismissOnClickOutside = true,
            )
        }
    }

    /** 文本框是唯一事实源:用户编辑 → state(suspendListeners 断 state→field→state 递归);state → field 直写。 */
    private fun buildAmountField(state: AmountState): TextField {
        val amountField = TopoTextField().apply {
            setId("topo_amount_popup_text_field")
            layout {
                it.widthPercent(100f)
                it.height(MachineUiComponentStyle.popupFieldHeight)
                it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
                it.paddingRight(MachineUiComponentStyle.boxAllPadding)
            }
            textFieldStyle {
                it.textColor(MachineUiComponentStyle.textSelected)
                it.cursorColor(MachineUiComponentStyle.textSelected)
                it.textShadow(false)
                it.focusOverlay(IGuiTexture.EMPTY)
            }
            style { it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture()) }
            setValue(state.value.toString(), false)
        }
        amountField.registerValueListener { typed ->
            if (state.suspendListeners) return@registerValueListener
            state.syncFromField(typed)
        }
        // State → field directly: the binding's polling path is too laggy for click-to-bump UX.
        state.onChanged {
            if (state.suspendListeners) return@onChanged
            state.suspendListeners = true
            amountField.setValue(state.value.toString(), false)
            state.suspendListeners = false
        }
        return amountField
    }

    private fun buildButtonRow(state: AmountState, amountField: TextField, refs: MutableList<Pair<Button, Long>>): UIElement = MachineUiLayout.row(
        gap = 0f,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
        justifyContent = AlignContent.SPACE_BETWEEN,
        id = "topo_amount_popup_step_row",
    ) {
        add(
            stepGroup(
                "topo_amount_popup_minus_group",
                listOf(
                    Triple("topo_amount_popup_minus_100", "-100", -100L),
                    Triple("topo_amount_popup_minus_10", "-10", -10L),
                    Triple("topo_amount_popup_minus_1", "-1", -1L),
                ),
                state,
                amountField,
                refs,
            ),
        )
        add(
            stepGroup(
                "topo_amount_popup_plus_group",
                listOf(
                    Triple("topo_amount_popup_plus_1", "+1", +1L),
                    Triple("topo_amount_popup_plus_10", "+10", +10L),
                    Triple("topo_amount_popup_plus_100", "+100", +100L),
                ),
                state,
                amountField,
                refs,
            ),
        )
    }

    private fun stepGroup(groupId: String, buttons: List<Triple<String, String, Long>>, state: AmountState, amountField: TextField, refs: MutableList<Pair<Button, Long>>): UIElement = MachineUiLayout.row(
        gap = MachineUiComponentStyle.boxAllGap,
        alignItems = AlignItems.CENTER,
        id = groupId,
    ) {
        for ((id, labelText, baseStep) in buttons) {
            val button = stepButton(id, labelText, baseStep, state, amountField)
            refs.add(button to baseStep)
            add(button)
        }
    }

    /**
     * 步进钮:不用 [MachineUiComponentTemplate.createButton](SelectableButton / widthMaxContent),
     * 固定宽高、文本铺满并居中,点击时先把文本框内容同步进 state 再加减,再回写文本框——
     * 避免"只改了文本框未 blur / 已在 max 钳制"时看起来像没反应。
     */
    private fun stepButton(id: String, labelText: String, baseStep: Long, state: AmountState, amountField: TextField): Button = Button().apply {
        setId(id)
        setText(Component.literal(labelText))
        buttonStyle {
            it.baseTexture(MachineUiComponentStyle.tabButtonBaseTexture(false))
            it.hoverTexture(MachineUiComponentStyle.tabButtonHoverTexture(false))
            it.pressedTexture(MachineUiComponentStyle.tabButtonPressedTexture(false))
        }
        textStyle {
            it.textColor(MachineUiComponentStyle.textSelected)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        // Button 内置 text 子节点:铺满内容盒,字才能在 34×15 里真正居中。
        text.layout {
            it.widthPercent(100f)
            it.heightPercent(100f)
            it.marginHorizontal(0f)
            it.flexShrink(1f)
        }
        layout {
            it.width(MachineUiComponentStyle.popupStepButtonWidth)
            it.height(MachineUiComponentStyle.buttonHeight)
            it.paddingTop(0f)
            it.paddingBottom(0f)
            it.paddingLeft(2f)
            it.paddingRight(2f)
            it.flexShrink(0f)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
        }
        // 用 MOUSE_DOWN 显式监听(与 Button 内置 onClick 同相),并 stop 防冒泡到遮罩取消。
        addEventListener(UIEvents.MOUSE_DOWN) { event ->
            if (event.button != 0) return@addEventListener
            // 步进前以文本框为准,避免用户手输后点步进却基于旧 state。
            state.syncFromField(amountField.value)
            state.applyDelta(baseStep * modifierMultiplier())
            // 直写文本框:不单靠 listener,保证即时可见反馈。
            state.suspendListeners = true
            amountField.setValue(state.value.toString(), false)
            state.suspendListeners = false
            event.stopPropagation()
        }
    }

    private fun buildActionRow(onConfirm: () -> Unit, onCancel: () -> Unit): UIElement = MachineUiLayout.row(
        gap = 0f,
        widthPercent = 100f,
        alignItems = AlignItems.CENTER,
        justifyContent = AlignContent.SPACE_BETWEEN,
        id = "topo_amount_popup_action_row",
    ) {
        add(
            actionButton(
                "topo_amount_popup_cancel",
                TopoApiLang.UI_AMOUNT_POPUP_CANCEL.getComponent(),
                onCancel,
            ),
        )
        add(
            actionButton(
                "topo_amount_popup_confirm",
                TopoApiLang.UI_AMOUNT_POPUP_CONFIRM.getComponent(),
                onConfirm,
            ),
        )
    }

    private fun actionButton(id: String, label: Component, onClick: () -> Unit): Button = Button().apply {
        setId(id)
        setText(label)
        buttonStyle {
            it.baseTexture(MachineUiComponentStyle.tabButtonBaseTexture(false))
            it.hoverTexture(MachineUiComponentStyle.tabButtonHoverTexture(false))
            it.pressedTexture(MachineUiComponentStyle.tabButtonPressedTexture(false))
        }
        textStyle {
            it.textColor(MachineUiComponentStyle.textSelected)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        text.layout {
            it.widthPercent(100f)
            it.heightPercent(100f)
            it.marginHorizontal(0f)
            it.flexShrink(1f)
        }
        layout {
            it.width(MachineUiComponentStyle.popupActionButtonWidth)
            it.height(MachineUiComponentStyle.buttonHeight)
            it.paddingTop(0f)
            it.paddingBottom(0f)
            it.paddingLeft(4f)
            it.paddingRight(4f)
            it.flexShrink(0f)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
        }
        addEventListener(UIEvents.MOUSE_DOWN) { event ->
            if (event.button != 0) return@addEventListener
            onClick()
            event.stopPropagation()
        }
    }

    private fun refreshStepLabels(refs: List<Pair<Button, Long>>) {
        val multiplier = modifierMultiplier()
        for ((button, baseStep) in refs) {
            button.setText(Component.literal(stepLabel(baseStep, multiplier)))
        }
    }

    /**
     * 步进钮标签的公共格式化(弹窗与屏上步进钮同口径):带符号紧凑数,千/百万档在不足
     * 10 个单位时保留一位小数(640×10 = "+6.4K" 而非误导性的 "+6K")。
     */
    @JvmStatic
    fun stepLabel(baseStep: Long, multiplier: Long): String {
        val effective = baseStep * multiplier
        val abs = kotlin.math.abs(effective)
        val prefix = if (effective >= 0) "+" else "-"
        return prefix + when {
            abs >= 1_000_000L -> compactUnit(abs, 1_000_000L) + "M"
            abs >= 1000L -> compactUnit(abs, 1000L) + "K"
            else -> abs.toString()
        }
    }

    private fun compactUnit(abs: Long, unit: Long): String {
        val whole = abs / unit
        if (whole >= 10L || abs % unit == 0L) {
            return whole.toString()
        }
        val tenth = (abs % unit) * 10 / unit
        return if (tenth == 0L) whole.toString() else "$whole.$tenth"
    }

    private fun saturatingAdd(a: Long, b: Long): Long {
        val r = a + b
        return if (((a xor r) and (b xor r)) < 0L) (if (b > 0) Long.MAX_VALUE else Long.MIN_VALUE) else r
    }

    /**
     * Mutable per-popup state; lives only while the popup is mounted. Every write clamps into
     * the caller's [min max] domain. suspendListeners breaks the field ↔ state listener cycle.
     */
    private class AmountState(initial: Long, private val min: Long, private val max: Long) {
        @Volatile
        var value: Long = initial.coerceIn(min, max)
            set(newValue) {
                field = newValue.coerceIn(min, max)
            }

        @Volatile
        var suspendListeners: Boolean = false
        private val listeners = ArrayList<() -> Unit>()

        fun onChanged(listener: () -> Unit) {
            listeners += listener
        }

        fun notifyChanged() {
            for (listener in listeners) {
                listener()
            }
        }

        /** 把文本框内容解析进 value(非法/空则保持当前值),并钳到 [min,max]。 */
        fun syncFromField(typed: String?) {
            val parsed = typed?.toLongOrNull()
            if (parsed != null) {
                value = parsed
            }
        }

        /** 按 delta 加减并通知监听(字段回写由调用方/onChanged 负责)。 */
        fun applyDelta(delta: Long) {
            value = saturatingAdd(value, delta)
            notifyChanged()
        }
    }
}
