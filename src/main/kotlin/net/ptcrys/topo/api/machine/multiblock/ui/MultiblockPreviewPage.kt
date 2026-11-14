package net.ptcrys.topo.api.machine.multiblock.ui

import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.MachineDefinition
import net.ptcrys.topo.api.machine.multiblock.MultiblockControllerMetadata
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiLayout

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen
import com.lowdragmc.lowdraglib2.gui.texture.Icons
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import dev.vfyjxf.taffy.style.AlignItems

/** 通用结构示例界面。调用方提供机器声明，不依赖 JEI；模块和重复数选择仅改变示例，不改写机器配置。 */
object MultiblockPreviewPage {
    @JvmStatic
    fun build(definition: MachineDefinition): ModularUI = modular(panel(definition, PreviewOptions(definition), false))

    private class PreviewOptions(definition: MachineDefinition) {
        val metadata = definition.metadata(MultiblockControllerMetadata.TYPE).first()
        val blueprints = listOf(metadata.blueprint()) + metadata.modules().map { it.blueprint() }
        val repeats = blueprints.map { BlueprintPreview.previewRepeats(it).countsCopy() }
        var scope = -1

        fun visible(): List<Int> {
            if (scope < 0) return blueprints.indices.toList()
            val selected = linkedSetOf(0)
            fun include(index: Int) {
                if (!selected.add(index)) return
                for (dependency in metadata.modules()[index - 1].dependencies()) {
                    include(metadata.modules().indexOfFirst { it.id() == dependency } + 1)
                }
            }
            if (scope > 0) include(scope)
            return selected.sorted()
        }

        fun scopeLabel(index: Int): Component = when (index) {
            -1 -> TopoApiLang.UI_MULTIBLOCK_PREVIEW_ALL_MODULES.getComponent()
            0 -> TopoApiLang.UI_MULTIBLOCK_PREVIEW_BODY.getComponent()
            else -> Component.literal("$index: ${metadata.modules()[index - 1].id()}")
        }
    }

