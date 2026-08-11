package net.ptcrys.topo.apiv2.machine.ui

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems

import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

/**
 * 通用模态资源选择弹窗(设计系统组件,与 [AmountEditorPopup] 同级,共用 [MachineUiContainerTemplate]
 * 的模态壳):一行 9 个暂存虚拟槽 + 玩家背包只读网格 + 取消/确认。两条入物路径——点击背包物品;
 * 从 JEI 把物品拖入某个暂存槽(经 [ItemGhostDrop] 插座,无 JEI 时该路径静默缺席)。
 * **暂存后确认**:确认才把暂存的条目一次性回调。
 *
 * **以"过滤条目字符串"为单位**(而非具体物品),从而物品管/流体管统一:调用方给三个回调——
 * [entryFor] 把背包/拖入的物品译成条目(物品管=注册名;流体管=经物品流体能力抽出的流体名),
 * [previewIcon] 把条目渲染成暂存槽图标(物品图标或流体图标), [onConfirm] 收下暂存的条目。
 * 暂存槽据此显示流体管的流体、物品管的物品,弹窗自身不必认识资源类型。
 */
object ItemPickerPopup {
    private const val COLUMNS = 9
    private const val SLOT = 18f
    private const val ICON = 16f
    private const val PLUS_ICON = 8f

    /**
     * @param host    任意已挂载的 UI 元素(取其 modularUI 根挂遮罩)
     * @param title   弹窗标题(调用方按语境给"添加到白名单/黑名单"等)
     * @param entryFor 物品 → 条目字符串(null = 不接纳;决定背包点击是否生效、JEI 槽是否高亮可放)
     * @param previewIcon 条目 → 暂存槽图标(物品/流体贴图)
     * @param onConfirm 确认回调:暂存的条目(已去重),取消则不触发
     * @param initialEntries 预填暂存(默认空;探针/复用方可用)
     */
    @JvmStatic
    @JvmOverloads
    fun open(host: UIElement, title: Component, entryFor: Function<ItemStack, String?>, previewIcon: Function<String, IGuiTexture>, onConfirm: Consumer<List<String>>, initialEntries: List<String> = emptyList()) {
        val player = Minecraft.getInstance().player ?: return

        val staging = arrayOfNulls<String>(COLUMNS)
        initialEntries.asSequence().filter { it.isNotEmpty() }.take(COLUMNS).forEachIndexed { i, e ->
            staging[i] = e
        }
        val cells = ArrayList<StagingCell>(COLUMNS)

        MachineUiContainerTemplate.openModalPopup(
            host,
            "oi_item_picker",
            MachineUiComponentStyle.itemPickerPanelWidth,
            title,
        ) { close ->
            fun setStaging(index: Int, entry: String?) {
                staging[index] = entry?.takeIf { it.isNotEmpty() }
                cells[index].refresh(staging[index])
            }

            fun alreadyStaged(entry: String): Boolean = staging.any { it == entry }

            fun stageItem(item: ItemStack) {
                if (item.isEmpty) return
                val entry = entryFor.apply(item) ?: return
                if (alreadyStaged(entry)) return
                val free = staging.indexOfFirst { it == null }
                if (free >= 0) {
                    setStaging(free, entry)
                }
            }

            fun stageInto(index: Int, item: ItemStack) {
                if (item.isEmpty) return
                val entry = entryFor.apply(item) ?: return
                if (!alreadyStaged(entry)) {
                    setStaging(index, entry)
                }
            }

            val confirm: () -> Unit = {
                onConfirm.accept(staging.filterNotNull())
                close()
            }

            // --- staging tray (9 ghost slots; click clears, JEI drop fills this exact slot) ----------
            val stagingRow = slotRow("oi_item_picker_staging_row")
            for (index in 0 until COLUMNS) {
                val cell = StagingCell(index, previewIcon) { setStaging(index, null) }
                cells.add(cell)
                ItemGhostDrop.register(
                    cell.root,
                    Predicate { entryFor.apply(it) != null },
                    Consumer { dropped -> stageInto(index, dropped) },
                )
                stagingRow.addChild(cell.root)
            }
            cells.forEachIndexed { index, cell -> cell.refresh(staging[index]) }

            // --- player inventory (read-only; click an item to stage it) ----------------------------
            val inventory = player.inventory
            val invGrid = MachineUiLayout.column(gap = 0f, id = "oi_item_picker_inventory") {
                for (rowIndex in 0 until 3) {
                    val row = slotRow("oi_item_picker_inv_row_$rowIndex")
                    for (column in 0 until COLUMNS) {
                        val slotIndex = COLUMNS + rowIndex * COLUMNS + column
                        row.addChild(inventoryCell(slotIndex, inventory.getItem(slotIndex), ::stageItem))
                    }
                    add(row)
                }
                val hotbar = slotRow("oi_item_picker_hotbar").apply {
                    layout { it.marginTop(MachineUiComponentStyle.boxAllGap) }
                }
                for (column in 0 until COLUMNS) {
                    hotbar.addChild(inventoryCell(column, inventory.getItem(column), ::stageItem))
                }
                add(hotbar)
            }

            // --- action row -------------------------------------------------------------------------
            val actionRow = MachineUiLayout.row(
                gap = 0f,
                widthPercent = 100f,
                alignItems = AlignItems.CENTER,
                justifyContent = AlignContent.SPACE_BETWEEN,
                id = "oi_item_picker_action_row",
            ) {
                add(
                    actionButton(
                        "oi_item_picker_cancel",
                        Component.translatable("ui.topo.item_picker.cancel"),
                        close,
                    ),
                )
                add(
                    actionButton(
                        "oi_item_picker_confirm",
                        Component.translatable("ui.topo.item_picker.confirm"),
                        confirm,
                    ),
                )
            }

            MachineUiContainerTemplate.ModalPopupContent(
                body = listOf(
                    hintLabel(Component.translatable("ui.topo.item_picker.hint")),
                    stagingRow,
                    divider(),
                    invGrid,
                    actionRow,
                ),
                onCancel = close,
                onConfirm = confirm,
                // 暂存流程不因误点关闭:点面板外只吞掉点击(壳 dismissOnClickOutside=false),不触发取消。
                // 焦点落遮罩本身(focusTarget=null):无文本框可聚焦,靠壳的焦点陷阱保 KEY_DOWN 路由。
                dismissOnClickOutside = false,
            )
        }
    }

