package net.ptcrys.topo.apiv2.machine.ui.tooltip

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry
import net.ptcrys.topo.apiv2.machine.ui.MachineUiTooltipTemplate
import net.ptcrys.topo.client.debug.UiPerfProbe

import net.minecraft.world.inventory.tooltip.TooltipComponent
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

import com.lowdragmc.lowdraglib2.gui.ui.UIElement
import com.mojang.logging.LogUtils

import java.util.concurrent.ConcurrentHashMap

/**
 * Owner facade for item tooltip UIs: register an LDLib2 panel provider per item.
 * Client-render-thread only for [componentFor].
 */
object ItemTooltipUis {
    private val LOGGER = LogUtils.getLogger()
    private const val CACHE_CAPACITY = 4

    private val REGISTRY =
        FreezableStrategyRegistry.create<Item, OiTooltipUiProvider, OiTooltipUiProvider>("item-tooltip-uis")

    private val CACHE =
        object : LinkedHashMap<CacheKey, TooltipComponent>(CACHE_CAPACITY * 2, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, TooltipComponent>): Boolean {
                if (size <= CACHE_CAPACITY) {
                    return false
                }
                MachineUiTooltipTemplate.dispose(eldest.value)
                return true
            }
        }

    private val LOGGED_BUILD_FAILURES: MutableSet<Item> = ConcurrentHashMap.newKeySet()

    @JvmStatic
    fun register(item: Item, provider: OiTooltipUiProvider): OiTooltipUiProvider = REGISTRY.register(item, provider, provider)

    @JvmStatic
    fun freeze() {
        REGISTRY.freeze()
    }

    @JvmStatic
    fun find(stack: ItemStack): OiTooltipUiProvider? = REGISTRY.get(stack.item)

    @JvmStatic
    fun componentFor(stack: ItemStack): TooltipComponent? {
        val provider = find(stack) ?: return null
        return try {
            val key = CacheKey(stack.item, provider.cacheKey(stack))
            CACHE[key]?.let { return it }
            val root = provider.build(stack)
            UiPerfProbe.instrumentExternal("tooltip", root)
            val component = MachineUiTooltipTemplate.createTooltipComponent(root)
            CACHE[key] = component
            component
        } catch (exception: Exception) {
            if (LOGGED_BUILD_FAILURES.add(stack.item)) {
                LOGGER.error(
                    "Tooltip UI provider failed for item '{}'; suppressing this panel",
                    stack.item,
                    exception,
                )
            }
            null
        }
    }

    private data class CacheKey(val item: Item, val providerKey: Any)
}
