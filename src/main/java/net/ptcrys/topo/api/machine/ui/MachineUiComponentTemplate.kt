package net.ptcrys.topo.api.machine.ui

import net.ptcrys.topo.api.machine.resource.RecipeSearchPoolId

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.Slot

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableUIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils
import dev.vfyjxf.taffy.style.AlignContent
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.FlexDirection
import org.lwjgl.glfw.GLFW

import java.util.Locale
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.Supplier

object MachineUiComponentTemplate {
    internal class SearchPoolDraft(initialValue: String, private val fieldDefault: String, private val submit: (String) -> Unit) {
        var value: String = initialValue
            private set

        fun edit(raw: String) {
            value = raw
        }

        fun commit(): Boolean {
            value = normalizeSearchPoolId(value, fieldDefault)
            if (!RecipeSearchPoolId.isValidConfiguredToken(value)) {
                return false
            }
            submit(value)
            return true
        }
    }

    /**
     * Client-only selected chrome for page tabs / local buttons (not server-bound).
     * For server-authoritative on/off state use [createServerToggleButton] + data bindings.
     */
    class SelectableButton : Button() {
        var selected: Boolean = false
            set(value) {
                field = value
                applyChrome()
            }

        init {
            applyChrome()
            // Width is intentionally outside applyChrome: selected toggles re-run chrome and must
            // not wipe caller-set fixed widths (full-width side-panel toggles).
            layout {
                it.paddingLeft(5f)
                it.paddingRight(5f)
                it.height(MachineUiComponentStyle.buttonHeight)
                it.widthMaxContent()
                it.flexShrink(0f)
                it.alignItems(AlignItems.CENTER)
                it.justifyContent(AlignContent.CENTER)
            }
        }

