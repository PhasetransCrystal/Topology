package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.api.registration.ExternalTargets;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import net.minecraft.world.item.Items;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.ptcrys.topo.api.api.plugin.internal.LifecycleTestEntrypoint.*;
import static org.junit.jupiter.api.Assertions.*;

class TopoPluginEngineIntegrationTest {

    @Test
    void builtMaterialsReceiveCrossPluginDataBeforeRulesAndRejectLateOrDuplicateWrites() {
        assertTrue(injectionSawAllMaterials);
        assertTrue(dataWriteReturnedTarget);
        assertTrue(builderDataSharedAfterBuild);
        assertNotNull(builderDuplicateDataFailure);
        assertTrue(builderDuplicateDataFailure.getMessage().contains("late_material"));
        assertNotNull(duplicateDataFailure);
        assertTrue(duplicateDataFailure.getMessage().contains("form_probe"));
        assertTrue(duplicateDataFailure.getMessage().contains("lifecycle_material"));
        assertNotNull(selectionTimeDataFailure);
        assertTrue(selectionTimeDataFailure.getMessage().contains("declarations are closed"));
        var target = MaterialRegistry.require(a.material().id("lifecycle_material"));
        assertThrows(IllegalStateException.class, () -> target.data(materialMarker.enabled()));
        assertThrows(IllegalStateException.class, () -> lateMaterialBuilder.data(materialMarker.enabled()));
        assertThrows(IllegalStateException.class, () -> lateMaterialBuilder.form(baseForm, options -> {}));
        assertThrows(IllegalStateException.class, () -> a.material().material("late_material"));
        assertThrows(IllegalStateException.class, lateMaterialBuilder::build);
    }

    @Test
    void formRulesApplyInBothDirectionsAcrossPluginsBeforeValidationAndContent() {
        var base = MaterialRegistry.require(a.material().id("lifecycle_material"));
        var extension = MaterialRegistry.require(b.material().id("extension_material"));
        for (var material : List.of(base, extension)) {
            assertEquals(List.of(baseForm, extensionForm), material.strategy().forms());
            assertEquals(material.strategy().forms(), generatedForms.get(material));
        }
        assertTrue(formValidationSawResolvedForms);
        assertSame(coreA, generatedFormOwners.get(base));
        assertSame(coreB, generatedFormOwners.get(extension));
        var before = base.strategy().forms();
        MaterialRegistry.freeze();
        assertSame(before, base.strategy().forms());
        assertEquals(2, generatedForms.get(base).size());
        assertTrue(MaterialFormRegistry.isFrozen());
    }

    @Test
    void externalItemOptionsCannotSelectANonMatchingForm() {
        var material = MaterialRegistry.require(a.material().id("lifecycle_material"));
        assertTrue(material.strategy().optionsFor(nonMatchingForm).orElseThrow().itemOverride().isPresent());
        assertFalse(nonMatchingForm.appliesTo(material));
        assertFalse(material.strategy().forms().contains(nonMatchingForm));
        assertFalse(generatedForms.get(material).contains(nonMatchingForm));
        assertEquals(1, scopedValidations);
        assertTrue(material.strategy().data(scopedMarker).isEmpty());
        assertTrue(material.strategy().data(scopedMarker, baseForm).orElseThrow());
    }

    @Test
    void materialSnapshotsAndRetainedBuildersRespectTheFreezeFence() {
        assertTrue(rulesSawEmptyForms);
        assertTrue(rulesReusedMaterialSnapshot);
        assertEquals(2, ruleCalls);
        assertNotNull(foreignFormFailure);
        assertNotNull(duplicateBuildFailure);
        var material = MaterialRegistry.require(a.material().id("lifecycle_material"));
        assertTrue(material.strategy().hasForm(baseForm));
        assertFalse(material.strategy().hasForm(nonMatchingForm));
        assertThrows(UnsupportedOperationException.class, () -> material.strategy().forms().clear());
        assertThrows(IllegalStateException.class, () -> retainedFormOptions.overrideItem(ExternalTargets.mcItem("diamond")));
        assertThrows(IllegalStateException.class, () -> retainedFormOptions.overrideBlock(ExternalTargets.mcBlock("stone")));
        assertThrows(IllegalStateException.class, () -> retainedFormOptions.data(materialMarker.enabled()));
        assertThrows(IllegalStateException.class, () -> builtMaterialBuilder.lang("Changed", "修改"));
        MaterialRegistry.freeze();
        assertEquals(2, ruleCalls);
    }

    @Test
    void processorsOnlyVisitActivatedMaterialsAndValidateBeforeProcessing() {
        var material = MaterialRegistry.require(a.material().id("lifecycle_material"));
        var extension = MaterialRegistry.require(b.material().id("extension_material"));
        assertEquals(List.of(alphaProcessor, zetaProcessor), material.strategy().postProcessors());
        assertTrue(extension.strategy().postProcessors().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> material.strategy().postProcessors().clear());
        // Activation data is validated before content, followed by the post-content validation pass.
        assertEquals(List.of("validate:zeta", "validate:alpha", "validate:alpha", "validate:zeta",
                "process:alpha", "process:zeta"), processorEvents);
    }

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
        TopoPluginEngine.host().bootstrapContent();
        assertEquals(1, recipeCalls);
    }

    @Test
    void hostRunsAllPluginsAndLegacyCommonSetupBeforeFreezingSharedRegistries() {
        TopoPluginEngine.host().bootstrapContent();
        assertTrue(TopoPlugins.view().contains(OfficialTopoAPIPlugin.INSTANCE));
        assertEquals(4, a.lang().registeredKeys().size());
        assertEquals(4, b.lang().registeredKeys().size());
        assertTrue(a.lang().registeredKeys().getFirst().key().contains("topo_test_a"));
        assertTrue(b.lang().registeredKeys().stream().allMatch(key -> key.key().contains("topo_test_b")));
        assertEquals(Set.of("ui.topo_test_a.owned_a", "material.topo_test_a.lifecycle_material", "form.topo_test_a.base_form", "form.topo_test_a.non_matching_form"), coreA.languages.keySet());
        assertEquals(Set.of("pipe.strategy.topo_test_b.owned_b", "jade.config.topo_test_b.owned", "form.topo_test_b.extension_form", "material.topo_test_b.extension_material"), coreB.languages.keySet());
        assertEquals("A", coreA.languages.get("ui.topo_test_a.owned_a").get("en_us"));
        assertEquals("甲", coreA.languages.get("ui.topo_test_a.owned_a").get("zh_cn"));
        assertEquals(Set.of("en_us", "zh_cn", "zh_tw"), coreA.languages.get("ui.topo_test_a.owned_a").keySet());
        TopoPluginEngine.host().bootstrapRuntime();
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
        TopoPluginEngine.host().bootstrapContent();
        TopoPluginEngine.host().bootstrapRuntime();
        TopoPluginEngine.host().freezeRuntime();
        TopoPlugins.register(a); // registering the same instance remains idempotent after lock
        assertEquals(Set.of("a", "b"), Set.copyOf(bindings));
        assertEquals(2, bindings.size());
        assertThrows(IllegalStateException.class, () -> TopoPlugins.register(new TestPlugin()));
    }
}
