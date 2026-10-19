package net.ptcrys.topo.api.api.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopoPluginsOrderTest {

    @Test
    void executionOrderIsStableAndDoesNotRequireDependencyMetadata() {
        var z = new TopoPlugins.BoundPlugin(new ZPlugin());
        var a = new TopoPlugins.BoundPlugin(new APlugin());

        assertEquals(List.of(a, z), TopoPlugins.executionOrder(List.of(z, a)));
        assertEquals(List.of(a, z), TopoPlugins.executionOrder(List.of(a, z)));
    }

    private static final class APlugin extends TestPlugin {

        @Override
        public String modId() {
            return "test";
        }
    }

    private static final class ZPlugin extends TestPlugin {

        @Override
        public String modId() {
            return "test";
        }
    }
}
