package net.ptcrys.topo.apiv2.machine.multiblock;

import net.ptcrys.topo.api.lang.LangRegistry;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.OrientedMachineBlock;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Cell;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.CellPredicate;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Orientations;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.PropertyRule;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.StructureEngine;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.StructureView;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiComponentStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Client-side structure inspector for the controller GUI: runs the same {@link StructureEngine}
 * recognition pass the server uses, but over a view of the <b>client</b> level, and folds the result
 * into human-readable per-cell {@link Reason}s plus the as-built block map for the structure scene.
 * Pure read path — no sync beyond what the client level already has; the GUI refreshes it on a short
 * tick interval like every other live panel.
 */
public final class MultiblockClientDiagnosis {

    /**
     * Why a structure is not formed: one row of the diagnosis list. {@code title} is the short row
     * form (the expected block's name); {@code message} the full sentence (tooltip); {@code icons}
     * the row's icon candidates — one entry for cell rows, every machine that can fulfil the
     * capability for part-count rows (the UI cycles through them).
     */
    public record Reason(
                         @Nullable BlockPos localPos,
                         Kind kind,
                         Component title,
                         Component message,
                         List<ItemStack> icons) {

        public Reason {
            Objects.requireNonNull(kind, "diagnosis kind");
            Objects.requireNonNull(title, "diagnosis title");
            Objects.requireNonNull(message, "diagnosis message");
            icons = List.copyOf(Objects.requireNonNull(icons, "diagnosis icons"));
        }
    }

    /**
     * The full diagnosis frame for the structure panel: a display-capped, category-diverse sample
     * of the problems plus the totals and scan stats the header/all-met surfaces show.
     * {@code matchedCells} counts blueprint cells currently satisfied; count-level shortfalls
     * ({@code MISSING_PART}/{@code EXCESS_PART}) are problems but not cells, so
     * {@code totalProblems} can exceed {@code totalCells - matchedCells}. {@code partCount} is the
     * number of capability-carrying parts detected; {@code scanNanos} the recognition wall time.
     */
    public record Report(
                         List<Reason> shown,
                         int totalProblems,
                         int matchedCells,
                         int totalCells,
                         int partCount,
                         long captureNanos,
                         long scanNanos) {

        public Report {
            shown = List.copyOf(Objects.requireNonNull(shown, "shown reasons"));
        }

        public int hiddenProblems() {
            return Math.max(0, totalProblems - shown.size());
        }

        public boolean allMet() {
            return totalProblems == 0;
        }
    }

    public enum Kind {

        EMPTY_CELL,
        WRONG_BLOCK,
        WRONG_PROPERTY,
        MISSING_PART,
        EXCESS_PART;

        /** Property-level issues are warnings (right block, wrong orientation); the rest are errors. */
        public boolean propertyLevel() {
            return this == WRONG_PROPERTY;
        }

        public String headlineKey() {
            return switch (this) {
                case EMPTY_CELL -> "ui.topo.multiblock.diagnose.empty";
                case WRONG_BLOCK -> "ui.topo.multiblock.diagnose.wrong_block";
                case WRONG_PROPERTY -> "ui.topo.multiblock.diagnose.wrong_property";
                case MISSING_PART -> "ui.topo.multiblock.diagnose.missing_part";
                case EXCESS_PART -> "ui.topo.multiblock.diagnose.excess_part";
            };
        }
    }

    private MultiblockClientDiagnosis() {}

