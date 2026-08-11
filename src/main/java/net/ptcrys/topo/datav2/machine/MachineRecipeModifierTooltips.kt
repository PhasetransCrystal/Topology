package net.ptcrys.topo.datav2.machine

import net.ptcrys.topo.apiv2.machine.MachineDefinition
import net.ptcrys.topo.apiv2.machine.Machines
import net.ptcrys.topo.apiv2.machine.component.RecipeModifierDisplay
import net.ptcrys.topo.apiv2.machine.ui.LcdData
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.apiv2.machine.ui.tooltip.ItemTooltipUis

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * 机器物品悬浮面板:展示声明期挂载的 [RecipeModifierDisplay](title | description)。
 * 纯注册期事实,不实例化 BE;无 Modifier 的机器不注册 provider。
 *
 * 布局对齐装备/管道:父行 `配方修正 | 数量`,其下每个 Modifier 一条缩进子项
 * `title | description`(方案 B)。
 */
object MachineRecipeModifierTooltips {

    /** 面板一行:sub 为真的行由 LCD 以键列缩进渲染。 */
    data class Row(val label: Component, val value: Component, val sub: Boolean)

    /** 由 [MachineRuntimeBindings] 在 FMLCommonSetup enqueueWork 中调用(物品已绑定)。 */
    @JvmStatic
    fun registerAll() {
        for (definition in Machines.registered()) {
            val displays = definition.metadata(RecipeModifierDisplay.TYPE)
            if (displays.isEmpty()) {
                continue
            }
            val item = definition.registeredBlock().get().asItem()
            ItemTooltipUis.register(item) { createPanel(definition) }
        }
    }

    fun createPanel(definition: MachineDefinition): UIElement {
        val lcd = MachineUiContainerTemplate.createTooltipLcdData(LcdData.Orientation.VERTICAL)
        lcd.setId("oi_machine_recipe_modifier_tooltip")
        for (row in panelRows(definition.metadata(RecipeModifierDisplay.TYPE))) {
            if (row.sub) {
                lcd.addStaticSubEntry(row.label, row.value, LcdData.LED_TEXT)
            } else {
                lcd.addStaticEntry(row.label, row.value, LcdData.LED_TEXT)
            }
        }
        return lcd
    }

    /**
     * 行内容纯函数:父行 `Recipe Modifiers | count`,每个 display 一条
     * `title | description` 子项,声明顺序。
     */
    @JvmStatic
    fun panelRows(displays: List<RecipeModifierDisplay>): List<Row> {
        if (displays.isEmpty()) {
            return emptyList()
        }
        val rows = mutableListOf(
            Row(
                BuiltinOIMachineUiLang.TOOLTIP_MACHINE_RECIPE_MODIFIERS.getComponent(),
                BuiltinOIMachineUiLang.TOOLTIP_MACHINE_MODIFIER_COUNT.getComponent(displays.size),
                false,
            ),
        )
        for (display in displays) {
            rows += Row(display.title(), display.description(), true)
        }
        return rows
    }
}
