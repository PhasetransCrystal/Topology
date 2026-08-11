package net.ptcrys.topo.integration.ae2;

import net.ptcrys.topo.apiv2.machine.Machines;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import appeng.api.AECapabilities;

/**
 * AE2 integration wiring: exposes the {@link AeGridNode} of any machine that mounts it as an
 * in-world grid node host on the shared machine block-entity type. Machines without the trait
 * resolve to {@code null}, so cables simply do not connect to them.
 */
public final class AeIntegration {

    private AeIntegration() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(AeIntegration::onRegisterCapabilities);
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        if (!Machines.hasSharedBlockEntityType()) {
            return;
        }
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                Machines.sharedBlockEntityType(),
                (machine, side) -> machine.machineComponents().optional(AeGridNode.AE_GRID).orElse(null));
    }
}
