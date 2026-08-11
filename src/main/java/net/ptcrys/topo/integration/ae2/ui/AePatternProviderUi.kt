package net.ptcrys.topo.integration.ae2.ui

import net.ptcrys.topo.api.machine.component.ComponentContext
import net.ptcrys.topo.api.machine.component.ComponentKey
import net.ptcrys.topo.api.machine.component.ComponentMount
import net.ptcrys.topo.api.machine.component.MachineComponent
import net.ptcrys.topo.api.machine.component.MachineComponents
import net.ptcrys.topo.api.machine.resource.RecipeSearchPoolId
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContribution
import net.ptcrys.topo.api.machine.ui.MachineUiLayout
import net.ptcrys.topo.api.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.integration.ae2.AePatternProvider

import net.minecraft.network.chat.Component

import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents

/**
 * Pattern provider UI: ME Patterns page + name (right) + mode toggles (left).
 *
 * Machine DataFields are syncNone (server persist only). Open-menu chrome follows LDLib2
 * data_bindings.html strictly:
 * - toggles: bidirectional [DataBindingBuilder.bool] on [MachineUiComponentTemplate.ServerToggleButton]
 * - display mirrors: [BindableValue] + S2C + remoteSetter
 */
class AePatternProviderUi private constructor(context: ComponentContext<AePatternProviderUi>) : MachineComponent(context) {

    private var provider: AePatternProvider? = null

    override fun resolveDependencies(traits: MachineComponents) {
        provider = traits.require(AePatternProvider.AE_PATTERN_PROVIDER)
    }

    override fun collectMachineUi(contribution: MachineUiContribution) {
        val trait = provider ?: return
        contribution.mainPage(
            PAGE_KEY,
            Component.translatable("ui.topo.ae_patterns.page"),
            createPatternsPage(trait),
        )
        contribution.leftPanel(
            "topo_ae_pattern_provider_modes",
            Component.translatable("ui.topo.ae_pattern_provider.modes"),
            element = createModesBody(trait),
        )
        contribution.rightPanel(
            "topo_ae_pattern_provider_name",
            Component.translatable("ui.topo.ae_pattern_provider.name"),
            element = createNameBody(trait),
        )
    }

