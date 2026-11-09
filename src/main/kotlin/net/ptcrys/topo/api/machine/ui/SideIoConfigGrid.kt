package net.ptcrys.topo.api.machine.ui

import net.ptcrys.topo.api.api.lang.LangKey
import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.resource.AutomationIo
import net.ptcrys.topo.api.machine.resource.PortAccess
import net.ptcrys.topo.api.machine.resource.RecipeRole
import net.ptcrys.topo.api.machine.resource.ResourcePort

import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection
import dev.vfyjxf.taffy.style.TaffyPosition

/**
 * 运行时 side-IO 配置网格:6 个本地面 + 重置/全禁按钮的 3×3 网格。
 *
 * ```
 * ┌──────┬───────┬──────┐
 * │ 重置 │  UP   │ 全禁 │
 * │ LEFT │ FRONT │ RIGHT│
 * │      │ DOWN  │ BACK │
 * └──────┴───────┴──────┘
 * ```
 *
 * **布局铁律**(多次回归):
 * - 根 COLUMN、每行 ROW；宽高用定值(3×cell+2×gap)，禁止 widthMaxContent。
 * - 间距只用容器 gapAll；**禁止**事后对子格再 layout{} 写 margin(会冲掉宽高)。
 * - 禁止绝对定位铺格(UIElement 上 position ABSOLUTE 不可靠，会退回 flex 换行，底行 2 格贴左)。
 *
 * **交互**:面/重置/全禁一律走网格级 [RPCEvent](Int payload),不依赖 Button.setOnServerClick
 * (UIEvent 跨端序列化不可靠,模具槽默认全关时会表现为"怎么点都打不开")。RPC 返回
 * 服务端最终 packed 值；稳定的隐藏数据绑定节点持续处理外部变化和多玩家对账。
 *
 * 本地面: FRONT=NORTH、BACK=SOUTH；视觉 LEFT=EAST、RIGHT=WEST。
 */
class SideIoConfigGrid private constructor(defaultPacked: Int, private val serverPacked: () -> Int, private val onCycle: (Direction) -> Unit, private val onReset: () -> Unit, private val onDisable: () -> Unit) : UIElement() {
    private val cells = LinkedHashMap<Direction, FaceCell>()
    private var packed = defaultPacked
    private val packedMirror = MachineUiComponentTemplate.createS2CMirror(
        "topo_side_io_packed",
        defaultPacked,
        DataBindingBuilder.intValS2C { serverPacked() },
    ) { value -> applyPacked(value) }

    /** payload: 0..5 = Direction.ordinal 循环该面; -1 = 重置; -2 = 全禁。 */
    private val actionRpc: RPCEvent = RPCEventBuilder.simple(
        Int::class.javaObjectType,
        Int::class.javaObjectType,
    ) { payload ->
        when (payload) {
            null -> Unit
            RESET_PAYLOAD -> onReset()
            DISABLE_PAYLOAD -> onDisable()
            else -> {
                val sides = Direction.values()
                if (payload in sides.indices) {
                    onCycle(sides[payload])
                }
            }
        }
        serverPacked()
    }

    init {
        setId("topo_side_io_grid")
        addRPCEvent(actionRpc)
        addChild(packedMirror)

        val cell = MachineUiComponentStyle.sideIoCellSize
        val gap = MachineUiComponentStyle.sideIoCellGap
        val gridW = cell * 3f + gap * 2f
        val gridH = cell * 3f + gap * 2f

        layout {
            it.flexDirection(FlexDirection.COLUMN)
            it.gapAll(gap)
            it.alignItems(AlignItems.FLEX_START)
            // 卡片 createCard 用 alignItems=STRETCH；必须 alignSelf 钉死，否则被拉宽后 gap 重分、列漂。
            it.alignSelf(AlignItems.FLEX_START)
            it.width(gridW)
            it.height(gridH)
            it.minWidth(gridW)
            it.minHeight(gridH)
            it.maxWidth(gridW)
            it.maxHeight(gridH)
            it.flexShrink(0f)
            it.flexGrow(0f)
        }

        addChild(
            row(
                "topo_side_io_row_top",
                gridW,
                cell,
                gap,
                controlButton("topo_side_io_reset", TopoApiLang.UI_SIDE_IO_RESET, MachineUiIcons.reset()) {
                    sendAction(RESET_PAYLOAD)
                },
                faceCell(Direction.UP, "up"),
                controlButton("topo_side_io_disable", TopoApiLang.UI_SIDE_IO_DISABLE, MachineUiIcons.forbidden()) {
                    sendAction(DISABLE_PAYLOAD)
                },
            ),
        )
        addChild(
            row(
                "topo_side_io_row_middle",
                gridW,
                cell,
                gap,
                faceCell(Direction.EAST, "left"),
                faceCell(Direction.NORTH, "front"),
                faceCell(Direction.WEST, "right"),
            ),
        )
        addChild(
            row(
                "topo_side_io_row_bottom",
                gridW,
                cell,
                gap,
                spacer(cell),
                faceCell(Direction.DOWN, "down"),
                faceCell(Direction.SOUTH, "back"),
            ),
        )
        applyPacked(packed)
    }