        private fun applyChrome() {
            style {
                it.background(MachineUiComponentStyle.tabButtonBaseTexture(selected))
            }
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.tabButtonBaseTexture(selected))
                it.hoverTexture(MachineUiComponentStyle.tabButtonHoverTexture(selected))
                it.pressedTexture(MachineUiComponentStyle.tabButtonPressedTexture(selected))
            }
            textStyle {
                it.textColor(if (selected) MachineUiComponentStyle.textSelected else MachineUiComponentStyle.textNormal)
                it.textShadow(true)
                it.adaptiveWidth(true)
                it.adaptiveHeight(true)
                it.textAlignHorizontal(Horizontal.CENTER)
                it.textAlignVertical(Vertical.CENTER)
            }
        }
    }

    /**
     * Server-bound on/off control. Extends [BindableUIElement] so it can use LDLib2
     * [DataBindingBuilder.bool] bidirectional binding (see data_bindings.html). Click flips the
     * local value and notifies observers → C2S; S2C pushes server value into [setValue].
     */
    class ServerToggleButton : BindableUIElement<Boolean>() {
        private var on: Boolean = false
        private val label = Label()

        init {
            layout {
                it.flexDirection(FlexDirection.ROW)
                it.paddingLeft(5f)
                it.paddingRight(5f)
                it.height(MachineUiComponentStyle.buttonHeight)
                it.widthMaxContent()
                it.flexShrink(0f)
                it.alignItems(AlignItems.CENTER)
                it.justifyContent(AlignContent.CENTER)
            }
            label.isAllowHitTest = false
            label.layout {
                it.heightPercent(100f)
                it.marginHorizontal(2f)
            }
            label.textStyle {
                it.textAlignHorizontal(Horizontal.CENTER)
                it.textAlignVertical(Vertical.CENTER)
                it.adaptiveWidth(true)
                it.adaptiveHeight(true)
                it.textShadow(true)
            }
            addChild(label)
            applyChrome()
            addEventListener(UIEvents.MOUSE_DOWN) { event ->
                if (event.button == 0 && isActive) {
                    UISoundUtils.playButtonClickSound()
                    // notify=true → IObservable observers → binding C2S to server setter
                    setValue(!on, true)
                    event.stopPropagation()
                }
            }
        }

        fun setText(title: Component): ServerToggleButton {
            label.setText(title)
            return this
        }

        override fun getValue(): Boolean = on

        override fun setValue(value: Boolean?, notify: Boolean): BindableUIElement<Boolean> {
            val next = value ?: false
            if (next == on) {
                return this
            }
            on = next
            applyChrome()
            if (notify) {
                notifyListeners()
            }
            return this
        }

        private fun applyChrome() {
            style {
                it.background(MachineUiComponentStyle.tabButtonBaseTexture(on))
            }
            label.textStyle {
                it.textColor(if (on) MachineUiComponentStyle.textSelected else MachineUiComponentStyle.textNormal)
            }
        }
    }

    enum class TextLayout {
        /** 文本决定元素宽度，只渲染单行，适合标题、按钮、Tab。 */
        AUTO_WIDTH_1_LINE,

        /** 元素占满父级宽度（父级不会被他撑大），并按该宽度自动增高换行，适合 Column 正文。 */
        MAX_WIDTH_AUTO_HEIGHT,

        /** 元素在 Row 中占用剩余宽度，并按最终宽度自动增高换行。 */
        FLEX_WIDTH_AUTO_HEIGHT,
    }

    @JvmOverloads
    fun createText(title: Component, mode: TextLayout = TextLayout.AUTO_WIDTH_1_LINE, align: Horizontal = Horizontal.LEFT): UIElement = styledLabel(mode, align).apply {
        bind(DataBindingBuilder.componentS2C { title }.build())
    }

    /**
     * [createText] 的纯客户端变体:直接 setText,不经 S2C 数据绑定。绑定式文本只在"服务端同名元素
     * 树推送数据"的机器界面里生效——JEI 预览、诊断侧栏这类纯客户端面板用绑定永远收不到值,
     * 会显示控件的默认占位("Label")。
     */
    @JvmOverloads
    fun createStaticText(title: Component, mode: TextLayout = TextLayout.AUTO_WIDTH_1_LINE, align: Horizontal = Horizontal.LEFT): UIElement = styledLabel(mode, align).apply {
        setText(title)
    }

    private fun styledLabel(mode: TextLayout, align: Horizontal): Label = Label().apply {
        when (mode) {
            TextLayout.AUTO_WIDTH_1_LINE -> {
                textStyle {
                    it.adaptiveWidth(true)
                    it.adaptiveHeight(true)
                    it.textWrap(TextWrap.NONE)
                }
                layout {
                    it.widthAuto()
                    it.flexShrink(0f)
                }
            }

            TextLayout.MAX_WIDTH_AUTO_HEIGHT -> {
                textStyle {
                    it.adaptiveWidth(false)
                    it.adaptiveHeight(true)
                    it.textWrap(TextWrap.WRAP)
                }
                layout {
                    it.widthPercent(100f)
                }
            }

            TextLayout.FLEX_WIDTH_AUTO_HEIGHT -> {
                textStyle {
                    it.adaptiveWidth(false)
                    it.adaptiveHeight(true)
                    it.textWrap(TextWrap.WRAP)
                }
                layout {
                    it.width(0f)
                    it.flexGrow(1f)
                    it.flexShrink(1f)
                }
            }
        }
        textStyle {
            it.textColor(MachineUiComponentStyle.textNormal)
            it.textAlignHorizontal(align)
        }
    }

    @JvmOverloads
    fun createButton(title: Component, selected: Boolean = false): SelectableButton = SelectableButton().apply {
        setText(title)
        style {
            it.tooltips(title)
        }
        this.selected = selected
    }

    /**
     * Server-authoritative on/off toggle per LDLib2 data-bindings doc
     * (https://low-drag-mc.github.io/LowDragMC-Doc/en/ldlib2/ui/preliminary/data_bindings.html):
     *
     * ```
     * new Switch().bind(DataBindingBuilder.bool(() -> bool, v -> bool = v).build());
     * ```
     *
     * Bidirectional binding owns S2C display and C2S writes — no hand-rolled RPCEvent.
     * Use for machine DataBoolean that must persist (search-pool separation, blocking, …).
     */
    @JvmStatic
    @JvmOverloads
    fun createServerToggleButton(title: Component, selectedGetter: BooleanSupplier, setSelected: Consumer<Boolean>, tooltip: Component? = null): ServerToggleButton = ServerToggleButton().apply {
        setText(title)
        if (tooltip != null) {
            style { it.tooltips(tooltip) }
        }
        // First lambda = server→client source; second = client→server apply (doc).
        bind(
            DataBindingBuilder.bool(
                { selectedGetter.asBoolean },
                { value -> setSelected.accept(value) },
            ).build(),
        )
    }

    /**
     * Server-authoritative toggle for a setter that may reject a requested value. The RPC returns
     * the final state so optimistic client chrome cannot remain stale after a rejection.
     */
    @JvmStatic
    @JvmOverloads
    fun createValidatedServerToggleButton(title: Component, selectedGetter: BooleanSupplier, applySelected: java.util.function.Function<Boolean, Boolean>, tooltip: Component? = null): ServerToggleButton {
        val button = ServerToggleButton().apply {
            setText(title)
            if (tooltip != null) {
                style { it.tooltips(tooltip) }
            }
        }
        val setRpc = RPCEventBuilder.simple(
            Boolean::class.javaObjectType,
            Boolean::class.javaObjectType,
        ) { requested -> applySelected.apply(requested) }
        button.addRPCEvent(setRpc)
        button.bind(
            DataBindingBuilder.boolS2C { selectedGetter.asBoolean }
                .remoteSetter { value -> button.setValue(value, false) }
                .build(),
        )
        button.registerValueListener { requested ->
            button.sendEvent<Boolean>(setRpc, { authoritative ->
                button.setValue(authoritative, false)
            }, requested)
        }
        return button
    }

    /**
     * Stable, layout-hidden data-binding anchor for chrome that is not itself [BindableUIElement].
     * The caller must add the returned element to the final UI tree so LDLib2 assigns matching
     * sync ids on the logical server and client.
     */
    @JvmStatic
    fun <T> createS2CMirror(id: String, initialValue: T, binding: DataBindingBuilder<T>, clientSetter: Consumer<T>): BindableValue<T> = BindableValue(initialValue).apply {
        setId(id)
        setDisplay(false)
        registerValueListener { value -> clientSetter.accept(value) }
        bind(binding.build())
    }

    /** Client-only clipboard helper for middle-click copy of pool ids, etc. */
    @JvmStatic
    fun copyToClipboard(text: String) {
        val mc = Minecraft.getInstance()
        mc.keyboardHandler.setClipboard(text)
    }

    /**
     * Machine-level search-pool config: [reset] [UNIVERSAL] [id field]. Valid tokens:
     * DEFAULT, UNIVERSAL, or six [0-9a-z]. On blur, border green/red. [fieldDefault] is the
     * reset target (DEFAULT for input hatches, UNIVERSAL for output-only).
     */
    @JvmStatic
    @JvmOverloads
    fun createSearchPoolConfigPanel(idGetter: Supplier<String>, idSetter: Consumer<String>, resetToFieldDefault: Runnable, setUniversal: Runnable, fieldDefault: String = "DEFAULT"): UIElement {
        val contentWidth = MachineUiComponentStyle.nameFieldWidth
        val fieldHeight = MachineUiComponentStyle.controlRowHeight
        val btnSize = MachineUiComponentStyle.sideIoCellSize

        fun isValidId(raw: String): Boolean = RecipeSearchPoolId.isValidConfiguredToken(raw)

        fun applyValidationBorder(field: TextField, raw: String) {
            field.style {
                it.backgroundTexture(MachineUiComponentStyle.textFieldValidationTexture(isValidId(raw)))
            }
        }

        val idField = TopoTextField().apply {
            setAnyString()
            textFieldStyle {
                it.placeholder(Component.translatable("ui.topo.search_pool.id_placeholder"))
            }
        }
        val submitRpc = RPCEventBuilder.simple(
            String::class.java,
            String::class.java,
        ) { submitted ->
            val normalized = normalizeSearchPoolId(submitted, fieldDefault)
            if (isValidId(normalized)) {
                when (normalized) {
                    fieldDefault -> resetToFieldDefault.run()
                    RecipeSearchPoolId.UNIVERSAL.value() -> setUniversal.run()
                    else -> idSetter.accept(normalized)
                }
            }
            idGetter.get()
        }
        idField.addRPCEvent(submitRpc)

        lateinit var draft: SearchPoolDraft
        fun applyAuthoritativeValue(serverValue: String) {
            draft.edit(serverValue)
            idField.setValue(serverValue, false)
            applyValidationBorder(idField, serverValue)
        }
        fun submitValue(value: String) {
            idField.sendEvent<String>(submitRpc, { serverValue ->
                applyAuthoritativeValue(serverValue)
            }, value)
        }
        draft = SearchPoolDraft(fieldDefault, fieldDefault) { submitted ->
            submitValue(submitted)
        }

        fun commitDraft() {
            draft.edit(idField.value)
            draft.commit()
            idField.setValue(draft.value, false)
            applyValidationBorder(idField, draft.value)
        }

        idField.bind(
            DataBindingBuilder.stringS2C { idGetter.get() }
                .remoteSetter { serverValue ->
                    if (!idField.isFocused) {
                        applyAuthoritativeValue(serverValue)
                    }
                }
                .build(),
        )
        idField.apply {
            setId("topo_search_pool_id_field")
            textFieldStyle {
                it.textColor(MachineUiComponentStyle.textNormal)
                it.cursorColor(MachineUiComponentStyle.textSelected)
                it.textShadow(false)
                it.focusOverlay(MachineUiComponentStyle.textFieldFocusTexture())
            }
            style {
                it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.tooltips(
                    Component.translatable("ui.topo.search_pool.id.tooltip"),
                    Component.translatable("ui.topo.search_pool.copy_hint"),
                )
            }
            layout {
                it.width(0f)
                it.flexGrow(1f)
                it.height(fieldHeight)
                it.flexShrink(1f)
                it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
                it.paddingRight(MachineUiComponentStyle.boxAllPadding)
            }
            addEventListener(UIEvents.BLUR) {
                commitDraft()
            }
            addEventListener(UIEvents.KEY_DOWN) { event ->
                if (event.keyCode == GLFW.GLFW_KEY_ENTER || event.keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    commitDraft()
                    event.stopPropagation()
                }
            }
            addEventListener(UIEvents.MOUSE_DOWN) { event ->
                if (event.button == 2) {
                    copyToClipboard(value)
                    event.stopPropagation()
                }
            }
            addEventListener(UIEvents.TICK) {
                if (!isFocused) {
                    applyValidationBorder(this, value)
                }
            }
        }

        fun squareIconButton(id: String, tooltipKey: String, icon: IGuiTexture, requestedValue: String): Button = Button().apply {
            setId(id)
            noText()
            style {
                it.background(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.tooltips(Component.translatable(tooltipKey))
            }
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
            }
            layout {
                it.width(btnSize)
                it.height(btnSize)
                it.minWidth(btnSize)
                it.minHeight(btnSize)
                it.maxWidth(btnSize)
                it.maxHeight(btnSize)
                it.paddingAll(0f)
                it.flexShrink(0f)
                it.flexGrow(0f)
            }
            addChild(
                UIElement().apply {
                    setId("${id}_glyph")
                    isAllowHitTest = false
                    layout {
                        it.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        it.left(0f)
                        it.top(0f)
                        it.width(btnSize)
                        it.height(btnSize)
                    }
                    style { it.backgroundTexture(icon) }
                },
            )
            setOnClick {
                submitValue(requestedValue)
            }
        }

        val resetButton = squareIconButton(
            "topo_search_pool_reset",
            "ui.topo.search_pool.reset.tooltip",
            MachineUiIcons.reset(),
            fieldDefault,
        )
        val universalButton = squareIconButton(
            "topo_search_pool_universal",
            "ui.topo.search_pool.universal.tooltip",
            MachineUiIcons.universal(),
            RecipeSearchPoolId.UNIVERSAL.value(),
        )

        return UIElement().apply {
            setId("topo_search_pool_config")
            layout {
                it.width(contentWidth)
                it.flexDirection(FlexDirection.ROW)
                it.alignItems(AlignItems.CENTER)
                it.gapColumn(MachineUiComponentStyle.boxAllGap)
                it.flexShrink(0f)
            }
            // Reset | UNIVERSAL | id field
            addChild(resetButton)
            addChild(universalButton)
            addChild(idField)
        }
    }

    /** 方形图标按钮(场景工具栏等):无文字、图标 + 提示,边长取 [MachineUiComponentStyle.sceneToolbarButtonSize]。 */
    fun createIconButton(icon: IGuiTexture, tooltipKey: String, action: Runnable): SelectableButton = SelectableButton().apply {
        noText()
        addPreIcon(icon)
        style {
            it.tooltips(Component.translatable(tooltipKey))
        }
        setOnClick { action.run() }
        layout {
            it.width(MachineUiComponentStyle.sceneToolbarButtonSize)
            it.height(MachineUiComponentStyle.sceneToolbarButtonSize)
            it.paddingLeft(0f)
            it.paddingRight(0f)
            it.flexShrink(0f)
        }
    }

    /**
     * 标量资源条(能量/高级能量/热量):横条用于机器 UI(物品栏上方)与 Jade,纵条用于 JEI 配方左侧。
     * 显示模式由调用方在返回值上选择([ResourceBar.bindStorage] / [ResourceBar.bindLocal] /
     * [ResourceBar.setStaticContent]);填充色属于资源定义,由调用方传入。
     */
    @JvmOverloads
    fun createResourceBar(resourceName: Component, color: Int, orientation: ResourceBar.Orientation, length: Float = ResourceBar.defaultLength(orientation)): ResourceBar = ResourceBar(resourceName, color, orientation, length)

    /**
     * 主题物品槽:统一套用我们的槽位框体(原版 MC `container/slot` sprite,见
     * [MachineUiComponentStyle.realSlotTexture])。**我们 UI 内**的物品槽一律走此工厂,换肤只动样式表;
     * 调用方再按需 [ItemSlot.bind]/挂 slotOverlay/setId。(Jade/JEI 内显示的槽不走此工厂,由各自上下文渲染。)
     */
    fun createItemSlot(): ItemSlot = ItemSlot().apply {
        style { it.backgroundTexture(MachineUiComponentStyle.realSlotTexture()) }
    }

    /**
     * 主题物品槽(绑定真实容器槽位的重载):走 [ItemSlot] 的 Slot 构造器,使 internalSetup 在正确的
     * 绑定槽位上运行(配方实况 IO 槽用,与无参版的 LocalSlot 幻影槽不同)。
     */
    fun createItemSlot(boundSlot: Slot): ItemSlot = ItemSlot(boundSlot).apply {
        style { it.backgroundTexture(MachineUiComponentStyle.realSlotTexture()) }
    }

    /**
     * 主题流体槽:同款槽框 + 规避 LDLib2 26.1 FluidSlot 渲染器的反相守卫
     * (`showSlotOverlayOnlyEmpty() || !empty`,本应是 `empty || !showOnlyEmpty`)——固定
     * showSlotOverlayOnlyEmpty(true),否则非空槽的 slotOverlay 整个不绘制。调用方再 bind/挂 overlay。
     */
    fun createFluidSlot(): FluidSlot = FluidSlot().apply {
        style { it.backgroundTexture(MachineUiComponentStyle.realSlotTexture()) }
        slotStyle.showSlotOverlayOnlyEmpty(true)
    }

    /**
     * 图标式流体槽:在 [createFluidSlot] 主题基础上屏蔽 FluidSlot 内置的右下角桶单位数量文字
     * ("1mB"/"22B")。供调用方自带角落紧凑数量覆盖层([net.ptcrys.topo.helper.TopoCompactNumber] 格式)的
     * 槽位使用——ME 配置页/缓冲页,否则两套数量文字同位重叠。悬浮提示不受影响,仍显示精确数量。
     */
    fun createFluidIconSlot(): FluidSlot = createFluidSlot().apply {
        amountLabel.isVisible = false
    }

    /**
     * 同步文本输入框:服务端 [getter]/[setter] 经数据绑定双向同步,带焦点保护。尺寸与 id 由调用方
     * 设置(侧栏名称框用 [MachineUiComponentStyle.nameFieldWidth]/[MachineUiComponentStyle.nameFieldHeight])。
     *
     * 不让 TextField 直接 bind 的原因——LDLib2 原版绑定 + TextField 有三个叠加缺陷(26.1.2.13 源码核实):
     * 1. 绑定默认双向 CHANGED_PERIODIC,两侧每 tick 轮询;
     * 2. 服务端收包写入不更新变更检测快照(UniqueDirectRef.oldValue 只在轮询时更新),因此每收一次
     *    上行,下一 tick 必把值回声给客户端,哪怕一字未改;
     * 3. TextField 收包走 setValue 全量覆盖:文本整体替换、光标无条件跳末尾、选区清空,无焦点保护。
     * 叠加结果:打字期间回声(约 2-4 tick 延迟)带着旧值/服务端规范化后的值覆盖正在输入的内容——
     * 字符被吃、文本闪回、光标乱跳,带空格的文本几乎打不出来。
     *
     * 我们的修改:
     * - remoteGetter 照常每 tick 轮询文本上行,实时保存不受影响;
     * - remoteSetter 在聚焦编辑期间丢弃下行回声,失焦后才应用(初始值、他人改名仍正常刷新显示);
     * - 失焦瞬间用 [normalizeForDisplay] 本地镜像服务端 setter 的规范化(trim/截断等),显示立即落到
     *   服务端将要保存的值。不能改取 binding 的 syncValue 缓存——快速失焦时它可能还是在途旧值,
     *   回填会经上行轮询覆盖服务端,造成真实丢字。
     *
     * remoteGetter/remoteSetter 见 data_bindings.html:设置后由我们自行负责客户端读写,LDLib2
     * 不再自动 bindDataSource/bindObserver。
     */
    @JvmOverloads
    fun createTextField(getter: () -> String, setter: (String) -> Unit, placeholder: Component? = null, normalizeForDisplay: (String) -> String = { it }): TextField {
        val field = TopoTextField().apply {
            setId("createTextField")
            setAnyString()
            // Match search-pool / amount-editor fields: Topo cell chrome, not LDLib RECT_RD_SOLID.
            textFieldStyle {
                it.textColor(MachineUiComponentStyle.textNormal)
                it.cursorColor(MachineUiComponentStyle.textSelected)
                it.textShadow(false)
                it.focusOverlay(MachineUiComponentStyle.textFieldFocusTexture())
                placeholder?.let { hint -> it.placeholder(hint) }
            }
            style {
                it.backgroundTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
            }
            layout {
                it.paddingLeft(MachineUiComponentStyle.boxAllPadding)
                it.paddingRight(MachineUiComponentStyle.boxAllPadding)
            }
        }
        field.bind(
            DataBindingBuilder.string({ getter() }, { value -> setter(value) })
                .remoteGetter { field.value }
                .remoteSetter { value -> if (!field.isFocused) field.setValue(value, false) }
                .build(),
        )
        field.addEventListener(UIEvents.BLUR) {
            field.setValue(normalizeForDisplay(field.value), false)
        }
        return field
    }

    /**
     * 可点击数量编辑器(设计系统组件,复用 ME 配置页/管道端口屏的数量编辑模式):一个显示当前值的
     * 按钮,左键打开 [AmountEditorPopup] 模态精确编辑(带修饰键步进)。提交值经 RPC 上行到服务端
     * [serverSetter] 权威钳制,服务端值经 [DataBindingBuilder.intValS2C] 每 tick 回显刷新标签。
     * 机器 trait 在 [MachineUiContribution] 里挂载即可,无需自己接线 RPC/同步。
     *
     * @param serverGetter 服务端读当前值(只在服务端求值,经 S2C 下发显示与弹窗初值)
     * @param serverSetter 服务端写新值(RPC 上行回调;组件已先按 [min]/[max] 钳制)
     */
    @JvmStatic
    @JvmOverloads
    fun createAmountEditor(title: Component, serverGetter: () -> Int, serverSetter: (Int) -> Unit, min: Int, max: Int, width: Float = 108f, format: (Int) -> Component = { Component.literal(it.toString()) }): UIElement {
        val label = Label().apply {
            isAllowHitTest = false
            textStyle {
                it.textColor(MachineUiComponentStyle.textNormal)
                it.textShadow(false)
                it.textAlignHorizontal(Horizontal.CENTER)
                it.textAlignVertical(Vertical.CENTER)
                it.adaptiveWidth(false)
                it.adaptiveHeight(false)
            }
            layout {
                it.widthPercent(100f)
                it.heightPercent(100f)
                it.flexShrink(1f)
            }
        }
        var clientValue = min
        fun refresh(value: Int) {
            clientValue = value.coerceIn(min, max)
            label.setText(format(clientValue))
        }
        refresh(clientValue)
        val valueMirror = createS2CMirror(
            "topo_amount_editor_value",
            clientValue,
            DataBindingBuilder.intValS2C { serverGetter() },
        ) { value -> refresh(value) }
        val setRpc = RPCEventBuilder.simple(
            Int::class.javaObjectType,
            Int::class.javaObjectType,
        ) { value ->
            serverSetter(value.coerceIn(min, max))
            serverGetter().coerceIn(min, max)
        }
        return Button().apply {
            setId("topo_amount_editor")
            noText()
            buttonStyle {
                it.baseTexture(MachineUiComponentStyle.sideIoCellBaseTexture())
                it.hoverTexture(MachineUiComponentStyle.sideIoCellHoverTexture())
                it.pressedTexture(MachineUiComponentStyle.sideIoCellPressedTexture())
            }
            layout {
                it.width(width)
                it.height(18f)
                it.paddingAll(0f)
                it.flexShrink(0f)
                it.justifyContent(AlignContent.CENTER)
                it.alignItems(AlignItems.CENTER)
            }
            addChild(label)
            addChild(valueMirror)
            style { it.tooltips(Component.translatable("ui.topo.amount_editor.click_to_edit")) }
            addRPCEvent(setRpc)
            setOnClick { event ->
                if (event.button == 0) {
                    AmountEditorPopup.open(this, title, clientValue.toLong(), min.toLong(), max.toLong()) { committed ->
                        val applied = committed.coerceIn(min.toLong(), max.toLong()).toInt()
                        sendEvent<Int>(setRpc, { authoritative ->
                            valueMirror.setValue(authoritative, true)
                        }, applied)
                    }
                    event.stopPropagation()
                }
            }
        }
    }

    private fun normalizeSearchPoolId(raw: String, fieldDefault: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return fieldDefault
        }
        if (trimmed.equals(RecipeSearchPoolId.DEFAULT.value(), ignoreCase = true)) {
            return RecipeSearchPoolId.DEFAULT.value()
        }
        if (trimmed.equals(RecipeSearchPoolId.UNIVERSAL.value(), ignoreCase = true)) {
            return RecipeSearchPoolId.UNIVERSAL.value()
        }
        return trimmed.lowercase(Locale.ROOT)
    }

    /**
     * 运行时 side-IO 配置网格(6 本地面 + 重置/全禁):仅对 playerConfigurableSides 端口有意义,
     * 由 [net.ptcrys.topo.api.machine.resource.ResourcePort] 在 LEFT 侧栏卡片里挂载。
     */
    fun createSideIoGrid(port: net.ptcrys.topo.api.machine.resource.ResourcePort<*, *>): SideIoConfigGrid = SideIoConfigGrid.create(port)

    /** side-IO 卡的图标标题栏:左 IO 方向箭头 + 右资源图形(悬浮报端口名/trait id)。 */
    fun createSideIoCardTitle(port: net.ptcrys.topo.api.machine.resource.ResourcePort<*, *>): UIElement = SideIoConfigGrid.createTitleBar(port)
}
