package net.ptcrys.topodemo.api.equipment.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.ui.tooltip.TopoTooltipUiProvider;

import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

/** Contains display-only elements; client UI construction is deferred until hovering. */
public record ToolHintPanel(LangKey hint) implements TopoTooltipUiProvider {

    @Override
    public UIElement build(ItemStack stack) {
        return new Label().setText(hint.getComponent());
    }
}