    // --- element helpers --------------------------------------------------------------------------

    private fun slotRow(id: String): UIElement = MachineUiLayout.row(gap = 0f, alignItems = AlignItems.CENTER, id = id) {}

    private fun slotCell(id: String, background: IGuiTexture): UIElement = UIElement().apply {
        setId(id)
        layout {
            it.width(SLOT)
            it.height(SLOT)
            it.flexShrink(0f)
            it.justifyContent(AlignContent.CENTER)
            it.alignItems(AlignItems.CENTER)
        }
        style { it.backgroundTexture(background) }
    }

    private fun inventoryCell(index: Int, stack: ItemStack, onPick: (ItemStack) -> Unit): UIElement {
        val cell = slotCell("oi_item_picker_inv_$index", MachineUiComponentStyle.realSlotTexture())
        if (!stack.isEmpty) {
            cell.addChild(iconElement("oi_item_picker_inv_icon_$index", ItemStackTexture(stack.copy()), ICON))
            cell.style { it.tooltips(stack.hoverName) }
            cell.addEventListener(UIEvents.MOUSE_DOWN, { event ->
                if (event.button == 0) {
                    onPick(stack)
                    event.stopPropagation()
                }
            })
        }
        return cell
    }

    private fun iconElement(id: String, texture: IGuiTexture, size: Float): UIElement = UIElement().apply {
        setId(id)
        isAllowHitTest = false
        layout {
            it.width(size)
            it.height(size)
            it.flexShrink(0f)
        }
        style { it.backgroundTexture(texture) }
    }

    private fun hintLabel(text: Component): Label = Label().apply {
        setId("oi_item_picker_hint")
        setText(text)
        // 正文按面板最大宽度换行撑高,不再单行撑宽溢出(同 MachineUiComponentTemplate.MAX_WIDTH_AUTO_HEIGHT)。
        textStyle {
            it.textColor(MachineUiComponentStyle.textMuted)
            it.textShadow(false)
            it.adaptiveWidth(false)
            it.adaptiveHeight(true)
            it.textWrap(TextWrap.WRAP)
        }
        layout {
            it.widthPercent(100f)
            it.flexShrink(0f)
        }
    }

    private fun divider(): UIElement = MachineUiLayout.horizontalDivider(height = 1f, id = "oi_item_picker_divider")

    private fun actionButton(id: String, label: Component, onClick: () -> Unit): Button = MachineUiComponentTemplate.createButton(label).apply {
        setId(id)
        layout { it.width(MachineUiComponentStyle.popupActionButtonWidth) }
        setOnClick { event ->
            if (event.button == 0) {
                onClick()
                event.stopPropagation()
            }
        }
    }

    /** 一个暂存格:满槽渲染条目图标([previewIcon]),空槽渲染居中的 + 图标;点击清除本格。 */
    private class StagingCell(index: Int, private val previewIcon: Function<String, IGuiTexture>, onClear: () -> Unit) {
        private val icon = UIElement().apply {
            setId("oi_item_picker_staging_icon_$index")
            isAllowHitTest = false
            layout {
                it.width(ICON)
                it.height(ICON)
                it.flexShrink(0f)
            }
        }

        // 居中绘制的 + 图标(代码绘制,几何居中,替换偏心的文字字形)。
        private val plus = UIElement().apply {
            setId("oi_item_picker_staging_plus_$index")
            isAllowHitTest = false
            layout {
                it.width(PLUS_ICON)
                it.height(PLUS_ICON)
                it.flexShrink(0f)
            }
            style { it.backgroundTexture(MachineUiIcons.plus()) }
        }

        // 暂存槽用深色(输入控件同款)底,与下方原版浅色背包槽明确分区,标记"投放/暂存区"。
        val root: UIElement = UIElement().apply {
            setId("oi_item_picker_staging_$index")
            layout {
                it.width(SLOT)
                it.height(SLOT)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            style {
                it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.tooltips(Component.translatable("ui.topo.item_picker.staged_tooltip"))
            }
            addChild(icon)
            addChild(plus)
            addEventListener(UIEvents.MOUSE_DOWN, { event ->
                if (event.button == 0) {
                    onClear()
                    event.stopPropagation()
                }
            })
        }

        fun refresh(entry: String?) {
            // setDisplay 切换(而非可见性):仅一个进布局流,由格子居中——避免图标与 + 并排。
            if (entry == null) {
                icon.setDisplay(false)
                plus.setDisplay(true)
            } else {
                icon.style { it.backgroundTexture(previewIcon.apply(entry)) }
                icon.setDisplay(true)
                plus.setDisplay(false)
            }
        }
    }
}
