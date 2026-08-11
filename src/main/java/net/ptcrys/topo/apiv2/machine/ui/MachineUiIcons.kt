package net.ptcrys.topo.apiv2.machine.ui

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceType

import com.lowdragmc.lowdraglib2.gui.texture.ColorRectTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTexture
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelperClient

import java.util.function.Supplier

/**
 * 机器 UI 的图标标准类(API 侧,与 [MachineUiComponentStyle] 平级:样式表管"面",本类管"图")。
 * 全部图标为代码绘制,无贴图资产;颜色一律取样式表的语义角色或由调用方传入。
 *
 * 三段结构(本类只承载框架标准,不认识任何内建家族——内建资源的图形声明住 data 侧
 * {@code BuiltinOIMachineUiIcons},addon 同理自带声明类):
 * 1. 构造原语——[pixelGlyph](14 格设计稿铺 1px 矩形,绘制时等比缩放,沿用旧版
 *    StoragePortIoSidePanel 的逐矩形画法,内建/addon 图形声明共用)与 bar(按钮内容区比例色条);
 * 2. 通用动作/方向图标——加/减/重置/禁止与 IO 方向三角等跨场景字形;
 * 3. 资源图标注册表——按 [MachineResourceType] 注册/查询家族图标,随 bootstrap 冻结;
 *    注册位置统一在各资源注册主线(内建为 {@code BuiltinOIResourceIntegrations.integrate});
 *    注册接收惰性 [Supplier](首查解析后记忆):注册发生在公共类初始化期,纹理构造必须推迟
 *    到 UI 运行时,否则无 FML 的纯 JVM 单测会炸。未注册家族经 [iconOr] 优雅降级。
 */
object MachineUiIcons {
    private const val GLYPH_GRID = 14f

    // ── 1. 构造原语 ─────────────────────────────────────────────────────────

    /**
     * 像素字形标准原语:在 14 格设计稿上铺 1px 矩形(每条 rect = [x, y, w, h]),绘制时按目标
     * 尺寸等比缩放(14px 格子即 1:1)。本类的动作字形与 data/addon 侧的资源家族图形声明都用它。
     */
    @JvmStatic
    fun pixelGlyph(color: Int, vararg rects: FloatArray): IGuiTexture = GuiTexture.of { ctx, x, y, w, h ->
        val u = w / GLYPH_GRID
        val v = h / GLYPH_GRID
        for (r in rects) {
            DrawerHelperClient.drawSolidRect(ctx, x + r[0] * u, y + r[1] * v, r[2] * u, r[3] * v, color)
        }
    }

    /** 比例色条(文本色):按宿主内容区的宽高比例缩放,加/减号的臂。 */
    private fun bar(width: Float, height: Float): IGuiTexture = ColorRectTexture(MachineUiComponentStyle.textNormal).scale(width, height)

    // ── 2. 通用动作图标 ─────────────────────────────────────────────────────

    /** 减号(横条;LDLib2 Icons 的加号是带框精灵,与扁条风格不配,故加/减都手绘)。 */
    @JvmStatic
    fun minus(): IGuiTexture = bar(0.55f, 0.15f)

    /** 加号(横竖条十字叠加,臂长臂粗与 [minus] 一致)。 */
    @JvmStatic
    fun plus(): IGuiTexture = GuiTextureGroup(bar(0.55f, 0.15f), bar(0.15f, 0.55f))

    /** 删除叉:8 格像素图标,用于 16px 微型按钮;不走字体,避免 × 字形视觉偏心。 */
    @JvmStatic
    fun remove(): IGuiTexture = GuiTexture.of { ctx, x, y, w, h ->
        val p = minOf(w, h) / 8f
        val ox = x + (w - p * 8f) / 2f
        val oy = y + (h - p * 8f) / 2f
        val color = MachineUiComponentStyle.ledError
        fun dot(px: Float, py: Float) {
            DrawerHelperClient.drawSolidRect(ctx, ox + px * p, oy + py * p, p, p, color)
        }
        dot(1f, 1f)
        dot(6f, 1f)
        dot(2f, 2f)
        dot(5f, 2f)
        dot(3f, 3f)
        dot(4f, 3f)
        dot(3f, 4f)
        dot(4f, 4f)
        dot(2f, 5f)
        dot(5f, 5f)
        dot(1f, 6f)
        dot(6f, 6f)
    }

