package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure structure engine over the {@link CompiledBlueprint grid-compiled} blueprint: it consumes
 * immutable values and never touches the world, a trait, or a block entity. Both passes walk the
 * symbol-ordinal grid with arithmetic world addressing — nothing is materialized per cell, which is
 * what makes million-cell footprints viable.
 *
 * <ul>
 * <li>{@link #verify} — the formed-structure upkeep check: short-circuits at the first mismatch,
 * tallies only count-requirement capabilities into a scratch array, allocates no collections.
 * <li>{@link #recognize} — the formation/diagnosis pass: full tally, members, greedy repeats, and
 * a <b>bounded</b> missing-cell sample ({@link #MISSING_SAMPLE_CAP}) plus the total count —
 * an empty million-cell footprint reports one integer, not a million records.
 * </ul>
 */
public final class StructureEngine {

    /** Missing-cell sample bound; diagnosis surfaces show ≤10 rows plus a count, so 64 is ample. */
    public static final int MISSING_SAMPLE_CAP = 64;

    private StructureEngine() {}

    /**
     * Recognition entry. Before any cell is visited it pre-checks representability: if any
     * {@link PropertyRule} of a state-pinning blueprint cell cannot represent {@code orientation}
     * ({@link Blueprint#supports(Orientation)}, cached per blueprint &times; orientation), the pass is
     * refused and the result carries
     * {@link RecognitionResult.FailureReason#ORIENTATION_UNSUPPORTED}. A mirrored orientation against
     * a blueprint that forbids mirroring is a caller error and throws.
     */
    public static RecognitionResult recognize(StructureView view, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(view, "structure view");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        if (orientation.mirror() && !blueprint.allowMirror()) {
            throw new IllegalArgumentException("Mirrored orientation is not allowed by this blueprint");
        }
        if (!blueprint.supports(orientation)) {
            return RecognitionResult.orientationUnsupported(blueprint.defaultRepeats());
        }
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        RecognizeTally tally = new RecognizeTally(compiled);
        RepeatResolution repeats = walk(view, compiled, tally);
        boolean formed = tally.missingCount == 0 && countsSatisfied(blueprint, tally.roleCounts);
        return new RecognitionResult(
                formed,
                tally.matchedCount,
                tally.missingCount,
                List.copyOf(tally.missingSample),
                tally.roleCounts,
                List.copyOf(tally.members),
                repeats,
                RecognitionResult.FailureReason.NONE);
    }

    /**
     * Zero-collection maintenance check: short-circuits at the first mismatching cell, tallies only
     * the count-requirement capabilities into a scratch array, and builds no missing-cell list —
     * the formed-structure upkeep path (backstops, targeted escalation, async upkeep). Semantically
     * equivalent to {@code recognize(...).formed()} (enforced by tests); use {@link #recognize}
     * when members or diagnostics are needed (formation transitions).
     */
    public static Verification verify(StructureView view, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(view, "structure view");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        if (orientation.mirror() && !blueprint.allowMirror()) {
            throw new IllegalArgumentException("Mirrored orientation is not allowed by this blueprint");
        }
        if (!blueprint.supports(orientation)) {
            return new Verification(false, blueprint.defaultRepeats(), 0L);
        }
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        VerifyTally tally = new VerifyTally(compiled.countedRoles());
        RepeatResolution repeats = walk(view, compiled, tally);
        if (tally.firstMismatch != 0L) {
            return new Verification(false, repeats, tally.firstMismatch);
        }
        if (!compiled.countedRoles().satisfied(tally.countScratch)) {
            return new Verification(false, repeats, 0L);
        }
        return new Verification(true, repeats, 0L);
    }

    /**
     * The shared grid walk: every segment's required copies, then greedy-maximal optional copies
     * (probe first, tally on acceptance, stop at the first non-matching copy — the same pinned
     * semantics recognition has always had). Returns the chosen repeats. The tally's
     * {@code shortCircuit} decides whether a required-copy mismatch aborts the walk.
     */
    private static RepeatResolution walk(StructureView view, CompiledBlueprint compiled, Tally tally) {
        BlockPos origin = view.controllerPos();
        Orientation orientation = compiled.orientation();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        CompiledBlueprint.CompiledSegment[] segments = compiled.segments();
        int[] counts = new int[segments.length];
        for (int s = 0; s < segments.length; s++) {
            CompiledBlueprint.CompiledSegment segment = segments[s];
            counts[s] = segment.minCount();
            for (int copy = 0; copy < segment.minCount(); copy++) {
                if (!walkCopy(view, compiled, segment, copy, origin, orientation, cursor, tally) && tally.shortCircuit()) {
                    return new RepeatResolution(counts);
                }
            }
            for (int copy = segment.minCount(); copy < segment.maxCount(); copy++) {
                if (!probeCopy(view, compiled, segment, copy, origin, orientation, cursor)) {
                    break;
                }
                walkCopy(view, compiled, segment, copy, origin, orientation, cursor, tally);
                counts[s] = copy + 1;
            }
        }
        return new RepeatResolution(counts);
    }

    /** Visits every non-empty cell of one copy through the tally; false when any cell mismatched. */
    private static boolean walkCopy(
                                    StructureView view,
                                    CompiledBlueprint compiled,
                                    CompiledBlueprint.CompiledSegment segment,
                                    int copy,
                                    BlockPos origin,
                                    Orientation orientation,
                                    BlockPos.MutableBlockPos cursor,
                                    Tally tally) {
        byte[] grid = segment.cells();
        CompiledBlueprint.CompiledDefinition[] definitions = compiled.definitions();
        int width = compiled.width();
        int depth = compiled.depth();
        int localY = segment.baseLocalY() + copy;
        int indexBase = segment.indexBase() + copy * compiled.layerStride();
        boolean allMatched = true;
        for (int gz = 0; gz < depth; gz++) {
            int lz = gz - compiled.controllerZ();
            int rowBase = gz * width;
            for (int gx = 0; gx < width; gx++) {
                byte ordinal = grid[rowBase + gx];
                if (ordinal == CompiledBlueprint.EMPTY) {
                    continue;
                }
                int lx = gx - compiled.controllerX();
                cursor.set(
                        origin.getX() + orientation.transformX(lx, localY, lz),
                        origin.getY() + orientation.transformY(lx, localY, lz),
                        origin.getZ() + orientation.transformZ(lx, localY, lz));
                CompiledBlueprint.CompiledDefinition definition = definitions[ordinal];
                if (matchesCompiled(view, cursor, definition)) {
                    tally.matched(view, cursor, definition);
                } else {
                    allMatched = false;
                    tally.mismatched(view, cursor, definition, lx, localY, lz, indexBase + rowBase + gx);
                    if (tally.shortCircuit()) {
                        return false;
                    }
                }
            }
        }
        return allMatched;
    }

    /** Whether every non-empty cell of one optional copy matches (no tallying). */
    private static boolean probeCopy(
                                     StructureView view,
                                     CompiledBlueprint compiled,
                                     CompiledBlueprint.CompiledSegment segment,
                                     int copy,
                                     BlockPos origin,
                                     Orientation orientation,
                                     BlockPos.MutableBlockPos cursor) {
        byte[] grid = segment.cells();
        CompiledBlueprint.CompiledDefinition[] definitions = compiled.definitions();
        int width = compiled.width();
        int depth = compiled.depth();
        int localY = segment.baseLocalY() + copy;
        for (int gz = 0; gz < depth; gz++) {
            int lz = gz - compiled.controllerZ();
            int rowBase = gz * width;
            for (int gx = 0; gx < width; gx++) {
                byte ordinal = grid[rowBase + gx];
                if (ordinal == CompiledBlueprint.EMPTY) {
                    continue;
                }
                int lx = gx - compiled.controllerX();
                cursor.set(
                        origin.getX() + orientation.transformX(lx, localY, lz),
                        origin.getY() + orientation.transformY(lx, localY, lz),
                        origin.getZ() + orientation.transformZ(lx, localY, lz));
                if (!matchesCompiled(view, cursor, definitions[ordinal])) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The compiled per-cell test: predicate, then a reference scan over the expanded
     * acceptable-state set. Definitions whose expansion overflowed fall back to the legacy compare
     * against the pre-folded oriented expected state (alternates included).
     */
    public static boolean matchesCompiled(
                                          StructureView view, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition) {
        BlockState[] acceptable = definition.acceptable();
        if (acceptable == null) {
            return matches(view, worldPos, definition.prototype(), definition.orientedExpected());
        }
        if (!definition.prototype().predicate().test(view, worldPos, definition.prototype())) {
            return false;
        }
        BlockState actual = view.blockState(worldPos);
        return actual != null && CompiledBlueprint.acceptedState(acceptable, actual);
    }

    /**
     * Pushes a cell's canonical (pre-orientation) expected state forward through its
     * {@code COVARIANT} rules — and only those — for {@code orientation}: {@code FIXED} rules are
     * identity-only (their property is compared verbatim in the canonical frame) and {@code DERIVED}
     * rules' properties are masked at compare time instead. Returns {@code null} when the cell pins no
     * canonical state.
     */
    static @Nullable BlockState orientExpected(Cell cell, Orientation orientation) {
        BlockState expected = cell.expectedState();
        if (expected == null) {
            return null;
        }
        for (PropertyRule rule : cell.rules()) {
            if (rule.classification() == PropertyRule.Classification.COVARIANT) {
                expected = rule.apply(orientation, expected);
            }
        }
        return expected;
    }

    /**
     * Whether {@code worldPos} satisfies {@code cell}: the predicate must accept it, and if the cell
     * pins a canonical state then the world must match the already-oriented expected state — or any
     * {@link PropertyRule#alternateEncodings(BlockState) alternate encoding} of it contributed by
     * the cell's rules — verbatim except for the cell's masked properties. Alternates widen only the
     * expected side: they are derived from the trusted pinned state, never from the world block's
     * (possibly unsettled) neighbour-recomputed properties.
     */
    static boolean matches(
                           StructureView view,
                           BlockPos worldPos,
                           Cell cell,
                           @Nullable BlockState orientedExpected) {
        if (!cell.predicate().test(view, worldPos, cell)) {
            return false;
        }
        if (orientedExpected == null) {
            return true;
        }
        BlockState actual = view.blockState(worldPos);
        if (actual == null) {
            return false;
        }
        if (statesMatch(orientedExpected, actual, cell.maskedProperties())) {
            return true;
        }
        for (PropertyRule rule : cell.rules()) {
            for (BlockState alternate : rule.alternateEncodings(orientedExpected)) {
                if (statesMatch(alternate, actual, cell.maskedProperties())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verbatim state compare except for {@code masked} — the cell's precomputed (at
     * {@code Blueprint.build()}) union of its rules' masked properties, so no set is allocated
     * per compare.
     */
    private static boolean statesMatch(BlockState expected, BlockState actual, Set<Property<?>> masked) {
        if (expected.getBlock() != actual.getBlock()) {
            return false;
        }
        for (Property<?> property : expected.getProperties()) {
            if (masked.contains(property)) {
                continue;
            }
            if (!actual.hasProperty(property) || !expected.getValue(property).equals(actual.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    private static boolean countsSatisfied(Blueprint blueprint, Map<PartRole, Integer> roleCounts) {
        for (Blueprint.CountRequirement requirement : blueprint.countRequirements().values()) {
            int count = roleCounts.getOrDefault(requirement.role(), 0);
            if (!requirement.accepts(count)) {
                return false;
            }
        }
        return true;
    }

    /** Result of {@link #verify}: formed flag, the greedy repeats, first mismatch pos ({@code 0} if n/a). */
    public record Verification(boolean formed, RepeatResolution repeats, long firstMismatchPos) {}

    /** Per-cell visitor of the shared walk; implementations are the two passes' tallies. */
    private sealed interface Tally permits RecognizeTally, VerifyTally {

        void matched(StructureView view, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition);

        void mismatched(
                        StructureView view,
                        BlockPos worldPos,
                        CompiledBlueprint.CompiledDefinition definition,
                        int lx,
                        int ly,
                        int lz,
                        int cellIndex);

        boolean shortCircuit();
    }

    private static final class RecognizeTally implements Tally {

        private final CompiledBlueprint compiled;
        private final List<RecognitionResult.MissingCell> missingSample = new ArrayList<>();
        private final Map<PartRole, Integer> roleCounts = new LinkedHashMap<>();
        private final List<BlockPos> members = new ArrayList<>();
        private int matchedCount;
        private int missingCount;

        private RecognizeTally(CompiledBlueprint compiled) {
            this.compiled = compiled;
        }

        @Override
        public void matched(StructureView view, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition) {
            matchedCount++;
            BlockState actual = view.blockState(worldPos);
            // Block-entity prune: capability/membership side tables only ever hold machine cells.
            if (actual != null && actual.hasBlockEntity()) {
                for (PartRole capability : view.roles(worldPos)) {
                    roleCounts.merge(capability, 1, Integer::sum);
                }
                if (view.isMember(worldPos)) {
                    members.add(worldPos.immutable());
                }
            }
        }

        @Override
        public void mismatched(
                               StructureView view,
                               BlockPos worldPos,
                               CompiledBlueprint.CompiledDefinition definition,
                               int lx,
                               int ly,
                               int lz,
                               int cellIndex) {
            missingCount++;
            if (missingSample.size() < MISSING_SAMPLE_CAP) {
                Cell prototype = definition.prototype();
                missingSample.add(new RecognitionResult.MissingCell(
                        new BlockPos(lx, ly, lz),
                        worldPos.immutable(),
                        new Cell(
                                new BlockPos(lx, ly, lz),
                                prototype.expectedState(),
                                prototype.rules(),
                                prototype.predicate(),
                                prototype.maskedProperties()),
                        view.blockState(worldPos)));
            }
        }

        @Override
        public boolean shortCircuit() {
            return false;
        }
    }

    private static final class VerifyTally implements Tally {

        private final CompiledBlueprint.PartRoleOrdinals counted;
        private final int[] countScratch;
        private long firstMismatch;

        private VerifyTally(CompiledBlueprint.PartRoleOrdinals counted) {
            this.counted = counted;
            this.countScratch = new int[counted.size()];
        }

        @Override
        public void matched(StructureView view, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition) {
            if (counted.size() == 0) {
                return;
            }
            BlockState actual = view.blockState(worldPos);
            if (actual == null || !actual.hasBlockEntity()) {
                return;
            }
            for (PartRole capability : view.roles(worldPos)) {
                int ordinal = counted.ordinalOf(capability);
                if (ordinal >= 0) {
                    countScratch[ordinal]++;
                }
            }
        }

        @Override
        public void mismatched(
                               StructureView view,
                               BlockPos worldPos,
                               CompiledBlueprint.CompiledDefinition definition,
                               int lx,
                               int ly,
                               int lz,
                               int cellIndex) {
            if (firstMismatch == 0L) {
                firstMismatch = worldPos.asLong();
            }
        }

        @Override
        public boolean shortCircuit() {
            return true;
        }
    }
}
