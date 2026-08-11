package net.ptcrys.topo.integration.ae2.ui

import net.ptcrys.topo.apiv2.machine.component.ComponentContext
import net.ptcrys.topo.apiv2.machine.component.ComponentKey
import net.ptcrys.topo.apiv2.machine.component.ComponentMount
import net.ptcrys.topo.apiv2.machine.component.MachineComponent
import net.ptcrys.topo.apiv2.machine.component.MachineComponents
import net.ptcrys.topo.apiv2.machine.ui.AmountEditorPopup
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.apiv2.machine.ui.MachineUiContribution
import net.ptcrys.topo.apiv2.machine.ui.MachineUiLayout
import net.ptcrys.topo.apiv2.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.helper.OiCompactNumber
import net.ptcrys.topo.integration.ae2.AeConfigSlot
import net.ptcrys.topo.integration.ae2.AeConfiguredResource

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.transfer.access.ItemAccess
import net.neoforged.neoforge.transfer.fluid.FluidResource
import net.neoforged.neoforge.transfer.item.ItemResource
import net.neoforged.neoforge.transfer.resource.Resource

import com.google.common.primitives.Ints
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEvent
import com.lowdragmc.lowdraglib2.gui.sync.rpc.RPCEventBuilder
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.utils.FluidHelper
import dev.vfyjxf.taffy.style.AlignItems
import dev.vfyjxf.taffy.style.TaffyPosition

/**
 * "ME Config" page for the drawing / direct (stocking) input hatches. Each config slot renders
 * as a vertical composite: a ghost slot on top (left-click with a held stack sets the resource,
 * right-click clears, scroll adjusts the target ±1 / Shift ±10 / Ctrl ×2÷2, middle-click opens
 * [AeAmountEditorPopup]) over a read-only stock slot showing the live cached / network-visible
 * amount. Both cells carry a 4-character compact count overlay; fluid counts are
 * bucket-denominated ("1" = 1000 mB, "1k" = 1000 buckets) while targets/popups stay in mB.
 *
 * Sync: ghost slots use LDLib2's bidirectional stack bindings. Numeric target/stock values use
 * hidden typed S2C mirrors, and target edits use an RPC that returns the server's final value.
 */
class AeConfigPageUi private constructor(context: ComponentContext<AeConfigPageUi>, private val hostKey: ComponentKey<out MachineComponent>) : MachineComponent(context) {

    private var host: AeConfiguredResource<*>? = null

    override fun resolveDependencies(traits: MachineComponents) {
        @Suppress("UNCHECKED_CAST")
        val resolved = traits.require(hostKey as ComponentKey<MachineComponent>)
        host = resolved as? AeConfiguredResource<*>
            ?: error("AE config page host '${hostKey.id()}' does not implement AeConfiguredResource")
    }

    override fun collectMachineUi(contribution: MachineUiContribution) {
        val trait = host ?: return
        contribution.mainPage(
            PAGE_KEY,
            Component.translatable("ui.topo.ae_config.page"),
            createPage(trait),
        )
    }

    private fun createPage(trait: AeConfiguredResource<*>): UIElement {
        val root = MachineUiContainerTemplate.createPageColumn(RecipeUiLayout.PLAYER_INVENTORY_WIDTH.toFloat())
        root.setId("oi_ae_config_page")
        root.addChild(
            MachineUiContainerTemplate.createSlotGrid(
                trait.aeConfigSlotCount(),
                visibleRows = COMPOSITE_VISIBLE_ROWS,
                cellHeight = COMPOSITE_CELL_HEIGHT,
            ) { slot -> compositeSlot(trait, slot) },
        )
        return root
    }

    private fun compositeSlot(trait: AeConfiguredResource<*>, slot: Int): UIElement = when (trait.aeConfigResourceType()) {
        ItemResource::class.java -> itemCompositeSlot(castTrait(trait), slot)
        FluidResource::class.java -> fluidCompositeSlot(castTrait(trait), slot)
        else -> error("Unsupported AE config resource type ${trait.aeConfigResourceType().name}")
    }

