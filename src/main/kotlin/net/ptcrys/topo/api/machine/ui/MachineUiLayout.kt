package net.ptcrys.topo.api.machine.ui

import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection
import dev.vfyjxf.taffy.style.FlexWrap

/**
 * 机器 / 管道 / JEI 共用的布局原语：全部返回 [UIElement]。
 * 与 [MachineUiContainerTemplate] / [MachineUiComponentTemplate] 同属 `MachineUi*` 族。
 *
 * 调用方只描述结构（[column] / [row] / [verticalList] / [pageColumn] / [box]）；
 * chrome 与绑定细节点进模板或具体组件。
 *
 * **列表间距**：taffy 的 gapRow/gapAll 在部分列容器上不可靠或与子项叠出超大空白。
 * 纵向列表统一用 [verticalList]；项间距由 [MachineUiLayoutScope] 插入定高 spacer，
 * **不**再设置容器 gap 属性。
 */
object MachineUiLayout {
    /**
     * 纵向列表：子项按 [gap] 分隔（配方修正块、侧栏卡列等）。
     * 间距仅通过 spacer 实现，见 [MachineUiLayoutScope]。
     */
    @JvmOverloads
    fun verticalList(gap: Float = MachineUiComponentStyle.mainElementGap, maxWidth: Float = Float.NaN, width: Float = Float.NaN, alignItems: AlignItems = AlignItems.FLEX_START, justifyContent: AlignContent? = null, id: String = "machine_ui_vertical_list", block: MachineUiLayoutScope.() -> Unit): UIElement = buildColumn(
        maxWidth = maxWidth,
        width = width,
        alignItems = alignItems,
        justifyContent = justifyContent,
        id = id,
        itemGap = gap.coerceAtLeast(0f),
        block = block,
    )

    /**
     * 纵向堆叠。默认 [gap]=0（纯结构、无列表间距）。
     * 需要项间距时请用 [verticalList]，避免与结构列混用导致间距异常。
     */
    @JvmOverloads
    fun column(gap: Float = 0f, maxWidth: Float = Float.NaN, width: Float = Float.NaN, alignItems: AlignItems = AlignItems.FLEX_START, justifyContent: AlignContent? = null, id: String = "machine_ui_column", block: MachineUiLayoutScope.() -> Unit): UIElement = buildColumn(
        maxWidth = maxWidth,
        width = width,
        alignItems = alignItems,
        justifyContent = justifyContent,
        id = id,
        itemGap = gap.coerceAtLeast(0f),
        block = block,
    )

    private fun buildColumn(maxWidth: Float, width: Float, alignItems: AlignItems, justifyContent: AlignContent?, id: String, itemGap: Float, block: MachineUiLayoutScope.() -> Unit): UIElement = UIElement().apply {
        setId(id)
        layout {
            it.flexDirection(FlexDirection.COLUMN)
            it.alignItems(alignItems)
            if (justifyContent != null) {
                it.justifyContent(justifyContent)
            }
            // 故意不设 gapRow/gapAll：与 spacer 叠加曾导致侧栏/列表出现「整卡高」空白。
            it.flexShrink(0f)
            when {
                !width.isNaN() -> it.width(width)
                else -> it.widthMaxContent()
            }
            if (!maxWidth.isNaN()) {
                it.maxWidth(maxWidth)
            }
        }
        MachineUiLayoutScope(this, itemGap = itemGap).block()
    }

    @JvmOverloads
    fun row(gap: Float = MachineUiComponentStyle.boxAllGap, width: Float = Float.NaN, height: Float = Float.NaN, widthPercent: Float = Float.NaN, alignItems: AlignItems = AlignItems.FLEX_START, justifyContent: AlignContent? = null, flexWrap: FlexWrap? = null, id: String = "machine_ui_row", block: MachineUiLayoutScope.() -> Unit): UIElement = UIElement().apply {
        setId(id)
        layout {
            it.flexDirection(FlexDirection.ROW)
            it.alignItems(alignItems)
            if (justifyContent != null) {
                it.justifyContent(justifyContent)
            }
            if (flexWrap != null) {
                it.flexWrap(flexWrap)
            }
            // 横向仍用 gapColumn（行内实测可用）；不用 gapAll。
            it.gapColumn(gap)
            it.flexShrink(0f)
            when {
                !width.isNaN() -> it.width(width)
                !widthPercent.isNaN() -> it.widthPercent(widthPercent)
                else -> it.widthMaxContent()
            }
            if (!height.isNaN()) {
                it.height(height)
            }
        }
        // 行内不用 spacer 列表间距，避免把纵向 list gap 逻辑混进横向。
        MachineUiLayoutScope(this, itemGap = 0f).block()
    }

