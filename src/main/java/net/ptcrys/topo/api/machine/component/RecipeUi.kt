package net.ptcrys.topo.api.machine.component

import net.ptcrys.topo.api.api.lang.TopoApiLang
import net.ptcrys.topo.api.machine.ui.LcdData
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContribution
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.api.machine.ui.recipe.LiveRecipeSlots
import net.ptcrys.topo.api.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.api.machine.ui.recipe.XeiRecipeLookup

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents

import java.util.function.BooleanSupplier
import java.util.function.Function
import java.util.function.Supplier

/** Recipe-machine UI trait bound to one explicit sibling [RecipeLogic] key. */
class RecipeUi private constructor(context: ComponentContext<RecipeUi>, private val recipeLogicKey: ComponentKey<RecipeLogic>, private val gate: RecipePageGate) : MachineComponent(context) {
    private val pageKey: String = context.id().path
    private val statusPanelKey: String = pageKey + "_lcd"
    private val modifiersPanelKey: String = pageKey + "_modifiers"
    private lateinit var logic: RecipeLogic
    private var pageOpen: BooleanSupplier? = null

    override fun resolveDependencies(traits: MachineComponents) {
        logic = traits.require(recipeLogicKey)
        pageOpen = gate.resolve(traits)
    }

    override fun collectMachineUi(contribution: MachineUiContribution) {
        contribution.mainPage(
            pageKey,
            TopoApiLang.UI_RECIPE_PAGE.getComponent(),
            createRecipePage(),
        )

        // Visible on every page: keeps the right column stable across tab switches.
        contribution.rightPanel(
            statusPanelKey,
            TopoApiLang.UI_RECIPE_STATUS.getComponent(),
        ) {
            lcdPanel(minValueWidth = LCD_VALUE_MIN_WIDTH) {
                addBoundEntry(
                    TopoApiLang.UI_RECIPE_STATE.getComponent(),
                    ::stateText,
                    stateLedSupplier(),
                )
                // Progress changes every tick. Pin this hot value so its changing natural width
                // cannot feed a right-aligned fractional width back into repeated layout passes.
                addPinnedBoundEntry(
                    TopoApiLang.UI_RECIPE_PROGRESS.getComponent(),
                    ::progressText,
                    LCD_VALUE_MIN_WIDTH,
                    LcdData.LED_RUNNING,
                )
                addBoundEntry(
                    TopoApiLang.UI_RECIPE_DURATION.getComponent(),
                    ::durationText,
                    LcdData.LED_TEXT,
                )
            }
        }

        val modifiers = machine().machineComponents().services(RecipeModifier.KEY, logic)
        if (modifiers.isNotEmpty()) {
            // rightPanel 正文已是 verticalList；块与块之间用 MODIFIER_BLOCK_GAP。
            contribution.rightPanel(
                modifiersPanelKey,
                TopoApiLang.UI_RECIPE_MODIFIERS.getComponent(),
                gap = MODIFIER_BLOCK_GAP,
                maxWidth = SIDE_PANEL_MAX_WIDTH,
            ) {
                for (match in modifiers) {
                    val modifier = match.value()
                    // 单块内部：标题 + 明细，用列表间距。
                    verticalList(
                        gap = MachineUiComponentStyle.boxAllGap,
                        maxWidth = SIDE_PANEL_MAX_WIDTH,
                        id = "topo_recipe_modifier_block",
                    ) {
                        add(createModifierTitle(modifier.title()))
                        add(modifier.createDetailsUi())
                    }
                }
            }
        }
    }

    private fun createModifierTitle(title: Component): UIElement = Label().apply {
        setId("topo_recipe_modifier_title")
        bind(DataBindingBuilder.componentS2C { title }.build())
        textStyle {
            it.textColor(MachineUiComponentStyle.textMuted)
            it.textShadow(false)
            it.fontSize(MachineUiComponentStyle.lcdFontSize.toFloat())
            it.textWrap(TextWrap.NONE)
            it.adaptiveWidth(true)
            it.adaptiveHeight(true)
            it.textAlignHorizontal(Horizontal.LEFT)
            it.textAlignVertical(Vertical.CENTER)
        }
        layout {
            it.widthAuto()
            it.maxWidth(SIDE_PANEL_MAX_WIDTH)
            it.height(MachineUiComponentStyle.lcdLineHeight.toFloat())
            it.flexShrink(0f)
        }
    }

    private fun createRecipePage(): UIElement {
        val rows = createRecipeRows()
        if (gate.isAlwaysOpen) {
            return rows
        }
        return GatedPage(::pageOpenNow, createGateNotice(gate.closedNotice), rows)
    }

    private fun createRecipeRows(): UIElement {
        val slotCounts = LiveRecipeSlots.forMachine(machine())
        return MachineUiLayout.pageColumn(RecipeUiLayout.PLAYER_INVENTORY_WIDTH.toFloat()) {
            root.setId("topo_recipe_page")
            for (recipeType in logic.recipeTypes()) {
                add(
                    RecipeUiLayout.buildRecipeIoRow(
                        recipeType,
                        slotCounts,
                        { io, index -> LiveRecipeSlots.createMachineSlot(machine(), slotCounts, io, index) },
                        { bar ->
                            bar.bind(DataBindingBuilder.floatValS2C(logic::progressPercent).build())
                            attachRecipeLookup(bar)
                        },
                    ),
                )
            }
        }
    }

