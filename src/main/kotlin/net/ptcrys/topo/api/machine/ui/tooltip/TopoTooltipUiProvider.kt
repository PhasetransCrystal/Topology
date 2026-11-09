package net.ptcrys.topo.api.machine.ui.tooltip

import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * Builds the LDLib2 display tree shown inside an item's hover tooltip. Functional: registering a
 * static panel is one lambda. Implementations must only use display-only template components.
 */
fun interface TopoTooltipUiProvider {
    /** Build a fresh display tree for this stack. Called on cache miss only. */
    fun build(stack: ItemStack): UIElement

    /**
     * Value-equality cache key: the component is rebuilt when this changes. Default keys on the
     * item instance — right for static per-item panels.
     */
    fun cacheKey(stack: ItemStack): Any = stack.item
}
