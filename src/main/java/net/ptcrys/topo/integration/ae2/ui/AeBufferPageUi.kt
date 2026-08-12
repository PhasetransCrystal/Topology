package net.ptcrys.topo.integration.ae2.ui

import net.ptcrys.topo.api.machine.component.ComponentContext
import net.ptcrys.topo.api.machine.component.ComponentKey
import net.ptcrys.topo.api.machine.component.ComponentMount
import net.ptcrys.topo.api.machine.component.MachineComponent
import net.ptcrys.topo.api.machine.component.MachineComponents
import net.ptcrys.topo.api.machine.ui.MachineUiComponentStyle
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContainerTemplate
import net.ptcrys.topo.api.machine.ui.MachineUiContribution
import net.ptcrys.topo.api.machine.ui.recipe.RecipeUiLayout
import net.ptcrys.topo.helper.TopoCompactNumber
import net.ptcrys.topo.integration.ae2.AeFluidBufferPort
import net.ptcrys.topo.integration.ae2.AeItemBufferPort
import net.ptcrys.topo.integration.ae2.AeKeyResourceBuffer

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.transfer.fluid.FluidResource
import net.neoforged.neoforge.transfer.item.ItemResource

import com.google.common.primitives.Ints
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.DataBindingBuilder
import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.lowdragmc.lowdraglib2.gui.ui.elements.BindableValue
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.lowdraglib2.utils.FluidHelper
import dev.vfyjxf.taffy.style.TaffyPosition

/**
 * "ME Push Buffer" page for the ME export hatch: the item buffer's sparse non-empty entries as a
 * read-only grid (view index → storage index via nthNonEmptySlot) above the fluid buffer's row.
 * Slots are display-only; the true long counts render as 4-character compact corner overlays
 * (fluids bucket-denominated: "1" = 1000 mB, "1k" = 1000 buckets).
 */
class AeBufferPageUi private constructor(context: ComponentContext<AeBufferPageUi>) : MachineComponent(context) {

    private var itemBuffer: AeKeyResourceBuffer<ItemResource>? = null
    private var fluidBuffer: AeKeyResourceBuffer<FluidResource>? = null

    override fun resolveDependencies(traits: MachineComponents) {
        itemBuffer = traits.require(AeItemBufferPort.AE_ITEM_BUFFER_PORT).buffer()
        fluidBuffer = traits.require(AeFluidBufferPort.AE_FLUID_BUFFER_PORT).buffer()
    }

    override fun collectMachineUi(contribution: MachineUiContribution) {
        val items = itemBuffer ?: return
        val fluids = fluidBuffer ?: return
        contribution.mainPage(
            PAGE_KEY,
            Component.translatable("ui.topo.ae_buffer.page"),
            createPage(items, fluids),
        )
    }

    private fun createPage(items: AeKeyResourceBuffer<ItemResource>, fluids: AeKeyResourceBuffer<FluidResource>): UIElement {
        val root = MachineUiContainerTemplate.createPageColumn(RecipeUiLayout.PLAYER_INVENTORY_WIDTH.toFloat())
        root.setId("topo_ae_buffer_page")
        root.addChild(
            MachineUiContainerTemplate.createSlotGrid(items.maxKinds()) { viewIndex ->
                itemSparseCell(items, viewIndex)
            },
        )
        root.addChild(
            MachineUiContainerTemplate.createSlotGrid(fluids.maxKinds()) { viewIndex ->
                fluidSparseCell(fluids, viewIndex)
            },
        )
        return root
    }

    private fun itemSparseCell(buffer: AeKeyResourceBuffer<ItemResource>, viewIndex: Int): UIElement {
        // Per-cell cache: the bind getter runs every render frame; toStack would otherwise
        // allocate a fresh ItemStack per call even with unchanged contents.
        var lastResource: ItemResource? = null
        var lastAmount: Long = Long.MIN_VALUE
        var lastStack: ItemStack = ItemStack.EMPTY
        val slot = MachineUiComponentTemplate.createItemSlot().apply {
            setId("topo_ae_buffer_item_slot")
            bind(
                DataBindingBuilder.itemStackS2C {
                    val slotIndex = buffer.nthNonEmptySlot(viewIndex)
                    val res = if (slotIndex < 0) null else buffer.getResource(slotIndex)
                    val amt = if (slotIndex < 0) 0L else buffer.getAmountAsLong(slotIndex)
                    if (res == null || res.isEmpty || amt <= 0L) {
                        if (lastStack !== ItemStack.EMPTY) {
                            lastResource = null
                            lastAmount = 0L
                            lastStack = ItemStack.EMPTY
                        }
                    } else if (res != lastResource || amt != lastAmount) {
                        lastResource = res
                        lastAmount = amt
                        // Icon stays count-1: the corner overlay is the count display, and a
                        // real count would add vanilla's own count text on top of it.
                        lastStack = res.toStack(1)
                    }
                    lastStack
                }.build(),
            )
        }
        val amountSync = hiddenLongSyncValue("topo_ae_buffer_amount_sync") {
            val slotIndex = buffer.nthNonEmptySlot(viewIndex)
            if (slotIndex < 0) 0L else buffer.getAmountAsLong(slotIndex)
        }
        return cellWithCountOverlay(slot, amountSync)
    }

