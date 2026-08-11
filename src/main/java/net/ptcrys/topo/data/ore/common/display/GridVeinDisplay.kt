package net.ptcrys.topo.data.ore.common.display

import net.ptcrys.topo.api.ore.OreVein
import net.ptcrys.topo.api.ore.display.OreVeinDisplay

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/** Preview plug for deterministic grid veins. */
object GridVeinDisplay : OreVeinDisplay.Strategy {
    override fun buildPreview(vein: OreVein): UIElement = OreVeinPanels.build(vein)
}
