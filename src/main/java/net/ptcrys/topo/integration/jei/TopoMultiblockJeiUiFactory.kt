package net.ptcrys.topo.integration.jei

import net.ptcrys.topo.api.machine.MachineDefinition
import net.ptcrys.topo.api.machine.Machines
import net.ptcrys.topo.api.machine.multiblock.MultiblockControllerMetadata
import net.ptcrys.topo.api.machine.multiblock.ui.BlueprintPreview
import net.ptcrys.topo.api.machine.multiblock.ui.MultiblockPreviewBlockList
import net.ptcrys.topo.api.machine.multiblock.ui.MultiblockScenePanel
import net.ptcrys.topo.api.machine.multiblock.ui.MultiblockScenePreview
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.data.machine.BuiltinTopoMachineUiLang

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.texture.Icons
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager

/**
 * Builds the LDLIB2 tree rendered inside the JEI multiblock-structure category, ported from the old
 * GTOdyssey preview panel: an info bar (structure dimensions + rotatable note), the interactive 3D
 * scene with its floating toolbar (layer slicer, auto-rotation, expand-to-fullscreen; the expanded
 * view adds zoom and reset-view controls), and the aggregated bill-of-materials dock below — cells
 * with identical candidates merge into `N×` rows, every alternative part renders as a slot whose
 * tooltip lists the roles it can fulfil, and clicking a block in the scene highlights its row.
 *
 * The example world is derived from the blueprint by [BlueprintPreview]: pinned expected states
 * verbatim, count-required part capabilities substituted into their first eligible cells, otherwise
 * each predicate's first block candidate; air cells stay open so galleries read correctly.
 */
object TopoMultiblockJeiUiFactory {
    /** Stable aliases of the style table's JEI multiblock panel dimensions (single edit point). */
    @JvmField
    val PANEL_WIDTH: Int = MachineUiComponentStyle.jeiMultiblockPanelWidth

    @JvmField
    val PANEL_HEIGHT: Int = MachineUiComponentStyle.jeiMultiblockPanelHeight

    private const val ISO_YAW = 45f
    private const val ISO_PITCH = 25f
    private const val FRONT_YAW = 90f
    private const val FRONT_PITCH = 30f
    private const val EXPANDED_ZOOM = 12f

    @JvmStatic
    fun buildPreview(definition: MachineDefinition): ModularUI = ModularUI.of(
        UI.of(
            buildPanel(definition, false, PANEL_WIDTH, PANEL_HEIGHT),
            StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC),
        ),
    )

    /** Every machine definition that mounts a multiblock controller, in registration order. */
    @JvmStatic
    fun controllerDefinitions(): List<MachineDefinition> = Machines.registered().filter { it.metadata(MultiblockControllerMetadata.TYPE).isNotEmpty() }

    private fun buildPanel(definition: MachineDefinition, expanded: Boolean, width: Int, height: Int): UIElement {
        val blueprint = definition.metadata(MultiblockControllerMetadata.TYPE).first().blueprint()
        val preview = BlueprintPreview.of(blueprint, definition)
        val scene = MultiblockScenePreview.scene(preview.blocks())
        val blockListHeight = if (expanded) {
            MachineUiComponentStyle.previewBlockListMaxExpanded
        } else {
            MachineUiComponentStyle.previewBlockListMaxCompact
        }
        val contentWidth = width - 2f * (MachineUiComponentStyle.boxTextureWidth + MachineUiComponentStyle.boxAllPadding)
        val blockList = MultiblockPreviewBlockList.build(
            preview,
            contentWidth - MachineUiComponentStyle.scrollBarWidth,
            blockListHeight,
        )

        // The closure trick the structure page also uses: onSelect runs only after the panel is
        // built, so this back-reference is always populated by the time a user clicks.
        val panelRef = arrayOfNulls<MultiblockScenePanel.Panel>(1)
        val builder = MultiblockScenePanel.of(scene, preview.blocks().keys)
            .zoomControls(expanded)
            // Reset stays available in the compact panel too: scroll-zoom has no other way back.
            .resetControl(true)
            .headerInfo(infoBar(preview))
            .onSelect { cell -> panelRef[0]?.highlight(blockList.toggleSelect(cell)) }
        if (expanded) {
            builder.homeView(FRONT_YAW, FRONT_PITCH, EXPANDED_ZOOM)
        } else {
            builder.homeView(ISO_YAW, ISO_PITCH)
            builder.control(Icons.RESIZE_BOTTOM_RIGHT, "ui.topo.multiblock.preview.expand") {
                openExpanded(definition)
            }
        }
        val panel = builder.build()
        panelRef[0] = panel

        return MachineUiLayout.box(needPadding = true, needGap = true) {
            root.setId("topo_multiblock_jei_preview")
            root.layout {
                it.width(width.toFloat())
                it.height(height.toFloat())
            }
            // Box chrome only; vertical stacking uses Taffy COLUMN default (same as MachineUiLayout.column).
            add(
                panel.sceneArea().apply {
                    layout { it.flexGrow(1f) }
                },
            )
            add(blockList.element)
        }
    }

    private fun infoBar(preview: BlueprintPreview): UIElement = MachineUiComponentTemplate.createStaticText(
        Component.literal(dimensions(preview.blocks().keys) + "  ")
            .append(BuiltinTopoMachineUiLang.UI_MULTIBLOCK_PREVIEW_ROTATABLE.getComponent()),
    ).apply {
        setId("topo_multiblock_preview_info")
        layout { it.height(9f) }
    }

    private fun dimensions(positions: Collection<BlockPos>): String {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE
        for (pos in positions) {
            minX = minOf(minX, pos.x)
            minY = minOf(minY, pos.y)
            minZ = minOf(minZ, pos.z)
            maxX = maxOf(maxX, pos.x)
            maxY = maxOf(maxY, pos.y)
            maxZ = maxOf(maxZ, pos.z)
        }
        return "${maxX - minX + 1}x${maxY - minY + 1}x${maxZ - minZ + 1}"
    }

    private fun openExpanded(definition: MachineDefinition) {
        val parent: Screen? = Minecraft.getInstance().screen
        val panel = buildPanel(
            definition,
            true,
            MachineUiComponentStyle.jeiMultiblockExpandedWidth,
            MachineUiComponentStyle.jeiMultiblockExpandedHeight,
        )
        val ui = ModularUI.of(UI.of(panel, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)))
        val title = definition.registeredBlock().get().name
        Minecraft.getInstance().setScreen(object : ModularUIScreen(ui, title) {
            override fun onClose() {
                Minecraft.getInstance().setScreen(parent)
            }
        })
    }
}
