package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.api.plugin.RegistrationDomain;
import net.ptcrys.topo.api.api.plugin.TopoContribution;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationPlanTest {

    @Test
    void dependencyAndPhaseOrderDoesNotDependOnModuleArrivalOrder() {
        List<RegistrationPlan.Step> steps = List.of(
                new RegistrationPlan.Step("z:declare", Phase.CONTENT, Set.of(), () -> {}),
                new RegistrationPlan.Step("a:generate", Phase.CONTENT, Set.of("z:declare"), () -> {}),
                new RegistrationPlan.Step("b:independent", Phase.CONTENT, Set.of(), () -> {}),
                new RegistrationPlan.Step("a:derived", Phase.DERIVED, Set.of(), () -> {}),
                new RegistrationPlan.Step("a:language", Phase.LANGUAGE, Set.of(), () -> {}),
                new RegistrationPlan.Step("a:runtime", Phase.RUNTIME, Set.of(), () -> {}),
                new RegistrationPlan.Step("a:ready", Phase.READY, Set.of(), () -> {}));
        List<String> expected = List.of("b:independent", "z:declare", "a:generate", "a:derived", "a:language", "a:runtime", "a:ready");
        for (int seed = 0; seed < 100; seed++) {
            List<RegistrationPlan.Step> shuffled = new ArrayList<>(steps);
            Collections.shuffle(shuffled, new Random(seed));
            RegistrationPlan.Builder builder = new RegistrationPlan.Builder();
            for (var step : shuffled) builder.step(step.phase(), step.id(), step.after(), step.action());
            assertEquals(expected, builder.compile().order(), "shuffle " + seed);
        }
    }

    @Test
    void rejectsInvalidPlansBeforeRunningAnyContent() {
        List<String> calls = new ArrayList<>();
        RegistrationPlan.Builder missing = new RegistrationPlan.Builder();
        missing.step(Phase.CONTENT, "test:one", List.of("test:missing"), () -> calls.add("missing"));
        assertTrue(assertThrows(IllegalStateException.class, missing::compile).getMessage().contains("test:missing"));

        RegistrationPlan.Builder cycle = new RegistrationPlan.Builder();
        cycle.step(Phase.CONTENT, "test:one", List.of("test:two"), () -> calls.add("one"));
        cycle.step(Phase.CONTENT, "test:two", List.of("test:one"), () -> calls.add("two"));
        assertTrue(assertThrows(IllegalStateException.class, cycle::compile).getMessage().contains("cycle"));

        RegistrationPlan.Builder later = new RegistrationPlan.Builder();
        later.step(Phase.CONTENT, "test:content", List.of("test:runtime"), () -> calls.add("content"));
        later.step(Phase.RUNTIME, "test:runtime", List.of(), () -> calls.add("runtime"));
        assertTrue(assertThrows(IllegalStateException.class, later::compile).getMessage().contains("later phase"));
        assertTrue(calls.isEmpty());
    }

    @Test
    void duplicateStepsAreRejectedAndCompiledPlansAreSnapshots() {
        RegistrationPlan.Builder builder = new RegistrationPlan.Builder();
        builder.step(Phase.CONTENT, "test:one", List.of(), () -> {});
        RegistrationPlan compiled = builder.compile();
        assertThrows(IllegalStateException.class, () -> builder.step(Phase.CONTENT, "test:one", List.of(), () -> {}));
        builder.step(Phase.CONTENT, "test:two", List.of(), () -> {});
        assertEquals(List.of("test:one"), compiled.order());
    }

    @Test
    void newDomainUsesNamedHooksWithoutChangingThePluginBaseOrEngine() {
        var table = FreezableStrategyRegistry.<String, String, String>create("future-domain regression");
        List<String> calls = new ArrayList<>();
        var a = new APlugin(api -> {
            table.register("a", "A", "A");
            calls.add("a");
        }, api -> {
            assertTrue(table.isFrozen());
            assertEquals(List.of("A", "Z"), table.handlesView());
            calls.add("derived");
        });
        var z = new ZPlugin(api -> {
            assertFalse(table.isFrozen());
            table.register("z", "Z", "Z");
            calls.add("z");
        });
        RegistrationModule module = (builder, context) -> {
            builder.point(Phase.CONTENT, CONTENT, List.of(), context, table::freeze);
            builder.point(Phase.DERIVED, DERIVED, List.of(CONTENT.id()), context, () -> {});
        };
        var snapshot = new PluginSnapshot(List.of(z, a), Map.of(Topology.MODID, Topology.REGISTRY));
        RegistrationPlan plan = TopoContentBootstrap.compile(snapshot, List.of(module));
        assertTrue(calls.isEmpty(), "compilation must not execute content");
        PluginLifecycle lifecycle = new PluginLifecycle();
        for (Phase phase : List.of(Phase.CONTENT, Phase.DERIVED)) {
            lifecycle.run(phase, () -> plan.execute(phase));
            lifecycle.run(phase, () -> fail("completed phase reran"));
        }
        assertEquals(List.of("a", "z", "derived"), calls);
        assertSame(a, a.domain(DOMAIN).owner());
        assertSame(a.domain(DOMAIN), a.domain(DOMAIN));
        assertSame(a.material(), a.material());
        assertSame(a.registry(), a.material().registry());
    }

    @Test
    void failedContributionStopsBeforeFreezeAndBeforeAnyLaterPhase() {
        List<String> calls = new ArrayList<>();
        IllegalArgumentException cause = new IllegalArgumentException("broken contribution");
        var a = new APlugin(api -> { throw cause; }, api -> {});
        var z = new ZPlugin(api -> calls.add("z"));
        RegistrationModule module = (builder, context) -> {
            builder.point(Phase.CONTENT, CONTENT, List.of(), context, () -> calls.add("freeze"));
            builder.step(Phase.DERIVED, "failure:derived", List.of(), () -> calls.add("derived"));
        };
        RegistrationPlan plan = TopoContentBootstrap.compile(
                new PluginSnapshot(List.of(z, a), Map.of()), List.of(module));
        PluginLifecycle lifecycle = new PluginLifecycle();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> lifecycle.run(Phase.CONTENT, () -> plan.execute(Phase.CONTENT)));
        assertTrue(failure.getMessage().contains(CONTENT.id()));
        assertTrue(failure.getCause().getMessage().contains(a.identity()));
        assertSame(cause, failure.getCause().getCause());
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> lifecycle.run(Phase.DERIVED, () -> plan.execute(Phase.DERIVED))).getCause());
        assertTrue(calls.isEmpty());
    }

    @Test
    void uninstalledContributionDomainStopsBeforeEveryContentAction() {
        List<String> calls = new ArrayList<>();
        var a = new APlugin(api -> calls.add("a"), api -> {});
        RegistrationModule module = (builder, context) -> builder.step(Phase.CONTENT, "test:action", List.of(), () -> calls.add("action"));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TopoContentBootstrap.compile(new PluginSnapshot(List.of(a), Map.of()), List.of(module)));
        assertTrue(error.getMessage().contains(a.identity()));
        assertTrue(error.getMessage().contains(FuturePlugin.class.getName()));
        assertTrue(calls.isEmpty());
    }

    @Test
    void pluginsWithoutAnOptionalDomainDoNotConstructItsApi() {
        List<TopoPlugin> created = new ArrayList<>();
        var domain = RegistrationDomain.of(plugin -> {
            created.add(plugin);
            return new FutureDomain(plugin);
        });
        var point = RegistrationPoint.of("future:optional", FuturePlugin.class, domain, FuturePlugin::registerFuture);
        var a = new APlugin(api -> {}, api -> {});
        var ordinary = new TestPlugin();
        RegistrationModule module = (builder, context) -> builder.point(Phase.CONTENT, point, List.of(), context, () -> {});
        var plan = TopoContentBootstrap.compile(new PluginSnapshot(List.of(ordinary, a), Map.of()), List.of(module));
        plan.execute(Phase.CONTENT);
        assertEquals(List.of(a), created);
    }

    private record FutureDomain(TopoPlugin owner) {}

    private interface FuturePlugin extends TopoContribution {

        void registerFuture(FutureDomain api);

        default void registerDerived(FutureDomain api) {}
    }

    private static final RegistrationDomain<FutureDomain> DOMAIN = RegistrationDomain.of(FutureDomain::new);
    private static final RegistrationPoint<FuturePlugin, FutureDomain> CONTENT = RegistrationPoint.of(
            "future:content", FuturePlugin.class, DOMAIN, FuturePlugin::registerFuture);
    private static final RegistrationPoint<FuturePlugin, FutureDomain> DERIVED = RegistrationPoint.of(
            "future:derived", FuturePlugin.class, DOMAIN, FuturePlugin::registerDerived);

    private abstract static class ProbePlugin extends TopoPlugin implements FuturePlugin {

        private final Consumer<FutureDomain> content;
        private final Consumer<FutureDomain> derived;

        ProbePlugin(Consumer<FutureDomain> content, Consumer<FutureDomain> derived) {
            super(Topology.REGISTRY);
            this.content = content;
            this.derived = derived;
        }

        @Override
        public void registerFuture(FutureDomain api) {
            content.accept(api);
        }

        @Override
        public void registerDerived(FutureDomain api) {
            derived.accept(api);
        }
    }

    private static final class APlugin extends ProbePlugin {

        APlugin(Consumer<FutureDomain> content, Consumer<FutureDomain> derived) {
            super(content, derived);
        }
    }

    private static final class ZPlugin extends ProbePlugin {

        ZPlugin(Consumer<FutureDomain> content) {
            super(content, api -> {});
        }
    }
}
