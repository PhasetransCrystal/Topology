package net.ptcrys.topo.datav2.ore.common.display

import net.ptcrys.topo.apiv2.ore.OreVein
import net.ptcrys.topo.apiv2.ore.display.OreVeinDisplay

import com.lowdragmc.lowdraglib2.gui.ui.UIElement

/** Preview plug for deterministic grid veins. */
object GridVeinDisplay : OreVeinDisplay.Strategy {
    override fun buildPreview(vein: OreVein): UIElement = OreVeinPanels.build(vein)
}
