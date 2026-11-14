package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.RepeatResolution;

import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * The controller's formed-state holder: the single source of truth for formation status and
 * geometry, so the two cannot diverge. The recheck pipeline writes it; the controller's query API
 * reads it. Targeted invalidation re-derives any cell from {@link FormedShape} arithmetically — no
 * per-cell index exists.
 */
final class MultiblockState {

    /** The orientation/repeats/bounds the formed structure recognized under. */
    record FormedShape(
                       Orientation orientation,
                       RepeatResolution repeats,
                       RecognitionResult.Bounds bounds,
                       Map<String, RepeatResolution> moduleRepeats) {}

    @Nullable
    FormedShape formedShape;
    /** Latest per-module match state; updated even when optional modules change without unforming. */
    Map<String, RecognitionResult.ModuleReport> modules = Map.of();
}
