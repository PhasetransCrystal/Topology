package net.ptcrys.topo.apiv2.ore;

import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;
import net.ptcrys.topo.apiv2.ore.policy.OreConflictPolicy;
import net.ptcrys.topo.apiv2.ore.shape.OreVeinShape;

import java.util.Objects;

/**
 * Sealed placement contract for an ore vein. Feature and grid universes do not share a flat field
 * bag — each record holds only the fields its channel understands. Built by {@link OreVeins.Builder}
 * and validated by the paired {@link OreVeinMode.Strategy} at commit.
 */
public sealed interface OrePlacement permits OrePlacement.Feature, OrePlacement.Grid {

    OreVeinMode mode();

    int minY();

    int maxY();

    /** Vanilla {@code Feature.ORE} scatter channel. */
    record Feature(
                   OreVeinMode mode,
                   int size,
                   int attemptsPerChunk,
                   int minY,
                   int maxY,
                   boolean triangularHeight)
            implements OrePlacement {

        public Feature {
            Objects.requireNonNull(mode, "mode");
            if (size < 1 || size > 64) {
                throw new IllegalArgumentException("feature size must be 1..64, got " + size);
            }
            if (attemptsPerChunk < 1 || attemptsPerChunk > 256) {
                throw new IllegalArgumentException(
                        "feature attemptsPerChunk must be 1..256, got " + attemptsPerChunk);
            }
            if (minY > maxY) {
                throw new IllegalArgumentException("feature minY cannot exceed maxY: " + minY + " > " + maxY);
            }
        }
    }

    /** Deterministic per-chunk grid placer channel. */
    record Grid(
                OreVeinMode mode,
                OreVeinShape shape,
                int radiusBlocks,
                int gridSizeChunks,
                int randomOffsetBlocks,
                double density,
                int weight,
                int priority,
                OreConflictPolicy conflictPolicy,
                int minY,
                int maxY)
            implements OrePlacement {

        public Grid {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(conflictPolicy, "conflictPolicy");
            if (radiusBlocks < 1 || radiusBlocks > 4096) {
                throw new IllegalArgumentException("grid radius must be 1..4096, got " + radiusBlocks);
            }
            if (gridSizeChunks < 1 || gridSizeChunks > 1024) {
                throw new IllegalArgumentException("grid size must be 1..1024 chunks, got " + gridSizeChunks);
            }
            if (randomOffsetBlocks < 0 || randomOffsetBlocks > 4096) {
                throw new IllegalArgumentException(
                        "grid random offset must be 0..4096, got " + randomOffsetBlocks);
            }
            if (density < 0.0 || density > 1.0) {
                throw new IllegalArgumentException("grid density must be 0..1, got " + density);
            }
            if (weight <= 0) {
                throw new IllegalArgumentException("grid weight must be positive, got " + weight);
            }
            if (minY > maxY) {
                throw new IllegalArgumentException("grid minY cannot exceed maxY: " + minY + " > " + maxY);
            }
        }
    }
}