    /** 固定宽高的三格行；[children] 的 layout 在此之后不得再改。 */
    private fun row(id: String, rowWidth: Float, cell: Float, gap: Float, vararg children: UIElement): UIElement = UIElement().apply {
        setId(id)
        layout {
            it.flexDirection(FlexDirection.ROW)
            it.gapAll(gap)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.FLEX_START)
            it.width(rowWidth)
            it.height(cell)
            it.minWidth(rowWidth)
            it.minHeight(cell)
            it.maxWidth(rowWidth)
            it.maxHeight(cell)
            it.flexShrink(0f)
            it.flexGrow(0f)
        }
        children.forEach { addChild(it) }
    }

    private fun spacer(cell: Float): UIElement = UIElement().apply {
        setId("topo_side_io_spacer")
        layout {
            it.width(cell)
            it.height(cell)
            it.minWidth(cell)
            it.minHeight(cell)
            it.maxWidth(cell)
            it.maxHeight(cell)
            it.flexShrink(0f)
            it.flexGrow(0f)
        }
    }

    private fun faceCell(localSide: Direction, faceKey: String): FaceCell {
        val cell = FaceCell(localSide, faceKey) {
            sendAction(localSide.ordinal)
        }
        cells[localSide] = cell
        return cell
    }

    private fun controlButton(id: String, tooltip: LangKey, glyph: IGuiTexture, action: () -> Unit): Button = Button().apply {
        setId(id)
        noText()
        val cell = MachineUiComponentStyle.sideIoCellSize
        style {
            it.background(MachineUiComponentStyle.sideIoCellBaseTexture())
            it.tooltips(tooltip.getComponent())
        }
        buttonStyle {
            it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
            it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
            it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
        }
        layout {
            it.width(cell)
            it.height(cell)
            it.minWidth(cell)
            it.minHeight(cell)
            it.maxWidth(cell)
            it.maxHeight(cell)
            it.paddingAll(0f)
            it.flexShrink(0f)
            it.flexGrow(0f)
        }
        val overlay = UIElement()
        overlay.setId("${id}_glyph")
        overlay.isAllowHitTest = false
        overlay.layout {
            it.positionType(TaffyPosition.ABSOLUTE)
            it.left(0f)
            it.top(0f)
            it.width(cell)
            it.height(cell)
        }
        overlay.style { it.backgroundTexture(glyph) }
        addChild(overlay)
        addEventListener(UIEvents.MOUSE_DOWN) { event ->
            if (event.button != 0) return@addEventListener
            action()
            event.stopPropagation()
        }
    }

    private fun applyPacked(value: Int) {
        packed = value
        for ((side, cell) in cells) {
            cell.applyMode(PortAccess.sideModeOf(value, side))
        }
    }

    private fun sendAction(payload: Int) {
        sendEvent<Int>(actionRpc, { authoritative ->
            packedMirror.setValue(authoritative, true)
        }, payload)
    }

    private class FaceCell(localSide: Direction, faceKey: String, onClick: () -> Unit) : Button() {
        private val faceName = TopoApiLang.sideIoFace(faceKey).getComponent()
        private val outerRing = ring("topo_side_io_ring_outer", MachineUiComponentStyle.sideIoRingSize)
        private val innerRing = ring("topo_side_io_ring_inner", MachineUiComponentStyle.sideIoInnerRingSize)

        init {
            setId("topo_side_io_face_${localSide.serializedName}")
            noText()
            val cell = MachineUiComponentStyle.sideIoCellSize
            style { it.background(MachineUiComponentStyle.sideIoCellBaseTexture()) }
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
            }
            layout {
                it.width(cell)
                it.height(cell)
                it.minWidth(cell)
                it.minHeight(cell)
                it.maxWidth(cell)
                it.maxHeight(cell)
                it.paddingAll(0f)
                it.flexShrink(0f)
                it.flexGrow(0f)
            }
            addChild(outerRing)
            addChild(innerRing)
            // 不用 setOnServerClick:改走父网格 Int RPC;本监听只负责客户端触发。
            addEventListener(UIEvents.MOUSE_DOWN) { event ->
                if (event.button != 0) return@addEventListener
                onClick()
                event.stopPropagation()
            }
            applyMode(AutomationIo.NONE)
        }

        private fun ring(id: String, size: Float): UIElement = UIElement().apply {
            setId(id)
            // 环只负责画,点击一律落到 FaceCell 上,避免子节点抢 hit 导致服务端事件对不上。
            isAllowHitTest = false
            val cell = MachineUiComponentStyle.sideIoCellSize
            val offset = (cell - size) / 2f
            layout {
                it.positionType(TaffyPosition.ABSOLUTE)
                it.left(offset)
                it.top(offset)
                it.width(size)
                it.height(size)
            }
        }

        fun applyMode(mode: AutomationIo) {
            outerRing.setDisplay(mode != AutomationIo.NONE)
            innerRing.setDisplay(mode == AutomationIo.BOTH)
            outerRing.style {
                it.backgroundTexture(MachineUiComponentStyle.sideIoRingTexture(mode != AutomationIo.EXTRACT))
            }
            if (mode == AutomationIo.BOTH) {
                innerRing.style { it.backgroundTexture(MachineUiComponentStyle.sideIoRingTexture(false)) }
            }
            style {
                it.tooltips(
                    TopoApiLang.UI_SIDE_IO_TOOLTIP.getComponent(
                        faceName,
                        TopoApiLang.sideIoMode(mode).getComponent(),
                    ),
                )
            }
        }
    }

    companion object {
        private const val RESET_PAYLOAD = -1
        private const val DISABLE_PAYLOAD = -2

        @JvmStatic
        fun create(port: ResourcePort<*, *>): SideIoConfigGrid {
            val policy = port.metadata().policy()
            return SideIoConfigGrid(
                policy.defaultPackedSideIo(),
                { port.packedSideIo() },
                { side -> port.cycleSideIo(side) },
                { port.resetSideIo() },
                { port.disableAllSideIo() },
            )
        }

        @JvmStatic
        fun createTitleBar(port: ResourcePort<*, *>): UIElement {
            val resourceType = port.resourceType()
            val ioIcon = when (port.recipeIo()) {
                RecipeRole.INPUT -> MachineUiIcons.ioInput()
                RecipeRole.OUTPUT -> MachineUiIcons.ioOutput()
                else -> MachineUiIcons.ioBoth()
            }
            val portLabel = portDisplayName(
                port.id(),
                resourceType.displayName(),
                TopoApiLang.sideIoTitle(port.recipeIo()).getComponent(),
            )
            val cell = MachineUiComponentStyle.sideIoCellSize
            val gap = MachineUiComponentStyle.sideIoCellGap
            val gridW = cell * 3f + gap * 2f
            return UIElement().apply {
                setId("topo_side_io_card_title")
                layout {
                    it.flexDirection(FlexDirection.ROW)
                    it.justifyContent(AlignContent.SPACE_BETWEEN)
                    it.alignItems(AlignItems.CENTER)
                    it.alignSelf(AlignItems.FLEX_START)
                    it.height(MachineUiComponentStyle.sideIoTitleIconSize)
                    it.width(gridW)
                    it.minWidth(gridW)
                    it.maxWidth(gridW)
                    it.flexShrink(0f)
                    it.flexGrow(0f)
                }
                style {
                    it.tooltips(
                        portLabel,
                        Component.literal(port.id().toString())
                            .withStyle { style -> style.withColor(MachineUiComponentStyle.textMuted and 0xFFFFFF) },
                    )
                }
                addChild(titleIcon("topo_side_io_title_io", ioIcon))
                addChild(
                    titleIcon(
                        "topo_side_io_title_resource",
                        MachineUiIcons.iconOr(resourceType, IGuiTexture.EMPTY),
                    ),
                )
                PortUiHighlight.attachHoverHighlight(this, port.id())
            }
        }

        @JvmStatic
        fun portDisplayName(portId: Identifier, resourceName: Component, roleName: Component): MutableComponent = Component.translatableWithFallback(portId.toLanguageKey("trait"), "%s %s", resourceName, roleName)

        private fun titleIcon(id: String, icon: IGuiTexture): UIElement = UIElement().apply {
            setId(id)
            layout {
                it.width(MachineUiComponentStyle.sideIoTitleIconSize)
                it.height(MachineUiComponentStyle.sideIoTitleIconSize)
                it.flexShrink(0f)
            }
            style { it.backgroundTexture(icon) }
        }
    }
}
