package net.ptcrys.topo.api.pipe.ui

import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.ui.AmountEditorPopup
import net.ptcrys.topo.api.machine.ui.ItemPickerPopup
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiIcons
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.api.machine.ui.TopoTextField
import net.ptcrys.topo.api.pipe.AggregationWindow
import net.ptcrys.topo.api.pipe.PipeDefinition
import net.ptcrys.topo.api.pipe.PipeDistributionStrategy
import net.ptcrys.topo.api.pipe.PipeFilterAdapter
import net.ptcrys.topo.api.pipe.PipePortFilter
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig
import net.ptcrys.topo.api.pipe.network.PipeLevelRuntime
import net.ptcrys.topo.api.pipe.network.PipeNetworkEngine

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack

import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder
import com.lowdragmc.lowdraglib2.gui.texture.FluidStackTexture
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI
import com.lowdragmc.lowdraglib2.gui.ui.UI
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager
import com.lowdragmc.lowdraglib2.utils.FluidHelper
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems

import java.util.Locale
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier
import java.util.function.UnaryOperator

/**
 * 管道抽取端口配置屏(扳手右键端帽打开),紧凑单列布局:顶部策略页签条(DIST/EQUL/ROBN)→
 * 标题行(管道图标 + 名称 + 朝向)→ 选中策略的设置卡(左缘强调竖条 + 策略名标题;Order 等策略
 * 自供行;Interval / Rate 各为一行"标签 + 可点数值按钮(带 › 角标)",点击打开 [AmountEditorPopup]
 * 精确编辑)→ 黑白名单过滤区(W/B 计数页签 + 加号(开物品选择弹窗)/输入框/加号(提交手输) +
 * 统一条目滚动列表)。主面板不再常驻玩家物品栏——物品选择改由 [ItemPickerPopup] 承担。
 *
 * 同步惯用法:策略/批量/聚合和策略自供选项各使用一个隐藏 [BindableValue] S2C 镜像；
 * 客户端编辑通过有返回值的 RPC 提交，回调立即写回服务端钳制后的最终值。朝向与两份名单
 * 只使用隐藏 S2C binding；过滤器输入框和页签仍是纯客户端草稿。
 */
object PipePortConfigUi {
    /** 单列工具面板;过滤区与设置卡上下堆叠,宽度容纳过滤列表一行(图标 + 名称 + 删除)。 */
    private const val PANEL_WIDTH = 236f
    private const val TAB_HEIGHT = 20f
    private const val TAB_MAX_WIDTH = 112f
    private val CONTROL_HEIGHT = MachineUiComponentStyle.controlRowHeight
    private const val SIDE_CHIP_WIDTH = 72f
    private const val HEADER_ICON_SIZE = 16f
    private const val CHEVRON_WIDTH = 9f
    private const val FILTER_LIST_HEIGHT = 60f
    private const val FILTER_ROW_HEIGHT = 20f
    private const val FILTER_TAB_WIDTH = 50f

    /** 条目行的方框边长:前导槽(图标/徽章)与删除钮统一同尺寸,行内垂直居中对齐到同一条线。 */
    private const val FILTER_ROW_BOX_SIZE = 18f
    private const val FILTER_ROW_ICON_SIZE = 16f
    private const val ENTRY_PAYLOAD_SEPARATOR = '|'
    private const val KEY_ENTER = 257
    private const val KEY_NUMPAD_ENTER = 335