    /** 重置:↺ 回转环(四边开角方环,右上开口处一枚上指箭头),文本色。 */
    @JvmStatic
    fun reset(): IGuiTexture = pixelGlyph(
        MachineUiComponentStyle.textNormal,
        floatArrayOf(4f, 3f, 5f, 1f),
        floatArrayOf(3f, 4f, 1f, 6f),
        floatArrayOf(4f, 10f, 6f, 1f),
        floatArrayOf(10f, 6f, 1f, 4f),
        floatArrayOf(10f, 4f, 1f, 1f),
        floatArrayOf(9f, 5f, 3f, 1f),
    )

    /**
     * 全局池 UNIVERSAL:外环 + 十字(四面连通),语义为「加入每一个编号池」。
     */
    @JvmStatic
    fun universal(): IGuiTexture = pixelGlyph(
        MachineUiComponentStyle.textNormal,
        floatArrayOf(4f, 2f, 6f, 1f),
        floatArrayOf(4f, 11f, 6f, 1f),
        floatArrayOf(2f, 4f, 1f, 6f),
        floatArrayOf(11f, 4f, 1f, 6f),
        floatArrayOf(3f, 3f, 1f, 1f),
        floatArrayOf(10f, 3f, 1f, 1f),
        floatArrayOf(3f, 10f, 1f, 1f),
        floatArrayOf(10f, 10f, 1f, 1f),
        floatArrayOf(6f, 4f, 2f, 6f),
        floatArrayOf(4f, 6f, 6f, 2f),
    )

    /** 禁止:⊘ 空环加对角线(旧版 drawDisableGlyph 原样移植),错误红示警。 */
    @JvmStatic
    fun forbidden(): IGuiTexture = pixelGlyph(
        MachineUiComponentStyle.ledError,
        floatArrayOf(3f, 3f, 8f, 1f),
        floatArrayOf(3f, 10f, 8f, 1f),
        floatArrayOf(3f, 4f, 1f, 6f),
        floatArrayOf(10f, 4f, 1f, 6f),
        floatArrayOf(3f, 3f, 1f, 1f),
        floatArrayOf(4f, 4f, 1f, 1f),
        floatArrayOf(5f, 5f, 1f, 1f),
        floatArrayOf(6f, 6f, 1f, 1f),
        floatArrayOf(7f, 7f, 1f, 1f),
        floatArrayOf(8f, 8f, 1f, 1f),
        floatArrayOf(9f, 9f, 1f, 1f),
        floatArrayOf(10f, 10f, 1f, 1f),
    )

    /** 时钟:◷ 八边圆环表盘 + 12 点/3 点等长指针(直角两臂各 3 格,距环各留 1 格),配方时长字形,LED 运行色(与秒数值同色)。 */
    @JvmStatic
    fun clock(): IGuiTexture = pixelGlyph(
        MachineUiComponentStyle.ledRunning,
        floatArrayOf(5f, 3f, 4f, 1f),
        floatArrayOf(5f, 10f, 4f, 1f),
        floatArrayOf(3f, 5f, 1f, 4f),
        floatArrayOf(10f, 5f, 1f, 4f),
        floatArrayOf(4f, 4f, 1f, 1f),
        floatArrayOf(9f, 4f, 1f, 1f),
        floatArrayOf(4f, 9f, 1f, 1f),
        floatArrayOf(9f, 9f, 1f, 1f),
        floatArrayOf(6f, 5f, 1f, 3f),
        floatArrayOf(7f, 7f, 2f, 1f),
    )

