package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.Topology;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TopoPluginsOrderTest {

    @Test
    void executionOrderIsStableAndDoesNotRequireDependencyMetadata() {
        var z = new ZPlugin();
        var a = new APlugin();
        var m = new MPlugin();
        for (List<TestPlugin> arrival : List.of(List.of(a, m, z), List.of(a, z, m), List.of(m, a, z),
                List.of(m, z, a), List.of(z, a, m), List.of(z, m, a))) {
            PluginCollection collection = new PluginCollection();
            for (TestPlugin plugin : arrival) collection.register(plugin);
            assertEquals(List.of(a, m, z), collection.view());
            assertEquals(List.of(a, m, z), collection.freeze().plugins());
            assertEquals(Map.of(a.modId(), a.registry()), collection.freeze().owners());
        }
    }

    @Test
    void sealingPublishesOneImmutableSnapshotAndClosesNewRegistrations() {
        PluginCollection collection = new PluginCollection();
        var a = new APlugin();
        collection.register(a);
        List<?> earlyView = collection.view();
        var z = new ZPlugin();
        collection.register(z);
        collection.register(a);
        assertFalse(collection.isLocked());
        PluginSnapshot snapshot = collection.freeze();
        assertTrue(collection.isLocked());
        assertSame(snapshot, collection.freeze());
        collection.register(a);
        assertEquals(List.of(a), earlyView);
        assertEquals(List.of(a, z), snapshot.plugins());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.plugins().add(a));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.owners().clear());
        assertThrows(IllegalStateException.class, () -> collection.register(new MPlugin()));
        assertEquals(List.of(a, z), collection.view());
    }

    @Test
    void conflictingInstancesAreRejectedInEitherArrivalOrder() {
        var first = new APlugin();
        var second = new APlugin();
        for (List<APlugin> arrival : List.of(List.of(first, second), List.of(second, first))) {
            PluginCollection collection = new PluginCollection();
            collection.register(arrival.getFirst());
            var error = assertThrows(IllegalStateException.class, () -> collection.register(arrival.getLast()));
            assertEquals("Duplicate plugin identity: " + first.identity(), error.getMessage());
            assertEquals(List.of(arrival.getFirst()), collection.freeze().plugins());
        }
    }

    @Test
    void emptyCollectionCannotBeSealedAndRemainsOpen() {
        PluginCollection collection = new PluginCollection();
        assertThrows(IllegalStateException.class, collection::freeze);
        assertFalse(collection.isLocked());
        var a = new APlugin();
        collection.register(a);
        assertEquals(List.of(a), collection.freeze().plugins());
    }

    @Test
    void namespaceOrderPrecedesClassNamesAndOwnerIterationIsCanonical() {
        class ZPlugin extends TestPlugin {}
        class MPlugin extends TestPlugin {

            MPlugin() {
                super(LifecycleTestEntrypoint.coreA);
            }
        }
        class APlugin extends TestPlugin {

            APlugin() {
                super(LifecycleTestEntrypoint.coreB);
            }
        }
        var a = new APlugin();
        var m = new MPlugin();
        var z = new ZPlugin();
        for (List<TestPlugin> arrival : List.of(List.of(a, m, z), List.of(z, m, a))) {
            PluginCollection collection = new PluginCollection();
            for (TestPlugin plugin : arrival) collection.register(plugin);
            var snapshot = collection.freeze();
            assertEquals(List.of(z, m, a), snapshot.plugins());
            assertEquals(List.of(Topology.MODID, m.modId(), a.modId()), List.copyOf(snapshot.owners().keySet()));
            assertSame(m.registry(), snapshot.registry(m.modId()));
            assertSame(a.registry(), snapshot.registry(a.modId()));
        }
    }

    private static final class APlugin extends TestPlugin {}

    private static final class MPlugin extends TestPlugin {}

    private static final class ZPlugin extends TestPlugin {}
}