    /**
     * 进度条点击查配方(原版熔炉/JEI 惯例):左键经 [XeiRecipeLookup] 打开本机全部配方类型分类页。
     */
    private fun attachRecipeLookup(bar: ProgressBar) {
        bar.addEventListener(UIEvents.MOUSE_DOWN) { event ->
            if (event.button == 0) {
                XeiRecipeLookup.showRecipes(logic.recipeTypes())
            }
        }
        bar.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            if (XeiRecipeLookup.isAvailable()) {
                event.hoverTooltips = HoverTooltips.create(
                    TopoApiLang.UI_RECIPE_SHOW_RECIPES.getComponent(),
                )
            }
        }
    }

    private fun pageOpenNow(): Boolean {
        val open = pageOpen
        return open == null || open.asBoolean
    }

    private fun stateText(): Component = if (logic.workMode() == MachineWorkControl.WorkMode.HALTED) {
        TopoApiLang.UI_RECIPE_STATE_HALTED.getComponent()
    } else {
        logic.state().displayName()
    }

    private fun stateLedSupplier(): Supplier<Int> = Supplier(::stateLed)

    private fun stateLed(): Int {
        if (logic.workMode() == MachineWorkControl.WorkMode.HALTED) {
            return LcdData.LED_WAITING
        }
        return when (logic.state()) {
            RecipeLogic.State.IDLE -> LcdData.LED_IDLE
            RecipeLogic.State.WORKING -> LcdData.LED_RUNNING
            RecipeLogic.State.WAITING_OUTPUT,
            RecipeLogic.State.WAITING_TICK_INPUT_TO_PROCESS,
            RecipeLogic.State.WAITING_TICK_INPUT_TO_START,
            RecipeLogic.State.WAITING_TICK_OUTPUT_TO_START,
            RecipeLogic.State.MISSING_ACTIVE_RECIPE,
            -> LcdData.LED_WAITING
        }
    }

    private fun progressText(): Component {
        if (logic.maxProgress() <= 0) {
            return Component.literal("-")
        }
        return Component.literal("${logic.progress()}/${logic.maxProgress()}")
    }

    private fun durationText(): Component = if (logic.maxProgress() <= 0) {
        Component.literal("-")
    } else {
        Component.literal("${logic.maxProgress()} t")
    }

    /**
     * Declarative availability gate for the live recipe page.
     */
    class RecipePageGate private constructor(private val availability: Function<MachineComponents, BooleanSupplier>, internal val closedNotice: Component) {
        internal val isAlwaysOpen: Boolean
            get() = this === ALWAYS_OPEN

        internal fun resolve(traits: MachineComponents): BooleanSupplier = availability.apply(traits)

        companion object {
            private val ALWAYS_OPEN = RecipePageGate({ BooleanSupplier { true } }, Component.empty())

            @JvmStatic
            fun alwaysOpen(): RecipePageGate = ALWAYS_OPEN

            @JvmStatic
            fun of(availability: Function<MachineComponents, BooleanSupplier>, closedNotice: Component): RecipePageGate = RecipePageGate(availability, closedNotice)
        }
    }

    private class GatedPage(private val open: BooleanSupplier, private val notice: UIElement, private val content: UIElement) : UIElement() {
        init {
            setId("topo_recipe_page_gate")
            MachineUiContainerTemplate.applyPageColumnLayout(
                this,
                RecipeUiLayout.PLAYER_INVENTORY_WIDTH.toFloat(),
            )
            applyOpen(false)
            addChildren(
                BindableValue(false).apply {
                    setId("topo_recipe_page_gate_open")
                    setDisplay(false)
                    registerValueListener(::applyOpen)
                    bind(DataBindingBuilder.boolS2C { open.asBoolean }.build())
                },
                notice,
                content,
            )
        }

        private fun applyOpen(openNow: Boolean) {
            notice.setDisplay(!openNow)
            content.setDisplay(openNow)
        }
    }

    companion object {
        private val LCD_VALUE_MIN_WIDTH =
            "9999/9999".length * 6f * MachineUiComponentStyle.lcdFontSize / 9f

        private const val SIDE_PANEL_MAX_WIDTH = 140f
        private const val MODIFIER_BLOCK_GAP = 10f

        @JvmField
        val RECIPE_UI_1: ComponentKey<RecipeUi> =
            ComponentKey.id("recipe_ui_1", RecipeUi::class.java)

        @JvmStatic
        fun mount(key: ComponentKey<RecipeUi>, recipeLogicKey: ComponentKey<RecipeLogic>): ComponentMount<RecipeUi> = mount(key, recipeLogicKey, RecipePageGate.alwaysOpen())

        @JvmStatic
        fun mount(key: ComponentKey<RecipeUi>, recipeLogicKey: ComponentKey<RecipeLogic>, gate: RecipePageGate): ComponentMount<RecipeUi> = key.mount { context -> RecipeUi(context, recipeLogicKey, gate) }

        private fun createGateNotice(noticeText: Component): UIElement {
            val notice = MachineUiComponentTemplate.createText(
                noticeText,
                MachineUiComponentTemplate.TextLayout.MAX_WIDTH_AUTO_HEIGHT,
                Horizontal.CENTER,
            )
            notice.setId("topo_recipe_page_notice")
            notice.setDisplay(false)
            return notice
        }
    }
}
