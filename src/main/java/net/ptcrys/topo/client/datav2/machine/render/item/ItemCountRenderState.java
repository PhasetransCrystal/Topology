package net.ptcrys.topo.client.datav2.machine.render.item;

import net.ptcrys.topo.client.apiv2.machine.render.MachineRenderState;
import net.ptcrys.topo.datav2.machine.common.component.ItemCountCounterRender;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/** Draws an {@link ItemCountCounterRender}'s total as a number floating on top of the machine. */
public final class ItemCountRenderState extends MachineRenderState<ItemCountCounterRender> {

    private final IntValue count;

    public ItemCountRenderState(ItemCountCounterRender trait) {
        super(trait);
        // Bind the synced field by reference, not by name. Only DataFields can be bound.
        count = bind(trait.count());
    }

    @Override
    protected void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        String text = Integer.toString(count.get());
        poseStack.pushPose();
        poseStack.translate(0.5f, 1.015f, 0.5f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        float scale = 1.0f / 64.0f;
        poseStack.scale(scale, -scale, scale);
        collector.submitText(
                poseStack,
                -text.length() * 3.0f,
                -4.0f,
                Component.literal(text).getVisualOrderText(),
                false,
                Font.DisplayMode.SEE_THROUGH,
                lightCoords,
                0xFFFFFFFF,
                0x66000000,
                0);
        poseStack.popPose();
    }
}
