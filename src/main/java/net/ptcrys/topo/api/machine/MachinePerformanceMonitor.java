package net.ptcrys.topo.api.machine;

import net.ptcrys.topo.api.api.tick.TickHandle;
import net.ptcrys.topo.api.machine.component.MachineComponent;
import net.ptcrys.topo.api.tick.MachineTicker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Per-machine performance sampling state: current-tick accumulators, rolling EMA/peak values, and
 * the lazily published snapshot. It is deliberately independent from block-entity lifecycle and
 * machine data; callers provide the current game time and mounted trait/ticker views.
 */
final class MachinePerformanceMonitor {

    private static final long[] EMPTY_BREAKDOWN = new long[0];

    private final LongSupplier currentGameTime;
    private final Supplier<List<MachineComponent>> traits;
    private final Supplier<List<MachineTicker>> tickers;

    private long currentGameTimeValue = Long.MIN_VALUE;
    private long currentTotalNanos;
    private long currentSelfNanos;
    private long currentComponentsNanos;
    private long[] currentTraitNanos = EMPTY_BREAKDOWN;
    private long emaTotalNanos;
    private long emaSelfNanos;
    private long emaComponentsNanos;
    private long peakTotalNanos;
    private long peakSelfNanos;
    private long peakComponentsNanos;
    private long[] emaTraitNanos = EMPTY_BREAKDOWN;
    private long[] peakTraitNanos = EMPTY_BREAKDOWN;
    private boolean snapshotDirty;
    private volatile long monitorUntilGameTime = Long.MIN_VALUE;
    private volatile MachinePerformanceSnapshot published = MachinePerformanceSnapshot.EMPTY;

    MachinePerformanceMonitor(
                              LongSupplier currentGameTime,
                              Supplier<List<MachineComponent>> traits,
                              Supplier<List<MachineTicker>> tickers) {
        this.currentGameTime = Objects.requireNonNull(currentGameTime, "current game time");
        this.traits = Objects.requireNonNull(traits, "machine traits");
        this.tickers = Objects.requireNonNull(tickers, "machine tickers");
    }

    void initialize(int traitCount) {
        currentTraitNanos = traitCount == 0 ? EMPTY_BREAKDOWN : new long[traitCount];
        emaTraitNanos = traitCount == 0 ? EMPTY_BREAKDOWN : new long[traitCount];
        peakTraitNanos = traitCount == 0 ? EMPTY_BREAKDOWN : new long[traitCount];
    }

    void activate(int ticks) {
        if (ticks <= 0) {
            return;
        }
        long until = currentGameTime.getAsLong() + ticks;
        if (until > monitorUntilGameTime) {
            monitorUntilGameTime = until;
        }
    }

    boolean isActive(long gameTime) {
        return gameTime <= monitorUntilGameTime;
    }

    MachinePerformanceSnapshot last() {
        publishIfDirty();
        return published;
    }

    MachinePerformanceSnapshot recent(int maxAgeTicks) {
        publishIfDirty();
        MachinePerformanceSnapshot snapshot = published;
        return isRecent(snapshot.gameTime(), currentGameTime.getAsLong(), maxAgeTicks) ? snapshot : MachinePerformanceSnapshot.EMPTY;
    }

    int sampleMaxAgeTicks() {
        int slowestInterval = 1;
        for (MachineTicker ticker : tickers.get()) {
            TickHandle handle = ticker.handle();
            int interval = handle == null ? ticker.tickInterval() : handle.interval();
            slowestInterval = Math.max(slowestInterval, interval);
        }
        return slowestInterval + 1;
    }

    boolean publishRecent(int maxAgeTicks) {
        long now = currentGameTime.getAsLong();
        if (currentGameTimeValue == Long.MIN_VALUE || currentTotalNanos <= 0L || !isRecent(currentGameTimeValue, now, maxAgeTicks)) {
            return false;
        }
        publishIfDirty();
        return true;
    }

    void record(long gameTime, long elapsedNanos, int profileSlot) {
        record(gameTime, elapsedNanos, 0L, profileSlot);
    }

