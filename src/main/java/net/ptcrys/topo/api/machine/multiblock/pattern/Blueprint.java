package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.FormedMachineBlock;
import net.ptcrys.topo.api.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Immutable blueprint data used by recognition, future build, and future export passes.
 *
 * <p>
 * A blueprint is an <b>ordered list of segments</b>. Each segment is one layer (its rows are the
 * depth/Z axis, its characters the width/X axis); segments stack along the up/Y axis in declaration
 * order. A segment carries a {@link RepeatRange}: a non-repeatable segment is {@code (1, 1)}, a
 * repeatable one stacks between {@code min} and {@code max} copies. The recognition pass picks one
 * count per segment (a {@link RepeatResolution}) and the cells are computed per resolution with
 * {@link #cells(RepeatResolution)}; the same resolution is reused by build/export so all three legs
 * agree on the count.
 *
 * <p>
 * Cells are normalized to controller-relative local positions. Because a repeatable segment changes
 * how many layers sit between the controller and the cells beyond it, local positions are a function
 * of the resolution and are therefore computed per pass rather than stored flat. The controller's own
 * segment must be non-repeatable so the anchor is unambiguous.
 *
 * <p>
 * <b>V1 repeat constraint</b>: at most one segment may repeat, and it must be the last declared
 * aisle (every preceding aisle, including the controller's, fixed). This keeps a repeatable cell's
 * controller-relative position invariant to the count, so the maximal-footprint snapshot taken once
 * up front already contains every position the repeat resolution might probe. Interior or multiple
 * repeatable aisles would shift other cells with the count and are deferred.
 */
public final class Blueprint {

    private static final char CONTROLLER_SYMBOL = '@';
    private static final PropertyRule[] NO_RULES = new PropertyRule[0];

    /**
     * Machine block-state properties that flip at runtime (multiblock formed flag, recipe active
     * flag). Pinning them in an expected state would unform/reform structures as they run, so
     * {@code build()} rejects them unless a derived rule on the same cell masks them out.
     */
    private static final List<Property<?>> RUNTIME_MACHINE_STATE_PROPERTIES = List.of(FormedMachineBlock.FORMED, OrientedActiveMachineBlock.ACTIVE);

    private final List<Segment> segments;
    private final Map<Character, CellDefinition> definitions;
    private final int controllerSegment;
    private final int controllerX;
    private final int controllerZ;
    private final Map<PartRole, CountRequirement> countRequirements;
    private final boolean allowMirror;
    private final Set<PropertyRule> orientationSensitiveRules;
    private final Map<Orientation, Boolean> orientationSupport = new ConcurrentHashMap<>();
    private final Map<Orientation, CompiledBlueprint> compiled = new ConcurrentHashMap<>();
    /** Lazily materialized cell list — UI/preview surfaces only; engine paths walk the grid. */
    private volatile @Nullable List<Cell> minCells;

    private Blueprint(
                      List<Segment> segments,
                      Map<Character, CellDefinition> definitions,
                      int controllerSegment,
                      int controllerX,
                      int controllerZ,
                      Map<PartRole, CountRequirement> countRequirements,
                      boolean allowMirror) {
        this.segments = List.copyOf(segments);
        this.definitions = Map.copyOf(definitions);
        this.controllerSegment = controllerSegment;
        this.controllerX = controllerX;
        this.controllerZ = controllerZ;
        this.countRequirements = Map.copyOf(countRequirements);
        this.allowMirror = allowMirror;
        this.orientationSensitiveRules = collectOrientationSensitiveRules(this.definitions);
        validateCountRequirements();
        validateRuntimeStateProperties(this.definitions);
    }

    public static Builder of() {
        return new Builder();
    }

    /**
     * The cells at the minimum repeat count of every segment. <b>Materializes one object per
     * cell</b> — preview/UI surfaces only; recognition and claims never call this (they walk the
     * compiled grid). Lazy and cached, so a million-cell blueprint pays nothing at bootstrap.
     */
    public List<Cell> cells() {
        List<Cell> cached = minCells;
        if (cached == null) {
            cached = cells(defaultRepeats());
            minCells = cached;
        }
        return cached;
    }

    /** The number of (non-empty) cells under {@code resolution}, computed from the grid — no materialization. */
    public int cellCount(RepeatResolution resolution) {
        Objects.requireNonNull(resolution, "repeat resolution");
        int total = 0;
        for (int s = 0; s < segments.size(); s++) {
            total += segments.get(s).nonEmptyCells() * resolution.count(s);
        }
        return total;
    }

    /** {@link #cellCount} at the maximal repeats — the capture/threshold sizing figure. */
    public int maxCellCount() {
        return cellCount(maxRepeats());
    }

    /**
     * Expands the blueprint into cells using the chosen per-segment repeat counts. Cells are
     * <b>computed per resolution</b> (按解析计算) rather than stored flat: a repeatable segment shifts
     * the controller-relative positions of later cells with its count, so every call derives the list
     * from the segment grid for exactly the given resolution.
     */
    private List<Cell> cells(RepeatResolution resolution) {
        Objects.requireNonNull(resolution, "repeat resolution");
        List<Cell> all = new ArrayList<>();
        for (int segment = 0; segment < segments.size(); segment++) {
            int count = resolution.count(segment);
            for (int copy = 0; copy < count; copy++) {
                all.addAll(segmentCells(segment, copy, resolution));
            }
        }
        return List.copyOf(all);
    }

    /**
     * The cells of one {@code copy} of {@code segment} under {@code resolution}, with local positions
     * relative to the controller (whose absolute Y depends on the resolved counts of earlier segments).
     */
    private List<Cell> segmentCells(int segment, int copy, RepeatResolution resolution) {
        Objects.requireNonNull(resolution, "repeat resolution");
        Segment seg = segments.get(segment);
        int controllerAbsY = 0;
        for (int j = 0; j < controllerSegment; j++) {
            controllerAbsY += resolution.count(j);
        }
        int baseAbsY = 0;
        for (int j = 0; j < segment; j++) {
            baseAbsY += resolution.count(j);
        }
        int localY = baseAbsY + copy - controllerAbsY;
        List<String> rows = seg.rows();
        List<Cell> cells = new ArrayList<>();
        for (int z = 0; z < rows.size(); z++) {
            String row = rows.get(z);
            for (int x = 0; x < row.length(); x++) {
                char symbol = row.charAt(x);
                if (symbol == ' ') {
                    continue;
                }
                CellDefinition definition = definitions.get(symbol);
                cells.add(new Cell(
                        new BlockPos(x - controllerX, localY, z - controllerZ),
                        definition.expectedState(),
                        definition.rules(),
                        definition.predicate(),
                        definition.maskedProperties()));
            }
        }
        return cells;
    }

    /** The resolution that picks every segment's minimum count. */
    public RepeatResolution defaultRepeats() {
        int[] counts = new int[segments.size()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = segments.get(i).repeat().min();
        }
        return new RepeatResolution(counts);
    }

    /** The resolution that picks every segment's maximum count. */
    public RepeatResolution maxRepeats() {
        int[] counts = new int[segments.size()];
        for (int i = 0; i < counts.length; i++) {
            counts[i] = segments.get(i).repeat().max();
        }
        return new RepeatResolution(counts);
    }

    public Map<PartRole, CountRequirement> countRequirements() {
        return countRequirements;
    }

    public boolean allowMirror() {
        return allowMirror;
    }

    /**
     * Whether every {@link PropertyRule} attached to a state-pinning cell can represent
     * {@code orientation} ({@link PropertyRule#supports(Orientation)} on all of them). Rules on cells
     * without an expected state never fold and are ignored. The answer is computed once per blueprint
     * &times; orientation and cached — the orientation group is finite (48 interned members).
     */
    public boolean supports(Orientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        return orientationSupport.computeIfAbsent(orientation, this::computeSupports);
    }

    /**
     * The blueprint compiled for {@code orientation} — per-definition acceptable-state sets, the
     * symbol-ordinal grid, arithmetic world→cell addressing, chunk-sorted capture order. Computed
     * once per blueprint &times; orientation and cached (the orientation group is finite and
     * interned).
     */
    public CompiledBlueprint compiled(Orientation orientation) {
        Objects.requireNonNull(orientation, "orientation");
        return compiled.computeIfAbsent(orientation, o -> CompiledBlueprint.compile(this, o));
    }

    /**
     * Read-only grid view for compilation: raw segment rows, dimensions, controller grid coords,
     * per-segment copy-0 local Y (fixed under the V1 repeat constraint), and one prototype
     * {@link Cell} per symbol (local position {@link BlockPos#ZERO} — positional fields are
     * reconstructed arithmetically by the consumer).
     */
    GridView gridView() {
        Map<Character, Cell> prototypes = new LinkedHashMap<>();
        for (Map.Entry<Character, CellDefinition> entry : definitions.entrySet()) {
            CellDefinition definition = entry.getValue();
            prototypes.put(
                    entry.getKey(),
                    new Cell(
                            BlockPos.ZERO,
                            definition.expectedState(),
                            definition.rules(),
                            definition.predicate(),
                            definition.maskedProperties()));
        }
        int[] baseLocalY = new int[segments.size()];
        RepeatResolution mins = defaultRepeats();
        int controllerAbsY = 0;
        for (int j = 0; j < controllerSegment; j++) {
            controllerAbsY += mins.count(j);
        }
        int baseAbsY = 0;
        for (int s = 0; s < segments.size(); s++) {
            baseLocalY[s] = baseAbsY - controllerAbsY;
            baseAbsY += mins.count(s);
        }
        return new GridView(this, prototypes, baseLocalY);
    }

    /** See {@link #gridView()}. */
    public record GridView(Blueprint blueprint, Map<Character, Cell> definitionPrototypes, int[] baseLocalY) {

        public int segmentCount() {
            return blueprint.segments.size();
        }

        public List<String> segmentRows(int segment) {
            return blueprint.segments.get(segment).rows();
        }

        public RepeatRange repeatRange(int segment) {
            return blueprint.segments.get(segment).repeat();
        }

        public int segmentBaseLocalY(int segment) {
            return baseLocalY[segment];
        }

        public int width() {
            return blueprint.segments.get(0).rows().get(0).length();
        }

        public int depth() {
            return blueprint.segments.get(0).rows().size();
        }

        public int controllerX() {
            return blueprint.controllerX;
        }

        public int controllerZ() {
            return blueprint.controllerZ;
        }
    }

    private boolean computeSupports(Orientation orientation) {
        for (PropertyRule rule : orientationSensitiveRules) {
            if (!rule.supports(orientation)) {
                return false;
            }
        }
        return true;
    }

    /** The distinct rules of every definition that pins an expected state (the only rules that fold). */
    private static Set<PropertyRule> collectOrientationSensitiveRules(Map<Character, CellDefinition> definitions) {
        Set<PropertyRule> sensitive = new LinkedHashSet<>();
        for (CellDefinition definition : definitions.values()) {
            if (definition.expectedState() != null) {
                sensitive.addAll(definition.rules());
            }
        }
        return Set.copyOf(sensitive);
    }

    private static void validateRuntimeStateProperties(Map<Character, CellDefinition> definitions) {
        for (CellDefinition definition : definitions.values()) {
            BlockState expected = definition.expectedState();
            if (expected == null) {
                continue;
            }
            for (Property<?> property : RUNTIME_MACHINE_STATE_PROPERTIES) {
                if (expected.hasProperty(property) && !definition.maskedProperties().contains(property)) {
                    throw new IllegalStateException(
                            "Blueprint symbol '" + definition.symbol() + "' pins an expected state carrying" + " the runtime machine property '" + property.getName() + "', which flips while the machine runs and would unform the structure:" + " drop the property from the expected state, or mask it with a" + " PropertyRules.derived(...) rule on the same cell");
                }
            }
        }
    }

    /** Grid-walk validation (per definition × occurrences), so it never materializes cells. */
    private void validateCountRequirements() {
        for (CountRequirement requirement : countRequirements.values()) {
            PartRole capability = requirement.role();
            int maxAchievable = 0;
            for (int s = 0; s < segments.size(); s++) {
                Segment segment = segments.get(s);
                for (String row : segment.rows()) {
                    for (int x = 0; x < row.length(); x++) {
                        char symbol = row.charAt(x);
                        if (symbol == ' ') {
                            continue;
                        }
                        if (definitions.get(symbol).predicate().partRoles().contains(capability)) {
                            maxAchievable += segment.repeat().max();
                        }
                    }
                }
            }
            if (maxAchievable == 0) {
                throw new IllegalStateException(
                        "Count requirement " + capability.id() + " has no matching capability cell");
            }
            if (requirement.min() > maxAchievable) {
                throw new IllegalArgumentException(
                        "Count requirement " + capability.id() + " demands a minimum of " + requirement.min() + " but at most " + maxAchievable + " blueprint cell(s) can ever carry that capability");
            }
        }
    }

    /** Inclusive repeat span of a segment; {@code (1, 1)} for a non-repeatable segment. */
    public record RepeatRange(int min, int max) {

        public RepeatRange {
            if (min < 1) {
                throw new IllegalArgumentException("Repeat minimum must be at least 1");
            }
            if (max < min) {
                throw new IllegalArgumentException("Repeat maximum must be >= minimum");
            }
        }
    }

    public record CountRequirement(PartRole role, int min, int max) {

        public CountRequirement {
            role = Objects.requireNonNull(role, "part role");
            if (min < 0) {
                throw new IllegalArgumentException("Minimum count must be non-negative");
            }
            if (max < min) {
                throw new IllegalArgumentException("Maximum count must be >= minimum count");
            }
        }

        public boolean accepts(int count) {
            return count >= min && count <= max;
        }
    }

    public static final class Builder {

        private final List<List<String>> segmentRows = new ArrayList<>();
        private final List<RepeatRange> segmentRepeats = new ArrayList<>();
        private final Map<Character, CellDefinition> definitions = new LinkedHashMap<>();
        private final Map<PartRole, CountRequirement> countRequirements = new LinkedHashMap<>();
        private boolean allowMirror;
        private int width = -1;
        private int depth = -1;

        private Builder() {}

        public Builder aisle(String... rows) {
            Objects.requireNonNull(rows, "aisle rows");
            if (rows.length == 0) {
                throw new IllegalArgumentException("Aisle must contain at least one row");
            }
            int rowWidth = rows[0].length();
            if (rowWidth == 0) {
                throw new IllegalArgumentException("Aisle rows must not be empty");
            }
            for (String row : rows) {
                Objects.requireNonNull(row, "aisle row");
                if (row.length() != rowWidth) {
                    throw new IllegalArgumentException("Every row in an aisle must have the same width");
                }
            }
            if (width < 0) {
                width = rowWidth;
                depth = rows.length;
            } else if (width != rowWidth || depth != rows.length) {
                throw new IllegalArgumentException("Every aisle must have the same width and depth");
            }
            segmentRows.add(List.of(rows));
            segmentRepeats.add(new RepeatRange(1, 1));
            return this;
        }

        /**
         * Makes the most recently declared aisle repeatable along the stacking axis, stacking between
         * {@code min} and {@code max} copies. Additive: aisles default to {@code (1, 1)}.
         */
        public Builder repeatable(int min, int max) {
            if (segmentRepeats.isEmpty()) {
                throw new IllegalStateException("repeatable() must follow an aisle()");
            }
            segmentRepeats.set(segmentRepeats.size() - 1, new RepeatRange(min, max));
            return this;
        }

        public Builder where(char symbol, CellPredicate predicate) {
            return where(symbol, predicate, null, NO_RULES);
        }

        public Builder where(char symbol, CellPredicate predicate, @Nullable BlockState expectedState) {
            return where(symbol, predicate, expectedState, NO_RULES);
        }

        /**
         * Defines a symbol's predicate, its canonical (pre-orientation) expected state, and the
         * {@link PropertyRule}s that govern how that state co-varies with the orientation during
         * recognition.
         */
        public Builder where(
                             char symbol,
                             CellPredicate predicate,
                             @Nullable BlockState expectedState,
                             PropertyRule... rules) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("Space is reserved for empty blueprint cells");
            }
            Objects.requireNonNull(rules, "property rules");
            CellDefinition previous = definitions.put(
                    symbol,
                    new CellDefinition(symbol, predicate, expectedState, new LinkedHashSet<>(Arrays.asList(rules))));
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate blueprint symbol definition: '" + symbol + "'");
            }
            return this;
        }

        public Builder count(PartRole capability, int min, int max) {
            CountRequirement requirement = new CountRequirement(capability, min, max);
            CountRequirement previous = countRequirements.put(capability, requirement);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate count requirement for " + capability.id());
            }
            return this;
        }

        public Builder allowMirror(boolean allowMirror) {
            this.allowMirror = allowMirror;
            return this;
        }

        public Blueprint build() {
            if (segmentRows.isEmpty()) {
                throw new IllegalStateException("Blueprint must contain at least one aisle");
            }

            Set<Character> usedSymbols = new LinkedHashSet<>();
            int controllerCount = 0;
            int controllerSegment = -1;
            int controllerX = -1;
            int controllerZ = -1;
            for (int segment = 0; segment < segmentRows.size(); segment++) {
                List<String> rows = segmentRows.get(segment);
                for (int z = 0; z < rows.size(); z++) {
                    String row = rows.get(z);
                    for (int x = 0; x < row.length(); x++) {
                        char symbol = row.charAt(x);
                        if (symbol == ' ') {
                            continue;
                        }
                        usedSymbols.add(symbol);
                        if (symbol == CONTROLLER_SYMBOL) {
                            controllerSegment = segment;
                            controllerX = x;
                            controllerZ = z;
                            controllerCount++;
                        }
                    }
                }
            }
            if (controllerCount != 1) {
                throw new IllegalStateException("Blueprint must contain exactly one '@' controller cell");
            }
            validateDefinitions(usedSymbols);
            RepeatRange controllerRange = segmentRepeats.get(controllerSegment);
            if (controllerRange.min() != 1 || controllerRange.max() != 1) {
                throw new IllegalStateException("The controller's aisle must not be repeatable");
            }
            // V1 repeat resolution is frame-stable only when a repeatable aisle's controller-relative
            // positions do not move as the count changes — which holds exactly when at most one aisle
            // repeats and it is the last one (every preceding aisle, including the controller's, fixed).
            // Then the maximal-footprint snapshot already contains every position any candidate count
            // probes. Richer layouts (interior or multiple repeatable aisles) are a later enhancement.
            int repeatableCount = 0;
            for (int segment = 0; segment < segmentRepeats.size(); segment++) {
                RepeatRange range = segmentRepeats.get(segment);
                if (range.min() != range.max()) {
                    repeatableCount++;
                    if (segment != segmentRepeats.size() - 1) {
                        throw new IllegalStateException("A repeatable aisle must be the last aisle");
                    }
                }
            }
            if (repeatableCount > 1) {
                throw new IllegalStateException("At most one repeatable aisle is supported");
            }

            List<Segment> segments = new ArrayList<>(segmentRows.size());
            for (int segment = 0; segment < segmentRows.size(); segment++) {
                segments.add(new Segment(List.copyOf(segmentRows.get(segment)), segmentRepeats.get(segment)));
            }
            return new Blueprint(
                    segments,
                    definitions,
                    controllerSegment,
                    controllerX,
                    controllerZ,
                    countRequirements,
                    allowMirror);
        }

        private void validateDefinitions(Set<Character> usedSymbols) {
            for (Character used : usedSymbols) {
                if (!definitions.containsKey(used)) {
                    throw new IllegalStateException("Missing blueprint definition for symbol '" + used + "'");
                }
            }
            for (Character defined : definitions.keySet()) {
                if (!usedSymbols.contains(defined)) {
                    throw new IllegalStateException("Blueprint definition for symbol '" + defined + "' is unused");
                }
            }
        }
    }

    private record Segment(List<String> rows, RepeatRange repeat) {

        private Segment {
            rows = List.copyOf(rows);
            repeat = Objects.requireNonNull(repeat, "repeat range");
        }

        private int nonEmptyCells() {
            int count = 0;
            for (String row : rows) {
                for (int x = 0; x < row.length(); x++) {
                    if (row.charAt(x) != ' ') {
                        count++;
                    }
                }
            }
            return count;
        }
    }

    private record CellDefinition(
                                  char symbol,
                                  CellPredicate predicate,
                                  @Nullable BlockState expectedState,
                                  Set<PropertyRule> rules,
                                  Set<Property<?>> maskedProperties) {

        private CellDefinition {
            predicate = Objects.requireNonNull(predicate, "cell predicate");
            rules = Set.copyOf(Objects.requireNonNull(rules, "property rules"));
            maskedProperties = Set.copyOf(Objects.requireNonNull(maskedProperties, "masked properties"));
        }

        /**
         * Precomputes the masked-property union once per definition (§3: no per-compare allocation),
         * including the automatic environment-mutable mask (waterlogged) of the pinned state.
         */
        private CellDefinition(
                               char symbol,
                               CellPredicate predicate,
                               @Nullable BlockState expectedState,
                               Set<PropertyRule> rules) {
            this(symbol, predicate, expectedState, rules, Cell.maskedPropertiesOf(rules, expectedState));
        }
    }
}
