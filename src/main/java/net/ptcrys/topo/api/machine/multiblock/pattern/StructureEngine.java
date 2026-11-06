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
 * Pure structure engine over the {@link CompiledBlueprint compiled} blueprint: it consumes
 * immutable values and never touches the world, a trait, or a block entity.
 *
 * <ul>
 * <li>{@link #verify} — the formed-structure upkeep check: short-circuits at the first mismatch,
 * tallies only count-requirement capabilities into a scratch array, allocates no collections.
 * <li>{@link #recognize} — the formation/diagnosis pass: full tally, members, greedy repeats, and a
 * <b>bounded</b> missing-cell sample ({@link #MISSING_SAMPLE_CAP}) plus the total count.
 * </ul>
 *
 * <h2>Count resolution</h2>
 *
 * Bricks are walked in declaration order. A section declared after the controller resolves its
 * copies greedily — required copies first, then optional copies until the first one that does not
 * fully match. Sections declared <b>before</b> the controller also shift the controller itself, so
 * their counts are resolved first (to a fixed point: a later pre-controller section changes the
 * anchor, which can let an earlier one grow further) and the final tally walk then uses the stable
 * layout. This is the only coupled case; the common blueprint (controller declared first, sections
 * above it) resolves in a single pass.
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
            return RecognitionResult.orientationUnsupported(blueprint.minRepeats());
        }
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        RecognizeTally tally = new RecognizeTally();
        RepeatResolution repeats = walk(view, compiled, tally);
        boolean formed = tally.unloadedCount == 0 && tally.missingCount == 0 && countsSatisfied(blueprint, tally.roleCounts);
        return new RecognitionResult(
                formed,
                tally.matchedCount,
                tally.missingCount,
                tally.unloadedCount,
                List.copyOf(tally.missingSample),
                tally.roleCounts,
                List.copyOf(tally.members),
                repeats,
                tally.bounds(),
                tally.unloadedCount > 0 ? RecognitionResult.FailureReason.CHUNK_UNLOADED : RecognitionResult.FailureReason.NONE);
    }

    /**
     * Zero-collection maintenance check: short-circuits at the first mismatching cell, tallies only
     * the count-requirement capabilities into a scratch array, and builds no missing-cell list —
     * the formed-structure upkeep path. Semantically equivalent to {@code recognize(...).formed()}.
     */
    public static Verification verify(StructureView view, Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(view, "structure view");
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        if (orientation.mirror() && !blueprint.allowMirror()) {
            throw new IllegalArgumentException("Mirrored orientation is not allowed by this blueprint");
        }
        if (!blueprint.supports(orientation)) {
            return new Verification(false, blueprint.minRepeats(), 0L, false);
        }
        CompiledBlueprint compiled = blueprint.compiled(orientation);
        VerifyTally tally = new VerifyTally(compiled.countedRoles());
        RepeatResolution repeats = walk(view, compiled, tally);
        if (tally.unloaded) {
            return new Verification(false, repeats, 0L, true);
        }
        if (tally.firstMismatch != 0L) {
            return new Verification(false, repeats, tally.firstMismatch, false);
        }
        if (!compiled.countedRoles().satisfied(tally.countScratch)) {
            return new Verification(false, repeats, 0L, false);
        }
        return new Verification(true, repeats, 0L, false);
    }

    // ------------------------------------------------------------------------------------------
    // Shared walk.
    // ------------------------------------------------------------------------------------------

    private static RepeatResolution walk(StructureView view, CompiledBlueprint compiled, Tally tally) {
        Blueprint blueprint = compiled.blueprint();
        List<Blueprint.Brick> bricks = compiled.bricks();
        int[] counts = minCountsOf(blueprint);
        int controllerBrick = compiled.controllerBrick();

        boolean sectionsBeforeController = false;
        for (int b = 0; b < controllerBrick; b++) {
            if (bricks.get(b).section() >= 0) {
                sectionsBeforeController = true;
                break;
            }
        }
        if (sectionsBeforeController) {
            resolveSectionsBeforeController(view, compiled, counts, tally);
        }

        BlockPos origin = view.controllerPos();
        Orientation orientation = compiled.effectiveOrientation();
        CompiledBlueprint.CompiledDefinition[] definitions = compiled.definitions();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] controllerDecl = blueprint.controllerDecl();
        int[] controllerShift = blueprint.shiftOf(bricks.get(controllerBrick), counts);

        for (int b = 0; b < bricks.size(); b++) {
            Blueprint.Brick brick = bricks.get(b);
            int[] shift = blueprint.shiftOf(brick, counts);
            if (brick.section() < 0) {
                int lx = brick.xs()[0] + shift[0] - controllerDecl[0] - controllerShift[0];
                int ly = brick.ys()[0] + shift[1] - controllerDecl[1] - controllerShift[1];
                int lz = brick.zs()[0] + shift[2] - controllerDecl[2] - controllerShift[2];
                if (!walkCell(view, orientation, origin, cursor, lx, ly, lz, definitions[brick.definitions()[0]], tally) && tally.shortCircuit()) {
                    return new RepeatResolution(counts);
                }
                continue;
            }
            Blueprint.RepeatSection section = blueprint.section(brick.section());
            int required = counts[brick.section()];
            for (int copy = 0; copy < required; copy++) {
                if (!walkSectionCopy(view, compiled, brick, section, copy, shift, controllerDecl, controllerShift, origin, orientation, cursor, tally) && tally.shortCircuit()) {
                    return new RepeatResolution(counts);
                }
            }
            if (b < controllerBrick) {
                continue; // counts of pre-controller sections are already stable
            }
            for (int copy = required; copy < section.maxCount(); copy++) {
                if (!probeSectionCopy(view, compiled, brick, section, copy, shift, controllerDecl, controllerShift, origin, orientation, cursor, tally)) {
                    break;
                }
                if (!probeDependents(view, compiled, brick, section, brick.section(), copy + 1, counts, origin, orientation, cursor, tally)) {
                    break;
                }
                walkSectionCopy(view, compiled, brick, section, copy, shift, controllerDecl, controllerShift, origin, orientation, cursor, tally);
                counts[brick.section()] = copy + 1;
            }
        }
        return new RepeatResolution(counts);
    }

    /**
     * Resolves the counts of sections declared before the controller. Their growth shifts the
     * controller, so the anchor is part of the unknown; iterate until the counts stop changing.
     * Counts only ever increase, so the loop terminates.
     */
    private static void resolveSectionsBeforeController(
                                                        StructureView view,
                                                        CompiledBlueprint compiled,
                                                        int[] counts,
                                                        Tally tally) {
        Blueprint blueprint = compiled.blueprint();
        List<Blueprint.Brick> bricks = compiled.bricks();
        int controllerBrick = compiled.controllerBrick();
        Orientation orientation = compiled.effectiveOrientation();
        BlockPos origin = view.controllerPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int[] controllerDecl = blueprint.controllerDecl();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int b = 0; b < controllerBrick; b++) {
                Blueprint.Brick brick = bricks.get(b);
                if (brick.section() < 0) {
                    continue;
                }
                Blueprint.RepeatSection section = blueprint.section(brick.section());
                int[] shift = blueprint.shiftOf(brick, counts);
                for (int copy = counts[brick.section()]; copy < section.maxCount(); copy++) {
                    counts[brick.section()] = copy + 1;
                    int[] controllerShift = blueprint.shiftOf(bricks.get(controllerBrick), counts);
                    // A section before the controller grows away from it: the new far copy is
                    // always copy 0 of the candidate layout, not the copy just appended.
                    if (probeSectionCopy(view, compiled, brick, section, 0, shift, controllerDecl, controllerShift, origin, orientation, cursor, tally) && probeDependents(view, compiled, brick, section, brick.section(), copy + 1, counts, origin, orientation, cursor, tally)) {
                        changed = true;
                    } else {
                        counts[brick.section()] = copy;
                        break;
                    }
                }
            }
        }
    }

    private static boolean walkCell(
                                    StructureView view,
                                    Orientation orientation,
                                    BlockPos origin,
                                    BlockPos.MutableBlockPos cursor,
                                    int lx,
                                    int ly,
                                    int lz,
                                    CompiledBlueprint.CompiledDefinition definition,
                                    Tally tally) {
        cursor.set(
                origin.getX() + orientation.transformX(lx, ly, lz),
                origin.getY() + orientation.transformY(lx, ly, lz),
                origin.getZ() + orientation.transformZ(lx, ly, lz));
        StructureView.CellInfo info = view.cellInfo(cursor);
        if (info == null) {
            tally.unloaded(cursor);
            return false;
        }
        if (matchesCompiled(info, view, cursor, definition)) {
            tally.matched(info, cursor, definition);
            return true;
        }
        tally.mismatched(info, cursor, new BlockPos(lx, ly, lz), definition);
        return false;
    }

    /** Visits every member of one copy through the tally; false when any cell mismatched. */
    private static boolean walkSectionCopy(
                                           StructureView view,
                                           CompiledBlueprint compiled,
                                           Blueprint.Brick brick,
                                           Blueprint.RepeatSection section,
                                           int copy,
                                           int[] shift,
                                           int[] controllerDecl,
                                           int[] controllerShift,
                                           BlockPos origin,
                                           Orientation orientation,
                                           BlockPos.MutableBlockPos cursor,
                                           Tally tally) {
        CompiledBlueprint.CompiledDefinition[] definitions = compiled.definitions();
        int offset = copy * section.stride();
        boolean allMatched = true;
        for (int k = 0; k < brick.cellCount(); k++) {
            int lx = brick.xs()[k] + shift[0] - controllerDecl[0] - controllerShift[0];
            int ly = brick.ys()[k] + shift[1] - controllerDecl[1] - controllerShift[1] + offset;
            int lz = brick.zs()[k] + shift[2] - controllerDecl[2] - controllerShift[2];
            if (!walkCell(view, orientation, origin, cursor, lx, ly, lz, definitions[brick.definitions()[k]], tally)) {
                allMatched = false;
                if (tally.shortCircuit()) {
                    return false;
                }
            }
        }
        return allMatched;
    }

    /** Whether every cell of one optional copy matches (no tallying). */
    private static boolean probeSectionCopy(
                                            StructureView view,
                                            CompiledBlueprint compiled,
                                            Blueprint.Brick brick,
                                            Blueprint.RepeatSection section,
                                            int copy,
                                            int[] shift,
                                            int[] controllerDecl,
                                            int[] controllerShift,
                                            BlockPos origin,
                                            Orientation orientation,
                                            BlockPos.MutableBlockPos cursor,
                                            Tally tally) {
        CompiledBlueprint.CompiledDefinition[] definitions = compiled.definitions();
        int offset = copy * section.stride();
        for (int k = 0; k < brick.cellCount(); k++) {
            int lx = brick.xs()[k] + shift[0] - controllerDecl[0] - controllerShift[0];
            int ly = brick.ys()[k] + shift[1] - controllerDecl[1] - controllerShift[1] + offset;
            int lz = brick.zs()[k] + shift[2] - controllerDecl[2] - controllerShift[2];
            cursor.set(
                    origin.getX() + orientation.transformX(lx, ly, lz),
                    origin.getY() + orientation.transformY(lx, ly, lz),
                    origin.getZ() + orientation.transformZ(lx, ly, lz));
            StructureView.CellInfo info = view.cellInfo(cursor);
            if (info == null) {
                tally.unloaded(cursor);
                return false;
            }
            if (!matchesCompiled(info, view, cursor, definitions[brick.definitions()[k]])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates the fixed cells that the candidate count would shift: a fixed cap directly beyond
     * the section must still match at its new position. Only cells whose position depends solely on
     * this section (and already-resolved earlier sections) are checked, so the probe stays sound when
     * later sections are still unresolved. This prevents a cap that shares a block with the repeat
     * unit from being mistaken for an extra copy.
     */
    private static boolean probeDependents(
                                           StructureView view,
                                           CompiledBlueprint compiled,
                                           Blueprint.Brick sectionBrick,
                                           Blueprint.RepeatSection section,
                                           int sectionIndex,
                                           int candidateCount,
                                           int[] counts,
                                           BlockPos origin,
                                           Orientation orientation,
                                           BlockPos.MutableBlockPos cursor,
                                           Tally tally) {
        Blueprint blueprint = compiled.blueprint();
        int[] candidate = counts.clone();
        candidate[sectionIndex] = candidateCount;
        int[] controllerDecl = blueprint.controllerDecl();
        int[] controllerShift = blueprint.shiftOf(blueprint.bricksInternal().get(compiled.controllerBrick()), candidate);
        CompiledBlueprint.CompiledDefinition[] definitions = compiled.definitions();
        boolean post = section.bandStart() >= 0;
        boolean mismatch = false;
        for (Blueprint.Brick brick : compiled.bricks()) {
            if (brick.section() >= 0 || brick.cellCount() != 1) {
                continue;
            }
            int y = brick.ys()[0];
            if (post ? y < section.bandEnd() : y >= section.bandStart()) {
                continue;
            }
            boolean otherDependency = false;
            for (int dependency : brick.dependencies()) {
                if (dependency != sectionIndex) {
                    otherDependency = true;
                    break;
                }
            }
            if (otherDependency) {
                continue;
            }
            int[] shift = blueprint.shiftOf(brick, candidate);
            int lx = brick.xs()[0] + shift[0] - controllerDecl[0] - controllerShift[0];
            int ly = brick.ys()[0] + shift[1] - controllerDecl[1] - controllerShift[1];
            int lz = brick.zs()[0] + shift[2] - controllerDecl[2] - controllerShift[2];
            cursor.set(
                    origin.getX() + orientation.transformX(lx, ly, lz),
                    origin.getY() + orientation.transformY(lx, ly, lz),
                    origin.getZ() + orientation.transformZ(lx, ly, lz));
            StructureView.CellInfo info = view.cellInfo(cursor);
            if (info == null) {
                tally.unloaded(cursor);
                return false;
            }
            if (!matchesCompiled(info, view, cursor, definitions[brick.definitions()[0]])) {
                mismatch = true;
                break;
            }
        }
        if (!mismatch) {
            return true;
        }
        // The candidate tail position may still be the next copy of the section (a taller build):
        // if the section's next copy matches there, keep growing instead of committing the cap.
        int nextCopy = post ? candidateCount : 0;
        int[] sectionShift = blueprint.shiftOf(sectionBrick, candidate);
        return probeSectionCopy(
                view,
                compiled,
                sectionBrick,
                section,
                nextCopy,
                sectionShift,
                controllerDecl,
                controllerShift,
                origin,
                orientation,
                cursor,
                tally);
    }

    private static int[] minCountsOf(Blueprint blueprint) {
        int[] counts = new int[blueprint.sectionCount()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = blueprint.section(i).minCount();
        }
        return counts;
    }

    /**
     * The compiled per-cell test: predicate, then a reference scan over the expanded acceptable-state
     * set. Definitions whose expansion overflowed fall back to the legacy compare against the
     * pre-folded oriented expected state (alternates included).
     */
    public static boolean matchesCompiled(
                                          StructureView view, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition) {
        StructureView.CellInfo info = view.cellInfo(worldPos);
        return info != null && matchesCompiled(info, view, worldPos, definition);
    }

    private static boolean matchesCompiled(
                                           StructureView.CellInfo info,
                                           StructureView view,
                                           BlockPos worldPos,
                                           CompiledBlueprint.CompiledDefinition definition) {
        BlockState[] acceptable = definition.acceptable();
        if (acceptable == null) {
            return matches(view, worldPos, definition.prototype(), definition.orientedExpected(), info.state());
        }
        if (!definition.prototype().predicate().test(view, worldPos, definition.prototype())) {
            return false;
        }
        return CompiledBlueprint.acceptedState(acceptable, info.state());
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
     * {@link PropertyRule#alternateEncodings(BlockState) alternate encoding} of it contributed by the
     * cell's rules — verbatim except for the cell's masked properties.
     */
    static boolean matches(
                           StructureView view,
                           BlockPos worldPos,
                           Cell cell,
                           @Nullable BlockState orientedExpected,
                           @Nullable BlockState actual) {
        if (!cell.predicate().test(view, worldPos, cell)) {
            return false;
        }
        if (orientedExpected == null) {
            return true;
        }
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
     * {@code Blueprint.build()}) union of its rules' masked properties, so no set is allocated per
     * compare.
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
    public record Verification(boolean formed, RepeatResolution repeats, long firstMismatchPos, boolean unloaded) {}

    // ------------------------------------------------------------------------------------------
    // Tallies.
    // ------------------------------------------------------------------------------------------

    private sealed interface Tally permits RecognizeTally, VerifyTally {

        void matched(StructureView.CellInfo info, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition);

        void unloaded(BlockPos worldPos);

        void mismatched(StructureView.CellInfo info, BlockPos worldPos, BlockPos localPos, CompiledBlueprint.CompiledDefinition definition);

        boolean shortCircuit();
    }

    private static final class RecognizeTally implements Tally {

        private final List<RecognitionResult.MissingCell> missingSample = new ArrayList<>();
        private final Map<PartRole, Integer> roleCounts = new LinkedHashMap<>();
        private final List<BlockPos> members = new ArrayList<>();
        private int matchedCount;
        private int missingCount;
        private int unloadedCount;
        private int minX = Integer.MAX_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        private RecognizeTally() {}

        RecognitionResult.Bounds bounds() {
            return minX > maxX ? RecognitionResult.Bounds.empty() : new RecognitionResult.Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        }

        private void touch(BlockPos worldPos) {
            minX = Math.min(minX, worldPos.getX());
            minY = Math.min(minY, worldPos.getY());
            minZ = Math.min(minZ, worldPos.getZ());
            maxX = Math.max(maxX, worldPos.getX());
            maxY = Math.max(maxY, worldPos.getY());
            maxZ = Math.max(maxZ, worldPos.getZ());
        }

        @Override
        public void matched(StructureView.CellInfo info, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition) {
            matchedCount++;
            touch(worldPos);
            BlockState actual = info.state();
            // Block-entity prune: capability/membership side tables only ever hold machine cells.
            if (actual != null && actual.hasBlockEntity()) {
                for (PartRole capability : info.roles()) {
                    roleCounts.merge(capability, 1, Integer::sum);
                }
                StructureView.Member member = info.member();
                if (member != null) {
                    members.add(member.pos());
                }
            }
        }

        @Override
        public void unloaded(BlockPos worldPos) {
            unloadedCount++;
        }

        @Override
        public void mismatched(
                               StructureView.CellInfo info,
                               BlockPos worldPos,
                               BlockPos localPos,
                               CompiledBlueprint.CompiledDefinition definition) {
            missingCount++;
            touch(worldPos);
            if (missingSample.size() < MISSING_SAMPLE_CAP) {
                Cell prototype = definition.prototype();
                missingSample.add(new RecognitionResult.MissingCell(
                        localPos,
                        worldPos.immutable(),
                        new Cell(
                                localPos,
                                prototype.expectedState(),
                                prototype.rules(),
                                prototype.predicate(),
                                prototype.maskedProperties()),
                        info.state()));
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
        private boolean unloaded;

        private VerifyTally(CompiledBlueprint.PartRoleOrdinals counted) {
            this.counted = counted;
            this.countScratch = new int[counted.size()];
        }

        @Override
        public void matched(StructureView.CellInfo info, BlockPos worldPos, CompiledBlueprint.CompiledDefinition definition) {
            if (counted.size() == 0) {
                return;
            }
            BlockState actual = info.state();
            if (actual == null || !actual.hasBlockEntity()) {
                return;
            }
            for (PartRole capability : info.roles()) {
                int ordinal = counted.ordinalOf(capability);
                if (ordinal >= 0) {
                    countScratch[ordinal]++;
                }
            }
        }

        @Override
        public void unloaded(BlockPos worldPos) {
            unloaded = true;
        }

        @Override
        public void mismatched(
                               StructureView.CellInfo info,
                               BlockPos worldPos,
                               BlockPos localPos,
                               CompiledBlueprint.CompiledDefinition definition) {
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
