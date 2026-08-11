package net.ptcrys.topo.api.machine.ui

import net.minecraft.resources.Identifier

import com.lowdragmc.lowdraglib2.gui.texture.AnimationTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture
import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.texture.VanillaSpriteTexture

/**
 * 机器 UI 的唯一样式控制点(弱规范·推荐遵守):换皮 = 只编辑这个文件。
 *
 * 四段结构,依赖只能自上而下:
 * 1. 调色板(private)——纯色值,不带语义;
 * 2. 语义角色(public)——文字/LED/面板等按用途命名的色;
 * 3. 组件配方(public 函数)——把角色组合成纹理;
 * 4. 尺寸——间距/字号/槽位/面板等所有跨文件尺寸 token。
 *
 * 模板([MachineUiComponentTemplate]/[MachineUiContainerTemplate])与组件(LcdData、布局 helper、
 * JEI 工厂)一律从这里取值;豁免项见标准《机器 UI 设计系统口径》。不做运行时换肤:样式在
 * 元素创建时取值,改这里后重新构建/重开界面生效。
 */
object MachineUiComponentStyle {
    // ── 1. 调色板(纯色值,无语义;新主题改这里) ──────────────────────────────
    private val panelFill = 0xFF1E2024.toInt()
    private val panelBorder = 0xFF141518.toInt()
    private val raisedHover = 0xFF3A4048.toInt()
    private val activeFill = 0xFF15222A.toInt()
    private val activeHover = 0xFF1B2B34.toInt()
    private val pressedDark = 0xFF0D0F12.toInt()
    private val accent = 0xFF4EA7C8.toInt()
    private val accentBright = 0xFF85DFFF.toInt()
    private val titleFill = 0xFF101214.toInt()
    private val tabFill = 0xFF1A1C20.toInt()
    private val tabStripFill = 0xF21E2024.toInt()
    private val scrollTrackFill = 0xFF07090B.toInt()
    private val scrollTrackBorder = 0xFF252A30.toInt()
    private val scrollThumbFill = 0xFF2F7188.toInt()
    private val scrollThumbHoverFill = 0xFF439AB5.toInt()
    private val scrollThumbPressedFill = 0xFF66C6DE.toInt()
    private val mutedGray = 0xFF8B96A3.toInt()

    /** 虚拟槽内区压暗色;槽位框体一律原版 MC `container/slot` sprite,不用 LDLib2 内置主题。 */
    private val ghostSlotInsetFill = 0x66000000
    private val vanillaSlotSprite = Identifier.withDefaultNamespace("container/slot")
    private val popupBackdropFill = 0xB8101214.toInt()
    private val idleGray = 0xFF7A8794.toInt()
    private val runningBlue = 0xFF3F94B5.toInt()
    private val waitingAmber = 0xFFE89E47.toInt()
    private val outputGreen = 0xFF7FBF35.toInt()
    private val errorRed = 0xFFE0584B.toInt()
    private val previewHighlightFill = 0x664EA7C8

    /** 策略设置段卡片的左缘强调竖条色(效果图的品红/玫红条,与 cyan 主强调区分,标记"当前策略"段)。 */
    private val sectionAccentPink = 0xFFE0567C.toInt()

    // ── 2. 语义角色(按用途命名;组件按角色取色,不直接碰调色板) ──────────────
    val textNormal = 0xFFD7DCE2.toInt()
    val textSelected = 0xFFBDEEFF.toInt()

    /** 次要说明文字(LCD 标题列等)。 */
    val textMuted = mutedGray

    /** 状态 LED:空闲/运行/等待/产出/错误/信息;[ledText] 用于纯文本行。 */
    val ledIdle = idleGray
    val ledRunning = runningBlue
    val ledWaiting = waitingAmber
    val ledOutput = outputGreen
    val ledError = errorRed
    val ledInfo = accent
    val ledText = textNormal