    private fun itemCompositeSlot(trait: AeConfiguredResource<ItemResource>, slot: Int): UIElement {
        // Phantom slot (LocalSlot-backed): vanilla container click routing is skipped, JEI ghost
        // drops arrive through the binding's setter.
        val ghost: ItemSlot = MachineUiComponentTemplate.createItemSlot().apply {
            setId("oi_ae_config_item_slot")
            bind(
                DataBindingBuilder.itemStack(
                    { configIconItem(trait.aeConfigSlot(slot)) },
                    { stack -> trait.setAeConfigSlot(slot, itemConfigOf(trait.aeConfigSlot(slot), stack)) },
                ).build(),
            )
            // 槽框由 createItemSlot 统一套用;"虚拟"语义只靠压暗内嵌层。
            slotStyle.slotOverlay(MachineUiComponentStyle.ghostSlotOverlayTexture())
            slotStyle.showSlotOverlayOnlyEmpty(false)
            attachFullItemTooltip(this)
            xeiPhantom()
        }
        val setTargetRpc = registerSetTargetRpc(ghost, trait, slot)
        val targetSync = targetAmountSyncValue(trait, slot)
        val stockSync = hiddenLongSyncValue("oi_ae_stock_amount_sync") { trait.aeStockedAmount(slot) }
        attachItemServerClickHandler(ghost, trait, slot)
        attachClientWheelHandler(ghost, { !ghost.value.isEmpty }, setTargetRpc, targetSync)
        attachMiddleClickAmountEditor(ghost, { !ghost.value.isEmpty }, setTargetRpc, targetSync)
        val stock: ItemSlot = MachineUiComponentTemplate.createItemSlot().apply {
            setId("oi_ae_stock_item_slot")
            bind(
                DataBindingBuilder.itemStackS2C { stockIconItem(trait, slot) }.build(),
            )
            attachFullItemTooltip(this)
        }
        return slotComposite(
            ghost,
            stock,
            targetSync = targetSync,
            stockSync = stockSync,
        )
    }

    private fun fluidCompositeSlot(trait: AeConfiguredResource<FluidResource>, slot: Int): UIElement {
        // 图标式流体槽:数量统一由 cellWithOverlay 的紧凑覆盖层渲染,内置桶单位文字已屏蔽。
        val ghost: FluidSlot = MachineUiComponentTemplate.createFluidIconSlot().apply {
            setId("oi_ae_config_fluid_slot")
            bind(
                DataBindingBuilder.fluidStack(
                    { configIconFluid(trait.aeConfigSlot(slot)) },
                    { stack -> trait.setAeConfigSlot(slot, fluidConfigOf(trait.aeConfigSlot(slot), stack)) },
                ).build(),
            )
            // 槽框 + FluidSlot 反相守卫规避由 createFluidSlot 统一处理;这里只加虚拟槽的压暗内嵌层。
            slotStyle.slotOverlay(MachineUiComponentStyle.ghostSlotOverlayTexture())
            xeiPhantom()
        }
        val setTargetRpc = registerSetTargetRpc(ghost, trait, slot)
        val targetSync = targetAmountSyncValue(trait, slot)
        val stockSync = hiddenLongSyncValue("oi_ae_stock_amount_sync") { trait.aeStockedAmount(slot) }
        attachFluidServerClickHandler(ghost, trait, slot)
        attachClientWheelHandler(ghost, { !ghost.value.isEmpty }, setTargetRpc, targetSync)
        attachMiddleClickAmountEditor(ghost, { !ghost.value.isEmpty }, setTargetRpc, targetSync)
        attachFluidExactTooltip(ghost) { readSyncedTarget(targetSync) }
        val stock: FluidSlot = MachineUiComponentTemplate.createFluidIconSlot().apply {
            setId("oi_ae_stock_fluid_slot")
            bind(
                DataBindingBuilder.fluidStackS2C { stockIconFluid(trait, slot) }.build(),
            )
        }
        attachFluidExactTooltip(stock) { stockSync.value }
        return slotComposite(
            ghost,
            stock,
            targetSync = targetSync,
            stockSync = stockSync,
            format = { OiCompactNumber.formatCompactBuckets(it) },
        )
    }

    /** Left-click with a held stack sets the ghost (cursor untouched); right-click clears. */
    private fun attachItemServerClickHandler(ghost: ItemSlot, trait: AeConfiguredResource<ItemResource>, slot: Int) {
        ghost.addServerEventListener(UIEvents.MOUSE_DOWN) { event ->
            val mui = event.currentElement?.modularUI ?: return@addServerEventListener
            val menu = mui.menu ?: return@addServerEventListener
            val cursor = menu.carried
            when (event.button) {
                0 -> if (!cursor.isEmpty) {
                    trait.setAeConfigSlot(slot, itemConfigOf(trait.aeConfigSlot(slot), cursor))
                }

                1 -> trait.setAeConfigSlot(slot, AeConfigSlot.empty())
            }
        }
    }

