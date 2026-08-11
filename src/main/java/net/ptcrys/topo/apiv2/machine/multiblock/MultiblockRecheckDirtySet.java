package net.ptcrys.topo.apiv2.machine.multiblock;

import net.minecraft.core.BlockPos;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Per-level dirty-set of controller positions, consumed by multiblock controllers on their tick.
 * Named for what it is — a set, not a scheduler: it has no timing, ordering, or priority logic; a
 * controller decides for itself when to act on its dirty mark.
 *
 * <p>
 * The set only ever contains controller positions: a cell change wakes exactly the controllers
 * claiming or watching that cell (resolved through the {@link MultiblockClaimIndex} passed by the
 * caller — this class never holds the index; composition stays in the watcher/controller layer). A
 * controller outside any claim/watch index is reached by its on-load dirty mark and the periodic
 * self-check backstop, so unconsumed arbitrary positions can no longer accumulate here.
 *
 * <p>
 * Each dirty mark additionally carries <b>why</b>: a bounded set of the changed cell positions
 * (≤ {@link #CHANGE_NOTE_LIMIT}), so a formed controller can re-test exactly the touched cells
 * instead of the whole footprint. Reason-less marks (load, backstop, freed claims) and overflowing
 * bursts degrade to a full recheck.
 */
public final class MultiblockRecheckDirtySet {

    /** Past this many distinct changed cells per controller, the note degrades to full-recheck. */
    static final int CHANGE_NOTE_LIMIT = 32;

    /** Identity sentinel: the mark demands a full recheck (no usable cell note). */
    private static final Set<BlockPos> FULL_RECHECK = new LinkedHashSet<>();

    private final Map<BlockPos, Set<BlockPos>> dirtyControllers = new LinkedHashMap<>();

    MultiblockRecheckDirtySet() {}

    /** Marks a controller for a full recheck on its next tick. */
    public void markDirty(BlockPos controllerPos) {
        dirtyControllers.put(
                Objects.requireNonNull(controllerPos, "controller position").immutable(), FULL_RECHECK);
    }

    /**
     * Routes a cell change to the controllers claiming or watching that cell, recording the changed
     * position so formed controllers can re-test just that cell. Cells nobody watches are dropped —
     * never stored — which is what bounds this set to live controllers.
     */
    public void markCellChanged(BlockPos changedCell, MultiblockClaimIndex claims) {
        Objects.requireNonNull(changedCell, "changed cell");
        Objects.requireNonNull(claims, "claim index");
        for (BlockPos controller : claims.controllersForCell(changedCell)) {
            Set<BlockPos> note = dirtyControllers.get(controller);
            if (note == FULL_RECHECK) {
                continue;
            }
            if (note == null) {
                note = new LinkedHashSet<>();
                dirtyControllers.put(controller.immutable(), note);
            }
            if (note.size() >= CHANGE_NOTE_LIMIT) {
                dirtyControllers.put(controller.immutable(), FULL_RECHECK);
                continue;
            }
            note.add(changedCell.immutable());
        }
    }

    /** Whether a dirty mark is pending (without consuming it) — used by in-flight async rechecks. */
    public boolean isDirty(BlockPos controllerPos) {
        return dirtyControllers.containsKey(Objects.requireNonNull(controllerPos, "controller position"));
    }

    /**
     * Removes and returns the dirty note for {@code controllerPos}: {@code null} when clean, a
     * {@link Note} otherwise. {@link Note#changedCells()} is null when the mark demands a full
     * recheck (reason-less or overflowed), else the bounded set of changed cell positions.
     */
    public @Nullable Note consume(BlockPos controllerPos) {
        Set<BlockPos> note = dirtyControllers.remove(
                Objects.requireNonNull(controllerPos, "controller position").immutable());
        if (note == null) {
            return null;
        }
        return note == FULL_RECHECK ? Note.FULL : new Note(Set.copyOf(note));
    }

    /** One consumed dirty mark; {@code changedCells == null} means "full recheck required". */
    public record Note(@Nullable Set<BlockPos> changedCells) {

        static final Note FULL = new Note(null);

        /** Whether the note names every changed cell (the targeted re-test fast path applies). */
        public boolean bounded() {
            return changedCells != null;
        }
    }
}