    /** side-IO 输入/输出语义色(模式环与方向箭头共用):输入信息蓝、输出等待琥珀。 */
    val ioInsert = accent
    val ioExtract = waitingAmber

    // ── 3. 组件配方(角色 → 纹理) ─────────────────────────────────────────────

    /** 描边宽度;必须先于 [boxTexture] 声明(object 按声明序初始化)。 */
    val boxTextureWidth = 1f

    val boxTexture: IGuiTexture = rect(panelFill, panelBorder)

    /**
     * 直角描边矩形 = 1 个填充四边形 + 4 条内描边四边形(GuiTextureGroup 组合)。
     * 不要换回 LDLib2 RectTexture:它无视半径是否为 0,恒按每角 8 段圆弧细分,一个描边矩形
     * ≈108 个三角形且每个三角形一个独立 RenderState——机器 UI 满屏直角小格(side-IO 卡等)
     * 时渲染开销淹没帧预算(UiPerfProbe 渲染二分:4 张 cap 卡 ≈ 基线 4 倍耗时)。
     */
    fun rect(fill: Int, border: Int, stroke: Float = boxTextureWidth): IGuiTexture {
        if (stroke <= 0f) {
            return ColorRectTexture(fill)
        }
        return GuiTextureGroup(
            ColorRectTexture(fill),
            ColorBorderTexture(-stroke.toInt(), border),
        )
    }

    fun tabStripTexture(): IGuiTexture = rect(tabStripFill, pressedDark)

    fun tabButtonBaseTexture(selected: Boolean): IGuiTexture = if (selected) rect(activeFill, accent) else rect(tabFill, pressedDark)

    fun tabButtonHoverTexture(selected: Boolean): IGuiTexture = if (selected) rect(activeHover, accent) else rect(raisedHover, pressedDark)

    fun tabButtonPressedTexture(selected: Boolean): IGuiTexture = if (selected) rect(titleFill, accentBright) else rect(titleFill, accent)

    fun scrollFrameTexture(): IGuiTexture = rect(panelFill, panelBorder)

    fun scrollTrackTexture(): IGuiTexture = rect(scrollTrackFill, scrollTrackBorder)

    fun scrollThumbBaseTexture(): IGuiTexture = rect(scrollThumbFill, accent)

    fun scrollThumbHoverTexture(): IGuiTexture = rect(scrollThumbHoverFill, accentBright)

    fun scrollThumbPressedTexture(): IGuiTexture = rect(scrollThumbPressedFill, textSelected)

    /** LCD 数据面板的底框。 */
    fun lcdFrameTexture(): IGuiTexture = rect(titleFill, scrollTrackBorder)

    /**
     * 槽位框体:原版 MC `container/slot` sprite。模组自建的物品/流体/虚拟槽一律显式设置,
     * 否则落回 LDLib2 内置主题(ItemSlot 浅色 GDP 片 / FluidSlot RECT_DARK),与原版风格冲突。
     */
    fun realSlotTexture(): IGuiTexture = VanillaSpriteTexture.of(vanillaSlotSprite)

    /**
     * ME ghost 配置槽的内区压暗层:槽位背景仍用 [realSlotTexture] 同款(亮边框保留,维持 MC 槽位
     * 观感),此层经 slotOverlay 只覆盖 16×16 内区、画在物品之下,空槽与已配置槽常驻。
     */
    fun ghostSlotOverlayTexture(): ColorRectTexture = ColorRectTexture(ghostSlotInsetFill)

    /** 模具槽幽灵图标:空槽显示模具剪影提示用途(slotOverlay 仅空槽显示,物品之下)。 */
    fun dieSlotOverlayTexture(): IGuiTexture = SpriteTexture.of("topo:textures/gui/slot/mold_overlay.png")

    /** 流体槽幽灵图标:空罐剪影,语义同 [dieSlotOverlayTexture]。 */
    fun fluidSlotOverlayTexture(): IGuiTexture = SpriteTexture.of("topo:textures/gui/slot/fluid_overlay.png")

