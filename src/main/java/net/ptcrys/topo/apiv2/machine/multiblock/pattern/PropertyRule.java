package net.ptcrys.topo.apiv2.machine.multiblock.pattern;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.List;

/**
 * Leaf element (L1) describing how a single block-state property co-varies with an
 * {@link Orientation} during structure recognition.
 *
 * <p>
 * Rules are <b>partial</b>: when the property is absent on {@code s}, or the orientation cannot
 * be represented for that property's restricted value space, {@link #apply(Orientation, BlockState)}
 * returns {@code s} unchanged. {@link #supports(Orientation)} declares that representability up
 * front, and the recognition entry refuses unsupported orientations before any cell is compared
 * (see {@link StructureEngine}).
 *
 * <p>
 * Rules are plain values referenced as strong handles from blueprint declarations (the builtin
 * catalog is {@code BuiltinOIPropertyRules}); identity is reference identity — rules never match by
 * string or id.
 */
public interface PropertyRule {

    /**
     * Forward transform: returns {@code s} with the governed property rotated/mirrored under
     * {@code o}, or {@code s} unchanged if the property is absent or the orientation is
     * unrepresentable for it.
     */
    BlockState apply(Orientation o, BlockState s);

    /** Classifies the rule's round-trip behaviour. */
    Classification classification();

    /**
     * Whether this rule's transform is <b>total</b> under {@code o}: every legal value of every
     * governed property maps onto a value that is again legal for that property. When {@code false},
     * {@link #apply(Orientation, BlockState)} would have to leave some states unchanged, and folding a
     * canonical expected state forward could then falsely match a world block that happens to carry
     * the untransformed value — so the recognition entry pre-checks this per blueprint &times;
     * orientation and refuses the pass with
     * {@link RecognitionResult.FailureReason#ORIENTATION_UNSUPPORTED} instead.
     *
     * <p>
     * Defaults to {@code true} (total everywhere). Covariant rules over restricted value spaces
     * (e.g. a horizontal-only direction property under a pitch orientation) override this.
     */
    default boolean supports(Orientation o) {
        return true;
    }

    /**
     * The <em>additional</em> legal encodings of {@code expected} — states spelling the same
     * physical geometry differently. Vanilla encodes some shapes redundantly (a corner stair is one
     * geometry with two legal facing/shape pairs, whichever the placing player's look direction
     * produced); the recognition compare ({@link StructureEngine}) accepts the oriented expected
     * state <em>or</em> any alternate a cell rule contributes.
     *
     * <p>
     * Contract: derived from the trusted, blueprint-pinned expected state only — a rule must
     * never need to read the world block's neighbour-recomputed properties, which may not have
     * settled when recognition runs. Confined to this rule's governed properties; empty when they
     * are absent. {@code expected} is already in the world frame (post-fold), so implementations
     * stay orientation-blind. The default is no alternates — most properties encode uniquely.
     */
    default Collection<BlockState> alternateEncodings(BlockState expected) {
        return List.of();
    }

    /**
     * The block-state properties recognition must <b>not</b> compare for this rule, because vanilla
     * recomputes them from neighbours after the block settles (stairs {@code SHAPE}, fence/wall/glass
     * connection booleans, chest {@code TYPE}, &hellip;).
     *
     * <p>
     * The recognition compare ({@link StructureEngine}) folds a cell's covariant rules forward onto
     * the expected state, then compares it against the world verbatim <em>except</em> for the union of
     * every rule's masked properties. Masking is a capability any rule may declare, orthogonal to its
     * {@link Classification}; the default is empty (a rule that masks nothing) and the builtin
     * {@code DERIVED} factories override it with their neighbour-recomputed properties.
     */
    default Collection<Property<?>> maskedProperties() {
        return List.of();
    }

    /**
     * The three property categories.
     *
     * <ul>
     * <li>{@link #FIXED} — identity-only: pinned and compared verbatim in the canonical frame; the
     * engine never folds a FIXED rule through the orientation.
     * <li>{@link #COVARIANT} — freely settable and rotates/mirrors with the orientation
     * (three-way round-trip: recognise forward, build forward, export inverse); the only
     * classification the engine folds.
     * <li>{@link #DERIVED} — recomputed from neighbours via {@code updateShape}; recognition
     * validates after settling or masks it, building tolerates the recompute, export drops it.
     * </ul>
     */
    enum Classification {
        FIXED,
        COVARIANT,
        DERIVED
    }
}
