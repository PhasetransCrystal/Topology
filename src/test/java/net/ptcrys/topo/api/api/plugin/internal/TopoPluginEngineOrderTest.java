package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.api.plugin.RegistrationDomain;
import net.ptcrys.topo.api.api.plugin.TopoContribution;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.api.api.plugin.internal.PluginLifecycle.Phase;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runs the real engine with isolated domain tables, without resetting the production host. */
class TopoPluginEngineOrderTest {

    private static final RegistrationDomain<Domain> DOMAIN = RegistrationDomain.of(Domain::new);
    private static final RegistrationPoint<Contributions, Domain> DECLARE = RegistrationPoint.of(
            "test:declare", Contributions.class, DOMAIN, Contributions::declare);
    private static final RegistrationPoint<Contributions, Domain> DERIVE = RegistrationPoint.of(
            "test:derive", Contributions.class, DOMAIN, Contributions::derive);
    private static final RegistrationPoint<Contributions, Domain> LANG = RegistrationPoint.of(
            "test:lang", Contributions.class, DOMAIN, Contributions::language);
    private static final RegistrationPoint<Contributions, Domain> RUNTIME = RegistrationPoint.of(
            "test:runtime", Contributions.class, DOMAIN, Contributions::runtime);

    @Test
    void allBuiltInModulePermutationsCompileToTheSamePlan() {
        var snapshot = new PluginSnapshot(List.of(new TestPlugin()), Map.of(Topology.MODID, Topology.REGISTRY));
        List<String> expected = TopoContentBootstrap.compile(snapshot, TopoContentBootstrap.MODULES).order();
        for (List<RegistrationModule> modules : permutations(TopoContentBootstrap.MODULES)) {
            assertEquals(expected, TopoContentBootstrap.compile(snapshot, modules).order());
        }
    }

    @Test
    void everyPluginAndModulePermutationProducesTheSameCompleteResult() {
        Result expected = null;
        for (List<Integer> plugins : permutations(List.of(0, 1, 2))) {
            for (List<Integer> modules : permutations(List.of(0, 1, 2))) {
                State state = new State();
                TopoPluginEngine engine = engine(state, plugins, modules);
                assertThrows(IllegalStateException.class, engine::bootstrapRuntime);
                assertThrows(IllegalStateException.class, engine::freezeRuntime);
                assertEquals(0, state.snapshots);
                assertTrue(state.calls.isEmpty());

                engine.bootstrapContent();
                assertTrue(state.declarations.isFrozen());
                assertTrue(state.derived.isFrozen());
                assertFalse(state.runtime.isFrozen());
                assertEquals(3, state.translations.size());
                assertEquals(1, state.snapshots);
                engine.bootstrapContent();
                assertThrows(IllegalStateException.class, engine::freezeRuntime);

                engine.bootstrapRuntime();
                assertFalse(state.runtime.isFrozen());
                // Legacy common/sided setup work remains legal until load complete.
                state.runtime.register("legacy", "legacy", "legacy");
                engine.freezeRuntime();
                assertTrue(state.runtime.isFrozen());
                assertThrows(IllegalStateException.class, () -> state.runtime.register("late", "late", "late"));
                engine.bootstrapContent();
                engine.bootstrapRuntime();
                engine.freezeRuntime();
                assertEquals(1, state.snapshots);
                assertEquals(List.of("APlugin", "BPlugin", "CPlugin"), state.declarations.handlesView());
                assertEquals(List.of("APlugin<-APlugin,BPlugin,CPlugin", "BPlugin<-APlugin,BPlugin,CPlugin", "CPlugin<-APlugin,BPlugin,CPlugin"),
                        state.derived.handlesView());
                Result actual = state.result();
                if (expected == null) expected = actual;
                assertEquals(expected, actual, "plugins=" + plugins + ", modules=" + modules);
            }
        }
    }

    @Test
    void interleavedEngineLifecyclesKeepTheirOwnPlansAndSnapshots() {
        State first = new State();
        State second = new State();
        TopoPluginEngine a = engine(first, List.of(2, 1, 0), List.of(2, 0, 1));
        TopoPluginEngine b = engine(second, List.of(1, 0, 2), List.of(1, 2, 0));
        a.bootstrapContent();
        b.bootstrapContent();
        a.bootstrapRuntime();
        assertEquals(3, first.runtime.handlesView().size());
        assertTrue(second.runtime.handlesView().isEmpty());
        b.bootstrapRuntime();
        b.freezeRuntime();
        assertFalse(first.runtime.isFrozen());
        a.freezeRuntime();
        assertEquals(first.result(), second.result());
        assertEquals(1, first.snapshots);
        assertEquals(1, second.snapshots);
    }

    @Test
    void everyPermutationFailsAtTheSamePluginWithoutFreezingOrContinuing() {
        Result expected = null;
        for (List<Integer> plugins : permutations(List.of(0, 1, 2))) {
            for (List<Integer> modules : permutations(List.of(0, 1, 2))) {
                State state = new State();
                state.fail = true;
                TopoPluginEngine engine = engine(state, plugins, modules);
                var error = assertThrows(IllegalStateException.class, engine::bootstrapContent);
                assertTrue(error.getMessage().contains(DECLARE.id()));
                assertTrue(error.getCause().getMessage().contains(BPlugin.class.getName()));
                assertSame(state.failure, error.getCause().getCause());
                assertSame(error, assertThrows(IllegalStateException.class, engine::bootstrapContent).getCause());
                assertSame(error, assertThrows(IllegalStateException.class, engine::bootstrapRuntime).getCause());
                assertSame(error, assertThrows(IllegalStateException.class, engine::freezeRuntime).getCause());
                assertFalse(state.declarations.isFrozen());
                assertFalse(state.derived.isFrozen());
                assertFalse(state.runtime.isFrozen());
                assertEquals(List.of("APlugin"), state.declarations.handlesView());
                assertEquals(List.of("declare:APlugin", "declare:BPlugin"), state.calls);
                assertEquals(1, state.snapshots);
                Result actual = state.result();
                if (expected == null) expected = actual;
                assertEquals(expected, actual, "plugins=" + plugins + ", modules=" + modules);
            }
        }
    }

