package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A blueprint compiled for one {@link Orientation}, in <b>grid form</b>: million-cell capable
 * because nothing is materialized per cell.
 *
 * <ul>
 * <li><b>Per-definition compilation</b> — predicates, the orientation-folded expected state, and
 * the expanded acceptable-state set exist once per blueprint <em>symbol</em>; cells reference
 * them through a byte grid ({@code symbol ordinal per (x,z)} per segment). A 10⁶-cell wall of
 * one casing costs one definition plus one byte per cell.
 * <li><b>Arithmetic addressing</b> — {@link #cellIndexOfWorld} inverts (world − controller)
 * through the orientation transpose and resolves segment/copy/x/z by integer math: any world
 * position maps to its canonical cell index (or −1) with no per-cell map. Valid under the V1
 * repeat constraint (single trailing repeatable aisle ⇒ all segment bases fixed).
 * <li><b>Acceptable-state sets</b> — pinned states fold COVARIANT rules, widen by
 * {@link PropertyRule#alternateEncodings}, and expand every masked combination into interned
 * {@link BlockState} references; the per-cell match is a reference scan. Expansions past
 * {@link #MASK_EXPANSION_LIMIT} fall back to the legacy property compare.
 * <li><b>Capture order</b> — the maximal footprint's oriented offsets sorted by chunk locality,
 * each entry paired with its canonical cell index so a dense snapshot writes straight into
 * its slot.
 * </ul>
 */
public final class CompiledBlueprint {

    /** Masked-combination expansion cap; beyond it the legacy compare keeps correctness. */
    static final int MASK_EXPANSION_LIMIT = 64;

    /** Grid value for an empty (' ') blueprint cell. */
    public static final byte EMPTY = -1;

    private final Orientation orientation;
    private final Orientation inverse;
    private final CompiledDefinition[] definitions;
    private final CompiledSegment[] segments;
    private final int width;
    private final int depth;
    private final int controllerX;
    private final int controllerZ;
    private final int layerStride;
    private final int cellCapacity;
    private final long[] captureOffsets;
    private final int[] captureCellIndex;
    private final PartRoleOrdinals countedCapabilities;

    private CompiledBlueprint(
                              Orientation orientation,
                              CompiledDefinition[] definitions,
                              CompiledSegment[] segments,
                              int width,
                              int depth,
                              int controllerX,
                              int controllerZ,
                              long[] captureOffsets,
                              int[] captureCellIndex,
                              PartRoleOrdinals countedCapabilities) {
        this.orientation = orientation;
        this.inverse = orientation.inverse();
        this.definitions = definitions;
        this.segments = segments;
        this.width = width;
        this.depth = depth;
        this.controllerX = controllerX;
        this.controllerZ = controllerZ;
        this.layerStride = width * depth;
        CompiledSegment last = segments[segments.length - 1];
        this.cellCapacity = last.indexBase() + last.maxCount() * layerStride;
        this.captureOffsets = captureOffsets;
        this.captureCellIndex = captureCellIndex;
        this.countedCapabilities = countedCapabilities;
    }

    static CompiledBlueprint compile(Blueprint blueprint, Orientation orientation) {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(orientation, "orientation");
        Blueprint.GridView grid = blueprint.gridView();
        List<CompiledDefinition> definitions = new ArrayList<>();
        Map<Character, Byte> ordinalBySymbol = new LinkedHashMap<>();
        for (Map.Entry<Character, Cell> entry : grid.definitionPrototypes().entrySet()) {
            Cell prototype = entry.getValue();
            BlockState oriented = StructureEngine.orientExpected(prototype, orientation);
            definitions.add(new CompiledDefinition(
                    prototype,
                    oriented,
                    oriented == null ? null : acceptableStates(prototype, oriented)));
            ordinalBySymbol.put(entry.getKey(), (byte) (definitions.size() - 1));
        }

        CompiledSegment[] segments = new CompiledSegment[grid.segmentCount()];
        int layerStride = grid.width() * grid.depth();
        int indexBase = 0;
        for (int s = 0; s < segments.length; s++) {
            List<String> rows = grid.segmentRows(s);
            byte[] cells = new byte[layerStride];
            for (int z = 0; z < grid.depth(); z++) {
                String row = rows.get(z);
                for (int x = 0; x < grid.width(); x++) {
                    char symbol = row.charAt(x);
                    cells[z * grid.width() + x] = symbol == ' ' ? EMPTY : ordinalBySymbol.get(symbol);
                }
            }
            Blueprint.RepeatRange range = grid.repeatRange(s);
            segments[s] = new CompiledSegment(
                    cells, grid.segmentBaseLocalY(s), range.min(), range.max(), indexBase);
            indexBase += range.max() * layerStride;
        }

        long[][] capture = captureOrder(
                segments, grid.width(), grid.depth(), grid.controllerX(), grid.controllerZ(), orientation);
        return new CompiledBlueprint(
                orientation,
                definitions.toArray(new CompiledDefinition[0]),
                segments,
                grid.width(),
                grid.depth(),
                grid.controllerX(),
                grid.controllerZ(),
                capture[0],
                toIntArray(capture[1]),
                PartRoleOrdinals.of(blueprint));
    }

    public Orientation orientation() {
        return orientation;
    }

    public CompiledDefinition[] definitions() {
        return definitions;
    }

    public CompiledSegment[] segments() {
        return segments;
    }

    public int width() {
        return width;
    }

    public int depth() {
        return depth;
    }

    public int controllerX() {
        return controllerX;
    }

    public int controllerZ() {
        return controllerZ;
    }

    public int layerStride() {
        return layerStride;
    }

    /** The canonical cell-index space size at maximal repeats (empty grid slots included). */
    public int cellCapacity() {
        return cellCapacity;
    }

    /**
     * The maximal footprint's oriented offsets ({@link BlockPos#asLong()} packed), sorted by chunk
     * locality, and the parallel canonical cell index of each entry — a dense snapshot walks the
     * world in chunk order and writes each state straight into its canonical slot.
     */
    public long[] captureOffsets() {
        return captureOffsets;
    }

    public int[] captureCellIndex() {
        return captureCellIndex;
    }

    public PartRoleOrdinals countedRoles() {
        return countedCapabilities;
    }

    /** The definition ordinal at a canonical cell index, {@link #EMPTY} for empty grid slots. */
    public byte definitionOrdinalAt(int cellIndex) {
        for (int s = segments.length - 1; s >= 0; s--) {
            CompiledSegment segment = segments[s];
            if (cellIndex >= segment.indexBase()) {
                return segment.cells()[(cellIndex - segment.indexBase()) % layerStride];
            }
        }
        return EMPTY;
    }

    /**
     * Canonical cell index of a world position under {@code controllerPos}, or {@code -1} when the
     * position is outside the maximal footprint (or on an empty grid slot). Pure integer math: the
     * orientation transpose maps world→local, segment/copy resolve by Y range, x/z by grid bounds.
     */
    public int cellIndexOfWorld(BlockPos worldPos, BlockPos controllerPos) {
        int dx = worldPos.getX() - controllerPos.getX();
        int dy = worldPos.getY() - controllerPos.getY();
        int dz = worldPos.getZ() - controllerPos.getZ();
        int lx = inverse.transformX(dx, dy, dz);
        int ly = inverse.transformY(dx, dy, dz);
        int lz = inverse.transformZ(dx, dy, dz);
        int gx = lx + controllerX;
        int gz = lz + controllerZ;
        if (gx < 0 || gx >= width || gz < 0 || gz >= depth) {
            return -1;
        }
        for (CompiledSegment segment : segments) {
            int copy = ly - segment.baseLocalY();
            if (copy >= 0 && copy < segment.maxCount()) {
                int index = segment.indexBase() + copy * layerStride + gz * width + gx;
                return segment.cells()[gz * width + gx] == EMPTY ? -1 : index;
            }
        }
        return -1;
    }

    /** Whether {@code worldPos} is a (non-empty) cell of the maximal footprint. */
    public boolean containsWorld(BlockPos worldPos, BlockPos controllerPos) {
        return cellIndexOfWorld(worldPos, controllerPos) >= 0;
    }

    /** Whether a canonical cell index falls inside the copies chosen by {@code repeats}. */
    public boolean withinRepeats(int cellIndex, RepeatResolution repeats) {
        for (int s = segments.length - 1; s >= 0; s--) {
            CompiledSegment segment = segments[s];
            if (cellIndex >= segment.indexBase()) {
                int copy = (cellIndex - segment.indexBase()) / layerStride;
                return copy < repeats.count(s);
            }
        }
        return false;
    }

    /**
     * The expected-state set a definition accepts (oriented + alternate encodings + every masked
     * combination), or {@code null} when the combination space is too large to expand — callers
     * then use the legacy {@code statesMatch} path against the oriented expected state.
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

    private static long[][] captureOrder(
                                         CompiledSegment[] segments,
                                         int width,
                                         int depth,
                                         int controllerX,
                                         int controllerZ,
                                         Orientation orientation) {
        int total = 0;
        int layerStride = width * depth;
        for (CompiledSegment segment : segments) {
            int nonEmpty = 0;
            for (byte cell : segment.cells()) {
                if (cell != EMPTY) {
                    nonEmpty++;
                }
            }
            total += nonEmpty * segment.maxCount();
        }
        // sortKey-major packing: (offsetPackedLong, cellIndex) pairs sorted by chunk locality.
        long[] offsets = new long[total];
        long[] indices = new long[total];
        Integer[] order = new Integer[total];
        BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        BlockPos zero = BlockPos.ZERO;
        int i = 0;
        for (CompiledSegment segment : segments) {
            for (int copy = 0; copy < segment.maxCount(); copy++) {
                int localY = segment.baseLocalY() + copy;
                for (int gz = 0; gz < depth; gz++) {
                    for (int gx = 0; gx < width; gx++) {
                        if (segment.cells()[gz * width + gx] == EMPTY) {
                            continue;
                        }
                        scratch.set(gx - controllerX, localY, gz - controllerZ);
                        BlockPos oriented = orientation.apply(scratch, zero);
                        offsets[i] = oriented.asLong();
                        indices[i] = segment.indexBase() + copy * layerStride + gz * width + gx;
                        order[i] = i;
                        i++;
                    }
                }
            }
        }
        java.util.Arrays.sort(order, (a, b) -> {
            int ax = BlockPos.getX(offsets[a]);
            int az = BlockPos.getZ(offsets[a]);
            int ay = BlockPos.getY(offsets[a]);
            int bx = BlockPos.getX(offsets[b]);
            int bz = BlockPos.getZ(offsets[b]);
            int by = BlockPos.getY(offsets[b]);
            int c = Integer.compare(ax >> 4, bx >> 4);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(az >> 4, bz >> 4);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(ay >> 4, by >> 4);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(ay, by);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(az, bz);
            return c != 0 ? c : Integer.compare(ax, bx);
        });
        long[] sortedOffsets = new long[total];
        long[] sortedIndices = new long[total];
        for (int k = 0; k < total; k++) {
            sortedOffsets[k] = offsets[order[k]];
            sortedIndices[k] = indices[order[k]];
        }
        return new long[][] { sortedOffsets, sortedIndices };
    }

    private static int[] toIntArray(long[] values) {
        int[] ints = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            ints[i] = (int) values[i];
        }
        return ints;
    }

    /** One compiled symbol definition, shared by every cell carrying that symbol. */
    public record CompiledDefinition(
                                     Cell prototype, @Nullable BlockState orientedExpected, BlockState @Nullable [] acceptable) {}

    /**
     * One compiled segment: the symbol-ordinal grid of a single layer copy ({@code z * width + x},
     * {@link #EMPTY} for blanks), its copy-0 local Y, the repeat range, and the canonical index
     * base of copy 0 (each further copy advances by {@code layerStride}).
     */
    public record CompiledSegment(byte[] cells, int baseLocalY, int minCount, int maxCount, int indexBase) {}

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