    private fun createPatternsPage(trait: AePatternProvider): UIElement {
        val patterns = trait.patterns()
        // Client mirrors via BindableValue S2C (doc: non-IBindable uses BindableValue + remoteSetter).
        var separatedChrome = false
        var slotPoolIdsChrome = ""
        return MachineUiLayout.pageColumn(RecipeUiLayout.PLAYER_INVENTORY_WIDTH.toFloat()) {
            root.setId("topo_ae_patterns_page")
            add(
                BindableValue(false).apply {
                    setId("topo_ae_pattern_separated_chrome")
                    setDisplay(false)
                    isAllowHitTest = false
                    bind(
                        DataBindingBuilder.boolS2C { trait.separated() }
                            .remoteSetter { value ->
                                separatedChrome = value ?: false
                            }
                            .build(),
                    )
                },
            )
            add(
                BindableValue("").apply {
                    setId("topo_ae_pattern_slot_pool_ids_chrome")
                    setDisplay(false)
                    isAllowHitTest = false
                    bind(
                        DataBindingBuilder.stringS2C { trait.encodeSlotPoolIdsForUi() }
                            .remoteSetter { value ->
                                slotPoolIdsChrome = value ?: ""
                            }
                            .build(),
                    )
                },
            )
            add(
                MachineUiContainerTemplate.createSlotGrid(patterns.size()) { slot ->
                    MachineUiComponentTemplate.createItemSlot().apply {
                        setId("topo_ae_pattern_slot")
                        slotStyle.slotOverlay(PATTERN_SLOT_OVERLAY)
                        slotStyle.showSlotOverlayOnlyEmpty(false)
                        bind(patterns, slot)
                        addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
                            if (!separatedChrome) {
                                return@addEventListener
                            }
                            val poolId = AePatternProvider.decodeSlotPoolId(
                                slotPoolIdsChrome,
                                slot,
                                RecipeSearchPoolId.DEFAULT,
                            ).value()
                            event.hoverTooltips = HoverTooltips.create(
                                Component.translatable(
                                    "ui.topo.ae_pattern_provider.slot_pool",
                                    poolId,
                                ),
                                Component.translatable("ui.topo.search_pool.copy_hint"),
                            )
                        }
                        addEventListener(UIEvents.MOUSE_DOWN) { event ->
                            if (event.button != 2 || !separatedChrome) {
                                return@addEventListener
                            }
                            val poolId = AePatternProvider.decodeSlotPoolId(
                                slotPoolIdsChrome,
                                slot,
                                RecipeSearchPoolId.DEFAULT,
                            ).value()
                            MachineUiComponentTemplate.copyToClipboard(poolId)
                            event.stopPropagation()
                        }
                    }
                },
            )
        }
    }

    private fun createModesBody(trait: AePatternProvider): UIElement {
        val width = MachineUiComponentStyle.nameFieldWidth
        fun modeButton(id: String, titleKey: String, tooltipKey: String, selectedGetter: () -> Boolean, setSelected: (Boolean) -> Unit) = MachineUiComponentTemplate.createServerToggleButton(
            Component.translatable(titleKey),
            { selectedGetter() },
            { value -> setSelected(value) },
            Component.translatable(tooltipKey),
        ).apply {
            setId(id)
            layout {
                it.width(width)
                it.height(MachineUiComponentStyle.controlRowHeight)
                it.flexShrink(0f)
            }
        }
        fun validatedModeButton(id: String, titleKey: String, tooltipKey: String, selectedGetter: () -> Boolean, applySelected: (Boolean) -> Boolean) = MachineUiComponentTemplate.createValidatedServerToggleButton(
            Component.translatable(titleKey),
            { selectedGetter() },
            { value -> applySelected(value) },
            Component.translatable(tooltipKey),
        ).apply {
            setId(id)
            layout {
                it.width(width)
                it.height(MachineUiComponentStyle.controlRowHeight)
                it.flexShrink(0f)
            }
        }
        return MachineUiLayout.column(
            gap = MachineUiComponentStyle.pageColumnGap,
            width = width,
            alignItems = dev.vfyjxf.taffy.style.AlignItems.STRETCH,
            id = "topo_ae_pattern_provider_modes",
        ) {
            add(
                modeButton(
                    "topo_ae_pattern_provider_blocking",
                    "ui.topo.ae_pattern_provider.blocking",
                    "ui.topo.ae_pattern_provider.blocking.tooltip",
                    selectedGetter = { trait.blocking() },
                    setSelected = { trait.setBlocking(it) },
                ),
            )
            add(
                validatedModeButton(
                    "topo_ae_pattern_provider_separated",
                    "ui.topo.ae_pattern_provider.separated",
                    "ui.topo.ae_pattern_provider.separated.tooltip",
                    selectedGetter = { trait.separated() },
                    applySelected = { trait.trySetSeparated(it) },
                ),
            )
        }
    }

    private fun createNameBody(trait: AePatternProvider): UIElement = MachineUiComponentTemplate.createTextField(
        getter = { trait.customName() ?: "" },
        setter = { newValue -> trait.setCustomName(newValue) },
        placeholder = Component.translatable("ui.topo.ae_pattern_provider.name_placeholder"),
        normalizeForDisplay = { it.trim().take(AePatternProvider.MAX_CUSTOM_NAME_LENGTH) },
    ).apply {
        setId("topo_ae_pattern_provider_name_field")
        layout {
            it.width(MachineUiComponentStyle.nameFieldWidth)
            it.height(MachineUiComponentStyle.nameFieldHeight)
        }
    }

    companion object {
        @JvmField
        val AE_PATTERN_PROVIDER_UI: ComponentKey<AePatternProviderUi> =
            ComponentKey.oi("ae_pattern_provider_ui", AePatternProviderUi::class.java)

        const val PAGE_KEY: String = "ae_patterns"

        private val PATTERN_SLOT_OVERLAY =
            SpriteTexture.of("topo:textures/gui/widget/pattern_overlay.png")

        @JvmStatic
        fun mount(): ComponentMount<AePatternProviderUi> = AE_PATTERN_PROVIDER_UI.mount { context -> AePatternProviderUi(context) }
    }
}
