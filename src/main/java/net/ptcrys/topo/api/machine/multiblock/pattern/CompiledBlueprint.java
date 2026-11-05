package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;

/**
 * A {@link Blueprint} compiled for one {@link Orientation}. Everything orientation-dependent is
 * resolved once per blueprint × orientation and cached: per-symbol acceptable-state sets, the
 * maximal footprint's capture order, and the arithmetic world→cell addressing.
 *
 * <p>
 * <b>No per-cell materialization.</b> The walk consumes the blueprint's primitive brick arrays;
 * membership and canonical cell indices are computed from the declaration grid plus per-section
 * shifts, so million-cell footprints stay viable.
 *
 * <p>
 * <b>World→cell addressing.</b> Layout along each axis is a monotone piecewise map: every section
 * copies its band ({@code count} times), and content beyond the band is shifted by
 * {@code (count - 1) * stride}. Inverting the map per axis yields the declaration coordinate; a
 * lookup over the (small) declared-cell table then yields the canonical cell index. Because
 * blueprint validation guarantees that a section's band contains only its own cells, a position
 * inside a copy interval always belongs to that section — the inverse needs no search.
 */
public final class CompiledBlueprint {

    /** Masked-combination expansion cap; beyond it the legacy compare keeps correctness. */
    static final int MASK_EXPANSION_LIMIT = 64;

    private final Blueprint blueprint;
    private final Orientation machineOrientation;
    private final Orientation orientation;
    private final Orientation inverse;
    private final CompiledDefinition[] definitions;
    private final int[] minCounts;
    private final int[] maxCounts;
    private final int[][] sectionsByAxis;
    private final int controllerBrick;
    private final int[] controllerDecl;
    private final int cellCapacity;
    private final int[][] anyLayoutBoxes;
    private final long[] captureOffsets;
    private final int[] captureCellIndex;
    private final PartRoleOrdinals countedRoles;

    private CompiledBlueprint(
                              Blueprint blueprint,
                              Orientation machineOrientation,
                              Orientation orientation,
                              CompiledDefinition[] definitions,
                              int[] minCounts,
                              int[] maxCounts,
                              int[][] sectionsByAxis,
                              int controllerBrick,
                              int[] controllerDecl,
                              int cellCapacity,
                              int[][] anyLayoutBoxes,
                              long[] captureOffsets,
                              int[] captureCellIndex,
                              PartRoleOrdinals countedRoles) {
        this.blueprint = blueprint;
        this.machineOrientation = machineOrientation;
        this.orientation = orientation;
        this.inverse = orientation.inverse();
        this.definitions = definitions;
        this.minCounts = minCounts;
        this.maxCounts = maxCounts;
        this.sectionsByAxis = sectionsByAxis;
        this.controllerBrick = controllerBrick;
        this.controllerDecl = controllerDecl;
        this.cellCapacity = cellCapacity;
        this.anyLayoutBoxes = anyLayoutBoxes;
        this.captureOffsets = captureOffsets;
        this.captureCellIndex = captureCellIndex;
        this.countedRoles = countedRoles;
    }

    static CompiledBlueprint compile(Blueprint blueprint, Orientation machineOrientation, Orientation orientation) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(machineOrientation, "machine orientation");
        Objects.requireNonNull(orientation, "effective orientation");

        List<Blueprint.CellDefinition> symbolDefinitions = blueprint.symbolDefinitions();
        CompiledDefinition[] definitions = new CompiledDefinition[symbolDefinitions.size()];
        for (int i = 0; i < definitions.length; i++) {
            Cell prototype = blueprint.cellOf(i, BlockPos.ZERO);
            BlockState oriented = StructureEngine.orientExpected(prototype, orientation);
            definitions[i] = new CompiledDefinition(
                    prototype,
                    oriented,
                    oriented == null ? null : acceptableStates(prototype, oriented));
        }

