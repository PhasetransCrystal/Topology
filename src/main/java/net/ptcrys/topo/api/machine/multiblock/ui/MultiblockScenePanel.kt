package net.ptcrys.topo.api.machine.multiblock.ui

import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiLayout

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.state.BlockState

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.Icons
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.TaffyPosition
import org.joml.Vector3f

import java.util.ArrayList
import java.util.function.Consumer

/**
 * Shared interactive scene panel for the JEI multiblock preview and the controller structure page,
 * ported from the old GTOdyssey panel: an [InspectorScene] with a floating toolbar — layer slicer
 * (ALL &rarr; L1 &rarr; &hellip; &rarr; LN), zoom in/out, auto-rotation toggle, reset view, plus
 * caller-supplied extra controls — and an optional header info element pinned to the top-left.
 */
object MultiblockScenePanel {
    private const val ROTATE_SPEED = 0.5f
    private const val ZOOM_MIN = 2.0f
    private const val ZOOM_MAX = 40.0f
    private const val ZOOM_STEP = 1.5f
    private const val LAYER_ALL = Int.MIN_VALUE
    private const val DEFAULT_YAW = 35f
    private const val DEFAULT_PITCH = 28f

    @JvmStatic
    fun of(scene: Scene, positions: Collection<BlockPos>): Builder = Builder(scene, positions)

    class Builder internal constructor(internal val scene: Scene, positions: Collection<BlockPos>) {
        internal val positions = ArrayList(positions)
        internal var homeYaw = DEFAULT_YAW
        internal var homePitch = DEFAULT_PITCH
        internal var homeZoom: Float? = null
        internal var zoomControls = true
        internal var resetControl = true
        internal val leadingControls = ArrayList<UIElement>()
        internal val extraControls = ArrayList<Control>()
        internal var headerInfo: UIElement? = null
        internal var onSelect: Consumer<BlockPos>? = null

        fun homeView(yaw: Float, pitch: Float): Builder = apply {
            homeYaw = yaw
            homePitch = pitch
        }

        fun homeView(yaw: Float, pitch: Float, zoom: Float): Builder = apply {
            homeView(yaw, pitch)
            homeZoom = zoom
        }

        fun zoomControls(show: Boolean): Builder = apply { zoomControls = show }

        fun resetControl(show: Boolean): Builder = apply { resetControl = show }

        /** Control pinned before the layer slicer (e.g. the required/as-built mode toggle). */
        fun leadingControl(control: UIElement): Builder = apply { leadingControls.add(control) }

        /** Label / element pinned to the toolbar's left side (dimensions + rotation info). */
        fun headerInfo(header: UIElement): Builder = apply { headerInfo = header }

        fun control(icon: IGuiTexture, tooltipKey: String, action: Runnable): Builder = apply {
            extraControls.add(Control(icon, tooltipKey, action))
        }

        fun onSelect(onSelect: Consumer<BlockPos>): Builder = apply { this.onSelect = onSelect }

        fun build(): Panel = assemble(this)
    }

    internal data class Control(val icon: IGuiTexture, val tooltipKey: String, val action: Runnable)

    class Panel internal constructor(private val sceneArea: UIElement, private val scene: Scene, private val resetView: Runnable) {
        fun sceneArea(): UIElement = sceneArea

        fun scene(): Scene = scene

        fun highlight(pos: BlockPos?): Panel = apply {
            (scene as? InspectorScene)?.selectBlock(pos)
        }

        fun swapBlocks(blocks: Map<BlockPos, BlockState>, clear: Collection<BlockPos>, reshape: Boolean): Panel = apply {
            (scene as? InspectorScene)?.setBlocks(blocks, clear, reshape)
        }

        fun resetView() {
            resetView.run()
        }
    }

    private fun assemble(builder: Builder): Panel {
        val scene = builder.scene.apply {
            setId("topo_multiblock_scene_view")
            layout {
                it.widthPercent(100f)
                it.heightPercent(100f)
            }
        }
        val homeCenter = Vector3f(scene.center)
        val homeZoom = builder.homeZoom ?: scene.zoom
        applyView(scene, homeCenter, builder.homeYaw, builder.homePitch, homeZoom)

        val spinning = booleanArrayOf(false)
        scene.setBeforeWorldRender {
            if (spinning[0] && !it.isDragging) {
                it.setCameraYawAndPitch((it.rotationYaw + ROTATE_SPEED) % 360f, it.rotationPitch)
            }
        }

        val resetView = Runnable {
            spinning[0] = false
            applyView(scene, homeCenter, builder.homeYaw, builder.homePitch, homeZoom)
        }

        builder.onSelect?.let { onSelect ->
            scene.setOnSelected { pos, _ -> onSelect.accept(pos) }
        }

        val sceneArea = UIElement().apply {
            setId("topo_multiblock_scene_area")
            layout {
                it.positionType(TaffyPosition.RELATIVE)
                it.widthPercent(100f)
            }
            addChild(scene)
            toolbarOverlays(scene, spinning, resetView, builder).forEach(::addChild)
        }
        return Panel(sceneArea, scene, resetView)
    }

