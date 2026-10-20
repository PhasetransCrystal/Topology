package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static net.ptcrys.topo.api.api.plugin.internal.LifecycleTestEntrypoint.*;
import static org.junit.jupiter.api.Assertions.*;

class TopoPluginEngineIntegrationTest {

    @Test
    void entryNamesAndIndependentKeysRejectConflictsInEitherDeclarationOrder() {
        assertNotNull(entryThenKeyFailure);
        assertTrue(entryThenKeyFailure.getMessage().contains("item.topo_test_a.lifecycle_tool"));
        assertTrue(entryThenKeyFailure.getMessage().contains("RegistryLib entry"));
        assertNotNull(keyThenEntryFailure);
        assertTrue(keyThenEntryFailure.getMessage().contains("ui.topo_test_a.owned_a"));
        assertTrue(keyThenEntryFailure.getMessage().contains("Topo LangKey"));
        assertFalse(coreA.languages.containsKey("item.topo_test_a.lifecycle_tool"));
        assertThrows(IllegalStateException.class, () -> LangRegistry.claimEntryName("item.topo_test_a.late"));
    }

    @Test
    void constructorCollectionRejectsDuplicateIdentityButAcceptsTheSameInstance() {
        assertNotNull(duplicateIdentityFailure);
        assertTrue(duplicateIdentityFailure.getMessage().contains(duplicateProbe.identity()));
        assertEquals(1, TopoPlugins.view().stream().filter(plugin -> plugin == duplicateProbe).count());
        assertTrue(TopoPlugins.isLocked());
    }

    @Test
    void recipesCanConsumeOtherDomainsAndOtherPluginsBeforeProductionLinesFreeze() {
        assertSame(EquipmentRegistry.require(a.equipment().id("lifecycle_tool")), recipeEquipment);
        assertSame(ProductionLines.require(b.recipe().id("machine_line")), recipeLine);
        assertTrue(recipesSawFrozenContent);
        assertTrue(ProductionLines.isFrozen());
        assertNotNull(ProductionLines.require(a.recipe().id("final_line")));
        TopoPluginEngine.bootstrapContent();
        assertEquals(1, recipeCalls);
    }

    @Test
    void hostRunsAllPluginsAndLegacyCommonSetupBeforeFreezingSharedRegistries() {
        TopoPluginEngine.bootstrapContent();
        assertTrue(TopoPlugins.view().contains(OfficialTopoAPIPlugin.INSTANCE));
        assertEquals(2, a.lang().registeredKeys().size());
        assertEquals(2, b.lang().registeredKeys().size());
        assertTrue(a.lang().registeredKeys().getFirst().key().contains("topo_test_a"));
        assertTrue(b.lang().registeredKeys().stream().allMatch(key -> key.key().contains("topo_test_b")));
        assertEquals(Set.of("ui.topo_test_a.owned_a", "material.topo_test_a.lifecycle_material"), coreA.languages.keySet());
        assertEquals(Set.of("pipe.strategy.topo_test_b.owned_b", "jade.config.topo_test_b.owned"), coreB.languages.keySet());
        assertEquals("A", coreA.languages.get("ui.topo_test_a.owned_a").get("en_us"));
        assertEquals("甲", coreA.languages.get("ui.topo_test_a.owned_a").get("zh_cn"));
        assertEquals(Set.of("en_us", "zh_cn", "zh_tw"), coreA.languages.get("ui.topo_test_a.owned_a").keySet());
        TopoPluginEngine.bootstrapRuntime();
        assertEquals(Set.of("a", "b"), Set.copyOf(bindings));
        assertTrue(runtimeSawEquipmentBehavior);
        assertSame(behavior, MachineItemBehaviors.find(generatedEquipment.get()));
        assertSame(behavior, MachineItemBehaviors.find(Items.STICK));
        assertSame(behavior, MachineItemBehaviors.find(Items.BONE));
        assertTrue(legacyBound);
        assertSame(behavior, MachineItemBehaviors.find(Items.COAL));
        assertThrows(IllegalStateException.class, () -> ItemTooltipUis.register(Items.DIAMOND, panelA));
        assertThrows(IllegalStateException.class, () -> MachineItemBehaviors.register(Items.DIAMOND, behavior));
        // Repeated registry events must not rerun contributions or mint content.
        TopoPluginEngine.bootstrapContent();
        TopoPluginEngine.bootstrapRuntime();
        TopoPluginEngine.freezeRuntime();
        TopoPlugins.register(a); // registering the same instance remains idempotent after lock
        assertEquals(Set.of("a", "b"), Set.copyOf(bindings));
        assertEquals(2, bindings.size());
        assertThrows(IllegalStateException.class, () -> TopoPlugins.register(new TestPlugin()));
    }
}
