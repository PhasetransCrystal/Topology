package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.api.api.registration.ExternalTargets;
import net.ptcrys.topo.api.material.MaterialFormTargets;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MaterialFormPlanTest {

    private static final MaterialContentType TYPE = new MaterialContentType(
            Identifier.fromNamespaceAndPath("topo_test_a", "plan_probe"));
    private static final MaterialContentKey KEY = new MaterialContentKey(
            TYPE, Identifier.fromNamespaceAndPath("topo_test_a", "plan_probe_output"));

    @Test
    void capturesOutputsAndTargetsAtConstruction() {
        List<MaterialContentKey> outputs = new ArrayList<>(List.of(KEY));
        MaterialFormTargets targets = MaterialFormTargets.empty()
                .withItem(ExternalTargets.mcItem("stick"));

        MaterialFormPlan plan = MaterialFormPlan.of(outputs, targets, () -> {}, context -> {});
        outputs.clear();

        assertEquals(List.of(KEY), plan.outputs());
        assertThrows(UnsupportedOperationException.class, () -> plan.outputs().clear());
        assertSame(targets, plan.targets());
    }

    @Test
    void threeArgumentFactoryUsesEmptyTargets() {
        MaterialFormPlan plan = MaterialFormPlan.of(List.of(KEY), () -> {}, context -> {});
        assertEquals(MaterialFormTargets.empty(), plan.targets());
    }
}
