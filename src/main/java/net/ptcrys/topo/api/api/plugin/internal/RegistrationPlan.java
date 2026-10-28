package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.api.plugin.TopoContribution;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;

import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Compiles a validated, deterministic dependency order before any registration action runs. */
public final class RegistrationPlan {

    record Step(Identifier id, Phase phase, Set<Identifier> after, Runnable action) {

        Step {
            Objects.requireNonNull(id, "step id");
            Objects.requireNonNull(phase, "step phase");
            TreeSet<Identifier> dependencies = new TreeSet<>(ID_ORDER);
            dependencies.addAll(after);
            after = java.util.Collections.unmodifiableSet(dependencies);
            Objects.requireNonNull(action, "step action");
        }
    }

    private static final Comparator<Identifier> ID_ORDER = Comparator.comparing(Identifier::toString);

    private final List<Step> steps;

    private RegistrationPlan(List<Step> steps) {
        this.steps = List.copyOf(steps);
    }

    List<Identifier> order() {
        return steps.stream().map(Step::id).toList();
    }

    void execute(Phase phase) {
        for (Step step : steps) {
            if (step.phase() != phase) continue;
            try {
                step.action().run();
            } catch (RuntimeException | Error error) {
                throw new IllegalStateException("Topo registration step '" + step.id() + "' failed", error);
            }
        }
    }

    public static final class Builder {

        private final Map<Identifier, Step> steps = new TreeMap<>(ID_ORDER);
        private final Set<RegistrationPoint<?, ?>> points = new HashSet<>();

        public Builder step(Phase phase, Identifier id, Collection<Identifier> after, Runnable action) {
            Step step = new Step(id, phase, Set.copyOf(after), action);
            if (steps.putIfAbsent(id, step) != null) throw new IllegalStateException("Duplicate registration step '" + id + "'");
            return this;
        }

        public <P extends TopoContribution, A> Builder point(Phase phase, RegistrationPoint<P, A> point, Collection<Identifier> after,
                                                             RegistrationContext context, Runnable finish) {
            step(phase, point.id(), after, () -> {
                context.contribute(point);
                finish.run();
            });
            points.add(point);
            return this;
        }

        Set<RegistrationPoint<?, ?>> points() {
            return Set.copyOf(points);
        }

        RegistrationPlan compile() {
            for (Step step : steps.values()) {
                for (Identifier dependency : step.after()) {
                    Step required = steps.get(dependency);
                    if (required == null) throw new IllegalStateException("Step '" + step.id() + "' requires missing step '" + dependency + "'");
                    if (required.phase().ordinal() > step.phase().ordinal()) {
                        throw new IllegalStateException("Step '" + step.id() + "' depends on later phase: '" + dependency + "'");
                    }
                }
            }
            Map<Identifier, Step> pending = new LinkedHashMap<>(steps);
            List<Step> result = new ArrayList<>();
            Set<Identifier> completed = new HashSet<>();
            for (Phase phase : Phase.values()) {
                while (pending.values().stream().anyMatch(step -> step.phase() == phase)) {
                    Step next = pending.values().stream()
                            .filter(step -> step.phase() == phase && completed.containsAll(step.after()))
                            .min(Comparator.comparing(Step::id, ID_ORDER)).orElseThrow(() -> new IllegalStateException(
                                    "Registration dependency cycle: " + pending.values().stream()
                                            .filter(step -> step.phase() == phase).map(Step::id).toList()));
                    result.add(next);
                    completed.add(next.id());
                    pending.remove(next.id());
                }
            }
            return new RegistrationPlan(result);
        }
    }
}
