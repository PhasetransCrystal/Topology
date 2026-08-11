package net.ptcrys.topo.api.machine.ui

import net.ptcrys.topo.api.machine.ui.tooltip.LazyHoverPanel

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Clip
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.util.TextFormattingUtil
import dev.vfyjxf.taffy.style.TaffyPosition

import java.util.function.LongSupplier
import java.util.function.Supplier

/**
 * 标量资源条(能量/高级能量/热量):暗轨 + 资源色填充 + 悬浮详情。三种宿主形态:
 *
 * - 机器 UI:横条([Orientation.HORIZONTAL]),玩家物品栏上方、与物品栏同宽,经 [bindStorage]
 *   做服务端存量/容量同步;
 * - Jade:横条,纯客户端,经 [bindLocal] 读取已解码的服务端数据;
 * - JEI:纵条([Orientation.VERTICAL],自下而上填充),经 [setStaticContent] 展示配方消耗/产出量,
 *   每刻内容带 `/t`。
 *
 * 仅作显示,不可交互。创建走 [MachineUiComponentTemplate.createResourceBar],尺寸 token 见
 * [MachineUiComponentStyle];填充色属于资源自身定义,由调用方传入。
 */
class ResourceBar @JvmOverloads constructor(private val resourceName: Component, private val color: Int, private val orientation: Orientation, length: Float = defaultLength(orientation)) : UIElement() {
    enum class Orientation { HORIZONTAL, VERTICAL }

    /**
     * 配方静态条的资源流向。方向不靠文字,靠动画语义表达:与配方进度同周期的壁钟循环里,
     * [CONSUMED] 条从满格持续排空(被吃掉), [PRODUCED] 条自下而上持续涨满(被产出)。
     */
    enum class Flow { CONSUMED, PRODUCED }

    private enum class Mode { UNBOUND, LIVE_SYNCED, LIVE_LOCAL, STATIC }

    private val progressBar = ProgressBar()
    private val overlayLabel = Label()
    private val nameStyle: Style = Style.EMPTY.withColor(color and 0xFFFFFF)

    private var mode = Mode.UNBOUND
    private var amountSupplier = LongSupplier { 0L }
    private var capacitySupplier = LongSupplier { 0L }
    private var syncedAmount = 0L
    private var syncedCapacity = 0L
    private var staticAmount = 0L
    private var perTick = false
    private var flow = Flow.PRODUCED
    private var flowCycleMillis = 0L
    private var staticDurationTicks = 0
    private var staticHover: LazyHoverPanel? = null

    init {
        setId("topo_resource_bar")
        val width: Float
        val height: Float
        if (orientation == Orientation.HORIZONTAL) {
            width = length
            height = MachineUiComponentStyle.resourceBarHeight
        } else {
            width = MachineUiComponentStyle.resourceBarVerticalWidth
            height = length
        }
        layout {
            it.width(width)
            it.height(height)
            it.flexShrink(0f)
        }

        progressBar.setId("topo_resource_bar_fill")
        progressBar.layout {
            it.width(width)
            it.height(height)
        }
        // 内缩量 = 轨道描边宽:填充色贴着边框内沿,不压线。
        val inset = MachineUiComponentStyle.boxTextureWidth
        progressBar.barContainer.layout { it.paddingAll(inset) }
        progressBar.barContainer.style {
            it.backgroundTexture(MachineUiComponentStyle.resourceBarTrackTexture())
        }
        progressBar.bar.style {
            it.backgroundTexture(IGuiTexture.EMPTY)
            it.clip(Clip.SCISSOR)
        }
        val fill = UIElement()
        fill.setId("topo_resource_bar_fill_color")
        // 实心填充色:被任意方向裁剪后视觉一致,无需关心填充窗口的锚边。
        fill.layout {
            it.width(width - 2 * inset)
            it.height(height - 2 * inset)
        }
        fill.style { it.backgroundTexture(ColorRectTexture(color)) }
        progressBar.bar.addChild(fill)
        progressBar.label.setDisplay(false)
        progressBar.progressBarStyle {
            it.fillDirection(
                if (orientation == Orientation.HORIZONTAL) FillDirection.LEFT_TO_RIGHT else FillDirection.DOWN_TO_UP,
            )
            it.interpolate(false)
        }
        progressBar.bindDataSource(SupplierDataSource.of(::fillRatio))
        addChild(progressBar)

        if (orientation == Orientation.HORIZONTAL) {
            overlayLabel.setId("topo_resource_bar_label")
            overlayLabel.layout {
                it.positionType(TaffyPosition.ABSOLUTE)
                it.left(0f)
                it.top(0f)
                it.width(width)
                it.height(height)
            }
            overlayLabel.textStyle {
                it.textAlignHorizontal(Horizontal.CENTER)
                it.textAlignVertical(Vertical.CENTER)
                it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
                it.textColor(MachineUiComponentStyle.textNormal)
                it.textShadow(true)
            }
            overlayLabel.bindDataSource(SupplierDataSource.of(::labelText))
            addChild(overlayLabel)
        }

        addEventListener(UIEvents.HOVER_TOOLTIPS, this::onHoverTooltips)
    }