    /** 100% 宽水平分隔线。 */
    @JvmOverloads
    fun horizontalDivider(height: Float = MachineUiComponentStyle.boxTextureWidth, id: String = "machine_ui_horizontal_divider"): UIElement = UIElement().apply {
        setId(id)
        layout {
            it.widthPercent(100f)
            it.height(height)
            it.minHeight(height)
            it.maxHeight(height)
            it.flexShrink(0f)
            it.flexGrow(0f)
        }
        style { it.backgroundTexture(MachineUiComponentStyle.previewDividerTexture()) }
    }

    /**
     * 主区分页内容根：固定宽、子项水平居中（与
     * [MachineUiContainerTemplate.createPageColumn] 同语义）。
     */
    @JvmOverloads
    fun pageColumn(width: Float, gap: Float = MachineUiComponentStyle.pageColumnGap, block: MachineUiLayoutScope.() -> Unit): UIElement = MachineUiContainerTemplate.createPageColumn(width, gap).also {
        // pageColumn 模板自带 gapRow；这里只填子节点，不再套 list spacer。
        MachineUiLayoutScope(it, itemGap = 0f).block()
    }

    /** 设计系统盒子底框（可选内边距/间距）。 */
    @JvmOverloads
    fun box(needPadding: Boolean = true, needGap: Boolean = false, block: MachineUiLayoutScope.() -> Unit): UIElement = MachineUiContainerTemplate.createBox(needPadding, needGap).also {
        MachineUiLayoutScope(it, itemGap = 0f).block()
    }

    /** 1px 竖向分隔（标量条组间等）。 */
    @JvmOverloads
    fun verticalDivider(height: Float, id: String = "machine_ui_vertical_divider"): UIElement = UIElement().apply {
        setId(id)
        layout {
            it.width(1f)
            it.height(height)
            it.minHeight(height)
            it.maxHeight(height)
            it.flexShrink(0f)
            it.flexGrow(0f)
        }
        style { it.backgroundTexture(MachineUiComponentStyle.previewDividerTexture()) }
    }

    /**
     * ModularUI 内容根：宽度必须是确定值。
     * 避免 widthMaxContent + 子级 widthPercent 测窄导致居中偏右。
     */
    @JvmOverloads
    fun modularContentRoot(width: Float, gap: Float = 0f, id: String = "machine_ui_modular_root", block: MachineUiLayoutScope.() -> Unit): UIElement = column(
        gap = gap,
        width = width,
        alignItems = AlignItems.STRETCH,
        id = id,
        block = block,
    )

    /**
     * 左缘强调竖条 + 内容列的标准设置卡（管道策略卡 / 过滤卡等）。
     *
     * 内边距与标题卡一致；竖条与内容列之间用 [MachineUiComponentStyle.boxAllGap]
     * 拉开，避免粉色条贴字、右侧内容被挤没 margin。
     */
    @JvmOverloads
    fun accentContentCard(id: String, accentId: String = "${id}_accent", contentId: String = "${id}_content", content: MachineUiLayoutScope.() -> Unit): UIElement = box(needPadding = false, needGap = false) {
        // needPadding=false 只留 1px 边框避让；这里重写为完整内边距 + 列间距，避免
        // createBox 默认 COLUMN/gapAll 语义和 ROW 强调条布局打架。
        val pad = MachineUiComponentStyle.boxTextureWidth + MachineUiComponentStyle.boxAllPadding
        root.setId(id)
        root.layout {
            it.flexDirection(FlexDirection.ROW)
            it.alignItems(AlignItems.STRETCH)
            it.widthPercent(100f)
            it.flexShrink(0f)
            it.paddingAll(pad)
            // 横向间距用 gapColumn（与 row 原语一致）；给粉色条右侧留出呼吸距。
            it.gapColumn(MachineUiComponentStyle.boxAllGap)
        }
        add(
            UIElement().apply {
                setId(accentId)
                isAllowHitTest = false
                layout {
                    it.width(MachineUiComponentStyle.sectionAccentBarWidth)
                    it.alignSelf(AlignItems.STRETCH)
                    it.flexShrink(0f)
                }
                style { it.backgroundTexture(MachineUiComponentStyle.sectionAccentBarTexture()) }
            },
        )
        // 必须用 MachineUiLayout.verticalList（顶层工厂）再 add：
        // 若写 add(verticalList {…})，scope.verticalList 内部已 add 一次，外层再 add 会炸
        // “Cannot add the same child twice”。
        add(
            MachineUiLayout.verticalList(
                gap = MachineUiComponentStyle.boxAllGap,
                id = contentId,
            ) {
                root.layout {
                    it.width(0f)
                    it.flexGrow(1f)
                    it.flexShrink(1f)
                    // 内容列占满剩余宽；卡片右侧 padding 负责外边距，不再被 flex 子项顶穿。
                    it.minWidth(0f)
                }
                content()
            },
        )
    }
}

