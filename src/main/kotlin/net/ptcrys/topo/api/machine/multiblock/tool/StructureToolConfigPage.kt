package net.ptcrys.topo.api.machine.multiblock.tool

import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.api.machine.ui.TopoTextField

import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import dev.vfyjxf.taffy.style.AlignItems

/** 按选项注册表生成的工具配置页。提交由服务端验证，绑定原手持栈，切换或替换工具后原菜单不能修改另一件工具。 */
object StructureToolConfigPage {
    @JvmStatic
    fun build(holder: HeldItemUIMenuType.HeldItemUIHolder): ModularUI {
        val root = MachineUiLayout.modularContentRoot(280f, 6f, "structure_tool_config") {
            root.layout { it.paddingAll(8f) }
            root.style { it.backgroundTexture(MachineUiComponentStyle.lcdFrameTexture()) }
            add(Label().setText(TopoApiLang.TOOL_STRUCTURE_TITLE.getComponent()))
            add(wrappedLabel(TopoApiLang.TOOL_STRUCTURE_CONFIG_HINT.getComponent()))
            val scroll = ScrollerView().apply {
                layout {
                    it.widthPercent(100f)
                    it.height(190f)
                }
            }
            for (option in StructureToolOptions.all()) scroll.addScrollViewChild(optionRow(holder, option))
            add(scroll)
        }
        return ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)), holder.player)
    }

    private fun wrappedLabel(text: Component): Label = Label().apply {
        setText(text)
        layout { it.widthPercent(100f) }
        textStyle {
            it.textWrap(TextWrap.WRAP)
            it.adaptiveWidth(false)
            it.adaptiveHeight(true)
        }
    }

    private fun optionRow(holder: HeldItemUIMenuType.HeldItemUIHolder, option: StructureToolOptions.Option): UIElement {
        val field = TopoTextField().apply {
            setAnyString()
            setValue(StructureToolOptions.get(holder.itemStack, option), false)
            layout {
                it.widthPercent(100f)
                it.height(18f)
            }
        }
        val submit = RPCEventBuilder.simple(String::class.java, String::class.java) { requested ->
            if (holder.player is ServerPlayer && holder.heldItemUI.stillValid(holder)) {
                try {
                    StructureToolOptions.set(holder.itemStack, option, requested ?: option.defaultValue())
                    holder.player.inventory.setChanged()
                    holder.player.inventoryMenu.broadcastChanges()
                } catch (_: IllegalArgumentException) {
                    holder.player.sendSystemMessage(TopoApiLang.TOOL_STRUCTURE_INVALID_VALUE.getComponent(option.label()))
                }
            }
            StructureToolOptions.get(holder.itemStack, option)
        }
        field.addRPCEvent(submit)
        fun apply(value: String) {
            field.sendEvent<String>(submit, { confirmed -> field.setValue(confirmed, false) }, value)
        }
        return MachineUiLayout.column(gap = 3f, alignItems = AlignItems.STRETCH, id = "structure_tool_option_${option.id()}") {
            root.layout {
                it.widthPercent(100f)
                it.paddingBottom(8f)
            }
            add(wrappedLabel(option.label()))
            if (option.choices().isEmpty()) {
                add(field)
                add(
                    Button().apply {
                        setText(TopoApiLang.TOOL_STRUCTURE_APPLY.getComponent())
                        setOnClick { apply(field.value) }
                        layout { it.height(18f) }
                    },
                )
            } else {
                field.setDisplay(false)
                add(field)
                val choice = Button().apply {
                    setText(option.displayValue(field.value))
                    layout { it.height(18f) }
                }
                choice.setOnClick {
                    val choices = option.choices()
                    val next = choices[(choices.indexOf(field.value) + 1) % choices.size]
                    field.sendEvent<String>(submit, { confirmed ->
                        field.setValue(confirmed, false)
                        choice.setText(option.displayValue(confirmed))
                    }, next)
                }
                add(choice)
            }
        }
    }
}
