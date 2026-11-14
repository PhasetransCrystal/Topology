package net.ptcrys.topo.client.machine.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;

import java.util.ArrayList;
import java.util.List;

/** Aggregate render state for one machine: the per-trait child states built for this extract. */
public final class MachineAggregateRenderState extends BlockEntityRenderState {

    final List<MachineRenderState<?>> children = new ArrayList<>();
}