    @JvmStatic
    fun build(definition: PipeDefinition, holder: BlockUIMenuType.BlockUIHolder): ModularUI {
        val session = resolveSession(holder)
        val offers = definition.strategies()
        val syncAnchors = ArrayList<BindableValue<*>>()

        // --- per-offer settings cards (prebuilt, setDisplay-switched) ---------------------------
        val railTabs = ArrayList<Button>(offers.size)
        val railLabels = ArrayList<Label>(offers.size)
        val strategySections = ArrayList<UIElement>(offers.size)
        val intervalLabels = ArrayList<Label>(offers.size)
        val rateLabels = ArrayList<Label>(offers.size)

        // 客户端最近权威值供弹窗初值/上限使用；提交 RPC 返回 runtime 钳制后的最终值。
        var lastAmount = 0
        var lastInterval = offers.firstOrNull()?.aggregation()?.initialInterval() ?: 1
        fun refreshAmountDerived() {
            rateLabels.forEach { it.setText(amountText(definition, lastAmount, lastInterval)) }
        }
        fun applyStrategy(selected: Int) {
            railTabs.forEachIndexed { index, tab ->
                val active = index == selected
                tab.buttonStyle {
                    it.baseTexture(MachineUiComponentStyle.tabButtonBaseTexture(active))
                    it.hoverTexture(MachineUiComponentStyle.tabButtonHoverTexture(active))
                    it.pressedTexture(MachineUiComponentStyle.tabButtonPressedTexture(active))
                }
                railLabels[index].textStyle {
                    it.textColor(
                        if (active) {
                            MachineUiComponentStyle.textSelected
                        } else {
                            MachineUiComponentStyle.textMuted
                        },
                    )
                }
            }
            strategySections.forEachIndexed { index, section ->
                section.setDisplay(index == selected)
            }
        }
        fun applyAmount(amount: Int) {
            lastAmount = amount
            refreshAmountDerived()
        }
        fun applyInterval(interval: Int) {
            lastInterval = interval.coerceAtLeast(1)
            offers.forEachIndexed { index, offer ->
                intervalLabels[index].setText(
                    intervalText(lastInterval, offer.aggregation()),
                )
            }
            refreshAmountDerived()
        }
        val sideBinding = boundValue(
            "topo_pipe_port_side_sync",
            -1,
            DataBindingBuilder.intValS2C { session?.side?.ordinal ?: -1 },
        ).also(syncAnchors::add)
        val strategyBinding = authoritativeIntValue(
            "topo_pipe_port_strategy_value",
            0,
            Supplier { session?.run { runtime.portStrategyIndex(pos, side) } ?: 0 },
            Consumer { selected -> session?.run { runtime.uiSelectStrategy(pos, side, selected) } },
            Consumer(::applyStrategy),
        ).also(syncAnchors::add)
        val amountBinding = authoritativeIntValue(
            "topo_pipe_port_amount_value",
            0,
            Supplier { session?.run { runtime.portAmount(pos, side) } ?: 0 },
            Consumer { amount -> session?.run { runtime.uiSetAmount(pos, side, amount) } },
            Consumer(::applyAmount),
        ).also(syncAnchors::add)
        val intervalBinding = authoritativeIntValue(
            "topo_pipe_port_interval_value",
            lastInterval,
            Supplier { session?.run { runtime.portInterval(pos, side) } ?: 1 },
            Consumer { interval -> session?.run { runtime.uiSetInterval(pos, side, interval) } },
            Consumer(::applyInterval),
        ).also(syncAnchors::add)

        offers.forEachIndexed { index, offer ->
            val collector = PipePortUiCollector()
            val access = object : PipePortAccess {
                override fun definition(): PipeDefinition = definition
                override fun currentConfig(): PipePortStrategyConfig {
                    val fallback = offer.strategy().initialPortConfig(definition, offer.aggregation())
                    val active = session ?: return fallback
                    return try {
                        active.runtime.portConfig(active.pos, active.side)
                    } catch (_: IllegalStateException) {
                        fallback
                    }
                }
                override fun updateConfig(update: UnaryOperator<PipePortStrategyConfig>) {
                    session?.run { runtime.uiUpdateConfig(pos, side, update) }
                }
                override fun bindInt(id: String, initialValue: Int, serverGetter: Supplier<Int>, serverSetter: Consumer<Int>, clientApply: Consumer<Int>): BindableValue<Int> {
                    val value = authoritativeIntValue(
                        id,
                        initialValue,
                        Supplier { if (session != null) serverGetter.get() else initialValue },
                        Consumer { selected -> if (session != null) serverSetter.accept(selected) },
                        clientApply,
                    )
                    syncAnchors.add(value)
                    return value
                }
            }
            offer.strategy().contributeConfigUi(collector, access)

            val window = offer.aggregation()
            val intervalValue = valueLabel(
                "topo_pipe_port_interval_value_$index",
                intervalText(window.initialInterval(), window),
            )
            intervalLabels.add(intervalValue)
            val intervalTitle = if (window.fixed()) {
                TopoApiLang.UI_PIPE_PORT_INTERVAL_FIXED.getComponent()
            } else {
                TopoApiLang.UI_PIPE_PORT_INTERVAL.getComponent()
            }
            val intervalElement: UIElement = if (window.fixed()) {
                valueChip("topo_pipe_port_interval_chip_$index", intervalValue)
            } else {
                clickableValue("topo_pipe_port_interval_click_$index", intervalValue) {
                    AmountEditorPopup.open(
                        intervalValue,
                        TopoApiLang.UI_PIPE_PORT_INTERVAL_POPUP_TITLE.getComponent(),
                        lastInterval.toLong(),
                        window.minInterval().toLong(),
                        window.maxInterval().toLong(),
                    ) { committed ->
                        intervalBinding.setValue(committed.coerceIn(1L, Int.MAX_VALUE.toLong()).toInt())
                    }
                }
            }

            val rateValue = valueLabel(
                "topo_pipe_port_rate_value_$index",
                amountText(definition, 0, window.initialInterval()),
            )
            rateLabels.add(rateValue)
            val rateElement = clickableValue("topo_pipe_port_rate_click_$index", rateValue) {
                AmountEditorPopup.open(
                    rateValue,
                    TopoApiLang.UI_PIPE_PORT_AMOUNT_POPUP_TITLE.getComponent(),
                    lastAmount.toLong(),
                    0L,
                    definition.maxBatchAmount(lastInterval).toLong(),
                ) { committed ->
                    amountBinding.setValue(committed.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
                }
            }

            strategySections.add(
                settingsSection(
                    index,
                    collector,
                    offer.strategy().displayName(),
                    intervalTitle,
                    intervalElement,
                    rateElement,
                ).apply { setDisplay(index == 0) },
            )
        }

        // --- shell ------------------------------------------------------------------------------
        val sideTitle = mutedLabel("topo_pipe_port_side", Component.literal("..."))
        val hasFilter = definition.filterSettings().enabled()
        val contentWidth = innerWidth(PANEL_WIDTH)
        val filterView = if (hasFilter) FilterView(definition, session, contentWidth) else null

        val settingsInset = MachineUiLayout.column(gap = 0f, id = "topo_pipe_port_settings") {
            root.layout {
                it.widthPercent(100f)
                it.flexShrink(0f)
            }
            strategySections.forEach { add(it) }
        }

        val panel = MachineUiContainerTemplate.createPopupPanel(PANEL_WIDTH).apply {
            setId("topo_pipe_port_panel")
            addChild(headerRow(definition, sideTitle, contentWidth))
            addChild(settingsInset)
            filterView?.let { addChild(it.root) }
        }

        val tabStrip = MachineUiLayout.row(
            gap = MachineUiComponentStyle.boxAllGap,
            widthPercent = 100f,
            id = "topo_pipe_port_tabs",
        ) {
            root.layout {
                it.paddingAll(MachineUiComponentStyle.boxAllPadding)
                it.flexShrink(0f)
            }
            root.style { it.backgroundTexture(MachineUiComponentStyle.tabStripTexture()) }
            offers.forEachIndexed { index, offer ->
                add(railTab(index, offer.strategy(), strategyBinding, railTabs, railLabels))
            }
        }

        // 确定宽内容根：ModularUI 按根尺寸居中。勿用 widthMaxContent + 子级 widthPercent，
        // 否则根宽测偏小 → leftPos 偏大 → 面板整体偏右（见 MachineUiLayout.modularContentRoot）。
        // 根也不占满屏，JEI 才能把物品列表画在面板两侧。
        val root = MachineUiLayout.modularContentRoot(PANEL_WIDTH, id = "topo_pipe_port_root") {
            add(
                MachineUiLayout.column(
                    gap = MachineUiComponentStyle.boxAllGap,
                    width = PANEL_WIDTH,
                    alignItems = AlignItems.STRETCH,
                    id = "topo_pipe_port_window",
                ) {
                    add(
                        UIElement().apply {
                            setId("topo_pipe_port_sync_host")
                            isAllowHitTest = false
                            syncAnchors.forEach(::addChild)
                        },
                    )
                    add(tabStrip)
                    add(panel)
                },
            )
        }

        // --- syncs --------------------------------------------------------------------------------
        sideBinding.registerValueListener { ordinal ->
            val side = ordinal?.takeIf { it in 0..5 }?.let { Direction.values()[it] }
            // 朝向芯片:muted "side" 前缀 + 高亮方向词(对齐效果图 "side North")。
            sideTitle.setText(
                if (side == null) {
                    Component.literal("...")
                } else {
                    Component.literal("side ")
                        .withColor(MachineUiComponentStyle.textMuted)
                        .append(
                            TopoApiLang.side(side).getComponent()
                                .withColor(MachineUiComponentStyle.textSelected),
                        )
                },
            )
        }
        return ModularUI.of(
            UI.of(root, StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)),
            holder.player,
        )
    }