    /**
     * 机器 UI 实况:服务端经 [serverAmount]/[serverCapacity] 每 tick 推送,客户端镜像到本地字段后
     * 由填充比例/文本的本地数据源读取(与 FluidSlot 的容量同步同型)。
     */
    fun bindStorage(serverAmount: LongSupplier, serverCapacity: LongSupplier): ResourceBar {
        mode = Mode.LIVE_SYNCED
        amountSupplier = LongSupplier { syncedAmount }
        capacitySupplier = LongSupplier { syncedCapacity }
        addChild(
            BindableValue(0L).apply {
                setId("topo_resource_bar_amount_sync")
                setDisplay(false)
                registerValueListener { value -> syncedAmount = value ?: 0L }
                bind(DataBindingBuilder.longValS2C { serverAmount.asLong }.build())
            },
        )
        addChild(
            BindableValue(0L).apply {
                setId("topo_resource_bar_capacity_sync")
                setDisplay(false)
                registerValueListener { value -> syncedCapacity = value ?: 0L }
                bind(DataBindingBuilder.longValS2C { serverCapacity.asLong }.build())
            },
        )
        return this
    }

    /** 纯客户端实况(Jade):直接轮询本地 supplier,不挂任何网络同步。 */
    fun bindLocal(amount: LongSupplier, capacity: LongSupplier): ResourceBar {
        mode = Mode.LIVE_LOCAL
        amountSupplier = amount
        capacitySupplier = capacity
        return this
    }

    /**
     * 静态配方内容(JEI):流向经填充动画表达 —— 以 [cycleDurationTicks](通常为配方时长,
     * 与进度条同周期)循环,消耗条排空、产出条涨满,无需任何文字;明细(方向/速率/总量)
     * 收进悬浮 LCD 面板([recipeContentTooltipPanel],与 JEI 信息 LCD 的资源条目同一构造)。
     */
    fun setStaticContent(amount: Long, perTick: Boolean, flow: Flow, cycleDurationTicks: Int): ResourceBar {
        mode = Mode.STATIC
        staticAmount = amount
        this.perTick = perTick
        this.flow = flow
        this.flowCycleMillis = cycleDurationTicks.coerceAtLeast(1) * 50L
        this.staticDurationTicks = cycleDurationTicks
        this.staticHover = LazyHoverPanel(
            Supplier {
                recipeContentTooltipPanel(
                    resourceName,
                    color,
                    staticAmount,
                    this.perTick,
                    this.flow,
                    staticDurationTicks,
                )
            },
        )
        amountSupplier = LongSupplier { staticAmount }
        capacitySupplier = LongSupplier { 0L }
        return this
    }

