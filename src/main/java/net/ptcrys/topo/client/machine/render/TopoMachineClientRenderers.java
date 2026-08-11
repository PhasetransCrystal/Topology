package net.ptcrys.topo.client.machine.render;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.machine.Machines;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Installs the single shared {@link MachineBlockEntityRenderer} for every machine. This is the only
 * client wiring the render framework needs; each render trait declares its own state via
 * {@code MachineRenderComponent.createRenderState()}, so there is nothing to register per render.
 */
@EventBusSubscriber(modid = Topology.MODID, value = Dist.CLIENT)
public final class TopoMachineClientRenderers {

    private TopoMachineClientRenderers() {}

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (!Machines.hasSharedBlockEntityType()) {
            return;
        }
        event.registerBlockEntityRenderer(Machines.sharedBlockEntityType(), MachineBlockEntityRenderer::new);
    }
}
