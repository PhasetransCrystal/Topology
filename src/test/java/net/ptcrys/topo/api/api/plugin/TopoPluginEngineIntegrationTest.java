package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;

import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.ptcrys.topo.api.api.plugin.LifecycleTestEntrypoint.*;
import static org.junit.jupiter.api.Assertions.*;

class TopoPluginEngineIntegrationTest {

    @Test
    void hostRunsAllPluginsAndLegacyCommonSetupBeforeFreezingSharedRegistries() {
        TopoPluginEngine.bootstrapContent();
        assertSame(OfficialTopoAPIPlugin.INSTANCE, TopoPlugins.view().getFirst());
        assertEquals(1, a.lang().registeredKeys().size());
        assertEquals(2, b.lang().registeredKeys().size());
        assertTrue(a.lang().registeredKeys().getFirst().key().contains("topo_test_a"));
        assertTrue(b.lang().registeredKeys().stream().allMatch(key -> key.key().contains("topo_test_b")));
        assertEquals(Set.of("ui.topo_test_a.owned_a"), coreA.languages.keySet());
        assertEquals(Set.of("pipe.strategy.topo_test_b.owned_b", "jade.config.topo_test_b.owned"), coreB.languages.keySet());
        assertEquals("A", coreA.languages.get("ui.topo_test_a.owned_a").get("en_us"));
        assertEquals("甲", coreA.languages.get("ui.topo_test_a.owned_a").get("zh_cn"));
        assertEquals(Set.of("en_us", "zh_cn", "zh_tw"), coreA.languages.get("ui.topo_test_a.owned_a").keySet());
        TopoPluginEngine.bootstrapRuntime();
        assertEquals(List.of("a", "b"), bindings);
        assertSame(behavior, MachineItemBehaviors.find(Items.STICK));
        assertSame(behavior, MachineItemBehaviors.find(Items.BONE));
        assertTrue(legacyBound);
        assertSame(behavior, MachineItemBehaviors.find(Items.COAL));
        assertThrows(IllegalStateException.class, () -> ItemTooltipUis.register(Items.DIAMOND, panelA));
        assertThrows(IllegalStateException.class, () -> MachineItemBehaviors.register(Items.DIAMOND, behavior));
        // Old callers may repeat the content pipeline; it must not rerun hooks or mint content.
        TopoPluginEngine.bootstrapContent();
        TopoPluginEngine.bootstrapRuntime();
        TopoPluginEngine.freezeRuntime();
        TopoPlugins.register(a); // registering the same instance remains idempotent after lock
        assertEquals(List.of("a", "b"), bindings);
        TopoPlugins.register("test:a", a);
        assertThrows(IllegalStateException.class, () -> TopoPlugins.register("renamed", a));
        assertThrows(IllegalStateException.class, () -> TopoPlugins.register(new TestPlugin()));
    }
}
