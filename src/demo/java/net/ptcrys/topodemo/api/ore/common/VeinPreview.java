package net.ptcrys.topodemo.api.ore.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.ore.display.OreVeinDisplay;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;

/** The callback constructs display elements only when the client opens the vein preview. */
public record VeinPreview(MaterialForm iconForm, LangKey height, LangKey composition) implements OreVeinDisplay.Strategy {

    @Override
    public UIElement buildPreview(OreVein vein) {
        UIElement root = new UIElement();
        root.addChild(new Label().setText(vein.displayName()));
        root.addChild(new Label().setText(height.getComponent(vein.minY(), vein.maxY())));
        for (var entry : vein.entries()) {
            root.addChild(MachineUiComponentTemplate.INSTANCE.createItemSlot().setItem(new ItemStack(MaterialHelper.requireItem(entry.material(), iconForm))));
            root.addChild(new Label().setText(composition.getComponent(
                    entry.material().displayName(), entry.weight())));
        }
        return root;
    }
}