    /** JEI 催化剂(不消耗)预览槽:槽框叠强调色内边与普通输入槽区分;文案在模具物品自身 tooltip。 */
    fun catalystSlotTexture(): IGuiTexture = GuiTextureGroup(realSlotTexture(), ColorBorderTexture(-1, accent))

    /** 弹窗全屏遮罩底色。 */
    fun popupBackdropTexture(): ColorRectTexture = ColorRectTexture(popupBackdropFill)

    /** 多方块预览 BOM 清单:选中行高亮 / 数量徽章 / 行分隔线。 */
    fun previewHighlightTexture(): IGuiTexture = rect(previewHighlightFill, accentBright)

    /**
     * 端口高亮覆盖层(side-IO 卡标题悬浮时盖在该端口槽位/资源条上,经 style.overlayTexture
     * 画在物品/填充之上):半透明强调填充 + 亮边描边,与 BOM 选中行同语义。单实例 val——
     * [PortUiHighlight] 还原时按实例同一性区分"高亮中"与调用方自带覆盖层。
     */
    val portHighlightOverlayTexture: IGuiTexture = rect(previewHighlightFill, accentBright)

    fun previewCountChipTexture(): IGuiTexture = rect(scrollTrackFill, waitingAmber)

    /** 分区分隔线:用滚动框边色,在面板底上可见(panelBorder 与底色几乎同色,等于隐形)。 */
    fun previewDividerTexture(): IGuiTexture = rect(scrollTrackBorder, scrollTrackBorder, 0f)

    /** 完全透明底(去除组件自带底框时用,如候选行里的无框 LCD 列)。 */
    fun transparentTexture(): IGuiTexture = rect(0x00000000, 0x00000000, 0f)

    /** 标量资源条的底槽(暗轨 + 边框);填充色由资源自身定义,不进样式表。 */
    fun resourceBarTrackTexture(): IGuiTexture = rect(scrollTrackFill, scrollTrackBorder)

    /**
     * 纯标量输入配方(无物品/流体输入槽)的进度条左侧装饰:旋转风扇动画。
     * 素材源自 GTCEU HPCA 主动散热器(16x64 四帧竖条),已重打包为 2x2 雪碧格;
     * 帧间隔沿用原 mcmeta 的 9 tick。每次调用新建实例(AnimationTexture 自带帧状态)。
     */
    fun scalarConversionFanTexture(): AnimationTexture = AnimationTexture("topo:textures/gui/recipe/scalar_conversion_fan.png")
        .setCellSize(2)
        .setAnimation(0, 3)
        .setAnimation(scalarFanFrameTicks)

    /** 策略设置段卡片左缘的强调竖条(实心,无描边;宽度取 [sectionAccentBarWidth])。 */
    fun sectionAccentBarTexture(): IGuiTexture = ColorRectTexture(sectionAccentPink)

    /**
     * 文本输入框聚焦覆盖层:仅强调色内描边(无填充),保留深色底,读作"文本框聚焦"而非"按钮按下"
     * (旧用 sideIoCellHover 的灰填充会让输入框看着像按钮)。
     */
    fun textFieldFocusTexture(): IGuiTexture = ColorBorderTexture(-boxTextureWidth.toInt(), accent)

    /**
     * 搜索池等输入框失焦校验描边:有效=产出绿,无效=错误红;底用暗轨填充与 side-IO 格一致。
     */
    fun textFieldValidationTexture(valid: Boolean): IGuiTexture = rect(scrollTrackFill, if (valid) outputGreen else errorRed)

    /** 透明底的行内按钮(诊断原因行等):平时无底色,悬浮/按下用淡色高亮提示可点。 */
    fun inlineRowButtonBaseTexture(): IGuiTexture = rect(0x00000000, 0x00000000, 0f)

    fun inlineRowButtonHoverTexture(): IGuiTexture = rect(previewHighlightFill, accent)