    private fun toolbarOverlays(scene: Scene, spinning: BooleanArray, resetView: Runnable, builder: Builder): List<UIElement> {
        val overlays = ArrayList<UIElement>(2)
        builder.headerInfo?.let { header ->
            overlays.add(
                MachineUiLayout.row(
                    gap = 2f,
                    alignItems = AlignItems.CENTER,
                    id = "topo_multiblock_scene_toolbar_leading",
                ) {
                    root.layout {
                        it.positionType(TaffyPosition.ABSOLUTE)
                        it.top(2f)
                        it.left(2f)
                    }
                    add(header)
                },
            )
        }
        overlays.add(
            MachineUiLayout.row(
                gap = 2f,
                alignItems = AlignItems.CENTER,
                id = "topo_multiblock_scene_toolbar",
            ) {
                root.layout {
                    it.positionType(TaffyPosition.ABSOLUTE)
                    it.top(2f)
                    it.right(2f)
                }
                builder.leadingControls.forEach { add(it) }
                layerSlicer(scene, builder.positions, Vector3f(scene.center), scene.zoom)?.let { add(it) }
                if (builder.zoomControls) {
                    add(
                        MachineUiComponentTemplate.createIconButton(Icons.ADD, "ui.topo.multiblock.preview.zoom_in") {
                            scene.setZoom(maxOf(ZOOM_MIN, scene.zoom - ZOOM_STEP))
                        },
                    )
                    add(
                        MachineUiComponentTemplate.createIconButton(Icons.REMOVE, "ui.topo.multiblock.preview.zoom_out") {
                            scene.setZoom(minOf(ZOOM_MAX, scene.zoom + ZOOM_STEP))
                        },
                    )
                }
                add(
                    MachineUiComponentTemplate.createIconButton(Icons.ROTATION, "ui.topo.multiblock.preview.rotate") {
                        spinning[0] = !spinning[0]
                    },
                )
                if (builder.resetControl) {
                    add(MachineUiComponentTemplate.createIconButton(Icons.REPLAY, "ui.topo.multiblock.preview.reset") { resetView.run() })
                }
                builder.extraControls.forEach { control ->
                    add(
                        MachineUiComponentTemplate.createIconButton(control.icon, control.tooltipKey) {
                            control.action.run()
                        },
                    )
                }
            },
        )
        return overlays
    }

    private fun layerSlicer(scene: Scene, positions: List<BlockPos>, homeCenter: Vector3f, homeZoom: Float): UIElement? {
        val minY = positions.minOfOrNull { it.y } ?: return null
        val maxY = positions.maxOfOrNull { it.y } ?: return null
        if (maxY <= minY) return null

        val selectedLayer = intArrayOf(LAYER_ALL)
        return MachineUiComponentTemplate.createButton(Component.literal(layerLabel(LAYER_ALL, minY))).apply {
            setId("topo_multiblock_scene_layer_button")
            style { it.tooltips(Component.translatable("ui.topo.multiblock.preview.layer")) }
            setOnClick {
                selectedLayer[0] = when {
                    selectedLayer[0] == LAYER_ALL -> minY
                    selectedLayer[0] >= maxY -> LAYER_ALL
                    else -> selectedLayer[0] + 1
                }
                val visibleCells = positions.filter { selectedLayer[0] == LAYER_ALL || it.y == selectedLayer[0] }
                scene.setRenderedCore(visibleCells, null, false)
                scene.setCenter(Vector3f(homeCenter))
                scene.setZoom(homeZoom)
                setText(Component.literal(layerLabel(selectedLayer[0], minY)))
            }
            layout {
                it.height(MachineUiComponentStyle.sceneToolbarButtonSize)
                it.flexShrink(0f)
            }
        }
    }

    private fun layerLabel(layerY: Int, minY: Int): String = if (layerY == LAYER_ALL) "ALL" else "L${layerY - minY + 1}"

    private fun applyView(scene: Scene, center: Vector3f, yaw: Float, pitch: Float, zoom: Float) {
        scene.setCenter(Vector3f(center))
        scene.setCameraYawAndPitch(yaw, pitch)
        scene.setZoom(zoom)
    }
}
