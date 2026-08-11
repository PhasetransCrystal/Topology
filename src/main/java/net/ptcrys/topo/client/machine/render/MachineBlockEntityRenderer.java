package net.ptcrys.topo.client.machine.render;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.component.ServiceMatch;
import net.ptcrys.topo.api.machine.component.render.MachineRenderComponent;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Single block-entity renderer shared by every machine. It discovers render-contributing traits through
 * {@link MachineRenderComponent#CAPABILITY}, asks each trait to build its own {@link MachineRenderState},
 * and delegates extract/submit to them.
 */
public final class MachineBlockEntityRenderer
                                              implements BlockEntityRenderer<MachineBlockEntity, MachineAggregateRenderState> {

    public MachineBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public MachineAggregateRenderState createRenderState() {
        return new MachineAggregateRenderState();
    }

    @Override
    public void extractRenderState(
                                   MachineBlockEntity blockEntity,
                                   MachineAggregateRenderState renderState,
                                   float partialTick,
                                   Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(
                blockEntity, renderState, partialTick, cameraPos, crumblingOverlay);

        // Cached: this runs every frame for every visible machine; the render-trait set is immutable.
        List<ServiceMatch<MachineRenderComponent<?>>> matches = blockEntity.machineComponents().servicesCached(MachineRenderComponent.KEY);
        // The shared renderer runs for every machine. One with no render traits, or whose client data
        // has not received its sync baseline yet, simply draws nothing this frame.
        if (matches.isEmpty() || !blockEntity.data().isBusinessReady()) {
            renderState.childKeys.clear();
            renderState.children.clear();
            return;
        }
        rebuildChildrenIfNeeded(renderState, matches);

        for (MachineRenderState<?> child : renderState.children) {
            child.lightCoords = renderState.lightCoords;
            child.capture();
        }
    }

    @Override
    public void submit(
                       MachineAggregateRenderState renderState,
                       PoseStack poseStack,
                       SubmitNodeCollector collector,
                       CameraRenderState cameraState) {
        for (MachineRenderState<?> child : renderState.children) {
            child.submit(poseStack, collector, cameraState);
        }
    }

    private static void rebuildChildrenIfNeeded(
                                                MachineAggregateRenderState renderState,
                                                List<ServiceMatch<MachineRenderComponent<?>>> matches) {
        if (childrenMatch(renderState, matches)) {
            return;
        }
        renderState.childKeys.clear();
        renderState.children.clear();
        for (ServiceMatch<MachineRenderComponent<?>> match : matches) {
            renderState.childKeys.add(match.key());
            renderState.children.add(match.value().createRenderState());
        }
    }

    private static boolean childrenMatch(
                                         MachineAggregateRenderState renderState,
                                         List<ServiceMatch<MachineRenderComponent<?>>> matches) {
        if (renderState.childKeys.size() != matches.size()) {
            return false;
        }
        for (int i = 0; i < matches.size(); i++) {
            if (!renderState.childKeys.get(i).equals(matches.get(i).key())) {
                return false;
            }
        }
        return true;
    }
}