    fun inlineRowButtonPressedTexture(): IGuiTexture = rect(previewHighlightFill, accentBright)

    /** 样板构建弹窗:能力卡片边框按约束达成态着色(达成=产出绿,未达成=等待琥珀)。 */
    fun patternBuilderCardTexture(satisfied: Boolean): IGuiTexture = if (satisfied) rect(panelFill, outputGreen) else rect(panelFill, waitingAmber)

    /** 样板构建弹窗:候选条目的暗色内嵌底(沿用 LCD 底框配方)。 */
    fun patternBuilderEntryTexture(): IGuiTexture = lcdFrameTexture()

    /** 样板构建弹窗:外壳组数量芯片(信息蓝描边,与结构方块的琥珀芯片 [previewCountChipTexture] 区分)。 */
    fun patternBuilderCasingChipTexture(): IGuiTexture = rect(scrollTrackFill, accent)

    /** 样板构建弹窗:写入按钮禁用态(平暗面、无悬浮高亮)。 */
    fun patternBuilderDisabledButtonTexture(): IGuiTexture = rect(pressedDark, panelBorder)

    // 图标(加/减/重置/禁止动作字形、资源家族图形、按资源类型的图标注册表)独立住
    // [MachineUiIcons]:样式表管"面"(底框/高亮/环),图标类管"图"。

    /** side-IO 配置网格:面格子底框(暗轨配方)与悬浮/按下高亮。 */
    fun sideIoCellBaseTexture(): IGuiTexture = rect(scrollTrackFill, scrollTrackBorder)

    fun sideIoCellHoverTexture(): IGuiTexture = rect(raisedHover, accent)

    fun sideIoCellPressedTexture(): IGuiTexture = rect(titleFill, accentBright)

    /** side-IO 模式环:空心方环(纯内描边,无填充面),INSERT 信息蓝、EXTRACT 等待琥珀(BOTH = 外蓝环套内橙环)。 */
    fun sideIoRingTexture(insert: Boolean): IGuiTexture = ColorBorderTexture(-boxTextureWidth.toInt(), if (insert) ioInsert else ioExtract)

    // ── 4. 尺寸(间距/字号/槽位/面板;跨文件尺寸 token 全部住这里) ────────────
    val mainElementGap = 8f
    val boxAllGap = 4f
    val boxAllPadding = 4f

    /** 卡片标题与内容之间的间距。 */
    val cardHeaderGap = 2f

    /** 卡片内分区(状态/列表/详情等)之间的间距。 */
    val cardSectionGap = 6f
    val pageColumnGap = 4f
    val tabViewGap = 2f
    val tabStripPadding = 2f
    val tabButtonGap = 2f
    val buttonHeight = 15f
    val scrollBarWidth = 5f

    /** LCD 数据面板字号与行高;横排条目间分隔线的上下内缩。 */
    val lcdFontSize = 7
    val lcdLineHeight = 9
    val lcdDividerInset = 2f

    /**
     * 工具提示内嵌 LCD([LcdData.Presentation.TOOLTIP]):对齐原版 tooltip 字号(9),
     * 行高略松;外层卡片由宿主 tooltip 提供,LCD 自身不再叠底框。
     * 键值间距/行距比机器 UI 宽一截,读掉"无框仪表盘"的紧凑表感。
     */
    val lcdTooltipFontSize = 9
    val lcdTooltipLineHeight = 12
    val lcdTooltipKeyValueGap = 10f
    val lcdTooltipRowGap = 2f

    /** 配方 IO 行:槽位边长 / 玩家背包栏宽 / 进度条边长 / 行高。 */
    val slotSize = 18
    val playerInventoryWidth = 162
    val progressSize = 20
    val ioRowHeight = 72

    /** 槽位网格:默认列数 / 超过该可见行数时切换为滚动视图。 */
    val slotGridColumns = 9
    val slotGridVisibleRows = 7

