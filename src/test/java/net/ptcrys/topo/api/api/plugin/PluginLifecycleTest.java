package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.api.plugin.PluginLifecycle.Phase;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PluginLifecycleTest {

    @Test
    void rejectsSkippedPhasesWithoutExecutingTheirWork() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> lifecycle.run(Phase.RUNTIME, () -> fail("out-of-order work ran")));
        assertTrue(error.getMessage().contains("CONTENT must run before RUNTIME"));
        lifecycle.run(Phase.CONTENT, () -> {});
    }

    @Test
    void successfulStagesAreIdempotent() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        List<Phase> calls = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            lifecycle.run(phase, () -> calls.add(phase));
        }
        for (Phase phase : Phase.values()) {
            lifecycle.run(phase, () -> fail("completed stage ran twice"));
        }
        assertEquals(List.of(Phase.values()), calls);
    }

    @Test
    void failurePreservesCauseAndPreventsRetryOrProgress() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        IllegalArgumentException cause = new IllegalArgumentException("broken plugin");
        assertSame(cause, assertThrows(IllegalArgumentException.class,
                () -> lifecycle.run(Phase.CONTENT, () -> { throw cause; })));
        for (Phase phase : List.of(Phase.CONTENT, Phase.DERIVED)) {
            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> lifecycle.run(phase, () -> fail("failed startup continued")));
            assertSame(cause, error.getCause());
            assertTrue(error.getMessage().contains("CONTENT"));
        }
    }

    @Test
    void rejectsReentrantStartup() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> lifecycle.run(Phase.CONTENT, () -> lifecycle.run(Phase.CONTENT, () -> {})));
        assertTrue(error.getMessage().contains("while running CONTENT"));
    }

    @Test
    void runtimeContributionsStayOpenThroughCommonSetupAndFreezeAtReady() {
        PluginLifecycle lifecycle = new PluginLifecycle();
        var registry = FreezableStrategyRegistry.<String, String, String>create("tooltip regression");
        for (Phase phase : Phase.values()) {
            if (phase == Phase.RUNTIME) break;
            lifecycle.run(phase, () -> {});
        }
        lifecycle.run(Phase.RUNTIME, () -> {
            // The official plugin has no pipes; it must not close other plugins' registration.
            registry.register("addon_a:tool", "A", "A");
            registry.register("addon_b:ore", "B", "B");
        });
        // A legacy contributor's deferred common-setup work can run after the engine's task.
        registry.register("legacy:panel", "C", "C");
        assertFalse(registry.isFrozen());
        lifecycle.run(Phase.READY, registry::freeze);
        assertEquals(List.of("A", "B", "C"), registry.handlesView());
        assertThrows(IllegalStateException.class, () -> registry.register("late", "D", "D"));
    }
}