    /**
     * Diagnoses the controller's structure against {@code blueprint} on the client and samples at
     * most {@code displayMax} rows for the panel. When the problems exceed the cap, the sample is
     * category-diverse: every {@link Kind} that occurs contributes its first problem before the
     * remaining slots fill in blueprint order, so a wall of missing casings cannot crowd out the one
     * state-mismatch row. Row order stays the original blueprint order.
     */
    public static Report report(MachineBlockEntity controller, Blueprint blueprint, int displayMax) {
        TimedRecognition timed = capturedRecognition(controller, blueprint);
        RecognitionResult result = timed.result();
        List<Reason> all = new ArrayList<>();
        for (RecognitionResult.MissingCell missing : result.missingCells()) {
            all.add(cellReason(missing));
        }
        int mismatchedCells = all.size();
        for (Blueprint.CountRequirement requirement : blueprint.countRequirements().values()) {
            int count = result.roleCounts().getOrDefault(requirement.role(), 0);
            if (count < requirement.min()) {
                all.add(countReason(Kind.MISSING_PART, requirement.role(), count, requirement.min()));
            } else if (count > requirement.max()) {
                all.add(countReason(Kind.EXCESS_PART, requirement.role(), count, requirement.max()));
            }
        }
        int totalCells = blueprint.cells().size();
        int partCount = result.roleCounts().values().stream().mapToInt(Integer::intValue).sum();
        return new Report(
                sample(all, displayMax),
                all.size(),
                totalCells - mismatchedCells,
                totalCells,
                partCount,
                timed.captureNanos(),
                timed.scanNanos());
    }

    /** Category-diverse cap: first of each kind reserved, rest by original order, order preserved. */
    private static List<Reason> sample(List<Reason> all, int displayMax) {
        if (all.size() <= displayMax) {
            return all;
        }
        boolean[] picked = new boolean[all.size()];
        Set<Kind> seenKinds = EnumSet.noneOf(Kind.class);
        int remaining = displayMax;
        for (int i = 0; i < all.size() && remaining > 0; i++) {
            if (seenKinds.add(all.get(i).kind())) {
                picked[i] = true;
                remaining--;
            }
        }
        for (int i = 0; i < all.size() && remaining > 0; i++) {
            if (!picked[i]) {
                picked[i] = true;
                remaining--;
            }
        }
        List<Reason> shown = new ArrayList<>(displayMax);
        for (int i = 0; i < all.size(); i++) {
            if (picked[i]) {
                shown.add(all.get(i));
            }
        }
        return shown;
    }

    private static Reason countReason(Kind kind, PartRole capability, int have, int bound) {
        Component name = capabilityName(capability);
        return new Reason(
                null,
                kind,
                name,
                Component.translatable(kind.headlineKey(), name, have, bound),
                capabilityIcons(capability));
    }

    /** Diagnosis of a single cell, or {@code null} when the cell currently satisfies the blueprint. */
    public static @Nullable Reason diagnoseCell(
                                                MachineBlockEntity controller, Blueprint blueprint, BlockPos localPos) {
        RecognitionResult result = recognize(controller, blueprint);
        for (RecognitionResult.MissingCell missing : result.missingCells()) {
            if (missing.localPos().equals(localPos)) {
                return cellReason(missing);
            }
        }
        return null;
    }