    private fun panel(definition: MachineDefinition, options: PreviewOptions, expanded: Boolean): UIElement {
        val width = if (expanded) MachineUiComponentStyle.jeiMultiblockExpandedWidth else MachineUiComponentStyle.jeiMultiblockPanelWidth
        val height = if (expanded) MachineUiComponentStyle.jeiMultiblockExpandedHeight else MachineUiComponentStyle.jeiMultiblockPanelHeight
        val contentWidth = width - 2f * (MachineUiComponentStyle.boxTextureWidth + MachineUiComponentStyle.boxAllPadding)
        val root = MachineUiLayout.box(needPadding = true, needGap = true) {
            root.setId("topo_multiblock_preview")
            root.layout {
                it.width(width.toFloat())
                it.height(height.toFloat())
            }
        }
        fun refresh() {
            root.clearAllChildren()
            val visible = options.visible()
            val preview = BlueprintPreview.combine(
                visible.map { index ->
                    BlueprintPreview.of(options.blueprints[index], definition, RepeatResolution(options.repeats[index]))
                },
            )
            if (options.metadata.modules().isNotEmpty()) {
                root.addChild(
                    MachineUiComponentTemplate.createButton(options.scopeLabel(options.scope)).apply {
                        setOnClick {
                            options.scope = if (options.scope >= options.blueprints.lastIndex) -1 else options.scope + 1
                            refresh()
                        }
                    },
                )
            }
            val repeatSections = visible.flatMap { index ->
                val blueprint = options.blueprints[index]
                (0 until blueprint.sectionCount()).filter { blueprint.section(it).maxCount() > blueprint.section(it).minCount() }.map { index to it }
            }
            if (repeatSections.isNotEmpty()) {
                root.addChild(
                    MachineUiContainerTemplate.createScrollView(contentWidth - MachineUiComponentStyle.scrollBarWidth, minOf(42f, repeatSections.size * 20f)).apply {
                        for ((index, sectionIndex) in repeatSections) {
                            val section = options.blueprints[index].section(sectionIndex)
                            val count = options.repeats[index][sectionIndex]
                            addScrollViewChild(
                                MachineUiLayout.row(gap = 2f, widthPercent = 100f, alignItems = AlignItems.CENTER) {
                                    add(
                                        MachineUiComponentTemplate.createStaticText(TopoApiLang.UI_MULTIBLOCK_PREVIEW_REPEAT.getComponent(options.scopeLabel(index), sectionIndex + 1, count)).apply {
                                            layout { it.flexGrow(1f) }
                                        },
                                    )
                                    fun change(delta: Int) {
                                        options.repeats[index][sectionIndex] = (count + delta).coerceIn(section.minCount(), section.maxCount())
                                        refresh()
                                    }
                                    add(MachineUiComponentTemplate.createIconButton(Icons.REMOVE, TopoApiLang.UI_MULTIBLOCK_PREVIEW_REPEAT_LESS) { change(-1) })
                                    add(MachineUiComponentTemplate.createIconButton(Icons.ADD, TopoApiLang.UI_MULTIBLOCK_PREVIEW_REPEAT_MORE) { change(1) })
                                },
                            )
                        }
                    },
                )
            }
            val scene = MultiblockScenePreview.scene(preview.blocks())
            val blockList = MultiblockPreviewBlockList.build(
                preview,
                contentWidth - MachineUiComponentStyle.scrollBarWidth,
                if (expanded) MachineUiComponentStyle.previewBlockListMaxExpanded else MachineUiComponentStyle.previewBlockListMaxCompact,
            )
            val panelRef = arrayOfNulls<MultiblockScenePanel.Panel>(1)
            val builder = MultiblockScenePanel.of(scene, preview.blocks().keys)
                .zoomControls(expanded)
                .resetControl(true)
                .homeView(45f, 25f)
                .onSelect { cell -> panelRef[0]?.highlight(blockList.toggleSelect(cell)) }
            if (!expanded) {
                builder.control(Icons.RESIZE_BOTTOM_RIGHT, TopoApiLang.UI_MULTIBLOCK_PREVIEW_EXPAND) { openExpanded(definition, options) }
            }
            val panel = builder.build()
            panelRef[0] = panel
            root.addChild(MachineUiComponentTemplate.createStaticText(Component.literal(dimensions(preview.blocks().keys) + "  ").append(TopoApiLang.UI_MULTIBLOCK_PREVIEW_ROTATABLE.getComponent())))
            root.addChild(
                panel.sceneArea().apply {
                    layout {
                        it.flexGrow(1f)
                        it.flexShrink(1f)
                        it.minHeight(70f)
                    }
                },
            )
            root.addChild(blockList.element)
        }
        refresh()
        return root
    }

    private fun dimensions(positions: Collection<BlockPos>): String {
        if (positions.isEmpty()) return "0×0×0"
        return "${positions.maxOf { it.x } - positions.minOf { it.x } + 1}×${positions.maxOf { it.y } - positions.minOf { it.y } + 1}×${positions.maxOf { it.z } - positions.minOf { it.z } + 1}"
    }

    private fun modular(root: UIElement): ModularUI = ModularUI.of(UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)))

    private fun openExpanded(definition: MachineDefinition, source: PreviewOptions) {
        val minecraft = Minecraft.getInstance()
        val parent = minecraft.screen
        // 独立选项避免关闭大图后，紧凑界面的旧场景与新选项不一致。
        val options = PreviewOptions(definition).also { copy ->
            copy.scope = source.scope
            source.repeats.forEachIndexed { index, counts -> counts.copyInto(copy.repeats[index]) }
        }
        minecraft.setScreen(object : ModularUIScreen(modular(panel(definition, options, true)), definition.registeredBlock().get().name) {
            override fun onClose() {
                minecraft.setScreen(parent)
            }
        })
    }
}