    private static TopoPluginEngine engine(State state, List<Integer> pluginOrder, List<Integer> moduleOrder) {
        List<TopoPlugin> plugins = List.of(new APlugin(state), new BPlugin(state), new CPlugin(state));
        PluginCollection collection = new PluginCollection();
        for (int index : pluginOrder) collection.register(plugins.get(index));
        List<RegistrationModule> modules = List.of(
                (plan, context) -> plan.point(Phase.CONTENT, DECLARE, List.of(), context, () -> {
                    state.declarations.freeze();
                    state.calls.add("freeze:declarations");
                }),
                (plan, context) -> {
                    plan.point(Phase.DERIVED, DERIVE, List.of(DECLARE.id()), context, () -> {
                        state.derived.freeze();
                        state.calls.add("freeze:derived");
                    });
                    plan.point(Phase.LANGUAGE, LANG, List.of(DERIVE.id()), context, () -> state.calls.add("finish:lang"));
                },
                (plan, context) -> {
                    plan.point(Phase.RUNTIME, RUNTIME, List.of(LANG.id()), context, () -> state.calls.add("finish:runtime"));
                    plan.step(Phase.READY, "test:freeze_runtime", List.of(RUNTIME.id()), () -> {
                        state.runtime.freeze();
                        state.calls.add("freeze:runtime");
                    });
                });
        return new TopoPluginEngine(() -> {
            state.snapshots++;
            return collection.freeze();
        }, moduleOrder.stream().map(modules::get).toList());
    }

    private static <T> List<List<T>> permutations(List<T> values) {
        if (values.isEmpty()) return List.of(List.of());
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            List<T> rest = new ArrayList<>(values);
            T first = rest.remove(i);
            for (List<T> tail : permutations(rest)) {
                List<T> order = new ArrayList<>();
                order.add(first);
                order.addAll(tail);
                result.add(List.copyOf(order));
            }
        }
        return result;
    }

    private record Domain(TopoPlugin owner) {}

    private interface Contributions extends TopoContribution {

        void declare(Domain domain);

        void derive(Domain domain);

        void language(Domain domain);

        void runtime(Domain domain);
    }

    private static class OrderedPlugin extends TestPlugin implements Contributions {

        private final State state;

        OrderedPlugin(State state) {
            this.state = state;
        }

        private String name(Domain domain) {
            assertSame(this, domain.owner());
            assertSame(domain, domain(DOMAIN));
            return getClass().getSimpleName();
        }

        @Override
        public void declare(Domain domain) {
            String name = name(domain);
            state.calls.add("declare:" + name);
            if (state.fail && this instanceof BPlugin) throw state.failure;
            assertFalse(state.declarations.isFrozen());
            state.declarations.register(name, name, name);
        }

        @Override
        public void derive(Domain domain) {
            assertTrue(state.declarations.isFrozen());
            assertEquals(3, state.declarations.handlesView().size());
            String name = name(domain);
            String value = name + "<-" + String.join(",", state.declarations.handlesView());
            state.derived.register(name, value, value);
            state.calls.add("derive:" + name);
        }

        @Override
        public void language(Domain domain) {
            assertTrue(state.derived.isFrozen());
            String name = name(domain);
            state.translations.put(name, state.derived.require(name));
            state.calls.add("lang:" + name);
        }

        @Override
        public void runtime(Domain domain) {
            assertEquals(3, state.translations.size());
            String name = name(domain);
            state.runtime.register(name, state.derived.require(name), name);
            state.calls.add("runtime:" + name);
        }
    }

    private static final class APlugin extends OrderedPlugin {

        APlugin(State state) {
            super(state);
        }
    }

    private static final class BPlugin extends OrderedPlugin {

        BPlugin(State state) {
            super(state);
        }
    }

    private static final class CPlugin extends OrderedPlugin {

        CPlugin(State state) {
            super(state);
        }
    }

    private static final class State {

        final FreezableStrategyRegistry<String, String, String> declarations = FreezableStrategyRegistry.create("declarations");
        final FreezableStrategyRegistry<String, String, String> derived = FreezableStrategyRegistry.create("derived");
        final FreezableStrategyRegistry<String, String, String> runtime = FreezableStrategyRegistry.create("runtime");
        final Map<String, String> translations = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();
        final IllegalArgumentException failure = new IllegalArgumentException("broken B contribution");
        int snapshots;
        boolean fail;

        Result result() {
            return new Result(List.copyOf(calls), declarations.entriesView(), derived.entriesView(), runtime.entriesView(), List.copyOf(translations.entrySet()));
        }
    }

    private record Result(List<String> calls,
                          List<FreezableStrategyRegistry.Entry<String, String, String>> declarations,
                          List<FreezableStrategyRegistry.Entry<String, String, String>> derived,
                          List<FreezableStrategyRegistry.Entry<String, String, String>> runtime,
                          List<Map.Entry<String, String>> translations) {}
}