    // ── 2b. IO 方向图标(实心三角,旧版 MachineUiIcons.arrow 的逐行宽度 2,3,4,5,4,3,2 移植;
    //        输入右指 ioInsert 蓝、输出左指 ioExtract 橙,双向为对指双三角) ──────────────

    /** 输入:▶ 右指实心三角(旧版 inputArrow 形状),输入语义蓝。 */
    @JvmStatic
    fun ioInput(): IGuiTexture = pixelGlyph(
        MachineUiComponentStyle.ioInsert,
        floatArrayOf(4f, 4f, 2f, 1f),
        floatArrayOf(4f, 5f, 3f, 1f),
        floatArrayOf(4f, 6f, 4f, 1f),
        floatArrayOf(4f, 7f, 5f, 1f),
        floatArrayOf(4f, 8f, 4f, 1f),
        floatArrayOf(4f, 9f, 3f, 1f),
        floatArrayOf(4f, 10f, 2f, 1f),
    )

    /** 输出:◀ 左指实心三角(旧版 outputArrow 形状),输出语义橙。 */
    @JvmStatic
    fun ioOutput(): IGuiTexture = pixelGlyph(
        MachineUiComponentStyle.ioExtract,
        floatArrayOf(8f, 4f, 2f, 1f),
        floatArrayOf(7f, 5f, 3f, 1f),
        floatArrayOf(6f, 6f, 4f, 1f),
        floatArrayOf(5f, 7f, 5f, 1f),
        floatArrayOf(6f, 8f, 4f, 1f),
        floatArrayOf(7f, 9f, 3f, 1f),
        floatArrayOf(8f, 10f, 2f, 1f),
    )

    /** 双向(存取):▶◀ 对指双三角,左蓝右橙。 */
    @JvmStatic
    fun ioBoth(): IGuiTexture = GuiTextureGroup(
        pixelGlyph(
            MachineUiComponentStyle.ioInsert,
            floatArrayOf(3f, 5f, 1f, 5f),
            floatArrayOf(4f, 6f, 1f, 3f),
            floatArrayOf(5f, 7f, 1f, 1f),
        ),
        pixelGlyph(
            MachineUiComponentStyle.ioExtract,
            floatArrayOf(10f, 5f, 1f, 5f),
            floatArrayOf(9f, 6f, 1f, 3f),
            floatArrayOf(8f, 7f, 1f, 1f),
        ),
    )

    // ── 3. 资源图标注册表(按类型分发;side-IO 配置卡、JEI 行图标等共用) ──────

    private val REGISTRY:
        FreezableStrategyRegistry<MachineResourceType<*>, MemoizedIcon, MemoizedIcon> =
        FreezableStrategyRegistry.create("machine resource UI icons")

    @JvmStatic
    fun register(resourceType: MachineResourceType<*>, icon: Supplier<IGuiTexture>) {
        val handle = MemoizedIcon(icon)
        REGISTRY.register(resourceType, handle, handle)
    }

    @JvmStatic
    fun icon(resourceType: MachineResourceType<*>): IGuiTexture? = REGISTRY.get(resourceType)?.resolve()

    @JvmStatic
    fun iconOr(resourceType: MachineResourceType<*>, fallback: IGuiTexture): IGuiTexture = icon(resourceType) ?: fallback

    @JvmStatic
    fun freeze() = REGISTRY.freeze()

    /** 首查解析后记忆;查询只发生在两逻辑侧的 UI 路径(主线程),无并发要求。 */
    private class MemoizedIcon(private val supplier: Supplier<IGuiTexture>) {
        private var resolved: IGuiTexture? = null

        fun resolve(): IGuiTexture {
            var icon = resolved
            if (icon == null) {
                icon = requireNotNull(supplier.get()) { "resource icon" }
                resolved = icon
            }
            return icon
        }
    }
}