    // --- header ----------------------------------------------------------------------------------

    private fun innerChrome(): Float = MachineUiComponentStyle.boxTextureWidth + MachineUiComponentStyle.boxAllPadding

    private fun innerWidth(width: Float): Float = width - innerChrome() * 2f

    private fun headerRow(definition: PipeDefinition, sideTitle: Label, contentWidth: Float): UIElement = MachineUiLayout.box(needPadding = true, needGap = false) {
        root.setId("topo_pipe_port_header")
        root.layout {
            it.widthPercent(100f)
            it.flexShrink(0f)
        }
        add(
            MachineUiLayout.row(
                gap = MachineUiComponentStyle.boxAllGap,
                widthPercent = 100f,
                alignItems = AlignItems.CENTER,
                id = "topo_pipe_port_header_row",
            ) {
                add(headerIcon(definition))
                add(
                    Label().apply {
                        setId("topo_pipe_port_title")
                        setText(Component.literal(definition.displayName()))
                        textStyle {
                            it.textColor(MachineUiComponentStyle.textSelected)
                            it.textShadow(true)
                            it.textWrap(TextWrap.WRAP)
                            it.adaptiveHeight(true)
                        }
                        // 固定宽换行:长名(Advanced Energy Pipe)折两行而不是溢出。绝不可用
                        // width(0)+flexGrow——adaptiveHeight 的 Label 在测量期按 0 宽计高,文本退化为
                        // 每字符一行,把整窗布局高度撑爆(BOM 行 nameLabel 注释同款陷阱)。
                        layout {
                            it.width(
                                (
                                    contentWidth - innerChrome() * 2f - HEADER_ICON_SIZE - SIDE_CHIP_WIDTH -
                                        MachineUiComponentStyle.boxAllGap * 2f
                                    ).coerceAtLeast(80f),
                            )
                            it.flexShrink(0f)
                        }
                    },
                )
                add(
                    fixedLabelChip(
                        "topo_pipe_port_side_chip",
                        sideTitle.apply {
                            textStyle {
                                it.textColor(MachineUiComponentStyle.textSelected)
                                it.textShadow(false)
                            }
                        },
                        SIDE_CHIP_WIDTH,
                    ),
                )
            },
        )
    }

    private fun headerIcon(definition: PipeDefinition): UIElement = UIElement().apply {
        setId("topo_pipe_port_icon")
        isAllowHitTest = false
        layout {
            it.width(HEADER_ICON_SIZE)
            it.height(HEADER_ICON_SIZE)
            it.flexShrink(0f)
        }
        style { it.backgroundTexture(ItemStackTexture(ItemStack(definition.registeredBlock().get()))) }
    }

