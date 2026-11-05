package net.ptcrys.topo.api.machine.multiblock.pattern;

import net.ptcrys.topo.api.machine.FormedMachineBlock;
import net.ptcrys.topo.api.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Immutable description of a multiblock structure: an ordered stack of layers in the controller's
 * canonical frame.
 *
 * <h2>Layers</h2>
 *
 * A blueprint is built from {@link Builder#aisle aisle} layers. Each layer is a rectangular grid:
 * characters run along canonical {@code +X}, rows along canonical {@code +Z}, and layers stack along
 * canonical {@code +Y}. The {@code '@'} cell is the controller anchor and defines the origin; the
 * whole declaration is normalized so {@code '@'} becomes {@code (0, 0, 0)}. An {@code aisle} may be
 * empty (all spaces), which simply reserves a layer.
 *
 * <h2>Frame</h2>
 *
 * {@link Builder#frame} rotates the whole blueprint relative to {@code '@'}: it maps canonical
 * {@code +Y} onto {@code up}, canonical {@code +Z} onto {@code front}, and canonical {@code +X} onto
 * {@code up × front}. The frame is a proper rotation (24 choices), applied after the canonical
 * layout and before the machine orientation. Blueprint cells are always authored in canonical
 * coordinates, so {@code aisle} stays an X×Z grid regardless of the frame.
 *
 * <h2>Repeat sections</h2>
 *
 * A repeat section ({@link Builder#repeat} or {@link Builder#beginRepeatable}) is a contiguous run of
 * layers copied as a unit along canonical {@code +Y}. Its thickness is the number of layers it
 * contains; copy {@code i} is placed {@code i * thickness} layers further from the controller, and
 * layers beyond the section on that side shift by {@code (count - 1) * thickness}. Sections cannot
 * nest, cannot contain the controller layer, and may appear before or after the controller.
 *
 * <h2>Recognition</h2>
 *
 * {@link RepeatResolution} holds one concrete copy count per section. {@link #cells(RepeatResolution)}
 * materializes the canonical layout; the recognition engine walks it through
 * {@link CompiledBlueprint}. Section counts are resolved from the controller outward: mandatory
 * copies are matched first, then additional copies are probed until the first one that does not
 * fully match.
 *
 * <p>
 * {@link #supportsSnapshot()} reports whether every cell's controller-relative position is
 * independent of every resolved count. Only such <b>frame-stable</b> blueprints may be recognized
 * through a dense max-footprint snapshot; unstable layouts use a live view.
 *
 * <h2>Directional blocks</h2>
 *
 * A cell may pin a canonical (pre-orientation) expected state together with
 * {@link PropertyRule}s. The recognition engine folds {@link PropertyRule.Classification#COVARIANT}
 * rules forward through the effective orientation (machine orientation composed with the blueprint
 * frame) before comparing, masks {@link PropertyRule#maskedProperties() masked} properties, and
 * accepts alternate encodings. A blueprint whose rules cannot represent an orientation reports
 * {@link RecognitionResult.FailureReason#ORIENTATION_UNSUPPORTED} instead of silently matching.
 */
public final class Blueprint {

    private static final char CONTROLLER_SYMBOL = '@';
    private static final PropertyRule[] NO_RULES = new PropertyRule[0];
    private static final long NO_DECLARED_CELL = -1L;

    /**
     * Machine block-state properties that flip at runtime (multiblock formed flag, recipe active
     * flag). Pinning them in an expected state would unform/reform structures as they run, so
     * {@code build()} rejects them unless a derived rule on the same cell masks them out.
     */
    private static final List<Property<?>> RUNTIME_MACHINE_STATE_PROPERTIES = List.of(FormedMachineBlock.FORMED, OrientedActiveMachineBlock.ACTIVE);

    private final List<DeclaredCell> cells;
    private final List<Brick> bricks;
    private final List<RepeatSection> sections;
    private final Map<Character, CellDefinition> definitions;
    private final List<Character> symbolOrder;
    private final int controllerCell;
    private final int controllerBrick;
    private final int[] controllerDecl;
    private final Orientation frame;
    private final boolean frameStable;
    private final int maxCellCount;
    private final Map<PartRole, CountRequirement> countRequirements;
    private final boolean allowMirror;
    private final Long2LongOpenHashMap declaredRefs;
    private final Set<PropertyRule> orientationSensitiveRules;
    private final Map<Orientation, Boolean> orientationSupport = new ConcurrentHashMap<>();
    private final Map<Orientation, CompiledBlueprint> compiled = new ConcurrentHashMap<>();
    /** Lazily materialized minimum-layout cell list — UI/preview/module surfaces only. */
    private volatile @Nullable List<Cell> minCells;

    private Blueprint(
                      List<DeclaredCell> cells,
                      List<Brick> bricks,
                      List<RepeatSection> sections,
                      Map<Character, CellDefinition> definitions,
                      List<Character> symbolOrder,
                      int controllerCell,
                      int controllerBrick,
                      Orientation frame,
                      boolean frameStable,
                      int maxCellCount,
                      Map<PartRole, CountRequirement> countRequirements,
                      boolean allowMirror) {
        this.cells = List.copyOf(cells);
        this.bricks = List.copyOf(bricks);
        this.sections = List.copyOf(sections);
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
        this.symbolOrder = List.copyOf(symbolOrder);
        this.controllerCell = controllerCell;
        this.controllerBrick = controllerBrick;
        DeclaredCell controller = cells.get(controllerCell);
        this.controllerDecl = new int[] { controller.x(), controller.y(), controller.z() };
        this.frame = Objects.requireNonNull(frame, "blueprint frame");
        this.frameStable = frameStable;
        this.maxCellCount = maxCellCount;
        this.countRequirements = Map.copyOf(countRequirements);
        this.allowMirror = allowMirror;
        this.declaredRefs = buildDeclaredRefs(this.bricks);
        this.orientationSensitiveRules = collectOrientationSensitiveRules(this.definitions);
        validateCountRequirements();
        validateRuntimeStateProperties(this.definitions);
    }

    public static Builder of() {
        return new Builder();
    }

    // ------------------------------------------------------------------------------------------
    // Public surface — declaration data and layout.
    // ------------------------------------------------------------------------------------------

    /** Number of repeat sections; {@link RepeatResolution}s are indexed by this order. */
    public int sectionCount() {
        return sections.size();
    }

    /** One repeat section, in declaration order. */
    public RepeatSection section(int index) {
        return sections.get(index);
    }

    /** All repeat sections, in declaration order. */
    public List<RepeatSection> sections() {
        return sections;
    }

    /**
     * The cells at the minimum count of every section. Materializes one object per cell —
     * preview/UI/module surfaces only; recognition walks {@link CompiledBlueprint} instead.
     */
    public List<Cell> cells() {
        List<Cell> cached = minCells;
        if (cached == null) {
            cached = List.copyOf(cells(minRepeats()));
            minCells = cached;
        }
        return cached;
    }

    /**
     * Expands the blueprint into canonical cells for the given per-section counts. Positions are
     * computed from the layer grid plus the section shifts — nothing is stored flat, so the same
     * blueprint can be laid out at any resolution.
     */
    public List<Cell> cells(RepeatResolution resolution) {
        Objects.requireNonNull(resolution, "repeat resolution");
        requireResolutionSize(resolution);
        List<Cell> out = new ArrayList<>(cellCount(resolution));
        int[] counts = resolution.countsCopy();
        int[] controllerShift = shiftOf(bricks.get(controllerBrick), counts);
        for (Brick brick : bricks) {
            int[] shift = shiftOf(brick, counts);
            int copies = brick.section() < 0 ? 1 : counts[brick.section()];
            int stride = brick.section() < 0 ? 0 : sections.get(brick.section()).stride();
            for (int copy = 0; copy < copies; copy++) {
                int layerOffset = copy * stride;
                for (int k = 0; k < brick.cellCount(); k++) {
                    int lx = brick.xs()[k] + shift[0] - controllerDecl[0] - controllerShift[0];
                    int ly = brick.ys()[k] + shift[1] - controllerDecl[1] - controllerShift[1] + layerOffset;
                    int lz = brick.zs()[k] + shift[2] - controllerDecl[2] - controllerShift[2];
                    out.add(cellOf(brick.definitions()[k], new BlockPos(lx, ly, lz)));
                }
            }
        }
        return List.copyOf(out);
    }

    /** Number of cells under {@code resolution} — arithmetic only, no materialization. */
    public int cellCount(RepeatResolution resolution) {
        Objects.requireNonNull(resolution, "repeat resolution");
        requireResolutionSize(resolution);
        int total = 0;
        for (Brick brick : bricks) {
            total += brick.cellCount() * (brick.section() < 0 ? 1 : resolution.count(brick.section()));
        }
        return total;
    }

    /** The maximal footprint size — the snapshot/threshold sizing figure. */
    public int maxCellCount() {
        return maxCellCount;
    }

    /** Every section at its minimum count. */
    public RepeatResolution minRepeats() {
        int[] counts = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            counts[i] = sections.get(i).minCount();
        }
        return new RepeatResolution(counts);
    }

    /** Every section at its maximum count. */
    public RepeatResolution maxRepeats() {
        int[] counts = new int[sections.size()];
        for (int i = 0; i < sections.size(); i++) {
            counts[i] = sections.get(i).maxCount();
        }
        return new RepeatResolution(counts);
    }

    /**
     * Whether every cell's controller-relative position is independent of the resolved section
     * counts. Only frame-stable blueprints can be recognized through a dense max-footprint snapshot;
     * unstable layouts shift cells when a count changes and must be read from a live view.
     */
    public boolean supportsSnapshot() {
        return frameStable;
    }

    public Map<PartRole, CountRequirement> countRequirements() {
        return countRequirements;
    }

    public boolean allowMirror() {
        return allowMirror;
    }

    /** The blueprint's local frame: canonical axes mapped onto the blueprint's {@code @}-relative axes. */
    public Orientation frame() {
        return frame;
    }

    /** The orientation a recognition pass actually uses: {@code machineOrientation ∘ frame}. */
    public Orientation effectiveOrientation(Orientation machineOrientation) {
        return Objects.requireNonNull(machineOrientation, "orientation").compose(frame);
    }

    /**
     * Whether every {@link PropertyRule} attached to a state-pinning cell can represent the
     * effective orientation ({@code machineOrientation ∘ frame}). Computed once per blueprint ×
     * machine orientation and cached.
     */
    public boolean supports(Orientation machineOrientation) {
        Objects.requireNonNull(machineOrientation, "orientation");
        return orientationSupport.computeIfAbsent(machineOrientation, this::computeSupports);
    }

    /**
     * The blueprint compiled for {@code machineOrientation}, with the blueprint frame folded into
     * the effective orientation. Per-symbol acceptable-state sets, primitive cell grids, arithmetic
     * world→cell addressing and chunk-sorted capture order are computed once per orientation.
     */
    public CompiledBlueprint compiled(Orientation machineOrientation) {
        Objects.requireNonNull(machineOrientation, "orientation");
        return compiled.computeIfAbsent(machineOrientation, orientation -> CompiledBlueprint.compile(this, orientation, orientation.compose(frame)));
    }

    // ------------------------------------------------------------------------------------------
    // Package-private surface for the compiled form and the engine.
    // ------------------------------------------------------------------------------------------

    List<Brick> bricksInternal() {
        return bricks;
    }

    List<CellDefinition> symbolDefinitions() {
        List<CellDefinition> ordered = new ArrayList<>(symbolOrder.size());
        for (Character symbol : symbolOrder) {
            ordered.add(definitions.get(symbol));
        }
        return ordered;
    }

    int controllerBrick() {
        return controllerBrick;
    }

    int[] controllerDecl() {
        return controllerDecl.clone();
    }

    /** Shift contributed by earlier sections to {@code brick} under {@code counts}. */
    int[] shiftOf(Brick brick, int[] counts) {
        int[] shift = new int[3];
        for (int dep : brick.dependencies()) {
            RepeatSection section = sections.get(dep);
            int delta = (counts[dep] - 1) * section.stride();
            shift[section.axis().ordinal()] += delta;
        }
        return shift;
    }

    int[] minShiftOf(Brick brick) {
        int[] shift = new int[3];
        for (int dep : brick.dependencies()) {
            RepeatSection section = sections.get(dep);
            shift[section.axis().ordinal()] += (section.minCount() - 1) * section.stride();
        }
        return shift;
    }

    int[] maxShiftOf(Brick brick) {
        int[] shift = new int[3];
        for (int dep : brick.dependencies()) {
            RepeatSection section = sections.get(dep);
            shift[section.axis().ordinal()] += (section.maxCount() - 1) * section.stride();
        }
        return shift;
    }

    /** Declared cell reference packed as {@code (brick << 32) | memberOrdinal}, or -1. */
    long declaredRefAt(long position) {
        return declaredRefs.get(position);
    }

    static long packRef(int brick, int member) {
        return ((long) brick << 32) | (member & 0xFFFFFFFFL);
    }

    static int refBrick(long ref) {
        return (int) (ref >>> 32);
    }

    static int refMember(long ref) {
        return (int) ref;
    }

    int declaredCellCount() {
        return cells.size();
    }

    DeclaredCell declaredCell(int index) {
        return cells.get(index);
    }

    Cell cellOf(int symbolOrdinal, BlockPos localPos) {
        CellDefinition definition = definitions.get(symbolOrder.get(symbolOrdinal));
        return new Cell(
                localPos,
                definition.expectedState(),
                definition.rules(),
                definition.predicate(),
                definition.maskedProperties());
    }

    CellDefinition definitionOf(char symbol) {
        return definitions.get(symbol);
    }

    private void requireResolutionSize(RepeatResolution resolution) {
        if (resolution.size() != sections.size()) {
            throw new IllegalArgumentException(
                    "Repeat resolution has " + resolution.size() + " counts but the blueprint declares " + sections.size() + " repeat section(s)");
        }
    }

    private boolean computeSupports(Orientation machineOrientation) {
        Orientation effective = machineOrientation.compose(frame);
        for (PropertyRule rule : orientationSensitiveRules) {
            if (!rule.supports(effective)) {
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

    private static Long2LongOpenHashMap buildDeclaredRefs(List<Brick> bricks) {
        Long2LongOpenHashMap refs = new Long2LongOpenHashMap();
        refs.defaultReturnValue(NO_DECLARED_CELL);
        for (int b = 0; b < bricks.size(); b++) {
            Brick brick = bricks.get(b);
            for (int k = 0; k < brick.cellCount(); k++) {
                long key = BlockPos.asLong(brick.xs()[k], brick.ys()[k], brick.zs()[k]);
                refs.put(key, packRef(b, k));
            }
        }
        return refs;
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

    /** Grid-walk validation (per definition × multiplicity), so it never materializes cells. */
    private void validateCountRequirements() {
        for (CountRequirement requirement : countRequirements.values()) {
            PartRole capability = requirement.role();
            int maxAchievable = 0;
            for (Brick brick : bricks) {
                int multiplicity = brick.section() < 0 ? 1 : sections.get(brick.section()).maxCount();
                for (int k = 0; k < brick.cellCount(); k++) {
                    if (definitions.get(symbolOrder.get(brick.definitions()[k])).predicate().partRoles().contains(capability)) {
                        maxAchievable += multiplicity;
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

    // ------------------------------------------------------------------------------------------
    // Value types.
    // ------------------------------------------------------------------------------------------

    /** Inclusive copy-count span of a repeat section; {@code min == max} is fixed. */
    public record RepeatRange(int min, int max) {

        public RepeatRange {
            if (min < 0) {
                throw new IllegalArgumentException("Repeat minimum must be non-negative");
            }
            if (max < min) {
                throw new IllegalArgumentException("Repeat maximum must be >= minimum");
            }
        }

        public boolean fixed() {
            return min == max;
        }
    }

    /**
     * One repeat section in layout terms. {@code bandStart} is the minimum normalized layer index of
     * its layers, {@code bandEnd} the maximum plus one, and {@code stride} the layer count — copies
     * advance by one stride per count.
     */
    public record RepeatSection(
                                Direction.Axis axis,
                                RepeatRange repeat,
                                int bandStart,
                                int bandEnd,
                                int stride,
                                int cellCount) {

        public RepeatSection {
            Objects.requireNonNull(axis, "repeat axis");
            Objects.requireNonNull(repeat, "repeat range");
            if (bandEnd <= bandStart) {
                throw new IllegalArgumentException("Repeat section band must be non-empty");
            }
            if (stride != bandEnd - bandStart) {
                throw new IllegalArgumentException("Repeat section stride must equal the band length");
            }
            if (cellCount <= 0) {
                throw new IllegalArgumentException("Repeat section must contain at least one cell");
            }
        }

        public int minCount() {
            return repeat.min();
        }

        public int maxCount() {
            return repeat.max();
        }

        public boolean repeatable() {
            return !repeat.fixed();
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

    // ------------------------------------------------------------------------------------------
    // Internal declaration model.
    // ------------------------------------------------------------------------------------------

    record DeclaredCell(int x, int y, int z, char symbol, int section) {}

    /**
     * One walk unit: either a single fixed cell ({@code section < 0}, one entry) or a repeat
     * section's members. {@code definitions}, {@code xs/ys/zs} are parallel arrays; {@code
     * dependencies} lists the earlier sections whose growth shifts this brick.
     */
    record Brick(
                 int section,
                 int indexBase,
                 int[] dependencies,
                 int[] definitions,
                 int[] xs,
                 int[] ys,
                 int[] zs) {

        int cellCount() {
            return definitions.length;
        }
    }

    record CellDefinition(
                          char symbol,
                          CellPredicate predicate,
                          @Nullable BlockState expectedState,
                          Set<PropertyRule> rules,
                          Set<Property<?>> maskedProperties) {

        CellDefinition {
            predicate = Objects.requireNonNull(predicate, "cell predicate");
            rules = Set.copyOf(Objects.requireNonNull(rules, "property rules"));
            maskedProperties = Set.copyOf(Objects.requireNonNull(maskedProperties, "masked properties"));
        }

        CellDefinition(
                       char symbol,
                       CellPredicate predicate,
                       @Nullable BlockState expectedState,
                       Set<PropertyRule> rules) {
            this(symbol, predicate, expectedState, rules, Cell.maskedPropertiesOf(rules, expectedState));
        }
    }

    // ------------------------------------------------------------------------------------------
    // Builder.
    // ------------------------------------------------------------------------------------------

    public static final class Builder {

        private final List<Layer> layers = new ArrayList<>();
        private final List<SectionDraft> sections = new ArrayList<>();
        private final List<DeclaredCell> cells = new ArrayList<>();
        private final Map<Character, CellDefinition> definitions = new LinkedHashMap<>();
        private final Map<PartRole, CountRequirement> countRequirements = new LinkedHashMap<>();
        private boolean allowMirror;
        private Orientation frame = Orientation.IDENTITY;
        private boolean frameSet;
        private boolean inSection;
        private @Nullable SectionDraft currentSection;
        private int layerWidth = -1;
        private int layerHeight = -1;

        private Builder() {}

        // ---- frame ----------------------------------------------------------------------------

        /**
         * Rotates the whole blueprint relative to {@code '@'}. {@code up} becomes the layer-stacking
         * direction, {@code front} the row direction and {@code up × front} the character direction.
         * The two directions must be perpendicular (different axes); the default is
         * {@code up = UP, front = SOUTH} (canonical layout, no rotation).
         */
        public Builder frame(Direction up, Direction front) {
            ensureNotInSection("set the frame");
            Objects.requireNonNull(up, "frame up");
            Objects.requireNonNull(front, "frame front");
            if (frameSet) {
                throw new IllegalStateException("frame(...) may be declared only once");
            }
            if (up.getAxis() == front.getAxis()) {
                throw new IllegalArgumentException("frame up and front must lie on different axes: " + up + " / " + front);
            }
            int rx = up.getStepY() * front.getStepZ() - up.getStepZ() * front.getStepY();
            int ry = up.getStepZ() * front.getStepX() - up.getStepX() * front.getStepZ();
            int rz = up.getStepX() * front.getStepY() - up.getStepY() * front.getStepX();
            Direction right = Objects.requireNonNull(Direction.getNearest(rx, ry, rz, null), "frame right direction");
            this.frame = Orientation.fromColumns(right, up, front);
            this.frameSet = true;
            return this;
        }

        // ---- layers ---------------------------------------------------------------------------

        /**
         * Declares one layer: characters run along the frame's character direction, rows along its
         * front direction, and the layer stacks along its up direction.
         */
        public Builder aisle(String... rows) {
            ensureNotInSection("declare a layer");
            addLayer(rows, null);
            return this;
        }

        // ---- repeat sections ------------------------------------------------------------------

        /**
         * Starts a repeat section and enters the section domain: {@link SectionBuilder} can only
         * declare layers, and {@link SectionBuilder#endRepeatable()} returns to this builder.
         */
        public SectionBuilder beginRepeatable(int min, int max) {
            ensureNotInSection("begin a repeat section");
            RepeatRange range = new RepeatRange(min, max);
            if (range.max() < 1) {
                throw new IllegalArgumentException("A repeat section must be able to appear at least once (max >= 1)");
            }
            SectionDraft draft = new SectionDraft(range, layers.size(), sections.size());
            sections.add(draft);
            inSection = true;
            currentSection = draft;
            return new SectionBuilder(this, draft);
        }

        /**
         * Scoped repeat section: {@code body} declares exactly the layers of the section, and the
         * section is closed when the lambda returns. Equivalent to
         * {@code beginRepeatable(min, max) ... endRepeatable()}.
         */
        public Builder repeat(int min, int max, Consumer<SectionBuilder> body) {
            Objects.requireNonNull(body, "repeat section body");
            SectionBuilder section = beginRepeatable(min, max);
            body.accept(section);
            return section.endRepeatable();
        }

        // ---- definitions ----------------------------------------------------------------------

        public Builder where(char symbol, CellPredicate predicate) {
            return where(symbol, predicate, null, NO_RULES);
        }

        public Builder where(char symbol, CellPredicate predicate, @Nullable BlockState expectedState) {
            return where(symbol, predicate, expectedState, NO_RULES);
        }

        /**
         * Defines a symbol's predicate, its canonical (pre-orientation) expected state, and the
         * {@link PropertyRule}s that govern how that state co-varies with the effective orientation
         * during recognition.
         */
        public Builder where(
                             char symbol,
                             CellPredicate predicate,
                             @Nullable BlockState expectedState,
                             PropertyRule... rules) {
            Objects.requireNonNull(predicate, "cell predicate");
            Objects.requireNonNull(rules, "property rules");
            if (symbol == CONTROLLER_SYMBOL) {
                if (predicate != CellPredicates.controller()) {
                    throw new IllegalArgumentException(
                            "The controller symbol '@' is defined automatically as the controller cell; a where('@', ...) definition may only repeat CellPredicates.controller()");
                }
                return this;
            }
            ensureNotInSection("define a symbol");
            validateSymbol(symbol);
            validateRules(symbol, rules);
            CellDefinition definition = new CellDefinition(
                    symbol,
                    predicate,
                    expectedState,
                    new LinkedHashSet<>(Arrays.asList(rules)));
            CellDefinition previous = definitions.put(symbol, definition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate blueprint symbol definition: '" + symbol + "'");
            }
            return this;
        }

        /** Matches a block by identity, without pinning any state. */
        public Builder where(char symbol, Block block) {
            return where(symbol, CellPredicates.block(Objects.requireNonNull(block, "block")));
        }

        /**
         * Matches a block by identity and pins its default state as the canonical expected state,
         * folding the given rules through the effective orientation. The common directional form:
         * {@code where('C', casing, PropertyRules.direction(FACING))}.
         */
        public Builder where(char symbol, Block block, PropertyRule... rules) {
            Objects.requireNonNull(block, "block");
            return where(symbol, CellPredicates.block(block), block.defaultBlockState(), rules);
        }

        /** Matches a block by identity and pins an explicit canonical expected state. */
        public Builder where(char symbol, Block block, BlockState expectedState, PropertyRule... rules) {
            Objects.requireNonNull(block, "block");
            return where(symbol, CellPredicates.block(block), expectedState, rules);
        }

        public Builder count(PartRole capability, int min, int max) {
            ensureNotInSection("declare a count requirement");
            CountRequirement requirement = new CountRequirement(capability, min, max);
            CountRequirement previous = countRequirements.put(capability, requirement);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate count requirement for " + capability.id());
            }
            return this;
        }

        public Builder allowMirror(boolean allowMirror) {
            ensureNotInSection("set allowMirror");
            this.allowMirror = allowMirror;
            return this;
        }

        // ---- build ----------------------------------------------------------------------------

        public Blueprint build() {
            if (inSection) {
                throw new IllegalStateException("A repeat section is still open; call endRepeatable() first");
            }
            if (layers.isEmpty()) {
                throw new IllegalStateException("Blueprint must declare at least one aisle");
            }

            int controllerLayer = -1;
            int controllerRow = -1;
            int controllerChar = -1;
            int controllerCount = 0;
            for (int w = 0; w < layers.size(); w++) {
                Layer layer = layers.get(w);
                for (int v = 0; v < layer.rows().length; v++) {
                    String row = layer.rows()[v];
                    for (int u = 0; u < row.length(); u++) {
                        if (row.charAt(u) == CONTROLLER_SYMBOL) {
                            controllerLayer = w;
                            controllerRow = v;
                            controllerChar = u;
                            controllerCount++;
                        }
                    }
                }
            }
            if (controllerCount != 1) {
                throw new IllegalStateException("Blueprint must contain exactly one '@' controller cell");
            }
            if (layers.get(controllerLayer).section() >= 0) {
                throw new IllegalStateException("The controller cell cannot be inside a repeat section");
            }

            cells.clear();
            int[] sectionFirst = new int[sections.size()];
            Arrays.fill(sectionFirst, -1);
            int[] sectionCells = new int[sections.size()];
            for (int w = 0; w < layers.size(); w++) {
                Layer layer = layers.get(w);
                int section = layer.section();
                if (section >= 0 && sectionFirst[section] < 0) {
                    sectionFirst[section] = cells.size();
                }
                for (int v = 0; v < layer.rows().length; v++) {
                    String row = layer.rows()[v];
                    for (int u = 0; u < row.length(); u++) {
                        char symbol = row.charAt(u);
                        if (symbol == ' ') {
                            continue;
                        }
                        cells.add(new DeclaredCell(
                                u - controllerChar,
                                w - controllerLayer,
                                v - controllerRow,
                                symbol,
                                section));
                        if (section >= 0) {
                            sectionCells[section]++;
                        }
                    }
                }
            }
            for (int s = 0; s < sections.size(); s++) {
                SectionDraft draft = sections.get(s);
                draft.firstCell = sectionFirst[s];
                draft.cellCount = sectionCells[s];
                if (draft.layerCount <= 0) {
                    throw new IllegalStateException("Repeat section #" + s + " contains no layers");
                }
                if (draft.cellCount <= 0) {
                    throw new IllegalStateException("Repeat section #" + s + " contains no cells");
                }
            }
            rejectDuplicatePositions();

            int controllerCell = -1;
            for (int i = 0; i < cells.size(); i++) {
                if (cells.get(i).symbol() == CONTROLLER_SYMBOL) {
                    controllerCell = i;
                    break;
                }
            }
            if (controllerCell < 0) {
                throw new IllegalStateException("Controller cell is missing from the generated cell list");
            }

            List<Character> symbolOrder = validateDefinitions();
            symbolOrder.add(CONTROLLER_SYMBOL);

            List<RepeatSection> sectionInfo = buildSectionInfo(controllerLayer);
            validateSectionLayout(sectionInfo);

            List<Brick> bricks = buildBricks(sectionInfo, symbolOrder);
            int controllerBrick = controllerBrick(bricks, controllerCell);
            boolean frameStable = computeFrameStable(sectionInfo);
            int maxCellCount = 0;
            for (Brick brick : bricks) {
                int multiplicity = brick.section() < 0 ? 1 : sectionInfo.get(brick.section()).maxCount();
                maxCellCount += brick.cellCount() * multiplicity;
            }

            // '@' is structural: its definition is always the controller predicate.
            CellDefinition controllerDefinition = new CellDefinition(
                    CONTROLLER_SYMBOL, CellPredicates.controller(), null, Set.of());
            Map<Character, CellDefinition> finalDefinitions = new LinkedHashMap<>(definitions);
            finalDefinitions.put(CONTROLLER_SYMBOL, controllerDefinition);

            return new Blueprint(
                    cells,
                    bricks,
                    sectionInfo,
                    finalDefinitions,
                    symbolOrder,
                    controllerCell,
                    controllerBrick,
                    frame,
                    frameStable,
                    maxCellCount,
                    countRequirements,
                    allowMirror);
        }

        private void addLayer(String[] rows, @Nullable SectionDraft draft) {
            validateRows(rows);
            int height = rows.length;
            int width = rows[0].length();
            if (layers.isEmpty()) {
                layerWidth = width;
                layerHeight = height;
            } else if (width != layerWidth || height != layerHeight) {
                throw new IllegalArgumentException(
                        "Every blueprint layer must have the same dimensions: expected " + layerWidth + "x" + layerHeight + ", got " + width + "x" + height);
            }
            if (draft != null) {
                if (!inSection || currentSection != draft) {
                    throw new IllegalStateException("Repeat section is not open");
                }
            } else if (inSection) {
                throw new IllegalStateException("Cannot declare a blueprint layer inside an open repeat section; use the section builder");
            }
            layers.add(new Layer(rows, draft == null ? -1 : draft.ordinal));
            if (draft != null) {
                draft.layerCount++;
            }
        }

        private void endSection(SectionDraft draft) {
            if (!inSection || currentSection != draft) {
                throw new IllegalStateException("Repeat section is not open");
            }
            if (draft.layerCount <= 0) {
                throw new IllegalStateException("A repeat section must contain at least one layer");
            }
            inSection = false;
            currentSection = null;
        }

        private void ensureNotInSection(String action) {
            if (inSection) {
                throw new IllegalStateException("Cannot " + action + " while a repeat section is open; call endRepeatable() first");
            }
        }

        private void rejectDuplicatePositions() {
            Map<Long, DeclaredCell> seen = new LinkedHashMap<>();
            for (DeclaredCell cell : cells) {
                DeclaredCell previous = seen.put(BlockPos.asLong(cell.x(), cell.y(), cell.z()), cell);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate blueprint cell at (" + cell.x() + "," + cell.y() + "," + cell.z() + "): symbols '" + previous.symbol() + "' and '" + cell.symbol() + "'");
                }
            }
        }

        private List<Character> validateDefinitions() {
            Set<Character> used = new LinkedHashSet<>();
            for (DeclaredCell cell : cells) {
                if (cell.symbol() != CONTROLLER_SYMBOL) {
                    used.add(cell.symbol());
                }
            }
            if (definitions.containsKey(CONTROLLER_SYMBOL)) {
                throw new IllegalArgumentException(
                        "The controller symbol '@' is reserved and defined automatically as the controller cell; remove its where(...) definition");
            }
            for (Character symbol : used) {
                if (!definitions.containsKey(symbol)) {
                    throw new IllegalStateException("Missing blueprint definition for symbol '" + symbol + "'");
                }
            }
            for (Character symbol : definitions.keySet()) {
                if (!used.contains(symbol)) {
                    throw new IllegalStateException("Blueprint definition for symbol '" + symbol + "' is unused");
                }
            }
            return new ArrayList<>(definitions.keySet());
        }

        private List<RepeatSection> buildSectionInfo(int controllerLayer) {
            List<RepeatSection> info = new ArrayList<>(sections.size());
            for (SectionDraft draft : sections) {
                int start = draft.startLayer - controllerLayer;
                int end = start + draft.layerCount;
                info.add(new RepeatSection(Direction.Axis.Y, draft.repeat, start, end, draft.layerCount, draft.cellCount));
            }
            return info;
        }

        private void validateSectionLayout(List<RepeatSection> info) {
            for (int s = 0; s < info.size(); s++) {
                RepeatSection section = info.get(s);
                SectionDraft draft = sections.get(s);
                int first = draft.firstCell;
                int last = first + draft.cellCount - 1;
                Direction.Axis axis = section.axis();

                for (int i = 0; i < cells.size(); i++) {
                    DeclaredCell cell = cells.get(i);
                    if (cell.section() == s) {
                        continue;
                    }
                    int coordinate = coordinate(cell, axis);
                    if (coordinate >= section.bandStart() && coordinate < section.bandEnd()) {
                        throw new IllegalStateException(
                                "Blueprint cell (" + cell.x() + "," + cell.y() + "," + cell.z() + ") lies inside the band of repeat section #" + s + " (" + axis + " " + section.bandStart() + ".." + (section.bandEnd() - 1) + "); every cell in a repeat band must belong to the section");
                    }
                    if (i < first && coordinate >= section.bandStart()) {
                        throw new IllegalStateException(
                                "Blueprint cell (" + cell.x() + "," + cell.y() + "," + cell.z() + ") is declared before repeat section #" + s + " but lies at or after its band; repeat sections only grow forward, so declare the cell after the section");
                    }
                }

                // A later section must lie entirely before or entirely beyond this band: a section
                // cannot be split by another section's growth.
                for (int other = 0; other < info.size(); other++) {
                    if (other == s) {
                        continue;
                    }
                    SectionDraft otherDraft = sections.get(other);
                    if (otherDraft.firstCell <= last) {
                        continue; // declared before; already covered by the cell rule above
                    }
                    RepeatSection later = info.get(other);
                    int min = Integer.MAX_VALUE;
                    int max = Integer.MIN_VALUE;
                    for (DeclaredCell cell : cells) {
                        if (cell.section() != other) {
                            continue;
                        }
                        int coordinate = coordinate(cell, axis);
                        min = Math.min(min, coordinate);
                        max = Math.max(max, coordinate);
                    }
                    if (min < section.bandStart() && max >= section.bandEnd()) {
                        throw new IllegalStateException(
                                "Repeat section #" + other + " straddles the band of repeat section #" + s + " along " + axis + "; a section must be entirely before or entirely after another band");
                    }
                }
            }
        }

        private List<Brick> buildBricks(List<RepeatSection> info, List<Character> symbolOrder) {
            List<Brick> bricks = new ArrayList<>();
            int[] indexBase = { 0 };
            int cellIndex = 0;
            int sectionOrdinal = 0;
            while (cellIndex < cells.size()) {
                int section = cells.get(cellIndex).section();
                if (section < 0) {
                    bricks.add(buildBrick(-1, cellIndex, List.of(cellIndex), info, symbolOrder, indexBase));
                    cellIndex++;
                    continue;
                }
                if (section != sectionOrdinal) {
                    throw new IllegalStateException("Repeat section cells must be contiguous");
                }
                int end = cellIndex;
                while (end < cells.size() && cells.get(end).section() == section) {
                    end++;
                }
                List<Integer> members = new ArrayList<>(end - cellIndex);
                for (int i = cellIndex; i < end; i++) {
                    members.add(i);
                }
                bricks.add(buildBrick(section, cellIndex, members, info, symbolOrder, indexBase));
                cellIndex = end;
                sectionOrdinal++;
            }
            if (sectionOrdinal != info.size()) {
                throw new IllegalStateException("Repeat section declarations do not match the cell list");
            }
            return bricks;
        }

        private Brick buildBrick(
                                 int section,
                                 int firstCell,
                                 List<Integer> members,
                                 List<RepeatSection> info,
                                 List<Character> symbolOrder,
                                 int[] indexBase) {
            int size = members.size();
            int[] defs = new int[size];
            int[] xs = new int[size];
            int[] ys = new int[size];
            int[] zs = new int[size];
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            int maxZ = Integer.MIN_VALUE;
            for (int k = 0; k < size; k++) {
                DeclaredCell cell = cells.get(members.get(k));
                defs[k] = symbolOrder.indexOf(cell.symbol());
                xs[k] = cell.x();
                ys[k] = cell.y();
                zs[k] = cell.z();
                minX = Math.min(minX, cell.x());
                minY = Math.min(minY, cell.y());
                minZ = Math.min(minZ, cell.z());
                maxX = Math.max(maxX, cell.x());
                maxY = Math.max(maxY, cell.y());
                maxZ = Math.max(maxZ, cell.z());
            }
            List<Integer> dependencies = new ArrayList<>();
            for (int s = 0; s < info.size(); s++) {
                RepeatSection earlier = info.get(s);
                // Only sections declared before this brick can shift it.
                SectionDraft draft = sections.get(s);
                if (draft.firstCell + draft.cellCount > firstCell) {
                    continue;
                }
                int brickMin = switch (earlier.axis()) {
                    case X -> minX;
                    case Y -> minY;
                    case Z -> minZ;
                };
                if (brickMin >= earlier.bandEnd()) {
                    dependencies.add(s);
                }
            }
            int[] deps = new int[dependencies.size()];
            for (int i = 0; i < deps.length; i++) {
                deps[i] = dependencies.get(i);
            }
            int base = indexBase[0];
            int multiplicity = section < 0 ? 1 : info.get(section).maxCount();
            indexBase[0] = base + size * multiplicity;
            return new Brick(section, base, deps, defs, xs, ys, zs);
        }

        private int controllerBrick(List<Brick> bricks, int controllerCell) {
            int cellIndex = 0;
            for (int b = 0; b < bricks.size(); b++) {
                Brick brick = bricks.get(b);
                if (controllerCell >= cellIndex && controllerCell < cellIndex + brick.cellCount()) {
                    return b;
                }
                cellIndex += brick.cellCount();
            }
            throw new IllegalStateException("Controller cell is not part of any brick");
        }

        private boolean computeFrameStable(List<RepeatSection> info) {
            for (int s = 0; s < info.size(); s++) {
                RepeatSection section = info.get(s);
                SectionDraft draft = sections.get(s);
                int last = draft.firstCell + draft.cellCount - 1;
                for (int i = last + 1; i < cells.size(); i++) {
                    DeclaredCell cell = cells.get(i);
                    if (cell.section() == s) {
                        continue;
                    }
                    if (coordinate(cell, section.axis()) >= section.bandEnd()) {
                        return false;
                    }
                }
            }
            return true;
        }

        private static void validateRows(String[] rows) {
            Objects.requireNonNull(rows, "plane rows");
            if (rows.length == 0) {
                throw new IllegalArgumentException("A layer must contain at least one row");
            }
            Objects.requireNonNull(rows[0], "layer row");
            int width = rows[0].length();
            if (width == 0) {
                throw new IllegalArgumentException("Layer rows must not be empty");
            }
            for (String row : rows) {
                Objects.requireNonNull(row, "layer row");
                if (row.length() != width) {
                    throw new IllegalArgumentException("Every row in a layer must have the same width");
                }
            }
        }

        private static void validateSymbol(char symbol) {
            if (symbol == ' ') {
                throw new IllegalArgumentException("Space is reserved for empty blueprint cells");
            }
            if (symbol == CONTROLLER_SYMBOL) {
                throw new IllegalArgumentException(
                        "The controller symbol '@' is reserved and defined automatically; remove its where(...) definition");
            }
        }

        private static void validateRules(char symbol, PropertyRule[] rules) {
            Set<Property<?>> governed = new LinkedHashSet<>();
            for (PropertyRule rule : rules) {
                Objects.requireNonNull(rule, "property rule");
                if (rule.classification() != PropertyRule.Classification.COVARIANT) {
                    continue;
                }
                for (Property<?> property : rule.governedProperties()) {
                    if (!governed.add(property)) {
                        throw new IllegalArgumentException(
                                "Blueprint symbol '" + symbol + "' has more than one covariant rule governing property '" + property.getName() + "'");
                    }
                }
            }
        }

        private static int coordinate(DeclaredCell cell, Direction.Axis axis) {
            return switch (axis) {
                case X -> cell.x();
                case Y -> cell.y();
                case Z -> cell.z();
            };
        }

        private record Layer(String[] rows, int section) {}

        private static final class SectionDraft {

            private final RepeatRange repeat;
            private final int startLayer;
            private final int ordinal;
            private int layerCount;
            private int firstCell;
            private int cellCount;

            private SectionDraft(RepeatRange repeat, int startLayer, int ordinal) {
                this.repeat = repeat;
                this.startLayer = startLayer;
                this.ordinal = ordinal;
            }
        }
    }

    /** Scoped builder handed to {@link Builder#beginRepeatable} and {@link Builder#repeat}. */
    public static final class SectionBuilder {

        private final Builder builder;
        private final Builder.SectionDraft draft;

        private SectionBuilder(Builder builder, Builder.SectionDraft draft) {
            this.builder = builder;
            this.draft = draft;
        }

        /** Declares one layer of this section. */
        public SectionBuilder aisle(String... rows) {
            builder.addLayer(rows, draft);
            return this;
        }

        /** Closes the section and returns to the blueprint domain. */
        public Builder endRepeatable() {
            builder.endSection(draft);
            return builder;
        }
    }
}
