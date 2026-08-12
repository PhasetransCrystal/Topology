package net.ptcrys.topo.data.machine.common.component

import net.ptcrys.topo.api.machine.component.ComponentContext
import net.ptcrys.topo.api.machine.component.ComponentKey
import net.ptcrys.topo.api.machine.component.ComponentMount
import net.ptcrys.topo.api.machine.component.MachineComponent
import net.ptcrys.topo.api.machine.component.MachineComponents
import net.ptcrys.topo.api.machine.resource.ResourcePort
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContribution
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.api.machine.ui.recipe.LiveRecipeSlots
import net.ptcrys.topo.api.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * Generic "Storage" page: each configured resource port as a 9-column slot grid section.
 * Slot widgets dispatch through [LiveRecipeSlots]; `HIDDEN` ports are skipped.
 */
class StoragePageUi private constructor(context: ComponentContext<StoragePageUi>, private val portKeys: List<ComponentKey<out ResourcePort<*, *>>>) : MachineComponent(context) {

    private var ports: List<ResourcePort<*, *>> = emptyList()

    override fun resolveDependencies(traits: MachineComponents) {
        ports = portKeys.map { key ->
            @Suppress("UNCHECKED_CAST")
            traits.require(key as ComponentKey<ResourcePort<*, *>>)
        }
    }

    override fun collectMachineUi(contribution: MachineUiContribution) {
        contribution.mainPage(
            PAGE_KEY,
            BuiltinTopoMachineUiLang.UI_STORAGE_PAGE.getComponent(),
            createStoragePage(),
        )
    }

    private fun createStoragePage(): UIElement = MachineUiLayout.pageColumn(RecipeUiLayout.PLAYER_INVENTORY_WIDTH.toFloat()) {
        root.setId("topo_storage_page")
        for (port in ports) {
            if (!port.playerSlotAccess().isVisible) {
                continue
            }
            add(
                MachineUiContainerTemplate.createSlotGrid(port.resourceIndexCount()) { slot ->
                    LiveRecipeSlots.createStorageSlot(port, slot)
                },
            )
        }
    }

    companion object {
        @JvmField
        val STORAGE_UI: ComponentKey<StoragePageUi> =
            ComponentKey.id("storage_ui", StoragePageUi::class.java)

        const val PAGE_KEY: String = "storage"

        @JvmStatic
        fun mount(vararg portKeys: ComponentKey<out ResourcePort<*, *>>): ComponentMount<StoragePageUi> {
            require(portKeys.isNotEmpty()) { "storage page needs at least one port key" }
            val keys = portKeys.toList()
            return STORAGE_UI.mount { context -> StoragePageUi(context, keys) }
        }
    }
}
