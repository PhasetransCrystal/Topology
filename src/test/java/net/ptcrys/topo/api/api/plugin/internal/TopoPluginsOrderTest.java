package net.ptcrys.topo.api.api.plugin.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopoPluginsOrderTest {

    @Test
    void executionOrderIsStableAndDoesNotRequireDependencyMetadata() {
        var z = new ZPlugin();
        var a = new APlugin();
        assertEquals(List.of(a, z), new PluginSnapshot(List.of(z, a), Map.of()).plugins());
        assertEquals(List.of(a, z), new PluginSnapshot(List.of(a, z), Map.of()).plugins());
    }

    private static final class APlugin extends TestPlugin {}

    private static final class ZPlugin extends TestPlugin {}
}
