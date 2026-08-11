package net.ptcrys.topo.api.machine;

import java.util.List;
import java.util.Objects;

/**
 * Immutable per-machine server tick timing snapshot used by debug overlays such as Jade.
 *
 * <p>
 * Every node carries three readings: {@code nanos} is the most recent completed sample,
 * {@code avgNanos} is a rolling average (EMA over monitored ticks), and {@code peakNanos} is a
 * slowly-decaying recent maximum. Overlays should lead with the average — a single tick sample is
 * dominated by scheduler/cache noise — and show last/peak as secondary detail.
 */
public record MachinePerformanceSnapshot(
                                         long gameTime,
                                         long totalNanos,
                                         long selfNanos,
                                         long componentsNanos,
                                         long totalAvgNanos,
                                         long totalPeakNanos,
                                         long selfAvgNanos,
                                         long selfPeakNanos,
                                         long componentsAvgNanos,
                                         long componentsPeakNanos,
                                         List<ComponentSample> components) {

    public static final MachinePerformanceSnapshot EMPTY = new MachinePerformanceSnapshot(Long.MIN_VALUE, 0L, 0L, 0L, List.of());

    public MachinePerformanceSnapshot {
        totalNanos = Math.max(0L, totalNanos);
        selfNanos = Math.max(0L, selfNanos);
        componentsNanos = Math.max(0L, componentsNanos);
        totalAvgNanos = Math.max(0L, totalAvgNanos);
        totalPeakNanos = Math.max(0L, totalPeakNanos);
        selfAvgNanos = Math.max(0L, selfAvgNanos);
        selfPeakNanos = Math.max(0L, selfPeakNanos);
        componentsAvgNanos = Math.max(0L, componentsAvgNanos);
        componentsPeakNanos = Math.max(0L, componentsPeakNanos);
        components = List.copyOf(Objects.requireNonNull(components, "component samples"));
    }

    /** Last-sample-only snapshot (no rolling stats yet); used by tests and synthetic samples. */
    public MachinePerformanceSnapshot(
                                      long gameTime,
                                      long totalNanos,
                                      long selfNanos,
                                      long componentsNanos,
                                      List<ComponentSample> components) {
        this(gameTime, totalNanos, selfNanos, componentsNanos, 0L, 0L, 0L, 0L, 0L, 0L, components);
    }

    public boolean isEmpty() {
        return totalNanos <= 0L;
    }

    public record ComponentSample(
                                  String id,
                                  String className,
                                  long nanos,
                                  long avgNanos,
                                  long peakNanos,
                                  List<TimingSample> children) {

        public ComponentSample(String id, String className, long nanos) {
            this(id, className, nanos, 0L, 0L, List.of());
        }

        public ComponentSample(String id, String className, long nanos, List<TimingSample> children) {
            this(id, className, nanos, 0L, 0L, children);
        }

        public ComponentSample {
            id = Objects.requireNonNull(id, "component id");
            className = Objects.requireNonNull(className, "component class name");
            nanos = Math.max(0L, nanos);
            avgNanos = Math.max(0L, avgNanos);
            peakNanos = Math.max(0L, peakNanos);
            children = List.copyOf(Objects.requireNonNull(children, "component timing children"));
        }
    }

    public record TimingSample(String id, String label, String detail, long nanos, long avgNanos, long peakNanos) {

        public TimingSample(String id, String label, String detail, long nanos) {
            this(id, label, detail, nanos, 0L, 0L);
        }

        public TimingSample {
            id = Objects.requireNonNull(id, "timing sample id");
            label = Objects.requireNonNull(label, "timing sample label");
            detail = Objects.requireNonNull(detail, "timing sample detail");
            nanos = Math.max(0L, nanos);
            avgNanos = Math.max(0L, avgNanos);
            peakNanos = Math.max(0L, peakNanos);
        }
    }
}
