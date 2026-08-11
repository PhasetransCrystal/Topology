package net.ptcrys.topo.integration.jade;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIClientAccess;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import snownee.jade.api.ui.Element;

/** Adapts an LDLib2 {@link UIElement} tree into one Jade tooltip row. */
public final class LDLibTooltipElement extends Element {

    private final ModularUI ui;
    private final UIElement root;

    public LDLibTooltipElement(UIElement root, int heightHint) {
        this.root = root;
        net.ptcrys.topo.client.debug.UiPerfProbe.instrumentExternal("jade", root);
        this.ui = ModularUI.of(UI.of(
                root,
                StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)));
        this.ui.init(heightHint, heightHint);
        this.ui.setTickWhileRending(true);
        this.width = Math.max(1, Math.round(root.getSizeWidth()));
        this.height = Math.max(1, Math.round(root.getSizeHeight()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var pose = graphics.pose();
        pose.pushMatrix();
        // ModularUI centers the root inside the init(width, height) space; when the hint is smaller
        // than the content, that centering offset is negative and the tree would draw outside the
        // Jade box. Subtract the root's actual layout position so content always starts at (x, y).
        pose.translate(getX() - root.getPositionX(), getY() - root.getPositionY());
        try {
            ModularUIClientAccess.getWidget(ui).extractRenderState(
                    graphics,
                    Integer.MAX_VALUE,
                    Integer.MAX_VALUE,
                    partialTick);
        } finally {
            pose.popMatrix();
        }
        width = Math.max(1, Math.round(root.getSizeWidth()));
        height = Math.max(1, Math.round(root.getSizeHeight()));
    }

    @Override
    public Component getNarration() {
        return Component.empty();
    }
}
