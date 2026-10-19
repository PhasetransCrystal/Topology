package net.ptcrys.topo.api.api.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TopoPluginsOrderTest {

    private static TopoPlugins.BoundPlugin binding(String id, Set<String> after) {
        return new TopoPlugins.BoundPlugin(id, new TestPlugin(), after);
    }

    @Test
    void dependenciesOverrideConstructorArrivalAndAlphabeticalOrder() {
        var foundation = binding("z:foundation", Set.of());
        var content = binding("a:content", Set.of("z:foundation"));
        assertEquals(List.of(foundation, content), TopoPlugins.order(List.of(content, foundation)));
    }

    @Test
    void independentPluginsHaveTheSameOrderForEitherArrivalOrder() {
        var a = binding("a", Set.of());
        var b = binding("b", Set.of());
        assertEquals(List.of(a, b), TopoPlugins.order(List.of(b, a)));
        assertEquals(List.of(a, b), TopoPlugins.order(List.of(a, b)));
    }

    @Test
    void missingDependenciesFailWithBothIds() {
        var plugin = binding("content", Set.of("missing"));
        var error = assertThrows(IllegalStateException.class, () -> TopoPlugins.order(List.of(plugin)));
        assertTrue(error.getMessage().contains("content"));
        assertTrue(error.getMessage().contains("missing"));
    }

    @Test
    void cyclesAndSelfDependenciesAreRejected() {
        var a = binding("a", Set.of("b"));
        var b = binding("b", Set.of("a"));
        assertThrows(IllegalStateException.class, () -> TopoPlugins.order(List.of(a, b)));
        assertThrows(IllegalStateException.class,
                () -> TopoPlugins.order(List.of(binding("self", Set.of("self")))));
    }

    @Test
    void duplicateIdsAreRejectedEvenWhenModsMayShareNamespaces() {
        assertThrows(IllegalStateException.class, () -> TopoPlugins.order(List.of(
                binding("same", Set.of()), binding("same", Set.of()))));
    }
}
