package net.ptcrys.topodemo.api.ore.common;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.ui.MachineUiComponentTemplate;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.ore.display.OreVeinDisplay;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.world.item.ItemStack;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import java.util.stream.Stream;

/** The callback constructs display elements only when the client opens the vein preview. */
public record VeinPreview(MaterialForm iconForm, LangKey height, LangKey composition) implements OreVeinDisplay.Strategy {

    @Override
    public UIElement buildPreview(OreVein vein) {
        UIElement root = new UIElement();
        root.addChild(new Label().setText(vein.displayName()));
        root.addChild(new Label().setText(height.getComponent(vein.minY(), vein.maxY())));
        for (var entry : vein.entries()) {
            ItemStack stack = new ItemStack(MaterialHelper.requireItem(entry.material(), iconForm));
            ItemSlot slot = MachineUiComponentTemplate.INSTANCE.createItemSlot().setItem(stack);
            ItemSlot.JEISupport.recipeIngredient(slot, IngredientIO.OUTPUT, () -> Stream.of(stack));
            ItemSlot.JEISupport.recipeSlot(slot, IngredientIO.OUTPUT, () -> Stream.of(stack));
            root.addChild(slot);
            root.addChild(new Label().setText(composition.getComponent(
                    entry.material().displayName(), entry.weight())));
        }
        return root;
    }
}