    /**
     * Fluid ghost click: probe the held container's fluid through the item fluid capability —
     * pure read, the cursor stack stays untouched. Right-click clears.
     */
    private fun attachFluidServerClickHandler(ghost: FluidSlot, trait: AeConfiguredResource<FluidResource>, slot: Int) {
        ghost.addServerEventListener(UIEvents.MOUSE_DOWN) { event ->
            val mui = event.currentElement?.modularUI ?: return@addServerEventListener
            val menu = mui.menu ?: return@addServerEventListener
            val cursor = menu.carried
            when (event.button) {
                0 -> if (!cursor.isEmpty) {
                    val handler = ItemAccess.forStack(cursor).oneByOne().getCapability(Capabilities.Fluid.ITEM)
                    if (handler != null && handler.size() > 0) {
                        val resource = handler.getResource(0)
                        val amount = handler.getAmountAsLong(0)
                        if (!resource.isEmpty && amount > 0L) {
                            trait.setAeConfigSlot(slot, AeConfigSlot.of(resource, amount.coerceAtLeast(1L)))
                        }
                    }
                }

                1 -> trait.setAeConfigSlot(slot, AeConfigSlot.empty())
            }
        }
    }

    /**
     * Ghost over stock, each 18×18 with its own bottom-right compact count overlay. [format]
     * renders the corner text: items keep raw counts, fluids are bucket-denominated
     * ([OiCompactNumber.formatCompactBuckets], "1" = 1000 mB).
     */
    private fun slotComposite(ghost: UIElement, stock: UIElement, targetSync: BindableValue<Long>, stockSync: BindableValue<Long>, format: (Long) -> String = { OiCompactNumber.formatCompact(it) }): UIElement {
        val ghostCell = cellWithOverlay(ghost, targetSync, "oi_ae_config_target_overlay", format)
        val stockCell = cellWithOverlay(stock, stockSync, "oi_ae_stock_count_overlay", format)
        return MachineUiLayout.column(
            gap = 0f,
            width = MachineUiComponentStyle.slotSize.toFloat(),
            alignItems = AlignItems.CENTER,
            id = "oi_ae_config_composite_slot",
        ) {
            root.layout { it.height(COMPOSITE_CELL_HEIGHT) }
            add(ghostCell)
            add(stockCell)
            add(targetSync)
            add(stockSync)
        }
    }

    private fun cellWithOverlay(slot: UIElement, amountSync: BindableValue<Long>, overlayId: String, format: (Long) -> String): UIElement = UIElement().apply {
        setId("oi_ae_slot_cell")
        layout {
            it.width(MachineUiComponentStyle.slotSize.toFloat())
            it.height(MachineUiComponentStyle.slotSize.toFloat())
        }
        addChild(slot)
        addChild(amountOverlay(overlayId, amountSync, format))
    }

    private fun amountOverlay(id: String, amountSync: BindableValue<Long>, format: (Long) -> String): Label = Label().apply {
        setId(id)
        val updateText: (Long) -> Unit = { amount ->
            setText(if (amount <= 0L) Component.literal("") else Component.literal(format(amount)))
        }
        updateText(amountSync.value)
        amountSync.registerValueListener(updateText)
        // No hit testing: the label must not steal hover from the slot underneath, or the
        // hover overlay flickers with pixel-precise cursor moves.
        isAllowHitTest = false
        layout {
            it.positionType(TaffyPosition.ABSOLUTE)
            it.right(1f)
            it.bottom(1f)
        }
        textStyle {
            it.textColor(MachineUiComponentStyle.textNormal)
            it.textShadow(true)
            it.fontSize(MachineUiComponentStyle.slotCountOverlayFontSize)
            it.adaptiveWidth(true)
            it.adaptiveHeight(true)
        }
    }

    /** Returns the target value re-read after the server applies or rejects the request. */
    private fun <R : Resource> registerSetTargetRpc(ghost: UIElement, trait: AeConfiguredResource<R>, slot: Int): RPCEvent {
        val rpc = RPCEventBuilder.simple(
            Long::class.javaObjectType,
            Long::class.javaObjectType,
        ) { newTarget ->
            val current = trait.aeConfigSlot(slot)
            if (current.configured()) {
                val clamped = (newTarget?.toLong() ?: 1L).coerceAtLeast(1L)
                trait.setAeConfigSlot(slot, AeConfigSlot.of(current.resource()!!, clamped))
            }
            trait.aeConfigSlot(slot).targetAmount()
        }
        ghost.addRPCEvent(rpc)
        return rpc
    }