    private fun fillRatio(): Float {
        if (mode == Mode.STATIC) {
            // 与 JEI 进度条同款壁钟循环:同一面板里的进度箭头、消耗条、产出条同步呼吸。
            val cycle = (System.currentTimeMillis() % flowCycleMillis) / flowCycleMillis.toFloat()
            return if (flow == Flow.CONSUMED) 1f - cycle else cycle
        }
        val capacity = capacitySupplier.asLong
        if (capacity <= 0L) {
            return 0f
        }
        val amount = amountSupplier.asLong
        return (amount.toDouble() / capacity.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    private fun labelText(): Component {
        val name = resourceName.copy().withStyle(nameStyle)
        val amount = TextFormattingUtil.formatLongToCompactString(amountSupplier.asLong, 3)
        val value = when {
            mode == Mode.STATIC && perTick -> "$amount/t"
            mode == Mode.STATIC -> amount
            else -> "$amount / ${TextFormattingUtil.formatLongToCompactString(capacitySupplier.asLong, 3)}"
        }
        return Component.empty().append(name).append(" ").append(value)
    }

    private fun onHoverTooltips(event: UIEvent) {
        val payload = ArrayList<Any>()
        val staticPanel = staticHover
        if (mode == Mode.STATIC && staticPanel != null) {
            payload.add(staticPanel.get())
        } else {
            payload.add(resourceName.copy().withStyle(nameStyle))
            payload.add(
                Component.translatable(
                    "ui.topo.resource.amount_capacity",
                    amountSupplier.asLong,
                    capacitySupplier.asLong,
                ),
            )
        }
        payload.addAll(style.tooltips().asList())
        event.hoverTooltips = HoverTooltips.create(*payload.toTypedArray())
    }

    override fun onRemoved() {
        super.onRemoved()
        staticHover?.release()
    }

    companion object {
        /** 横条默认与玩家物品栏同宽;纵条默认与配方 IO 行同高。 */
        @JvmStatic
        fun defaultLength(orientation: Orientation): Float = if (orientation == Orientation.HORIZONTAL) {
            MachineUiComponentStyle.playerInventoryWidth.toFloat()
        } else {
            MachineUiComponentStyle.ioRowHeight.toFloat()
        }

        /**
         * 配方标量内容的统一悬浮面板(竖排 LCD,与化学/装备面板同管线),JEI 里左侧纵条与
         * 信息 LCD 的资源条目共用同一构造,保证两处读到完全一致的行:资源名(资源色)|
         * 消耗/产出方向、Rate | 每刻速率(仅 tick 内容)、Amount | 整单总量(tick 内容为
         * 速率×配方时长)。方向已有专行,速率/总量不带符号。包装/缓存/释放走 [LazyHoverPanel]。
         */
        @JvmStatic
        fun recipeContentTooltipPanel(resourceName: Component, color: Int, amount: Long, perTick: Boolean, flow: Flow, durationTicks: Int): UIElement {
            val lcd = MachineUiContainerTemplate.createTooltipLcdData(LcdData.Orientation.VERTICAL)
            lcd.setId("topo_recipe_scalar_tooltip")
            lcd.addStaticEntry(
                resourceName.copy().withStyle(Style.EMPTY.withColor(color and 0xFFFFFF)),
                Component.translatable(
                    if (flow == Flow.CONSUMED) {
                        "ui.topo.resource.consumed"
                    } else {
                        "ui.topo.resource.produced"
                    },
                ),
                LcdData.LED_TEXT,
            )
            if (perTick) {
                lcd.addStaticEntry(
                    Component.translatable("ui.topo.resource.rate"),
                    Component.literal("$amount/t"),
                    LcdData.LED_TEXT,
                )
            }
            val total = if (perTick) amount * durationTicks else amount
            lcd.addStaticEntry(
                Component.translatable("ui.topo.resource.amount"),
                Component.literal(total.toString()),
                LcdData.LED_TEXT,
            )
            return lcd
        }
    }
}