    private fun fixedLabelChip(id: String, label: Label, width: Float): UIElement = MachineUiLayout.row(
        gap = 0f,
        width = width,
        height = CONTROL_HEIGHT,
        alignItems = AlignItems.CENTER,
        justifyContent = AlignContent.CENTER,
        id = id,
    ) {
        label.fillButtonText()
        root.layout {
            it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
            it.paddingRight(MachineUiComponentStyle.boxAllPadding)
        }
        root.style { it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture()) }
        add(label)
    }

    // --- strategy tab strip ----------------------------------------------------------------------

    /** One top-strip tab: short code label, full strategy name in the tooltip. */
    private fun railTab(index: Int, strategy: PipeDistributionStrategy, selectedStrategy: BindableValue<Int>, tabSink: ArrayList<Button>, labelSink: ArrayList<Label>): Button {
        val button = Button()
        button.setId("topo_pipe_port_tab_$index")
        button.noText()
        button.buttonStyle {
            it.baseTexture(MachineUiComponentStyle.tabButtonBaseTexture(index == 0))
            it.hoverTexture(MachineUiComponentStyle.tabButtonHoverTexture(index == 0))
            it.pressedTexture(MachineUiComponentStyle.tabButtonPressedTexture(index == 0))
        }
        button.layout {
            it.height(TAB_HEIGHT)
            it.width(0f)
            it.flexGrow(1f)
            it.maxWidth(TAB_MAX_WIDTH)
            it.paddingAll(0f)
            it.justifyContent(AlignContent.CENTER)
            it.alignItems(AlignItems.CENTER)
        }
        val label = centeredButtonLabel(
            "topo_pipe_port_tab_label_$index",
            strategy.shortName(),
            if (index == 0) MachineUiComponentStyle.textSelected else MachineUiComponentStyle.textMuted,
        )
        button.addChild(label)
        button.style.tooltips(strategy.displayName())
        button.setOnClick { event ->
            if (event.button == 0) {
                selectedStrategy.setValue(index)
                event.stopPropagation()
            }
        }
        tabSink.add(button)
        labelSink.add(label)
        return button
    }

    // --- settings card ---------------------------------------------------------------------------

    /**
     * 行式设置卡(每 offer 一张,选中 setDisplay 切换):左缘强调竖条 + 内容列(策略名标题行 +
     * 策略自供行 + Interval 行 + Rate 行)。值元素为预构建的可点击/固定 chip,RPC 在调用方接好。
     */
    private fun settingsSection(index: Int, collector: PipePortUiCollector, strategyTitle: Component, intervalTitle: Component, intervalElement: UIElement, rateElement: UIElement): UIElement {
        val card = MachineUiLayout.accentContentCard(
            id = "topo_pipe_port_section_card_$index",
            accentId = "topo_pipe_port_section_accent_$index",
            contentId = "topo_pipe_port_section_content_$index",
        ) {
            add(sectionHeader(index, strategyTitle))
            collector.rows().forEach { add(it) }
            add(parameterRow("topo_pipe_port_interval_row_$index", intervalTitle, intervalElement))
            add(
                parameterRow(
                    "topo_pipe_port_rate_row_$index",
                    TopoApiLang.UI_PIPE_PORT_RATE.getComponent(),
                    rateElement,
                ),
            )
        }
        return MachineUiLayout.column(gap = 0f, id = "topo_pipe_port_section_$index") {
            root.layout {
                it.widthPercent(100f)
                it.flexShrink(0f)
            }
            add(card)
        }
    }

    private fun sectionHeader(index: Int, title: Component): UIElement = MachineUiLayout.row(
        gap = MachineUiComponentStyle.boxAllGap,
        alignItems = AlignItems.CENTER,
        justifyContent = AlignContent.SPACE_BETWEEN,
        id = "topo_pipe_port_section_header_$index",
    ) {
        root.layout {
            it.widthPercent(100f)
            it.flexShrink(0f)
        }
        add(
            Label().apply {
                setId("topo_pipe_port_section_title_$index")
                setText(title)
                textStyle {
                    it.textColor(MachineUiComponentStyle.textSelected)
                    it.textShadow(true)
                    it.adaptiveWidth(true)
                    it.adaptiveHeight(true)
                }
                layout { it.flexShrink(0f) }
            },
        )
        add(
            mutedLabel(
                "topo_pipe_port_section_hint_$index",
                TopoApiLang.UI_PIPE_PORT_SECTION_HINT.getComponent(),
            ),
        )
    }

    private fun parameterRow(id: String, title: Component, control: UIElement): UIElement = MachineUiLayout.row(
        gap = MachineUiComponentStyle.boxAllGap,
        alignItems = AlignItems.CENTER,
        id = id,
    ) {
        root.layout {
            it.widthPercent(100f)
            it.flexShrink(0f)
        }
        add(rowLabel("${id}_title", title))
        add(control)
    }

    /** 设置行左侧标签:定宽列(让各行右侧控件左对齐、等宽)、左对齐 muted 文本。 */
    private fun rowLabel(id: String, text: Component): Label = Label().apply {
        setId(id)
        setText(text)
        textStyle {
            it.textColor(MachineUiComponentStyle.textMuted)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.LEFT)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        layout {
            it.width(MachineUiComponentStyle.controlLabelColumnWidth)
            it.height(CONTROL_HEIGHT)
            it.flexShrink(0f)
        }
    }

    /**
     * 可点击数值行(整行宽,带 › 角标):hover 淡色高亮提示可点,左键打开数量编辑弹窗(与 ME
     * 配置页同款组件)。内部 [label] 仍由同步 setText 刷新。
     *
     * 布局注意:Button 默认 paddingAll(2)+height(14),若只改左右 padding 会留下上下 2px,
     * 再嵌套 height=CONTROL_HEIGHT 的内行会把文字顶偏。这里清零 padding,标签与 › 直接
     * 作为按钮子节点并在内容盒内垂直居中。
     */
    private fun clickableValue(id: String, label: Label, onClick: () -> Unit): Button = Button().apply {
        setId(id)
        noText()
        flexValueLabel(label)
        buttonStyle {
            it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
            it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
            it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
        }
        layout {
            it.width(0f)
            it.flexGrow(1f)
            it.flexShrink(1f)
            it.height(CONTROL_HEIGHT)
            // 逐侧写:Button 默认 paddingAll(2);PADDING_ALL 与 LEFT/RIGHT 分槽,勿混用。
            it.paddingTop(0f)
            it.paddingBottom(0f)
            it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
            it.paddingRight(MachineUiComponentStyle.boxAllPadding)
            it.alignItems(AlignItems.CENTER)
            it.justifyContent(AlignContent.CENTER)
        }
        addChild(label)
        addChild(chevronGlyph("${id}_chevron"))
        style.tooltips(TopoApiLang.UI_PIPE_PORT_CLICK_TO_EDIT.getComponent())
        setOnClick { event ->
            if (event.button == 0) {
                onClick()
                event.stopPropagation()
            }
        }
    }

    private fun valueChip(id: String, label: Label): UIElement = MachineUiLayout.row(
        gap = 0f,
        height = CONTROL_HEIGHT,
        alignItems = AlignItems.CENTER,
        justifyContent = AlignContent.CENTER,
        id = id,
    ) {
        flexValueLabel(label)
        root.layout {
            it.width(0f)
            it.flexGrow(1f)
            it.flexShrink(1f)
            it.paddingTop(0f)
            it.paddingBottom(0f)
            it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
            it.paddingRight(MachineUiComponentStyle.boxAllPadding)
        }
        root.style { it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture()) }
        add(label)
    }

    private fun chevronGlyph(id: String): Label = Label().apply {
        setId(id)
        setText(Component.literal("›"))
        isAllowHitTest = false
        textStyle {
            it.textColor(MachineUiComponentStyle.textMuted)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        layout {
            it.width(CHEVRON_WIDTH)
            it.heightPercent(100f)
            it.flexShrink(0f)
        }
    }

    /** 数值标签在行式按钮里占满剩余宽度、文本水平+垂直居中(给 › 角标让出右端固定宽)。 */
    private fun flexValueLabel(label: Label) {
        label.isAllowHitTest = false
        label.textStyle {
            it.textColor(MachineUiComponentStyle.textSelected)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        label.layout {
            it.width(0f)
            it.flexGrow(1f)
            it.flexShrink(1f)
            it.heightPercent(100f)
        }
    }

    private fun Label.fillButtonText(): Label = apply {
        isAllowHitTest = false
        textStyle {
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        layout {
            it.widthPercent(100f)
            it.heightPercent(100f)
            it.marginHorizontal(0f)
            it.flexShrink(1f)
        }
    }

    private fun centeredButtonLabel(id: String, text: Component, color: Int): Label = Label().apply {
        setId(id)
        setText(text)
        textStyle {
            it.textColor(color)
        }
        fillButtonText()
    }

    private fun centeredGlyphLabel(id: String, glyph: String, color: Int): Label = Label().apply {
        setId(id)
        setText(Component.literal(glyph))
        isAllowHitTest = false
        textStyle {
            it.textColor(color)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
        }
        layout {
            it.widthPercent(100f)
            it.heightPercent(100f)
            it.flexShrink(0f)
        }
    }

    /** 居中的 + 图标(代码绘制,几何居中;替换偏心的文字字形)——过滤区两个加号钮共用。 */
    private fun plusIconChild(id: String): UIElement = UIElement().apply {
        setId(id)
        isAllowHitTest = false
        layout {
            it.width(8f)
            it.height(8f)
            it.flexShrink(0f)
        }
        style { it.backgroundTexture(MachineUiIcons.plus()) }
    }

    // --- filter section ---------------------------------------------------------------------------

    /**
     * 过滤区:页签头(白/黑计数 + 计数芯片) + 输入行(加号开物品选择弹窗 + id/#tag 输入框 +
     * 加号提交手输) + 统一条目滚动列表。服务端真值经两条合并串 S2C 下行;客户端持有解析后的
     * 两份列表,页签切换重渲染。
     */
    private class FilterView(private val definition: PipeDefinition, session: Session?, private val filterWidth: Float) {
        private val capacity = definition.filterSettings().entryCapacity()
        private val adapter: PipeFilterAdapter<*>? = definition.profile().filterAdapter()

        // 过滤卡现为"强调竖条 + 内容列"行式卡片,内容宽要扣掉竖条与列间距(与设置卡同口径)。
        private val filterContentWidth =
            innerWidth(filterWidth) - MachineUiComponentStyle.sectionAccentBarWidth - MachineUiComponentStyle.boxAllGap
        private val filterRowContentWidth =
            (filterContentWidth - MachineUiComponentStyle.scrollBarWidth * 2f - MachineUiComponentStyle.boxAllGap)
                .coerceAtLeast(96f)
        private var whiteEntries: List<String> = emptyList()
        private var blackEntries: List<String> = emptyList()
        private var activeWhite = true

        /** "w|entry" / "b|entry" 上行通道;移除通道同载荷格式。 */
        private val addRpc = RPCEventBuilder.simple(String::class.java) { payload ->
            session?.run {
                parsePayload(payload)?.let { (white, entry) ->
                    runtime.uiAddFilterEntry(pos, side, white, entry)
                }
            }
        }
        private val removeRpc = RPCEventBuilder.simple(String::class.java) { payload ->
            session?.run {
                parsePayload(payload)?.let { (white, entry) ->
                    runtime.uiRemoveFilterEntry(pos, side, white, entry)
                }
            }
        }

        private val whiteTabLabel = tabLabel("topo_pipe_port_filter_tab_white_label", whiteText())
        private val blackTabLabel = tabLabel("topo_pipe_port_filter_tab_black_label", blackText())
        private val whiteTab = pageTab(
            "topo_pipe_port_filter_tab_white",
            whiteTabLabel,
            TopoApiLang.UI_PIPE_PORT_WHITELIST.getComponent(),
        ) { selectPage(true) }
        private val blackTab = pageTab(
            "topo_pipe_port_filter_tab_black",
            blackTabLabel,
            TopoApiLang.UI_PIPE_PORT_BLACKLIST.getComponent(),
        ) { selectPage(false) }
        private val countLabel = mutedLabel("topo_pipe_port_filter_count", countText())
        private val rowsHost = MachineUiLayout.column(gap = 0f, width = filterRowContentWidth, id = "topo_pipe_port_filter_rows") {}
        private val scroller: ScrollerView =
            MachineUiContainerTemplate.createScrollView(
                filterContentWidth - MachineUiComponentStyle.scrollBarWidth,
                FILTER_LIST_HEIGHT,
            )
                .apply {
                    setId("topo_pipe_port_filter_scroller")
                    addScrollViewChild(rowsHost)
                }
        private val input = TopoTextField().apply {
            setId("topo_pipe_port_filter_input")
            setAnyString()
            setText("", false)
            // 空值时渲染的是 TextFieldStyle.placeholder(默认字面 "Empty"),换成本屏的输入提示。
            textFieldStyle {
                it.placeholder(TopoApiLang.UI_PIPE_PORT_FILTER_PLACEHOLDER.getComponent())
                it.textColor(MachineUiComponentStyle.textSelected)
                it.cursorColor(MachineUiComponentStyle.textSelected)
                it.textShadow(false)
                // 聚焦只描强调色边框(不填灰),读作文本框聚焦而非按钮按下。
                it.focusOverlay(MachineUiComponentStyle.textFieldFocusTexture())
            }
            style {
                it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
            }
            layout {
                it.height(CONTROL_HEIGHT)
                it.width(0f)
                it.flexGrow(1f)
                it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
                it.paddingRight(MachineUiComponentStyle.boxAllPadding)
            }
            style { it.tooltips(TopoApiLang.UI_PIPE_PORT_FILTER_INPUT_TOOLTIP.getComponent()) }
        }
        private val whitelistBinding = boundValue(
            "topo_pipe_port_filter_whitelist_value",
            "",
            DataBindingBuilder.stringS2C {
                session?.run { runtime.portFilter(pos, side).whitelist().joinToString("\n") } ?: ""
            },
        ).apply {
            registerValueListener { joined ->
                whiteEntries = splitEntries(joined)
                onListsChanged()
            }
        }
        private val blacklistBinding = boundValue(
            "topo_pipe_port_filter_blacklist_value",
            "",
            DataBindingBuilder.stringS2C {
                session?.run { runtime.portFilter(pos, side).blacklist().joinToString("\n") } ?: ""
            },
        ).apply {
            registerValueListener { joined ->
                blackEntries = splitEntries(joined)
                onListsChanged()
            }
        }

        // 行式卡片:左缘强调竖条(与设置卡同标准件 accentContentCard)+ 内容列。
        val root: UIElement = MachineUiLayout.accentContentCard(
            id = "topo_pipe_port_filter",
            accentId = "topo_pipe_port_filter_accent",
            contentId = "topo_pipe_port_filter_content",
        ) {
            add(whitelistBinding)
            add(blacklistBinding)
            add(filterTitle())
            add(filterHeaderRow())
            add(inputRow())
            add(scroller)
        }.apply {
            layout {
                it.width(filterWidth)
                it.flexShrink(0f)
            }
            addRPCEvent(addRpc)
            addRPCEvent(removeRpc)
        }

        private fun filterTitle(): Label = Label().apply {
            setId("topo_pipe_port_filter_title")
            setText(TopoApiLang.UI_PIPE_PORT_FILTER_RULES.getComponent())
            textStyle {
                it.textColor(MachineUiComponentStyle.textSelected)
                it.textShadow(true)
                it.adaptiveWidth(true)
                it.adaptiveHeight(true)
            }
            layout { it.flexShrink(0f) }
        }

        init {
            input.addEventListener(UIEvents.KEY_DOWN) { event ->
                if (event.keyCode == KEY_ENTER || event.keyCode == KEY_NUMPAD_ENTER) {
                    commitInput()
                    event.stopPropagation()
                }
            }
            restyleTabs()
            rebuildRows()
        }

        private fun filterHeaderRow(): UIElement = MachineUiLayout.row(
            gap = MachineUiComponentStyle.boxAllGap,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "topo_pipe_port_filter_tabs_row",
        ) {
            add(whiteTab)
            add(blackTab)
            add(countChip())
        }

        private fun inputRow(): UIElement = MachineUiLayout.row(
            gap = MachineUiComponentStyle.boxAllGap,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "topo_pipe_port_filter_input_row",
        ) {
            add(pickerButton())
            add(input)
            add(addButton())
        }

        private fun countChip(): UIElement = MachineUiLayout.row(
            gap = 0f,
            height = CONTROL_HEIGHT,
            alignItems = AlignItems.CENTER,
            justifyContent = AlignContent.FLEX_END,
            id = "topo_pipe_port_filter_count_chip",
        ) {
            root.layout {
                it.width(0f)
                it.flexGrow(1f)
                it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
                it.paddingRight(MachineUiComponentStyle.boxAllPadding)
            }
            root.style { it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture()) }
            add(countLabel)
        }

        /** 加号(左):打开物品选择弹窗,确认后把暂存物品逐个转条目写入当前页名单。 */
        private fun pickerButton(): Button = Button().apply {
            setId("topo_pipe_port_filter_pick")
            noText()
            addChild(plusIconChild("topo_pipe_port_filter_pick_icon"))
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
            }
            layout {
                it.width(CONTROL_HEIGHT)
                it.height(CONTROL_HEIGHT)
                it.paddingAll(0f)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            style.tooltips(TopoApiLang.UI_PIPE_PORT_FILTER_PICK_TOOLTIP.getComponent())
            setOnClick { event ->
                if (event.button == 0) {
                    openPicker()
                    event.stopPropagation()
                }
            }
        }

        private fun openPicker() {
            val entryAdapter = adapter ?: return
            ItemPickerPopup.open(
                root,
                if (activeWhite) {
                    TopoApiLang.UI_PIPE_PORT_ADD_TO_WHITELIST.getComponent()
                } else {
                    TopoApiLang.UI_PIPE_PORT_ADD_TO_BLACKLIST.getComponent()
                },
                // 物品→条目:物品管=注册名;流体管=经 entryFromCarried 读物品流体能力抽出的流体名。
                Function { stack -> entryAdapter.entryFromCarried(stack) },
                // 条目→暂存槽图标:流体管渲染流体、物品管渲染物品。
                Function { entry -> previewIconFor(entry) },
                Consumer { entries -> entries.forEach(::sendAdd) },
            )
        }

        /** 条目预览图标:有流体取流体贴图(流体管),否则物品贴图;都无则透明(未解析 id)。 */
        private fun previewIconFor(entry: String): IGuiTexture {
            val a = adapter ?: return MachineUiComponentStyle.transparentTexture()
            val fluids = a.displayFluidStacks(entry).filter { !it.isEmpty }
            if (fluids.isNotEmpty()) {
                return FluidStackTexture(*fluids.map { it.copy() }.toTypedArray())
            }
            val stacks = a.displayStacks(entry).filter { !it.isEmpty }
            if (stacks.isNotEmpty()) {
                return ItemStackTexture(*stacks.map { it.copy() }.toTypedArray())
            }
            return MachineUiComponentStyle.transparentTexture()
        }

        private fun addButton(): Button = Button().apply {
            setId("topo_pipe_port_filter_add")
            noText()
            addChild(plusIconChild("topo_pipe_port_filter_add_icon"))
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
            }
            layout {
                it.width(CONTROL_HEIGHT)
                it.height(CONTROL_HEIGHT)
                it.paddingAll(0f)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            style.tooltips(TopoApiLang.UI_PIPE_PORT_FILTER_ADD_TOOLTIP.getComponent())
            setOnClick { event ->
                if (event.button == 0) {
                    commitInput()
                    event.stopPropagation()
                }
            }
        }

        private fun commitInput() {
            val raw = input.text.trim()
            if (raw.isNotEmpty()) {
                sendAdd(raw)
                input.setText("", false)
            }
        }

        private fun sendAdd(entry: String) {
            root.sendEvent(addRpc, (if (activeWhite) "w" else "b") + ENTRY_PAYLOAD_SEPARATOR + entry)
        }

        private fun sendRemove(white: Boolean, entry: String) {
            root.sendEvent(removeRpc, (if (white) "w" else "b") + ENTRY_PAYLOAD_SEPARATOR + entry)
        }

        private fun selectPage(white: Boolean) {
            if (activeWhite != white) {
                activeWhite = white
                restyleTabs()
                rebuildRows()
            }
        }

        private fun restyleTabs() {
            fun apply(tab: Button, active: Boolean) = tab.buttonStyle {
                it.baseTexture(MachineUiComponentStyle.tabButtonBaseTexture(active))
                it.hoverTexture(MachineUiComponentStyle.tabButtonHoverTexture(active))
                it.pressedTexture(MachineUiComponentStyle.tabButtonPressedTexture(active))
            }
            apply(whiteTab, activeWhite)
            apply(blackTab, !activeWhite)
        }

        // 紧凑面板下页签用缩写计数(W 6 / B 2),全称与语义住 tooltip。
        private fun whiteText(): Component = Component.literal("● ")
            .withColor(MachineUiComponentStyle.ledOutput)
            .append(
                TopoApiLang.UI_PIPE_PORT_WHITELIST_SHORT.getComponent(whiteEntries.size)
                    .withColor(MachineUiComponentStyle.textNormal),
            )

        private fun blackText(): Component = Component.literal("● ")
            .withColor(MachineUiComponentStyle.ledError)
            .append(
                TopoApiLang.UI_PIPE_PORT_BLACKLIST_SHORT.getComponent(blackEntries.size)
                    .withColor(MachineUiComponentStyle.textNormal),
            )

        private fun countText(): Component = TopoApiLang.UI_PIPE_PORT_FILTER_COUNT.getComponent(
            (if (activeWhite) whiteEntries else blackEntries).size,
            capacity,
        )

        private fun onListsChanged() {
            whiteTabLabel.setText(whiteText())
            blackTabLabel.setText(blackText())
            rebuildRows()
        }

        private fun rebuildRows() {
            rowsHost.clearAllChildren()
            val white = activeWhite
            val entries = if (white) whiteEntries else blackEntries
            entries.forEach { entry -> rowsHost.addChild(entryRow(white, entry)) }
            countLabel.setText(countText())
        }

        /** 统一条目行:槽图标(物品)或 # 徽章(Tag)/? 徽章(未解析 id) + 名称 + × 删除。 */
        private fun entryRow(white: Boolean, entry: String): UIElement = MachineUiLayout.row(
            gap = MachineUiComponentStyle.boxAllGap,
            height = FILTER_ROW_HEIGHT,
            widthPercent = 100f,
            alignItems = AlignItems.CENTER,
            id = "topo_pipe_port_filter_row",
        ) {
            root.layout {
                it.paddingLeft(2f)
                it.paddingRight(2f)
            }
            val tag = PipePortFilter.isTagEntry(entry)
            val previewFluids = adapter?.displayFluidStacks(entry)?.filter { !it.isEmpty } ?: emptyList()
            val previewStacks = if (previewFluids.isEmpty()) {
                adapter?.displayStacks(entry)?.filter { !it.isEmpty } ?: emptyList()
            } else {
                emptyList()
            }
            // 前导框统一为同尺寸白框槽(图标或徽章字形),与删除钮同尺寸、行内垂直居中,对齐到同一条线。
            add(
                when {
                    previewFluids.isNotEmpty() ->
                        leadingSlot(rowIcon(FluidStackTexture(*previewFluids.map { it.copy() }.toTypedArray())))

                    previewStacks.isNotEmpty() ->
                        leadingSlot(rowIcon(ItemStackTexture(*previewStacks.map { it.copy() }.toTypedArray())))

                    else ->
                        leadingSlot(
                            centeredGlyphLabel(
                                "topo_pipe_port_filter_row_badge_label",
                                if (tag) "#" else "?",
                                MachineUiComponentStyle.ledInfo,
                            ),
                        )
                },
            )
            val plainStack = if (tag) null else previewStacks.firstOrNull()
            val plainFluid = if (tag) null else previewFluids.firstOrNull()
            val name = plainStack?.hoverName ?: plainFluid?.let(FluidHelper::getDisplayName) ?: Component.literal(entry)
            add(
                Label().apply {
                    setId("topo_pipe_port_filter_row_name")
                    setText(name)
                    textStyle {
                        it.textColor(if (tag) MachineUiComponentStyle.ledInfo else MachineUiComponentStyle.textNormal)
                        it.textShadow(false)
                        it.adaptiveHeight(true)
                    }
                    layout {
                        it.width(0f)
                        it.flexGrow(1f)
                        it.flexShrink(1f)
                    }
                    style { it.tooltips(Component.literal(entry)) }
                },
            )
            add(entryTypeHint(tag, previewStacks, previewFluids))
            add(removeButton(white, entry))
        }

        /** 条目类型提示:item / fluid / preview N(标签按预览物品数)/ tag(标签无预览)。 */
        private fun entryTypeHint(tag: Boolean, stacks: List<ItemStack>, fluids: List<FluidStack>): Label {
            val text = when {
                tag && fluids.isNotEmpty() ->
                    TopoApiLang.UI_PIPE_PORT_FILTER_ENTRY_PREVIEW.getComponent(fluids.size)

                tag && stacks.isNotEmpty() ->
                    TopoApiLang.UI_PIPE_PORT_FILTER_ENTRY_PREVIEW.getComponent(stacks.size)

                tag -> TopoApiLang.UI_PIPE_PORT_FILTER_ENTRY_TAG.getComponent()

                fluids.isNotEmpty() -> TopoApiLang.UI_PIPE_PORT_FILTER_ENTRY_FLUID.getComponent()

                stacks.isNotEmpty() -> TopoApiLang.UI_PIPE_PORT_FILTER_ENTRY_ITEM.getComponent()

                else -> Component.empty()
            }
            return mutedLabel("topo_pipe_port_filter_row_type", text)
        }

        /** 前导框:统一白框槽([FILTER_ROW_BOX_SIZE] 见方),内含图标或徽章字形;与删除钮同尺寸同基线。 */
        private fun leadingSlot(content: UIElement): UIElement = UIElement().apply {
            setId("topo_pipe_port_filter_row_lead")
            layout {
                it.width(FILTER_ROW_BOX_SIZE)
                it.height(FILTER_ROW_BOX_SIZE)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            style { it.backgroundTexture(MachineUiComponentStyle.realSlotTexture()) }
            addChild(content)
        }

        private fun rowIcon(texture: IGuiTexture): UIElement = UIElement().apply {
            setId("topo_pipe_port_filter_row_icon")
            isAllowHitTest = false
            layout {
                it.width(FILTER_ROW_ICON_SIZE)
                it.height(FILTER_ROW_ICON_SIZE)
                it.flexShrink(0f)
            }
            style { it.backgroundTexture(texture) }
        }

        private fun removeButton(white: Boolean, entry: String): Button = Button().apply {
            setId("topo_pipe_port_filter_row_remove")
            noText()
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
            }
            layout {
                it.width(FILTER_ROW_BOX_SIZE)
                it.height(FILTER_ROW_BOX_SIZE)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            addChild(
                UIElement().apply {
                    setId("topo_pipe_port_filter_row_remove_icon")
                    isAllowHitTest = false
                    layout {
                        it.width(8f)
                        it.height(8f)
                        it.flexShrink(0f)
                    }
                    style { it.backgroundTexture(MachineUiIcons.remove()) }
                },
            )
            setOnClick { event ->
                if (event.button == 0) {
                    sendRemove(white, entry)
                    event.stopPropagation()
                }
            }
        }

        private fun tabLabel(id: String, text: Component): Label = Label().apply {
            setId(id)
            setText(text)
            fillButtonText()
        }

        private fun pageTab(id: String, label: Label, tooltip: Component, onClick: () -> Unit): Button = Button().apply {
            setId(id)
            noText()
            layout {
                it.width(FILTER_TAB_WIDTH)
                it.height(CONTROL_HEIGHT)
                it.paddingAll(0f)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            addChild(label)
            style.tooltips(tooltip)
            setOnClick { event ->
                if (event.button == 0) {
                    onClick()
                    event.stopPropagation()
                }
            }
        }

        private fun splitEntries(joined: String?): List<String> = joined?.takeIf { it.isNotEmpty() }?.split('\n') ?: emptyList()

        private fun parsePayload(payload: String?): Pair<Boolean, String>? {
            if (payload == null || payload.length < 3 || payload[1] != ENTRY_PAYLOAD_SEPARATOR) {
                return null
            }
            val white = when (payload[0]) {
                'w' -> true
                'b' -> false
                else -> return null
            }
            return white to payload.substring(2)
        }
    }

    private fun authoritativeIntValue(id: String, initialValue: Int, serverGetter: Supplier<Int>, serverSetter: Consumer<Int>, clientApply: Consumer<Int>): BindableValue<Int> {
        lateinit var value: BindableValue<Int>
        fun applyAuthoritative(authoritative: Int?) {
            val resolved = authoritative ?: initialValue
            value.setValue(resolved, false)
            clientApply.accept(resolved)
        }
        val setRpc = RPCEventBuilder.simple(
            Int::class.javaObjectType,
            Int::class.javaObjectType,
        ) { requested ->
            serverSetter.accept(requested ?: initialValue)
            serverGetter.get()
        }
        value = BindableValue(initialValue).apply {
            setId(id)
            setDisplay(false)
            addRPCEvent(setRpc)
            registerValueListener { requested ->
                if (requested != null) {
                    sendEvent(
                        setRpc,
                        Consumer<Int> { authoritative -> applyAuthoritative(authoritative) },
                        requested,
                    )
                }
            }
            bind(
                DataBindingBuilder.intValS2C { serverGetter.get() }
                    .remoteSetter { authoritative -> applyAuthoritative(authoritative) }
                    .build(),
            )
        }
        return value
    }

    private fun <T> boundValue(id: String, initialValue: T, binding: DataBindingBuilder<T>): BindableValue<T> = BindableValue(initialValue).apply {
        setId(id)
        setDisplay(false)
        bind(binding.build())
    }

    // --- session and text helpers ------------------------------------------------------------------

    /** 服务端解析被点击的端口;客户端(或 pending 缺失)为 null,展示值等待同步。 */
    private fun resolveSession(holder: BlockUIMenuType.BlockUIHolder): Session? {
        val player = holder.player as? ServerPlayer ?: return null
        val level = player.level()
        val runtime = PipeNetworkEngine.runtime(level)
        val pending = runtime.consumePendingPortScreen(player.uuid) ?: return null
        return Session(runtime, pending.pos, pending.side)
    }

    private class Session(val runtime: PipeLevelRuntime, val pos: BlockPos, val side: Direction)

    /** 等效每周期批量值 "amount / max",max = 每 tick 承载量 × 当前周期。 */
    private fun amountText(definition: PipeDefinition, amount: Int, interval: Int): Component = TopoApiLang.UI_PIPE_PORT_RATE_VALUE.getComponent(
        definition.profile().formatAmount(amount.toLong()),
        definition.profile().formatAmount(definition.maxBatchAmount(interval).toLong()),
    )

    private fun intervalText(interval: Int, window: AggregationWindow): Component {
        val clamped = window.clamp(interval)
        val seconds = String.format(Locale.ROOT, "%.1f", clamped / 20.0)
        return TopoApiLang.UI_PIPE_PORT_INTERVAL_VALUE.getComponent(clamped, seconds)
    }

    private fun mutedLabel(id: String, text: Component): Label = Label().apply {
        setId(id)
        setText(text)
        textStyle {
            it.textColor(MachineUiComponentStyle.textMuted)
            it.textShadow(false)
            it.adaptiveWidth(true)
            it.adaptiveHeight(true)
        }
        layout { it.flexShrink(0f) }
    }

    private fun valueLabel(id: String, text: Component): Label = Label().apply {
        setId(id)
        setText(text)
        // 尺寸/对齐由 flexValueLabel 在挂入 chip/按钮时统一设置(避免 adaptive 把高度收成字高、顶偏)。
        textStyle {
            it.textColor(MachineUiComponentStyle.textSelected)
            it.textShadow(false)
            it.textAlignHorizontal(Horizontal.CENTER)
            it.textAlignVertical(Vertical.CENTER)
            it.adaptiveWidth(false)
            it.adaptiveHeight(false)
        }
        layout {
            it.width(0f)
            it.flexGrow(1f)
            it.flexShrink(1f)
            it.heightPercent(100f)
        }
    }
}