    /** Client wheel: ±1 plain, ±10 with Shift, ×2/÷2 with Ctrl; sends the final value via RPC. */
    private fun attachClientWheelHandler(ghost: UIElement, configured: () -> Boolean, setTargetRpc: RPCEvent, targetSync: BindableValue<Long>) {
        ghost.addEventListener(UIEvents.MOUSE_WHEEL) { event ->
            if (!configured()) return@addEventListener
            val up = event.deltaY > 0f
            val mc = Minecraft.getInstance()
            val ctrl = mc.hasControlDown()
            val shift = mc.hasShiftDown()
            val currentTarget = readSyncedTarget(targetSync)
            val next: Long = when {
                ctrl -> if (up) saturatingMul(currentTarget, 2L) else (currentTarget / 2L).coerceAtLeast(1L)
                shift -> if (up) saturatingAdd(currentTarget, 10L) else (currentTarget - 10L).coerceAtLeast(1L)
                else -> if (up) saturatingAdd(currentTarget, 1L) else (currentTarget - 1L).coerceAtLeast(1L)
            }
            if (next != currentTarget) {
                requestTargetUpdate(ghost, setTargetRpc, targetSync, next)
            }
            event.stopPropagation()
        }
    }

    /**
     * Middle-click opens the modal amount editor (the shared [AmountEditorPopup] design-system
     * component). Capture-phase MOUSE_DOWN runs before the slot's default handler; CLICK is the
     * fallback when something upstream swallows MOUSE_DOWN. A 200 ms guard stops one press from
     * opening the popup twice.
     */
    private fun attachMiddleClickAmountEditor(ghost: UIElement, configured: () -> Boolean, setTargetRpc: RPCEvent, targetSync: BindableValue<Long>) {
        val lastOpen = LongArray(1)
        val launch: () -> Unit = launch@{
            if (!configured()) return@launch
            val now = System.currentTimeMillis()
            if (now - lastOpen[0] < 200L) return@launch
            lastOpen[0] = now
            AmountEditorPopup.open(
                ghost,
                Component.translatable("ui.topo.ae_amount_popup.title"),
                readSyncedTarget(targetSync),
                1L,
                Long.MAX_VALUE,
            ) { newAmount ->
                requestTargetUpdate(ghost, setTargetRpc, targetSync, newAmount)
            }
        }
        ghost.addEventListener(UIEvents.MOUSE_DOWN, { event ->
            if (event.button == 2) {
                launch()
                event.stopPropagation()
            }
        }, true)
        ghost.addEventListener(UIEvents.CLICK) { event ->
            if (event.button == 2) {
                launch()
                event.stopPropagation()
            }
        }
    }

    /** Sends a target request and applies only the authoritative RPC response to client state. */
    private fun requestTargetUpdate(ghost: UIElement, setTargetRpc: RPCEvent, targetSync: BindableValue<Long>, requested: Long) {
        ghost.sendEvent<Long>(setTargetRpc, { authoritative ->
            targetSync.setValue(authoritative.coerceAtLeast(0L), true)
        }, java.lang.Long.valueOf(requested))
    }

    private fun <R : Resource> targetAmountSyncValue(trait: AeConfiguredResource<R>, slot: Int): BindableValue<Long> = hiddenLongSyncValue("oi_ae_config_target_sync") { trait.aeConfigSlot(slot).targetAmount() }

    /** Hidden typed S2C mirror shared by overlays, tooltips, and target-edit callbacks. */
    private fun hiddenLongSyncValue(id: String, getter: () -> Long): BindableValue<Long> = BindableValue(0L).apply {
        setId(id)
        setDisplay(false)
        isAllowHitTest = false
        bind(DataBindingBuilder.longValS2C(getter).build())
    }