/**
 * 布局建造接收者。仅建造期存在，不进入运行时树。
 *
 * @param itemGap 纵向列表项间距；>0 时从第二项起写 marginTop（不插入额外节点、不改写其它 layout 语义）。
 */
class MachineUiLayoutScope(val root: UIElement, private val itemGap: Float = 0f) {
    private var childCount = 0

    fun add(child: UIElement): UIElement {
        if (itemGap > 0f && childCount > 0) {
            // 只追加顶距；LDLib layout 为属性合并写入，不会清掉子项已有 width/height。
            child.layout {
                it.marginTop(itemGap)
                it.flexGrow(0f)
                it.flexShrink(0f)
            }
        }
        root.addChild(child)
        childCount++
        return child
    }

    operator fun UIElement.unaryPlus(): UIElement = add(this)

    @JvmOverloads
    fun verticalList(gap: Float = MachineUiComponentStyle.mainElementGap, maxWidth: Float = Float.NaN, width: Float = Float.NaN, alignItems: AlignItems = AlignItems.FLEX_START, justifyContent: AlignContent? = null, id: String = "machine_ui_vertical_list", block: MachineUiLayoutScope.() -> Unit): UIElement = add(MachineUiLayout.verticalList(gap, maxWidth, width, alignItems, justifyContent, id, block))

    @JvmOverloads
    fun column(gap: Float = 0f, maxWidth: Float = Float.NaN, width: Float = Float.NaN, alignItems: AlignItems = AlignItems.FLEX_START, justifyContent: AlignContent? = null, id: String = "machine_ui_column", block: MachineUiLayoutScope.() -> Unit): UIElement = add(MachineUiLayout.column(gap, maxWidth, width, alignItems, justifyContent, id, block))

    @JvmOverloads
    fun row(gap: Float = MachineUiComponentStyle.boxAllGap, width: Float = Float.NaN, height: Float = Float.NaN, widthPercent: Float = Float.NaN, alignItems: AlignItems = AlignItems.FLEX_START, justifyContent: AlignContent? = null, flexWrap: FlexWrap? = null, id: String = "machine_ui_row", block: MachineUiLayoutScope.() -> Unit): UIElement = add(
        MachineUiLayout.row(gap, width, height, widthPercent, alignItems, justifyContent, flexWrap, id, block),
    )

    fun horizontalDivider(height: Float = MachineUiComponentStyle.boxTextureWidth, id: String = "machine_ui_horizontal_divider"): UIElement = add(MachineUiLayout.horizontalDivider(height, id))

    @JvmOverloads
    fun box(needPadding: Boolean = true, needGap: Boolean = false, block: MachineUiLayoutScope.() -> Unit): UIElement = add(MachineUiLayout.box(needPadding, needGap, block))

    @JvmOverloads
    fun lcdPanel(orientation: LcdData.Orientation = LcdData.Orientation.VERTICAL, minValueWidth: Float = Float.NaN, block: LcdData.() -> Unit): LcdData {
        val panel = MachineUiContainerTemplate.createLcdData(orientation)
        if (!minValueWidth.isNaN()) {
            panel.minValueWidth(minValueWidth)
        }
        panel.block()
        return add(panel) as LcdData
    }
}