    /** 槽位角落数量角标:4 字符紧凑数字在 16px 内净宽放得下的字号。 */
    val slotCountOverlayFontSize = 4.5f

    /**
     * 标量资源条:横条高(机器 UI/Jade) / 纵条宽(JEI) / 条间距 / Jade 内横条宽
     * (与 Jade 计时面板的 MIN_WIDTH=150 对齐,同一 tooltip 内两块面板等宽)。
     */
    val resourceBarHeight = 10f
    val resourceBarVerticalWidth = 10f
    val resourceBarGap = 2f
    val jadeResourceBarWidth = 150f

    /** 标量转换风扇装饰:边长(与槽位同 18,占位输入槽栏) / 帧间隔 tick(沿用 GTCEU 原 mcmeta)。 */
    val scalarFanSize = 18f
    val scalarFanFrameTicks = 9

    /** 数量编辑弹窗:层级 / 面板宽 / 步进与操作按钮宽 / 文本框高。名称框宽见 [nameFieldWidth](撑满侧栏卡片内宽:卡片上限 140 减两侧边框+内边距 10)。 */
    val popupZIndex = 100
    val popupPanelWidth = 232f
    val popupStepButtonWidth = 34f
    val popupActionButtonWidth = 56f
    val popupFieldHeight = 17f

    /**
     * 物品选择弹窗:面板宽(9 列暂存槽/背包栏 162 + 盒边距 10) / 暂存槽行与背包行之间的分隔间距 /
     * 策略段左缘强调竖条宽。
     */
    val itemPickerPanelWidth = 172f
    val itemPickerSectionGap = 6f
    val sectionAccentBarWidth = 3f

    /**
     * 设置行(端口屏 Order/Interval/Rate 等)统一度量:行/控件高 + 左侧标签列定宽。标签列定宽让
     * 所有行的右侧控件左对齐到同一列、宽度一致(PipePortConfigUi 与 PipePortUiCollector 共用)。
     */
    val controlRowHeight = 16f
    val controlLabelColumnWidth = 52f

    /** Same height as [controlRowHeight] so name fields align with mode toggles / search-pool inputs. */
    val nameFieldHeight = controlRowHeight
    val nameFieldWidth = 130f

    /** JEI 面板:配方类目最小宽高 / 多方块结构预览宽高(场景 + BOM 清单,沿用旧版紧凑面板尺寸)。 */
    val jeiPanelMinWidth = 176
    val jeiPanelMinHeight = 108
    val jeiMultiblockPanelWidth = 280
    val jeiMultiblockPanelHeight = 280

    /** 多方块场景:工具栏图标按钮边长 / 结构页场景高度 / BOM 清单最大高度(紧凑、扩展)。 */
    val sceneToolbarButtonSize = 16f
    val structurePageSceneHeight = 120f
    val previewBlockListMaxCompact = 90f
    val previewBlockListMaxExpanded = 140f

    /** 结构诊断侧栏:列表内容宽(卡片内宽 130 减滚动条 5,贴齐右缘) / 问题列表视口最大高度(约 7 行)。 */
    val diagnosisListContentWidth = 125f
    val diagnosisListMaxHeight = 100f

    /** 多方块预览扩展(全屏)面板宽高。 */
    val jeiMultiblockExpandedWidth = 500
    val jeiMultiblockExpandedHeight = 380

    /** 样板构建弹窗:面板宽 / 能力区滚动视口最大高 / 候选计数列宽。 */
    val patternBuilderPanelWidth = 320f
    val patternBuilderScrollerMaxHeight = 200f
    val patternBuilderCountWidth = 26f

    /** side-IO 配置网格:格子边长 / 格距 / 外环边长 / 内环边长(BOTH 双环) / 卡片标题图标边长。 */
    val sideIoCellSize = 14f
    val sideIoCellGap = 2f
    val sideIoRingSize = 8f
    val sideIoInnerRingSize = 4f
    val sideIoTitleIconSize = 10f
}