    /**
     * 悬浮显示精确量(桶 + mB):内置提示读的是图标 FluidStack(ghost 恒 1 mB、stock 经 int
     * 饱和截断),真值须经隐藏 long 镜像取。同元素监听器后注册先执行(addFirst),设完提示即
     * [com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent.stopLaterPropagation] 拦下内置监听器的覆盖。
     */
    private fun attachFluidExactTooltip(slot: FluidSlot, amountGetter: () -> Long) {
        slot.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            val fluid = slot.value
            if (fluid.isEmpty) return@addEventListener
            event.hoverTooltips = HoverTooltips.create(
                FluidHelper.getDisplayName(fluid),
                Component.literal(OiCompactNumber.exactBucketsAndMb(amountGetter())),
            )
            event.stopLaterPropagation()
        }
    }

    private fun attachFullItemTooltip(slot: ItemSlot) {
        slot.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            val item = slot.value
            if (!item.isEmpty) {
                val mc = Minecraft.getInstance()
                val tips = Screen.getTooltipFromItem(mc, item)
                event.hoverTooltips = HoverTooltips.create(*tips.toTypedArray()).stack(item)
            }
        }
    }

    private fun readSyncedTarget(sync: BindableValue<Long>): Long = sync.value.coerceAtLeast(1L)

    private fun saturatingAdd(a: Long, b: Long): Long {
        val r = a + b
        return if (((a xor r) and (b xor r)) < 0L) Long.MAX_VALUE else r
    }

    private fun saturatingMul(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val r = a * b
        return if (r / b != a) Long.MAX_VALUE else r
    }

    private fun configIconItem(config: AeConfigSlot<ItemResource>): ItemStack = if (!config.configured()) ItemStack.EMPTY else config.resource()!!.toStack(1)

    private fun stockIconItem(trait: AeConfiguredResource<ItemResource>, slot: Int): ItemStack {
        val config = trait.aeConfigSlot(slot)
        if (!config.configured()) return ItemStack.EMPTY
        if (trait.aeStockedAmount(slot) <= 0L) return ItemStack.EMPTY
        return config.resource()!!.toStack(1)
    }

    private fun configIconFluid(config: AeConfigSlot<FluidResource>): FluidStack = if (!config.configured()) FluidStack.EMPTY else config.resource()!!.toStack(1)

    private fun stockIconFluid(trait: AeConfiguredResource<FluidResource>, slot: Int): FluidStack {
        val config = trait.aeConfigSlot(slot)
        if (!config.configured()) return FluidStack.EMPTY
        val amount = trait.aeStockedAmount(slot)
        if (amount <= 0L) return FluidStack.EMPTY
        return config.resource()!!.toStack(Ints.saturatedCast(amount))
    }

    /** Ghost drop / click set: keep the previous target when re-setting the same resource. */
    private fun itemConfigOf(previous: AeConfigSlot<ItemResource>, stack: ItemStack): AeConfigSlot<ItemResource> {
        if (stack.isEmpty) return AeConfigSlot.empty()
        val newResource = ItemResource.of(stack)
        val newTarget =
            if (previous.configured() && previous.resource() == newResource) {
                previous.targetAmount()
            } else {
                stack.count.toLong().coerceAtLeast(1L)
            }
        return AeConfigSlot.of(newResource, newTarget)
    }

    private fun fluidConfigOf(previous: AeConfigSlot<FluidResource>, stack: FluidStack): AeConfigSlot<FluidResource> {
        if (stack.isEmpty) return AeConfigSlot.empty()
        val newResource = FluidResource.of(stack)
        val newTarget =
            if (previous.configured() && previous.resource() == newResource) {
                previous.targetAmount()
            } else {
                stack.amount.toLong().coerceAtLeast(1L)
            }
        return AeConfigSlot.of(newResource, newTarget)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <R : Resource> castTrait(trait: AeConfiguredResource<*>): AeConfiguredResource<R> = trait as AeConfiguredResource<R>

    companion object {
        @JvmField
        val AE_CONFIG_UI: ComponentKey<AeConfigPageUi> =
            ComponentKey.oi("ae_config_ui", AeConfigPageUi::class.java)

        const val PAGE_KEY: String = "ae_config"

        /** One ghost+stock composite per config slot; ME 配置页 composites are two slots tall. */
        private const val COMPOSITE_VISIBLE_ROWS = 3
        private val COMPOSITE_CELL_HEIGHT = MachineUiComponentStyle.slotSize.toFloat() * 2

        /** [hostKey] must resolve to a trait implementing [AeConfiguredResource]. */
        @JvmStatic
        fun mount(hostKey: ComponentKey<out MachineComponent>): ComponentMount<AeConfigPageUi> = AE_CONFIG_UI.mount { context -> AeConfigPageUi(context, hostKey) }
    }
}