        int[] minCounts = new int[blueprint.sectionCount()];
        int[] maxCounts = new int[blueprint.sectionCount()];
        List<List<Integer>> byAxis = new ArrayList<>(3);
        for (int axis = 0; axis < 3; axis++) {
            byAxis.add(new ArrayList<>());
        }
        for (int s = 0; s < blueprint.sectionCount(); s++) {
            Blueprint.RepeatSection section = blueprint.section(s);
            minCounts[s] = section.minCount();
            maxCounts[s] = section.maxCount();
            byAxis.get(section.axis().ordinal()).add(s);
        }
        int[][] sectionsByAxis = new int[3][];
        for (int axis = 0; axis < 3; axis++) {
            List<Integer> ordinals = byAxis.get(axis);
            sectionsByAxis[axis] = new int[ordinals.size()];
            for (int i = 0; i < ordinals.size(); i++) {
                sectionsByAxis[axis][i] = ordinals.get(i);
            }
        }

        int[][] boxes = buildAnyLayoutBoxes(blueprint);
        long[][] capture = captureOrder(blueprint, orientation, maxCounts);

        return new CompiledBlueprint(
                blueprint,
                machineOrientation,
                orientation,
                definitions,
                minCounts,
                maxCounts,
                sectionsByAxis,
                blueprint.controllerBrick(),
                blueprint.controllerDecl(),
                blueprint.maxCellCount(),
                boxes,
                capture[0],
                toIntArray(capture[1]),
                PartRoleOrdinals.of(blueprint));
    }

    // ------------------------------------------------------------------------------------------
    // Public surface.
    // ------------------------------------------------------------------------------------------

    /** The controller (machine) orientation this compiled form was requested for. */
    public Orientation orientation() {
        return machineOrientation;
    }

    /** The orientation actually used for positions and state folding: {@code machine ∘ frame}. */
    public Orientation effectiveOrientation() {
        return orientation;
    }

    /** The compiled definition of the cell at a canonical index (see {@link #cellCapacity()}). */
    public CompiledDefinition definitionAt(int cellIndex) {
        Objects.checkIndex(cellIndex, cellCapacity);
        for (Blueprint.Brick brick : blueprint.bricksInternal()) {
            int size = brick.cellCount();
            int span = size * (brick.section() < 0 ? 1 : maxCounts[brick.section()]);
            if (cellIndex >= brick.indexBase() && cellIndex < brick.indexBase() + span) {
                return definitions[brick.definitions()[(cellIndex - brick.indexBase()) % size]];
            }
        }
        throw new IndexOutOfBoundsException("No brick covers cell index " + cellIndex);
    }

    /** The canonical cell-index space size at maximal counts (fixed cells and every copy). */
    public int cellCapacity() {
        return cellCapacity;
    }

    /**
     * The maximal footprint's oriented offsets ({@link BlockPos#asLong()} packed, relative to the
     * controller), sorted by chunk locality, and the parallel canonical cell index of each entry — a
     * dense snapshot walks the world in chunk order and writes each state straight into its slot.
     */
    public long[] captureOffsets() {
        return captureOffsets;
    }

    public int[] captureCellIndex() {
        return captureCellIndex;
    }

    /** Capabilities named by count requirements — the only roles verification tallies. */
    public PartRoleOrdinals countedRoles() {
        return countedRoles;
    }

    /** Canonical index of {@code worldPos} at maximal counts, or {@code -1} when it is no cell. */
    public int cellIndexOfWorld(BlockPos worldPos, BlockPos controllerPos) {
        return cellIndexOf(worldPos, controllerPos, maxCounts);
    }

    /** Canonical index of {@code worldPos} under {@code repeats}, or {@code -1} when it is no cell. */
    public int cellIndexOfWorld(BlockPos worldPos, BlockPos controllerPos, RepeatResolution repeats) {
        Objects.requireNonNull(repeats, "repeat resolution");
        if (repeats.size() != blueprint.sectionCount()) {
            throw new IllegalArgumentException(
                    "Repeat resolution has " + repeats.size() + " counts but the blueprint declares " + blueprint.sectionCount() + " repeat section(s)");
        }
        return cellIndexOf(worldPos, controllerPos, repeats.countsCopy());
    }

    /** Whether {@code worldPos} is occupied under {@code repeats}. */
    public boolean containsWorld(BlockPos worldPos, BlockPos controllerPos, RepeatResolution repeats) {
        return cellIndexOfWorld(worldPos, controllerPos, repeats) >= 0;
    }

    /**
     * Conservative membership over <b>every</b> possible repeat count. Used by watches that exist
     * before a resolution is known; a true answer may include positions reachable only by some
     * candidate counts, which is safe for routing a change to a controller.
     */
    public boolean containsWorldAnyLayout(BlockPos worldPos, BlockPos controllerPos) {
        Objects.requireNonNull(worldPos, "world position");
        Objects.requireNonNull(controllerPos, "controller position");
        int dx = worldPos.getX() - controllerPos.getX();
        int dy = worldPos.getY() - controllerPos.getY();
        int dz = worldPos.getZ() - controllerPos.getZ();
        int lx = inverse.transformX(dx, dy, dz);
        int ly = inverse.transformY(dx, dy, dz);
        int lz = inverse.transformZ(dx, dy, dz);
        for (int[] box : anyLayoutBoxes) {
            if (lx >= box[0] && lx <= box[3] && ly >= box[1] && ly <= box[4] && lz >= box[2] && lz <= box[5]) {
                return true;
            }
        }
        return false;
    }

    /** Axis-aligned world-space bounds over every possible repeat count. */
    public int[] worldBounds(BlockPos controllerPos) {
        Objects.requireNonNull(controllerPos, "controller position");
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] box : anyLayoutBoxes) {
            int x1 = orientation.transformX(box[0], box[1], box[2]);
            int y1 = orientation.transformY(box[0], box[1], box[2]);
            int z1 = orientation.transformZ(box[0], box[1], box[2]);
            int x2 = orientation.transformX(box[3], box[4], box[5]);
            int y2 = orientation.transformY(box[3], box[4], box[5]);
            int z2 = orientation.transformZ(box[3], box[4], box[5]);
            minX = Math.min(minX, Math.min(x1, x2));
            minY = Math.min(minY, Math.min(y1, y2));
            minZ = Math.min(minZ, Math.min(z1, z2));
            maxX = Math.max(maxX, Math.max(x1, x2));
            maxY = Math.max(maxY, Math.max(y1, y2));
            maxZ = Math.max(maxZ, Math.max(z1, z2));
        }
        if (minX == Integer.MAX_VALUE) {
            return new int[] { controllerPos.getX(), controllerPos.getY(), controllerPos.getZ(), controllerPos.getX(), controllerPos.getY(), controllerPos.getZ() };
        }
        return new int[] {
                controllerPos.getX() + minX,
                controllerPos.getY() + minY,
                controllerPos.getZ() + minZ,
                controllerPos.getX() + maxX,
                controllerPos.getY() + maxY,
                controllerPos.getZ() + maxZ
        };
    }

    /** Visits every world position occupied by {@code repeats}. */
    public void forEachWorldPos(BlockPos controllerPos, RepeatResolution repeats, LongConsumer consumer) {
        Objects.requireNonNull(controllerPos, "controller position");
        Objects.requireNonNull(repeats, "repeat resolution");
        Objects.requireNonNull(consumer, "consumer");
        int[] counts = repeats.countsCopy();
        if (counts.length != blueprint.sectionCount()) {
            throw new IllegalArgumentException("Repeat resolution does not match the blueprint's sections");
        }
        int[] controllerShift = blueprint.shiftOf(blueprint.bricksInternal().get(controllerBrick), counts);
        for (Blueprint.Brick brick : blueprint.bricksInternal()) {
            int[] shift = blueprint.shiftOf(brick, counts);
            int copies = brick.section() < 0 ? 1 : counts[brick.section()];
            int stride = brick.section() < 0 ? 0 : blueprint.section(brick.section()).stride();
            int axis = brick.section() < 0 ? -1 : blueprint.section(brick.section()).axis().ordinal();
            for (int copy = 0; copy < copies; copy++) {
                int cx = axis == 0 ? copy * stride : 0;
                int cy = axis == 1 ? copy * stride : 0;
                int cz = axis == 2 ? copy * stride : 0;
                for (int k = 0; k < brick.cellCount(); k++) {
                    int lx = brick.xs()[k] + shift[0] - controllerDecl[0] - controllerShift[0] + cx;
                    int ly = brick.ys()[k] + shift[1] - controllerDecl[1] - controllerShift[1] + cy;
                    int lz = brick.zs()[k] + shift[2] - controllerDecl[2] - controllerShift[2] + cz;
                    int wx = controllerPos.getX() + orientation.transformX(lx, ly, lz);
                    int wy = controllerPos.getY() + orientation.transformY(lx, ly, lz);
                    int wz = controllerPos.getZ() + orientation.transformZ(lx, ly, lz);
                    consumer.accept(BlockPos.asLong(wx, wy, wz));
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Package-private surface for the engine.
    // ------------------------------------------------------------------------------------------

    Blueprint blueprint() {
        return blueprint;
    }

    List<Blueprint.Brick> bricks() {
        return blueprint.bricksInternal();
    }

    int controllerBrick() {
        return controllerBrick;
    }

    int[] maxCounts() {
        return maxCounts.clone();
    }

    CompiledDefinition[] definitions() {
        return definitions;
    }

    // ------------------------------------------------------------------------------------------
    // World→cell inversion.
    // ------------------------------------------------------------------------------------------

    private int cellIndexOf(BlockPos worldPos, BlockPos controllerPos, int[] counts) {
        Objects.requireNonNull(worldPos, "world position");
        Objects.requireNonNull(controllerPos, "controller position");
        int dx = worldPos.getX() - controllerPos.getX();
        int dy = worldPos.getY() - controllerPos.getY();
        int dz = worldPos.getZ() - controllerPos.getZ();
        int lx = inverse.transformX(dx, dy, dz);
        int ly = inverse.transformY(dx, dy, dz);
        int lz = inverse.transformZ(dx, dy, dz);

        int[] controllerShift = blueprint.shiftOf(blueprint.bricksInternal().get(controllerBrick), counts);
        long ax = (long) lx + controllerDecl[0] + controllerShift[0];
        long ay = (long) ly + controllerDecl[1] + controllerShift[1];
        long az = (long) lz + controllerDecl[2] + controllerShift[2];

        long[] declaration = new long[3];
        int[] copy = { -1, -1, -1 };
        int[] copySection = { -1, -1, -1 };
        invertAxis(0, ax, counts, declaration, copy, copySection);
        invertAxis(1, ay, counts, declaration, copy, copySection);
        invertAxis(2, az, counts, declaration, copy, copySection);
        for (long coordinate : declaration) {
            if (coordinate < Integer.MIN_VALUE || coordinate > Integer.MAX_VALUE) {
                return -1;
            }
        }

        long ref = blueprint.declaredRefAt(BlockPos.asLong((int) declaration[0], (int) declaration[1], (int) declaration[2]));
        if (ref < 0) {
            return -1;
        }
        int brickOrdinal = Blueprint.refBrick(ref);
        int member = Blueprint.refMember(ref);
        Blueprint.Brick brick = blueprint.bricksInternal().get(brickOrdinal);
        if (brick.section() < 0) {
            // A fixed cell can never be inside a section band; an inversion that found one means
            // the position belongs to a copy of a section, not to this cell.
            return copySection[0] >= 0 || copySection[1] >= 0 || copySection[2] >= 0 ? -1 : brick.indexBase();
        }
        int section = brick.section();
        int axis = blueprint.section(section).axis().ordinal();
        if (copySection[axis] != section || copy[axis] < 0 || copy[axis] >= counts[section]) {
            return -1;
        }
        for (int other = 0; other < 3; other++) {
            if (other != axis && copySection[other] >= 0) {
                return -1;
            }
        }
        return brick.indexBase() + copy[axis] * brick.cellCount() + member;
    }

    /**
     * Inverts one axis. {@code outDeclaration[axis]} receives the declaration coordinate;
     * {@code copy[axis]}/{@code copySection[axis]} are set when the layout coordinate falls inside a
     * section's copy interval.
     */
    private void invertAxis(
                            int axis,
                            long layoutCoordinate,
                            int[] counts,
                            long[] outDeclaration,
                            int[] copy,
                            int[] copySection) {
        long offset = 0;
        for (int s : sectionsByAxis[axis]) {
            Blueprint.RepeatSection section = blueprint.section(s);
            long start = (long) section.bandStart() + offset;
            if (layoutCoordinate < start) {
                outDeclaration[axis] = layoutCoordinate - offset;
                copy[axis] = -1;
                copySection[axis] = -1;
                return;
            }
            long span = (long) counts[s] * section.stride();
            if (layoutCoordinate < start + span) {
                long relative = layoutCoordinate - start;
                outDeclaration[axis] = section.bandStart() + relative % section.stride();
                copy[axis] = (int) (relative / section.stride());
                copySection[axis] = s;
                return;
            }
            offset += (long) (counts[s] - 1) * section.stride();
        }
        outDeclaration[axis] = layoutCoordinate - offset;
        copy[axis] = -1;
        copySection[axis] = -1;
    }

    // ------------------------------------------------------------------------------------------
    // Any-layout boxes and capture order.
    // ------------------------------------------------------------------------------------------

    private static int[][] buildAnyLayoutBoxes(Blueprint blueprint) {
        int[] controllerDecl = blueprint.controllerDecl();
        Blueprint.Brick controller = blueprint.bricksInternal().get(blueprint.controllerBrick());
        int[] controllerMinShift = blueprint.minShiftOf(controller);
        int[] controllerMaxShift = blueprint.maxShiftOf(controller);
        int total = blueprint.declaredCellCount();
        int[][] boxes = new int[total][];
        int index = 0;
        for (Blueprint.Brick brick : blueprint.bricksInternal()) {
            int[] minShift = blueprint.minShiftOf(brick);
            int[] maxShift = blueprint.maxShiftOf(brick);
            Blueprint.RepeatSection section = brick.section() < 0 ? null : blueprint.section(brick.section());
            for (int k = 0; k < brick.cellCount(); k++) {
                int[] box = new int[6];
                int[] declaration = { brick.xs()[k], brick.ys()[k], brick.zs()[k] };
                for (int axis = 0; axis < 3; axis++) {
                    int low = declaration[axis] + minShift[axis] - controllerDecl[axis] - controllerMaxShift[axis];
                    int high = declaration[axis] + maxShift[axis] - controllerDecl[axis] - controllerMinShift[axis];
                    if (section != null && section.axis().ordinal() == axis) {
                        high += (section.maxCount() - 1) * section.stride();
                    }
                    box[axis] = low;
                    box[3 + axis] = high;
                }
                boxes[index++] = box;
            }
        }
        return boxes;
    }

    private static long[][] captureOrder(Blueprint blueprint, Orientation orientation, int[] maxCounts) {
        int total = blueprint.maxCellCount();
        long[] offsets = new long[total];
        int[] indices = new int[total];
        int[] controllerShift = blueprint.shiftOf(blueprint.bricksInternal().get(blueprint.controllerBrick()), maxCounts);
        int[] controllerDecl = blueprint.controllerDecl();
        int cursor = 0;
        for (Blueprint.Brick brick : blueprint.bricksInternal()) {
            int[] shift = blueprint.shiftOf(brick, maxCounts);
            int copies = brick.section() < 0 ? 1 : maxCounts[brick.section()];
            int stride = brick.section() < 0 ? 0 : blueprint.section(brick.section()).stride();
            int axis = brick.section() < 0 ? -1 : blueprint.section(brick.section()).axis().ordinal();
            for (int copy = 0; copy < copies; copy++) {
                int cx = axis == 0 ? copy * stride : 0;
                int cy = axis == 1 ? copy * stride : 0;
                int cz = axis == 2 ? copy * stride : 0;
                for (int k = 0; k < brick.cellCount(); k++) {
                    int lx = brick.xs()[k] + shift[0] - controllerDecl[0] - controllerShift[0] + cx;
                    int ly = brick.ys()[k] + shift[1] - controllerDecl[1] - controllerShift[1] + cy;
                    int lz = brick.zs()[k] + shift[2] - controllerDecl[2] - controllerShift[2] + cz;
                    int ox = orientation.transformX(lx, ly, lz);
                    int oy = orientation.transformY(lx, ly, lz);
                    int oz = orientation.transformZ(lx, ly, lz);
                    offsets[cursor] = BlockPos.asLong(ox, oy, oz);
                    indices[cursor] = brick.indexBase() + copy * brick.cellCount() + k;
                    cursor++;
                }
            }
        }
        int[] order = new int[total];
        for (int i = 0; i < total; i++) {
            order[i] = i;
        }
        sortByChunk(offsets, order);
        long[] sortedOffsets = new long[total];
        long[] sortedIndices = new long[total];
        for (int i = 0; i < total; i++) {
            sortedOffsets[i] = offsets[order[i]];
            sortedIndices[i] = indices[order[i]];
        }
        return new long[][] { sortedOffsets, sortedIndices };
    }

    /** In-place heapsort of {@code order} by chunk-sorted offset locality — primitive, no boxing. */
    private static void sortByChunk(long[] keys, int[] order) {
        int size = order.length;
        for (int i = size / 2 - 1; i >= 0; i--) {
            siftDown(keys, order, i, size - 1);
        }
        for (int end = size - 1; end > 0; end--) {
            int swap = order[0];
            order[0] = order[end];
            order[end] = swap;
            siftDown(keys, order, 0, end - 1);
        }
    }

    private static void siftDown(long[] keys, int[] order, int root, int end) {
        while (true) {
            int child = root * 2 + 1;
            if (child > end) {
                return;
            }
            if (child + 1 <= end && compareOffsets(keys[order[child]], keys[order[child + 1]]) < 0) {
                child++;
            }
            if (compareOffsets(keys[order[root]], keys[order[child]]) >= 0) {
                return;
            }
            int swap = order[root];
            order[root] = order[child];
            order[child] = swap;
            root = child;
        }
    }

    private static int compareOffsets(long a, long b) {
        int ax = BlockPos.getX(a);
        int ay = BlockPos.getY(a);
        int az = BlockPos.getZ(a);
        int bx = BlockPos.getX(b);
        int by = BlockPos.getY(b);
        int bz = BlockPos.getZ(b);
        int result = Integer.compare(ax >> 4, bx >> 4);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(az >> 4, bz >> 4);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(ay >> 4, by >> 4);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(ay, by);
        if (result != 0) {
            return result;
        }
        result = Integer.compare(az, bz);
        return result != 0 ? result : Integer.compare(ax, bx);
    }

    private static int[] toIntArray(long[] values) {
        int[] ints = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            ints[i] = (int) values[i];
        }
        return ints;
    }

    // ------------------------------------------------------------------------------------------
    // Acceptable-state expansion.
    // ------------------------------------------------------------------------------------------

    /**
     * The expected-state set a definition accepts (oriented + alternate encodings + every masked
     * combination), or {@code null} when the combination space is too large to expand — callers then
     * use the legacy {@code statesMatch} path against the oriented expected state.
     */
    static @Nullable BlockState[] acceptableStates(Cell cell, BlockState oriented) {
        Set<BlockState> seeds = new LinkedHashSet<>();
        seeds.add(oriented);
        for (PropertyRule rule : cell.rules()) {
            seeds.addAll(rule.alternateEncodings(oriented));
        }
        Set<BlockState> accepted = new LinkedHashSet<>();
        for (BlockState seed : seeds) {
            if (!expandMasked(seed, cell.maskedProperties(), accepted)) {
                return null;
            }
        }
        return accepted.toArray(new BlockState[0]);
    }

    /** Expands {@code seed} over every masked-property combination into {@code out}; false = over cap. */
    private static boolean expandMasked(BlockState seed, Set<Property<?>> masked, Set<BlockState> out) {
        List<BlockState> worklist = new ArrayList<>();
        worklist.add(seed);
        for (Property<?> property : masked) {
            if (!seed.hasProperty(property)) {
                continue;
            }
            List<BlockState> next = new ArrayList<>(worklist.size() * 2);
            for (BlockState state : worklist) {
                expandProperty(state, property, next);
                if (next.size() > MASK_EXPANSION_LIMIT) {
                    return false;
                }
            }
            worklist = next;
        }
        out.addAll(worklist);
        return out.size() <= MASK_EXPANSION_LIMIT;
    }

    private static <T extends Comparable<T>> void expandProperty(
                                                                 BlockState state, Property<T> property, List<BlockState> out) {
        for (T value : property.getPossibleValues()) {
            out.add(state.setValue(property, value));
        }
    }

    /** Whether {@code actual} is one of the definition's accepted states — a reference scan. */
    static boolean acceptedState(BlockState[] acceptable, BlockState actual) {
        for (BlockState state : acceptable) {
            if (state == actual) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------
    // Value types.
    // ------------------------------------------------------------------------------------------

    /** One compiled symbol definition, shared by every cell carrying that symbol. */
    public record CompiledDefinition(
                                     Cell prototype,
                                     @Nullable BlockState orientedExpected,
                                     BlockState @Nullable [] acceptable) {}

    /**
     * Ordinals for the capabilities named by count requirements — the only ones verification needs
     * to tally. {@code ordinalOf} is a linear scan over a tiny array (count requirements are few).
     */
    public static final class PartRoleOrdinals {

        private final PartRole[] capabilities;
        private final int[] mins;
        private final int[] maxs;

        private PartRoleOrdinals(PartRole[] capabilities, int[] mins, int[] maxs) {
            this.capabilities = capabilities;
            this.mins = mins;
            this.maxs = maxs;
        }

        private static PartRoleOrdinals of(Blueprint blueprint) {
            Map<PartRole, Blueprint.CountRequirement> requirements = blueprint.countRequirements();
            PartRole[] capabilities = new PartRole[requirements.size()];
            int[] mins = new int[requirements.size()];
            int[] maxs = new int[requirements.size()];
            int i = 0;
            for (Blueprint.CountRequirement requirement : requirements.values()) {
                capabilities[i] = requirement.role();
                mins[i] = requirement.min();
                maxs[i] = requirement.max();
                i++;
            }
            return new PartRoleOrdinals(capabilities, mins, maxs);
        }

        public int size() {
            return capabilities.length;
        }

        public int ordinalOf(PartRole capability) {
            for (int i = 0; i < capabilities.length; i++) {
                if (capabilities[i] == capability) {
                    return i;
                }
            }
            return -1;
        }

        public boolean satisfied(int[] counts) {
            for (int i = 0; i < capabilities.length; i++) {
                if (counts[i] < mins[i] || counts[i] > maxs[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
