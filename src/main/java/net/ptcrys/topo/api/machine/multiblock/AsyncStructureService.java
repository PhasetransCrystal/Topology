package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.api.machine.multiblock.pattern.CompiledSnapshot;
import net.ptcrys.topo.api.machine.multiblock.pattern.Orientation;
import net.ptcrys.topo.api.machine.multiblock.pattern.RecognitionResult;
import net.ptcrys.topo.api.machine.multiblock.pattern.StructureEngine;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker-thread structure recognition for large footprints: the server thread captures immutable
 * {@link CompiledSnapshot}s and commits the verdict; only the pure
 * {@code recognize(snapshot, blueprint, orientation)} function runs off-thread. The service
 * deliberately accepts no {@code Level}, block entity, or trait — world sampling and commit stay on
 * the server thread, this pool only consumes immutable data and returns a pure result.
 *
 * <p>
 * Small structures stay on the synchronous path (lowest latency); the cell-count threshold is
 * tunable through the {@code oi.multiblock.asyncThreshold} system property (gametests lower it to
 * force the async path).
 */
public final class AsyncStructureService {

    private static final int DEFAULT_ASYNC_CELL_THRESHOLD = 2048;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, new ThreadFactory() {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "Topo-Multiblock-Recognize-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    });

    private AsyncStructureService() {}

    /** Footprints at or above this many (maximal) cells recognize on the worker pool. */
    public static int asyncCellThreshold() {
        return Integer.getInteger("oi.multiblock.asyncThreshold", DEFAULT_ASYNC_CELL_THRESHOLD);
    }

    /** Recognizes the captured views on the worker pool; same decision as the synchronous path. */
    public static CompletableFuture<Outcome> recognizeAsync(Captures captures, Blueprint blueprint) {
        return CompletableFuture.supplyAsync(() -> recognizePreferringMirror(captures, blueprint), POOL);
    }

    /**
     * Verifies the captured snapshot against its own compiled orientation on the worker pool — the
     * formed-structure backstop upkeep for large footprints (single capture, zero-allocation
     * short-circuit walk instead of a full recognition tally).
     */
    public static CompletableFuture<StructureEngine.Verification> verifyAsync(
                                                                              CompiledSnapshot snapshot, Blueprint blueprint) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(blueprint, "blueprint");
        return CompletableFuture.supplyAsync(
                () -> StructureEngine.verify(snapshot, blueprint, snapshot.compiled().orientation()), POOL);
    }

    /**
     * The shared primary-then-mirror recognition decision (pure; used by both sync and async
     * paths): the mirrored pass runs only when the primary fails and a mirrored capture exists.
     */
    public static Outcome recognizePreferringMirror(Captures captures, Blueprint blueprint) {
        Objects.requireNonNull(captures, "captures");
        Objects.requireNonNull(blueprint, "blueprint");
        Orientation primaryOrientation = captures.primary().compiled().orientation();
        RecognitionResult result = StructureEngine.recognize(captures.primary(), blueprint, primaryOrientation);
        CompiledSnapshot mirrored = captures.mirrored();
        if (result.formed() || mirrored == null) {
            return new Outcome(result, primaryOrientation);
        }
        Orientation mirrorOrientation = mirrored.compiled().orientation();
        RecognitionResult mirroredResult = StructureEngine.recognize(mirrored, blueprint, mirrorOrientation);
        return mirroredResult.formed() ? new Outcome(mirroredResult, mirrorOrientation) : new Outcome(result, primaryOrientation);
    }

    /** The main-thread captures a recognition consumes: primary view plus the optional mirror twin. */
    public record Captures(CompiledSnapshot primary, @Nullable CompiledSnapshot mirrored) {

        public Captures {
            Objects.requireNonNull(primary, "primary capture");
        }
    }

    /** A recognition verdict plus the orientation that produced it (the mirror twin when it won). */
    public record Outcome(RecognitionResult result, Orientation orientation) {}
}