    void record(long gameTime, long elapsedNanos, long frameworkNanos, int profileSlot) {
        beginTick(gameTime);
        long elapsed = normalizeElapsed(elapsedNanos);
        long framework = Math.max(0L, frameworkNanos);
        currentTotalNanos = saturatingAdd(currentTotalNanos, saturatingAdd(elapsed, framework));
        currentComponentsNanos = saturatingAdd(currentComponentsNanos, elapsed);
        currentSelfNanos = saturatingAdd(currentSelfNanos, framework);
        if (profileSlot >= 0 && profileSlot < currentTraitNanos.length) {
            currentTraitNanos[profileSlot] = saturatingAdd(currentTraitNanos[profileSlot], elapsed);
        }
        snapshotDirty = true;
    }

    private void beginTick(long gameTime) {
        if (currentGameTimeValue == gameTime) {
            return;
        }
        if (currentGameTimeValue != Long.MIN_VALUE && currentTotalNanos > 0L) {
            foldRollingStats();
        }
        currentGameTimeValue = gameTime;
        currentTotalNanos = 0L;
        currentSelfNanos = 0L;
        currentComponentsNanos = 0L;
        Arrays.fill(currentTraitNanos, 0L);
    }

    private void foldRollingStats() {
        emaTotalNanos = emaFold(emaTotalNanos, currentTotalNanos);
        emaSelfNanos = emaFold(emaSelfNanos, currentSelfNanos);
        emaComponentsNanos = emaFold(emaComponentsNanos, currentComponentsNanos);
        peakTotalNanos = peakFold(peakTotalNanos, currentTotalNanos);
        peakSelfNanos = peakFold(peakSelfNanos, currentSelfNanos);
        peakComponentsNanos = peakFold(peakComponentsNanos, currentComponentsNanos);
        int length = Math.min(currentTraitNanos.length, emaTraitNanos.length);
        for (int i = 0; i < length; i++) {
            emaTraitNanos[i] = emaFold(emaTraitNanos[i], currentTraitNanos[i]);
            peakTraitNanos[i] = peakFold(peakTraitNanos[i], currentTraitNanos[i]);
        }
    }

    private void publishIfDirty() {
        if (!snapshotDirty) {
            return;
        }
        snapshotDirty = false;
        List<MachineComponent> mounted = traits.get();
        int length = Math.min(mounted.size(), currentTraitNanos.length);
        List<MachinePerformanceSnapshot.ComponentSample> samples = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            MachineComponent trait = mounted.get(i);
            long nanos = currentTraitNanos[i];
            long average = emaTraitNanos.length > i ? emaTraitNanos[i] : 0L;
            long peak = peakTraitNanos.length > i ? peakTraitNanos[i] : 0L;
            List<MachinePerformanceSnapshot.TimingSample> children = List.of();
            if (trait instanceof MachineTicker ticker) {
                TickHandle handle = ticker.handle();
                int liveInterval = handle == null || handle.isCancelled() ? ticker.tickInterval() : handle.interval();
                children = List.of(new MachinePerformanceSnapshot.TimingSample(
                        trait.id() + ".tick",
                        "tick",
                        ticker.kind() + " interval=" + liveInterval,
                        nanos,
                        average,
                        peak));
            }
            samples.add(new MachinePerformanceSnapshot.ComponentSample(
                    trait.id(),
                    trait.getClass().getSimpleName(),
                    nanos,
                    average,
                    peak,
                    children));
        }
        published = new MachinePerformanceSnapshot(
                currentGameTimeValue,
                currentTotalNanos,
                currentSelfNanos,
                currentComponentsNanos,
                emaTotalNanos,
                peakTotalNanos,
                emaSelfNanos,
                peakSelfNanos,
                emaComponentsNanos,
                peakComponentsNanos,
                samples);
    }

    private static long emaFold(long ema, long sample) {
        return ema == 0L ? sample : ema + (sample - ema) / 8L;
    }

    private static long peakFold(long peak, long sample) {
        return Math.max(sample, peak - (peak >>> 4));
    }

    private static boolean isRecent(long sampleGameTime, long currentGameTime, int maxAgeTicks) {
        if (sampleGameTime == Long.MIN_VALUE || currentGameTime == Long.MIN_VALUE) {
            return false;
        }
        long age = currentGameTime - sampleGameTime;
        return age >= 0L && age <= Math.max(0, maxAgeTicks);
    }

    private static long normalizeElapsed(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return 0L;
        }
        return elapsedNanos == 0L ? 1L : elapsedNanos;
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        return result < 0L ? Long.MAX_VALUE : result;
    }
}
