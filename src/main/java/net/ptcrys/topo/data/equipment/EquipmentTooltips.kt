package net.ptcrys.topo.data.equipment

import net.ptcrys.topo.api.equipment.EquipmentRegistry
import net.ptcrys.topo.api.machine.ui.LcdData
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis
import net.ptcrys.topo.api.machine.ui.tooltip.TopoTooltipUiProvider
import net.ptcrys.topo.data.equipment.common.StandardEquipmentBase
import net.ptcrys.topo.data.equipment.common.SurveyorEquipment
import net.ptcrys.topo.data.equipment.common.SurveyorRanges

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * 装备物品的悬浮面板:耐久(剩余/上限,随损耗实时失效缓存)、功能区两级展示——父行
 * `Function | 声明行数`,其下每个声明 [EquipmentTooltipLine] 一条缩进子项 `name | desc`。
 * 攻击/防御等 vanilla 已展示的属性不重复;物质量不展示 —— 用户裁决:worth 只属于 Form
 * 物品的化学面板([net.ptcrys.topo.data.material.MaterialFormTooltips])。
 *
 * 缓存键 = 损伤值:[ItemTooltipUis] 的缓存键本就是 (item, provider.cacheKey),耐久变化自动重建。
 */
object EquipmentTooltips {

    /** 面板一行:sub 为真的行由 LCD 以键列缩进渲染(子项),否则是顶级条目。 */
    data class Row(val label: Component, val value: Component, val sub: Boolean)

    /** 由 [EquipmentRuntimeBindings] 在 FMLCommonSetup enqueueWork 中调用(物品已绑定)。 */
    @JvmStatic
    fun registerAll() {
        for (record in EquipmentRegistry.itemRecords()) {
            val strategy = record.equipment().strategy() as? StandardEquipmentBase ?: continue
            ItemTooltipUis.register(
                record.entry().get(),
                object : TopoTooltipUiProvider {
                    override fun build(stack: ItemStack): UIElement = createPanel(stack, strategy)

                    override fun cacheKey(stack: ItemStack): Any = stack.damageValue
                },
            )
        }
    }

    fun createPanel(stack: ItemStack, strategy: StandardEquipmentBase): UIElement {
        val lcd = MachineUiContainerTemplate.createTooltipLcdData(LcdData.Orientation.VERTICAL)
        lcd.setId("topo_equipment_tooltip")
        // 勘测仪:观测范围随材质档(铁 16/青铜 32),从台账反查物品的 SURVEY_STATS;其余装备无范围行。
        val surveyRange = if (strategy is SurveyorEquipment) SurveyorRanges.rangeOf(stack.item) else null
        for (row in panelRows(
            stack.isDamageableItem,
            stack.maxDamage - stack.damageValue,
            stack.maxDamage,
            strategy.tooltipLines(),
            surveyRange,
        )) {
            if (row.sub) {
                lcd.addStaticSubEntry(row.label, row.value, LcdData.LED_TEXT)
            } else {
                lcd.addStaticEntry(row.label, row.value, LcdData.LED_TEXT)
            }
        }
        return lcd
    }

    /**
     * 行内容纯函数(测试 274/291):耐久(可损耗才有)+ 功能区(有声明才有)——父行
     * `Function | 数量`,每个 [EquipmentTooltipLine] 子项 `name | desc`,声明顺序。
     */
    @JvmStatic
    @JvmOverloads
    fun panelRows(damageable: Boolean, remainingDurability: Int, maxDurability: Int, tooltipLines: List<EquipmentTooltipLine>, surveyRange: Int? = null): List<Row> {
        val rows = mutableListOf<Row>()
        if (damageable) {
            rows += Row(
                BuiltinTopoEquipmentLang.TOOLTIP_EQUIPMENT_DURABILITY.getComponent(),
                Component.literal("$remainingDurability / $maxDurability"),
                false,
            )
        }
        if (surveyRange != null) {
            rows += Row(
                BuiltinTopoEquipmentLang.TOOLTIP_EQUIPMENT_RANGE.getComponent(),
                BuiltinTopoEquipmentLang.TOOLTIP_EQUIPMENT_RANGE_BLOCKS.getComponent(surveyRange),
                false,
            )
        }
        if (tooltipLines.isNotEmpty()) {
            rows += Row(
                BuiltinTopoEquipmentLang.TOOLTIP_EQUIPMENT_FUNCTION.getComponent(),
                Component.literal(tooltipLines.size.toString()),
                false,
            )
            for (line in tooltipLines) {
                rows += Row(line.name(), line.description(), true)
            }
        }
        return rows
    }
}
