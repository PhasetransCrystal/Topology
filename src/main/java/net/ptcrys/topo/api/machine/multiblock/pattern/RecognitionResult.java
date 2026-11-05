package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Strongly typed result of a structure recognition pass. It is <b>block-entity-free</b>: members are
 * carried as {@link BlockPos} handles, so the result can cross threads safely. The controller
 * re-resolves the live block entities by position on the server thread when it aggregates.
 *
 * <p>
 * Million-cell discipline: the result carries <b>no per-cell collections of the footprint</b>.
 * Mismatches are a bounded sample ({@link StructureEngine#MISSING_SAMPLE_CAP}) plus the total
 * {@link #missingCount()}; the footprint itself is a pure function of (blueprint, orientation,
 * {@link #repeats()}, controller position) and is re-derived arithmetically by consumers
 * ({@code CompiledBlueprint.cellIndexOfWorld}) instead of being materialized.
 *
 * @param formed        whether every cell matched and every capability count requirement is met
 * @param matchedCount  number of cells that matched
 * @param missingCount  total number of cells that failed to match (the sample below is bounded)
 * @param unloadedCount number of footprint cells inside unloaded chunks; unknown, not missing
 * @param missingCells  a bounded sample of the cells that failed to match (diagnosis rows)
 * @param roleCounts    how many matched cells exposed each part capability
 * @param members       world positions of matched cells backed by a machine member (a part)
 * @param modules       per-named-module match report; optional modules do not affect {@link #formed()}
 * @param repeats       the per-segment repeat counts chosen for this pass (reused by build/export)
 * @param failureReason typed engine-level refusal classification; {@link FailureReason#NONE} for
 *                      every result whose pass actually ran ({@link #formed()} semantics unchanged)
 */
public record RecognitionResult(
                                boolean formed,
                                int matchedCount,
                                int missingCount,
                                int unloadedCount,
                                List<MissingCell> missingCells,
                                Map<PartRole, Integer> roleCounts,
                                List<BlockPos> members,
                                Map<String, ModuleReport> modules,
                                RepeatResolution repeats,
                                FailureReason failureReason) {

    public RecognitionResult {
        if (matchedCount < 0) {
            throw new IllegalArgumentException("Matched cell count must be non-negative");
        }
        if (missingCount < 0) {
            throw new IllegalArgumentException("Missing cell count must be non-negative");
        }
        if (unloadedCount < 0) {
            throw new IllegalArgumentException("Unloaded cell count must be non-negative");
        }
        missingCells = List.copyOf(Objects.requireNonNull(missingCells, "missing cells"));
        if (missingCells.size() > missingCount) {
            throw new IllegalArgumentException("Missing sample cannot exceed the missing count");
        }
        roleCounts = Map.copyOf(Objects.requireNonNull(roleCounts, "capability counts"));
        members = List.copyOf(Objects.requireNonNull(members, "members"));
        modules = Map.copyOf(Objects.requireNonNull(modules, "module reports"));
        repeats = Objects.requireNonNull(repeats, "repeat resolution");
        failureReason = Objects.requireNonNull(failureReason, "failure reason");
        if (formed && failureReason != FailureReason.NONE) {
            throw new IllegalArgumentException("A formed result cannot carry an engine failure reason");
        }
    }

    /**
     * An empty unformed result for a recognition entry refusal: no cell was visited, so members and
     * counts are empty and the reason is {@link FailureReason#ORIENTATION_UNSUPPORTED}.
     */
    public static RecognitionResult orientationUnsupported(RepeatResolution repeats) {
        return new RecognitionResult(
                false,
                0,
                0,
                0,
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                repeats,
                FailureReason.ORIENTATION_UNSUPPORTED);
    }

    /** Match status of one named blueprint module. */
    public enum ModuleState {
        ABSENT,
        PARTIAL,
        SATISFIED
    }

    /** Per-module result: required modules gate formation; optional modules are reported only. */
    public record ModuleReport(String id, boolean required, int matched, int total) {

        public ModuleReport {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Module id must not be blank");
            }
            if (matched < 0 || total < 0 || matched > total) {
                throw new IllegalArgumentException("Invalid module match counts: " + matched + "/" + total);
            }
        }

        public boolean present() {
            return matched > 0;
        }

        public boolean satisfied() {
            return total > 0 && matched == total;
        }

        public ModuleState state() {
            if (total == 0 || matched == 0) {
                return ModuleState.ABSENT;
            }
            return matched == total ? ModuleState.SATISFIED : ModuleState.PARTIAL;
        }
    }

    /**
     * Engine-level refusal classification, additive to {@link #formed()}: a reason other than
     * {@link #NONE} means the pass never visited a cell, while {@link #NONE} means the outcome is
     * fully described by {@link #formed()}, {@link #missingCount()} and {@link #roleCounts()}.
     */
    public enum FailureReason {
        /** No engine-level refusal; the recognition pass ran to completion. */
        NONE,
        /**
         * Some {@link PropertyRule} of a state-pinning blueprint cell cannot represent the requested
         * {@link Orientation} ({@link PropertyRule#supports(Orientation)} returned {@code false}), so
         * the pass was refused before comparing any cell.
         */
        ORIENTATION_UNSUPPORTED,
        /**
         * At least one footprint cell lies in an unloaded chunk. The structure is unknown, not
         * broken; callers must retain the current formation state and retry later.
         */
        CHUNK_UNLOADED
    }

    public record MissingCell(
                              BlockPos localPos,
                              BlockPos worldPos,
                              Cell expected,
                              @Nullable BlockState actual) {

        public MissingCell {
            localPos = Objects.requireNonNull(localPos, "local position").immutable();
            worldPos = Objects.requireNonNull(worldPos, "world position").immutable();
            expected = Objects.requireNonNull(expected, "expected cell");
        }
    }
}
