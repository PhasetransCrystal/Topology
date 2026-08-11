package net.ptcrys.topo.apiv2.machine.ui.recipe

import net.ptcrys.topo.apiv2.machine.MachineBlockEntity
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole
import net.ptcrys.topo.apiv2.machine.resource.ResourcePort
import net.ptcrys.topo.apiv2.recipe.RecipePreviewPlan.SlotPlan
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapabilities
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapability
import net.ptcrys.topo.apiv2.recipe.capability.SlottedRecipeCapability

import net.neoforged.neoforge.transfer.resource.Resource

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/**
 * 机器实况槽位的运行期解析：把实况槽位计划里的全局索引落到具体存储端口槽，再派遣回家族
 * capability 的 [SlottedRecipeCapability.createLiveSlotWidget]。
 */
object LiveRecipeSlots {
    /** 一台实况机器的逐家族可见槽位计数（输入/输出），保持 capability 注册序。 */
    @JvmStatic
    fun forMachine(machine: MachineBlockEntity): Map<SlottedRecipeCapability<*, *, *>, SlotPlan> {
        val counts = LinkedHashMap<SlottedRecipeCapability<*, *, *>, SlotPlan>()
        for (capability in RecipeCapabilities.registered()) {
            if (capability !is SlottedRecipeCapability<*, *, *>) {
                continue
            }
            val input = liveVisibleSlots(machine, capability, RecipeRole.INPUT)
            val output = liveVisibleSlots(machine, capability, RecipeRole.OUTPUT)
            if (input > 0 || output > 0) {
                counts[capability] = SlotPlan(input, output)
            }
        }
        return counts
    }

    /** 按计划的全局槽位索引创建一个实况槽 widget（配方页用）。 */
    @JvmStatic
    fun createMachineSlot(machine: MachineBlockEntity, slotCounts: Map<SlottedRecipeCapability<*, *, *>, SlotPlan>, io: RecipeRole, globalIndex: Int): UIElement {
        var remaining = globalIndex
        for ((capability, plan) in slotCounts) {
            val count = plan.count(io)
            if (remaining < count) {
                return createTyped(machine, capability, io, remaining)
            }
            remaining -= count
        }
        throw IllegalArgumentException("Recipe slot index $globalIndex is out of range for $io")
    }

    /**
     * 一个具体存储端口槽的实况 widget（存储页 UI 直接用；配方页走 [createMachineSlot]）。
     */
    @JvmStatic
    @Suppress("UNCHECKED_CAST")
    fun createStorageSlot(storage: ResourcePort<*, *>, slot: Int): UIElement {
        val capability = RecipeCapabilities.slottedFor(storage.resourceType())
        return createFromStorage(
            capability as SlottedRecipeCapability<*, *, Resource>,
            storage as ResourcePort<*, Resource>,
            slot,
        )
    }

    private fun <R : Resource> createTyped(machine: MachineBlockEntity, capability: SlottedRecipeCapability<*, *, R>, io: RecipeRole, localIndex: Int): UIElement {
        var remaining = localIndex
        for (storage in machine.machineComponents().resources().uiSide().ports(capability.resourceType(), io)) {
            val resourceIndexes = storage.resourceIndexCount()
            if (remaining < resourceIndexes) {
                return capability.createLiveSlotWidget(storage, remaining)
            }
            remaining -= resourceIndexes
        }
        throw IllegalArgumentException(
            "No visible resource storage slot $localIndex for ${capability.resourceType().id()} / $io",
        )
    }

    private fun <R : Resource> liveVisibleSlots(machine: MachineBlockEntity, capability: SlottedRecipeCapability<*, *, R>, io: RecipeRole): Int {
        var slots = 0
        for (storage in machine.machineComponents().resources().uiSide().ports(capability.resourceType(), io)) {
            slots += storage.resourceIndexCount()
        }
        return slots
    }

    private fun <R : Resource> createFromStorage(capability: SlottedRecipeCapability<*, *, R>, storage: ResourcePort<*, R>, slot: Int): UIElement = capability.createLiveSlotWidget(storage, slot)
}