    /**
     * The as-built structure indexed by <b>blueprint-local</b> offsets (controller at
     * {@link BlockPos#ZERO}): each cell's current world state read from the client level, rigidly
     * folded back into the canonical frame — positions through the inverse orientation and the
     * states' spatial properties through {@link Orientations#rotated(Orientation, BlockState)} with
     * that same inverse — so the scene shows the build exactly as its canonical (south-facing) twin.
     * A verbatim world state at a canonical-frame position would render every oriented block
     * mis-rotated by the controller's yaw (flipped stair grooves, hidden controller front).
     */
    public static Map<BlockPos, BlockState> detectedBlocks(MachineBlockEntity controller, Blueprint blueprint) {
        Level level = controller.getLevel();
        Map<BlockPos, BlockState> detected = new LinkedHashMap<>();
        if (level == null) {
            return detected;
        }
        Orientation orientation = orientationOf(controller);
        Orientation inverse = orientation.inverse();
        BlockPos origin = controller.getBlockPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Cell cell : blueprint.cells()) {
            orientation.apply(cell.localPos(), origin, cursor);
            BlockState state = level.isLoaded(cursor) ? level.getBlockState(cursor) : null;
            if (state != null && !state.isAir()) {
                detected.put(cell.localPos(), Orientations.rotated(inverse, state));
            }
        }
        return detected;
    }

    /**
     * The state a blueprint cell pins, folded forward through its COVARIANT rules for the
     * controller's current orientation — the facing shown to the player is one they can actually
     * place, in the world frame. When the world block already realizes an
     * {@link PropertyRule#alternateEncodings(BlockState) alternate encoding} of that expectation,
     * the alternate is shown instead, so the comparison table agrees with the recognition verdict
     * (a satisfied cell never shows a facing "to fix"). {@code null} when the cell pins no state.
     */
    public static @Nullable BlockState expectedStateAt(
                                                       MachineBlockEntity controller, Blueprint blueprint, BlockPos localPos) {
        for (Cell cell : blueprint.cells()) {
            if (!cell.localPos().equals(localPos)) {
                continue;
            }
            BlockState expected = cell.expectedState();
            if (expected == null) {
                return null;
            }
            Orientation orientation = orientationOf(controller);
            for (PropertyRule rule : cell.rules()) {
                if (rule.classification() == PropertyRule.Classification.COVARIANT) {
                    expected = rule.apply(orientation, expected);
                }
            }
            return bestEncodingFor(cell, expected, foundBlockAt(controller, blueprint, localPos));
        }
        return null;
    }

    /**
     * The encoding of {@code expected} to display: the world-realized alternate when the actual
     * block matches one (verbatim except masked properties), else {@code expected} itself.
     */
    private static BlockState bestEncodingFor(Cell cell, BlockState expected, @Nullable BlockState actual) {
        if (actual == null || unmaskedPropertiesMatch(cell, expected, actual)) {
            return expected;
        }
        for (PropertyRule rule : cell.rules()) {
            for (BlockState alternate : rule.alternateEncodings(expected)) {
                if (unmaskedPropertiesMatch(cell, alternate, actual)) {
                    return alternate;
                }
            }
        }
        return expected;
    }

    /** The same verbatim-except-masked compare recognition uses (block identity plus properties). */
    private static boolean unmaskedPropertiesMatch(Cell cell, BlockState expected, BlockState actual) {
        if (expected.getBlock() != actual.getBlock()) {
            return false;
        }
        for (Property<?> property : expected.getProperties()) {
            if (cell.maskedProperties().contains(property)) {
                continue;
            }
            if (!actual.hasProperty(property) || !expected.getValue(property).equals(actual.getValue(property))) {
                return false;
            }
        }
        return true;
    }

    /** The world block currently at a blueprint-local offset, verbatim, or {@code null} if unloaded. */
    public static @Nullable BlockState foundBlockAt(
                                                    MachineBlockEntity controller, Blueprint blueprint, BlockPos localPos) {
        Level level = controller.getLevel();
        if (level == null) {
            return null;
        }
        BlockPos worldPos = orientationOf(controller).apply(localPos, controller.getBlockPos());
        return level.isLoaded(worldPos) ? level.getBlockState(worldPos) : null;
    }

    /** Display name of the block a cell expects: pinned state first, else its first candidate. */
    private static Component expectedName(Cell expected) {
        BlockState pinned = expected.expectedState();
        if (pinned != null) {
            return pinned.getBlock().getName();
        }
        List<ItemStack> candidates = expected.predicate().candidates();
        if (!candidates.isEmpty()) {
            return candidates.get(0).getHoverName();
        }
        return LangRegistry.require("ui.topo.multiblock.none").getComponent();
    }

    /** Representative icon of what a cell expects, or {@code null} when it has no item face. */
    private static @Nullable ItemStack expectedIcon(Cell expected) {
        BlockState pinned = expected.expectedState();
        if (pinned != null) {
            ItemStack stack = new ItemStack(pinned.getBlock());
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        List<ItemStack> candidates = expected.predicate().candidates();
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static RecognitionResult recognize(MachineBlockEntity controller, Blueprint blueprint) {
        return capturedRecognition(controller, blueprint).result();
    }

    /**
     * Capture-then-recognize with phase timings: the client takes the same chunk-locality snapshot
     * the server recheck uses ({@link StructureCaptures}) and recognizes over it — one batched
     * world read per refresh, and the panel reports capture vs scan cost separately.
     */
    private static TimedRecognition capturedRecognition(MachineBlockEntity controller, Blueprint blueprint) {
        Level level = Objects.requireNonNull(controller.getLevel(), "controller level");
        Orientation orientation = orientationOf(controller);
        long captureStart = System.nanoTime();
        StructureView snapshot = StructureCaptures.capture(level, controller.getBlockPos(), blueprint, orientation);
        long captureNanos = System.nanoTime() - captureStart;
        long scanStart = System.nanoTime();
        RecognitionResult result = StructureEngine.recognize(snapshot, blueprint, orientation);
        long scanNanos = System.nanoTime() - scanStart;
        return new TimedRecognition(result, captureNanos, scanNanos);
    }

    private record TimedRecognition(RecognitionResult result, long captureNanos, long scanNanos) {}

    private static Orientation orientationOf(MachineBlockEntity controller) {
        BlockState state = controller.getBlockState();
        if (state.hasProperty(OrientedMachineBlock.FACING)) {
            return Orientations.forFacing(state.getValue(OrientedMachineBlock.FACING));
        }
        return Orientations.IDENTITY;
    }

    private static Reason cellReason(RecognitionResult.MissingCell missing) {
        Kind kind = classify(missing);
        // Structured emphasis (tooltip sentence): the expected block carries the severity color,
        // the found-block and coordinate parts stay muted.
        int severity = kind.propertyLevel() ? MachineUiComponentStyle.INSTANCE.getLedWaiting() : MachineUiComponentStyle.INSTANCE.getLedError();
        Component title = expectedName(missing.expected());
        Component expected = tinted(title, severity);
        int muted = MachineUiComponentStyle.INSTANCE.getTextMuted();
        BlockState actual = missing.actual();
        Component message;
        if (kind == Kind.EMPTY_CELL || actual == null) {
            message = tinted(LangRegistry.require("ui.topo.multiblock.diagnose.need").getComponent(
                    expected,
                    coords(missing.localPos())), muted);
        } else {
            message = tinted(LangRegistry.require("ui.topo.multiblock.diagnose.need_found").getComponent(
                    expected,
                    actual.getBlock().getName(),
                    coords(missing.localPos())), muted);
        }
        ItemStack icon = expectedIcon(missing.expected());
        return new Reason(
                missing.localPos(), kind, title, message, icon == null ? List.of() : List.of(icon));
    }

    /**
     * Colors the component root; translation arguments with their own color (the severity-tinted
     * expected block) keep it, everything else inherits this tint.
     */
    private static Component tinted(Component text, int color) {
        return text.copy().withStyle(style -> style.withColor(net.minecraft.network.chat.TextColor.fromRgb(color & 0xFFFFFF)));
    }

    private static Kind classify(RecognitionResult.MissingCell missing) {
        BlockState actual = missing.actual();
        if (actual == null || actual.isAir()) {
            return Kind.EMPTY_CELL;
        }
        // The predicate's state-pair test is block-identity level; if it accepts the actual block
        // the mismatch must come from the pinned state's properties.
        CellPredicate predicate = missing.expected().predicate();
        BlockState pinned = missing.expected().expectedState();
        if (pinned != null && actual.getBlock() == pinned.getBlock()) {
            return Kind.WRONG_PROPERTY;
        }
        return predicate.test(actual, pinned) ? Kind.WRONG_PROPERTY : Kind.WRONG_BLOCK;
    }

    private static Component coords(BlockPos localPos) {
        return Component.literal(localPos.getX() + "," + localPos.getY() + "," + localPos.getZ());
    }

    private static Component capabilityName(PartRole capability) {
        return capability.displayName();
    }

    /** Every registered machine that can fulfil the capability — the row icon cycles through them. */
    private static List<ItemStack> capabilityIcons(PartRole capability) {
        List<ItemStack> icons = new ArrayList<>();
        for (MachineDefinition definition : Machines.registered()) {
            for (PartRoleAttachment metadata : definition.metadata(PartRoleAttachment.TYPE)) {
                if (metadata.role() == capability) {
                    icons.add(new ItemStack(definition.registeredBlock().get()));
                    break;
                }
            }
        }
        return icons;
    }
}
