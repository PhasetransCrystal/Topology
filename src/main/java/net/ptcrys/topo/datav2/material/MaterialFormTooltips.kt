package net.ptcrys.topo.datav2.material

import net.ptcrys.topo.apiv2.machine.ui.LcdData
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.apiv2.machine.ui.tooltip.ItemTooltipUis
import net.ptcrys.topo.apiv2.material.Material
import net.ptcrys.topo.apiv2.material.MaterialRegistry
import net.ptcrys.topo.apiv2.material.form.MaterialForm
import net.ptcrys.topo.datav2.material.common.form.FormAmountDataType
import net.ptcrys.topo.helper.MaterialHelper

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * Form 物品的悬浮化学面板:材料([Material.displayName])、形态([MaterialForm.displayName])、
 * 物质的量(仅 Form 声明 AMOUNT 时显示;整锭显示 ingot/ingots、非整锭显示 units)、
 * 质量(AMOUNT × 材料 MASS;任一数据缺席时整行缺席)。
 * 覆盖全部 材料×Form 物品,含 vanilla 覆写件(原版铁锭等)。
 *
 * 面板纯注册期事实,逐物品恒定 —— 用 [net.ptcrys.topo.apiv2.machine.ui.tooltip.OiTooltipUiProvider]
 * 默认缓存键(item 实例)。
 */
object MaterialFormTooltips {

    /** 由 [MaterialRuntimeBindings] 在 FMLCommonSetup enqueueWork 中调用(物品已绑定)。 */
    @JvmStatic
    fun registerAll() {
        for (material in MaterialRegistry.registered()) {
            for (form in material.strategy().forms()) {
                val item = MaterialHelper.item(material, form).orElse(null) ?: continue
                ItemTooltipUis.register(item) { createPanel(material, form) }
            }
        }
    }

    fun createPanel(material: Material, form: MaterialForm): UIElement {
        val lcd = MachineUiContainerTemplate.createTooltipLcdData(LcdData.Orientation.VERTICAL)
        lcd.setId("oi_material_form_tooltip")
        val massPerUnit = material.strategy()
            .data(BuiltinOIMaterialDataTypes.MASS)
            .map { it.value() }
            .orElse(null)
        val amountUnits = form.strategy()
            .data(BuiltinOIFormDataTypes.AMOUNT)
            .orElse(null)
        for ((label, value) in panelRows(material.displayName(), form.displayName(), amountUnits, massPerUnit)) {
            lcd.addStaticEntry(label, value, LcdData.LED_TEXT)
        }
        return lcd
    }

    /**
     * 行内容纯函数(测试 273):amount 或 mass 缺席时对应行整行缺席。
     * 材料/形态显示名由调用方从 [Material.displayName] / [MaterialForm.displayName] 传入,
     * 本函数不做键拼接。
     */
    @JvmStatic
    fun panelRows(materialName: Component, formName: Component, amountUnits: Int?, massPerUnit: Int?): List<Pair<Component, Component>> {
        val rows: MutableList<Pair<Component, Component>> = mutableListOf(
            BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_MATERIAL.getComponent() to materialName,
            BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_FORM.getComponent() to formName,
        )
        if (amountUnits != null) {
            rows += BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_AMOUNT.getComponent() to amountText(amountUnits)
        }
        if (amountUnits != null && massPerUnit != null) {
            rows += BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_MASS.getComponent() to
                Component.literal(Math.multiplyExact(amountUnits, massPerUnit).toString())
        }
        return rows
    }

    /** 整锭单数 ingot、复数 ingots,非整锭按原始份数 units(72 份 = 1 锭)。 */
    private fun amountText(units: Int): Component = when {
        units % FormAmountDataType.UNITS_PER_INGOT != 0 ->
            BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_AMOUNT_UNITS.getComponent(units)

        units == FormAmountDataType.UNITS_PER_INGOT ->
            BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_AMOUNT_INGOT.getComponent(1)

        else ->
            BuiltinOIMaterialFormLang.TOOLTIP_MATERIAL_AMOUNT_INGOTS.getComponent(
                units / FormAmountDataType.UNITS_PER_INGOT,
            )
    }
}
