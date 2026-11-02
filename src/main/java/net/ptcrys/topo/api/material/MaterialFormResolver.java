package net.ptcrys.topo.api.material;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates form predicates once and freezes a form plan table per material.
 *
 * <p>
 * Forms may declare material-level data hints. The resolver builds a BitSet index over material
 * data and evaluates a form predicate only for materials that satisfy all hints. Forms without
 * hints still fall back to the full material set, so arbitrary predicates remain supported.
 */
final class MaterialFormResolver {

    private MaterialFormResolver() {}

    static void resolve(List<Material> materials, List<MaterialForm> candidates) {
        List<MaterialForm> ordered = candidates.stream()
                .sorted(Comparator.comparing(MaterialForm::id))
                .toList();
        Set<MaterialDataType<?>> indexedTypes = new HashSet<>();
        for (MaterialForm form : ordered) {
            indexedTypes.addAll(form.requiredData());
        }
        Map<MaterialDataType<?>, BitSet> dataIndex = buildDataIndex(materials, indexedTypes);
        List<List<MaterialForm>> resolved = new ArrayList<>(materials.size());
        for (int index = 0; index < materials.size(); index++) {
            resolved.add(new ArrayList<>());
        }
        for (MaterialForm form : ordered) {
            BitSet candidatesByData = candidateBits(form.requiredData(), dataIndex);
            if (candidatesByData == null) {
                for (int index = 0; index < materials.size(); index++) {
                    if (form.appliesTo(materials.get(index))) {
                        resolved.get(index).add(form);
                    }
                }
                continue;
            }
            for (int index = candidatesByData.nextSetBit(0); index >= 0; index = candidatesByData.nextSetBit(index + 1)) {
                if (form.appliesTo(materials.get(index))) {
                    resolved.get(index).add(form);
                }
            }
        }
        // Predicates run against an unpublished table. Publish every material's forms before
        // creating any plan, so plan construction can safely inspect the complete form table.
        for (int index = 0; index < materials.size(); index++) {
            materials.get(index).strategy().publishForms(resolved.get(index));
        }
        for (Material material : materials) {
            material.strategy().resolvePlans(form -> form.strategy().plan(material, form));
        }
    }

    private static Map<MaterialDataType<?>, BitSet> buildDataIndex(
                                                                   List<Material> materials, Set<MaterialDataType<?>> indexedTypes) {
        if (indexedTypes.isEmpty()) {
            return Map.of();
        }
        Map<MaterialDataType<?>, BitSet> index = new HashMap<>(indexedTypes.size());
        for (MaterialDataType<?> type : indexedTypes) {
            index.put(type, new BitSet(materials.size()));
        }
        for (int materialIndex = 0; materialIndex < materials.size(); materialIndex++) {
            for (MaterialDataUse<?> use : materials.get(materialIndex).dataUses()) {
                BitSet bits = index.get(use.type());
                if (bits != null) {
                    bits.set(materialIndex);
                }
            }
        }
        return index;
    }

    /** Null means no hint was declared; an empty BitSet means no material can satisfy the hints. */
    private static BitSet candidateBits(Set<MaterialDataType<?>> requiredData,
                                        Map<MaterialDataType<?>, BitSet> dataIndex) {
        if (requiredData.isEmpty()) {
            return null;
        }
        BitSet candidates = null;
        for (MaterialDataType<?> type : requiredData) {
            BitSet bits = dataIndex.get(type);
            if (bits == null) {
                return new BitSet(0);
            }
            if (candidates == null) {
                candidates = (BitSet) bits.clone();
            } else {
                candidates.and(bits);
            }
            if (candidates.isEmpty()) {
                return candidates;
            }
        }
        return candidates;
    }
}
