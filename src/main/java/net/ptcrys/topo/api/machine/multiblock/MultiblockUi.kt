package net.ptcrys.topo.api.machine.multiblock

import net.ptcrys.topo.api.machine.component.ComponentContext
import net.ptcrys.topo.api.machine.component.ComponentKey
import net.ptcrys.topo.api.machine.component.ComponentMount
import net.ptcrys.topo.api.machine.component.MachineComponent
import net.ptcrys.topo.api.machine.component.MachineComponents
import net.ptcrys.topo.api.machine.multiblock.ui.MultiblockStructurePage
import net.ptcrys.topo.api.machine.ui.MachineUiContribution

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * Multiblock structure UI trait: Structure main page (interactive 3D scene) plus a right-side
 * diagnostics panel. Labels are translation keys (see `BuiltinTopoMachineUiLang`).
 */
class MultiblockUi private constructor(context: ComponentContext<MultiblockUi>, private val controllerKey: ComponentKey<MultiblockController>) : MachineComponent(context) {
    private val pageKey: String = context.id().path
    private val diagnosticsPanelKey: String = pageKey + "_diagnostics"
    private lateinit var controller: MultiblockController

    /**
     * Selection channel shared by scene, reason rows, and detail dock for one UI build.
     * Minted at the start of [collectMachineUi] so page and panel share a fresh channel.
     */
    private var selection = MultiblockStructurePage.Selection()

    override fun resolveDependencies(traits: MachineComponents) {
        controller = traits.require(controllerKey)
    }

    override fun collectMachineUi(contribution: MachineUiContribution) {
        selection = MultiblockStructurePage.Selection()
        contribution.mainPage(
            pageKey,
            Component.translatable("ui.topo.multiblock.structure"),
            createStructurePage(),
        )
        contribution.rightPanel(
            diagnosticsPanelKey,
            Component.translatable("ui.topo.multiblock.structure"),
            pageKey = pageKey,
            element = MultiblockStructurePage.diagnosticsPanel(machine(), controller, blueprint(), selection),
        )
    }

    private fun createStructurePage(): UIElement = MultiblockStructurePage.page(machine(), controller, blueprint(), selection)

    private fun blueprint() = machine().definition().metadata(MultiblockControllerMetadata.TYPE).first().blueprint()

    companion object {
        @JvmField
        val MULTIBLOCK_UI: ComponentKey<MultiblockUi> =
            ComponentKey.id("multiblock_ui", MultiblockUi::class.java)

        @JvmStatic
        fun mount(): ComponentMount<MultiblockUi> = mount(MULTIBLOCK_UI, MultiblockController.CONTROLLER)

        @JvmStatic
        fun mount(key: ComponentKey<MultiblockUi>, controllerKey: ComponentKey<MultiblockController>): ComponentMount<MultiblockUi> = key.mount { context -> MultiblockUi(context, controllerKey) }
    }
}
