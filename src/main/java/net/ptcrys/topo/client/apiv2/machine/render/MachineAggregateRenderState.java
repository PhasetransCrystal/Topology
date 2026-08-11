package net.ptcrys.topo.client.apiv2.machine.render;

import net.ptcrys.topo.apiv2.machine.component.ComponentKey;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import java.util.ArrayList;
import java.util.List;

/** Aggregate render state for one machine: the per-trait child states plus the keys they were built from. */
public final class MachineAggregateRenderState extends BlockEntityRenderState {

    final List<ComponentKey<?>> childKeys = new ArrayList<>();
    final List<MachineRenderState<?>> children = new ArrayList<>();
}
