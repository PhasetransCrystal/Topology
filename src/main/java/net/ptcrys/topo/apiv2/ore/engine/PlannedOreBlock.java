package net.ptcrys.topo.apiv2.ore.engine;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;

/** One block the planner decided to place: where, which vein/material/form, and the resolved state. */
public record PlannedOreBlock(
                              BlockPos pos,
                              Identifier veinId,
                              Material material,
                              MaterialForm form,
                              BlockState blockState) {}