    private fun fluidSparseCell(buffer: AeKeyResourceBuffer<FluidResource>, viewIndex: Int): UIElement {
        var lastResource: FluidResource? = null
        var lastAmount: Long = Long.MIN_VALUE
        var lastStack: FluidStack = FluidStack.EMPTY
        // 图标式流体槽:数量统一由 cellWithCountOverlay 的桶计价覆盖层渲染,内置数量文字已屏蔽。
        val slot = MachineUiComponentTemplate.createFluidIconSlot().apply {
            setId("topo_ae_buffer_fluid_slot")
            bind(
                DataBindingBuilder.fluidStackS2C {
                    val slotIndex = buffer.nthNonEmptySlot(viewIndex)
                    val res = if (slotIndex < 0) null else buffer.getResource(slotIndex)
                    val amt = if (slotIndex < 0) 0L else buffer.getAmountAsLong(slotIndex)
                    if (res == null || res.isEmpty || amt <= 0L) {
                        if (lastStack !== FluidStack.EMPTY) {
                            lastResource = null
                            lastAmount = 0L
                            lastStack = FluidStack.EMPTY
                        }
                    } else if (res != lastResource || amt != lastAmount) {
                        lastResource = res
                        lastAmount = amt
                        lastStack = res.toStack(Ints.saturatedCast(amt))
                    }
                    lastStack
                }.build(),
            )
        }
        val amountSync = hiddenLongSyncValue("topo_ae_buffer_amount_sync") {
            val slotIndex = buffer.nthNonEmptySlot(viewIndex)
            if (slotIndex < 0) 0L else buffer.getAmountAsLong(slotIndex)
        }
        attachFluidExactTooltip(slot) { amountSync.value }
        return cellWithCountOverlay(slot, amountSync, format = { TopoCompactNumber.formatCompactBuckets(it) })
    }

    /** [format] renders the corner text: items keep raw counts, fluids are bucket-denominated. */
    private fun cellWithCountOverlay(slot: UIElement, amountSync: BindableValue<Long>, format: (Long) -> String = { TopoCompactNumber.formatCompact(it) }): UIElement = UIElement().apply {
        setId("topo_ae_buffer_slot_cell")
        layout {
            it.width(MachineUiComponentStyle.slotSize.toFloat())
            it.height(MachineUiComponentStyle.slotSize.toFloat())
        }
        addChild(slot)
        addChild(countOverlay(format, amountSync))
        addChild(amountSync)
    }

    /** Typed S2C mirror shared by count overlays and exact-amount tooltips. */
    private fun hiddenLongSyncValue(id: String, getter: () -> Long): BindableValue<Long> = BindableValue(0L).apply {
        setId(id)
        setDisplay(false)
        isAllowHitTest = false
        bind(DataBindingBuilder.longValS2C(getter).build())
    }

    /**
     * 悬浮显示精确量(桶 + mB):内置提示读的是经 int 饱和截断的图标 FluidStack,真值经隐藏
     * long 镜像取。同元素监听器后注册先执行(addFirst),设完提示即 stopLaterPropagation 拦下
     * 内置监听器的覆盖。
     */
    private fun attachFluidExactTooltip(slot: FluidSlot, amountGetter: () -> Long) {
        slot.addEventListener(UIEvents.HOVER_TOOLTIPS) { event ->
            val fluid = slot.value
            if (fluid.isEmpty) return@addEventListener
            event.hoverTooltips = HoverTooltips.create(
                FluidHelper.getDisplayName(fluid),
                Component.literal(TopoCompactNumber.exactBucketsAndMb(amountGetter())),
            )
            event.stopLaterPropagation()
        }
    }

    private fun countOverlay(format: (Long) -> String, amountSync: BindableValue<Long>): Label = Label().apply {
        setId("topo_ae_buffer_count_overlay")
        val updateText: (Long) -> Unit = { amount ->
            setText(if (amount <= 0L) Component.literal("") else Component.literal(format(amount)))
        }
        updateText(amountSync.value)
        amountSync.registerValueListener(updateText)
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

    companion object {
        @JvmField
        val AE_BUFFER_UI: ComponentKey<AeBufferPageUi> =
            ComponentKey.id("ae_buffer_ui", AeBufferPageUi::class.java)

        const val PAGE_KEY: String = "ae_buffer"

        @JvmStatic
        fun mount(): ComponentMount<AeBufferPageUi> = AE_BUFFER_UI.mount { context -> AeBufferPageUi(context) }
    }
}
