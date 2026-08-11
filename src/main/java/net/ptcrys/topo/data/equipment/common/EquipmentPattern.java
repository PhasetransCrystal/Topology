package net.ptcrys.topo.data.equipment.common;

import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.data.material.BuiltinTopoFormDataTypes;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The shaped assembly of one equipment kind: crafting-grid rows plus a symbol → part-form map.
 * Parts are material forms, so the same pattern stamps out one recipe per material. Construction
 * validates the grid shape and the symbol/part wiring; {@link #worthUnits()} derives the material
 * worth (Σ part AMOUNT × cells) — the ledger a future recycling chain consumes.
 */
public final class EquipmentPattern {

    private final List<String> rows;
    private final Map<Character, MaterialForm> parts;
    private final int worthUnits;

    private EquipmentPattern(List<String> rows, Map<Character, MaterialForm> parts) {
        if (rows.isEmpty() || rows.size() > 3) {
            throw new IllegalArgumentException("equipment pattern needs 1-3 rows, got " + rows.size());
        }
        int width = rows.get(0).length();
        if (width < 1 || width > 3) {
            throw new IllegalArgumentException("equipment pattern rows must be 1-3 wide, got " + width);
        }
        if (parts.containsKey(' ')) {
            throw new IllegalArgumentException("equipment pattern must not map the blank symbol ' '");
        }
        Set<Character> used = new HashSet<>();
        int worth = 0;
        for (String row : rows) {
            if (row.length() != width) {
                throw new IllegalArgumentException(
                        "equipment pattern rows must share one width: '" + row + "' vs width " + width);
            }
            for (int i = 0; i < row.length(); i++) {
                char symbol = row.charAt(i);
                if (symbol == ' ') {
                    continue;
                }
                MaterialForm form = parts.get(symbol);
                if (form == null) {
                    throw new IllegalArgumentException(
                            "equipment pattern symbol '" + symbol + "' has no part mapping");
                }
                used.add(symbol);
                worth = Math.addExact(worth,
                        form.strategy().requireData(BuiltinTopoFormDataTypes.AMOUNT));
            }
        }
        for (Character symbol : parts.keySet()) {
            if (!used.contains(symbol)) {
                throw new IllegalArgumentException(
                        "equipment pattern maps symbol '" + symbol + "' but never uses it");
            }
        }
        this.rows = rows;
        // Symbol-sorted so per-material recipe emission iterates parts deterministically run-to-run.
        this.parts = new TreeMap<>(parts);
        this.worthUnits = worth;
    }

    public static EquipmentPattern of(Map<Character, MaterialForm> parts, String... rows) {
        return new EquipmentPattern(List.of(rows), parts);
    }

    public List<String> rows() {
        return rows;
    }

    /** Symbol → part form, iteration ordered by symbol. */
    public Map<Character, MaterialForm> parts() {
        return parts;
    }

    public Collection<MaterialForm> forms() {
        return parts.values();
    }

    /** Material worth of one finished item, in AMOUNT units (72 = one ingot). */
    public int worthUnits() {
        return worthUnits;
    }
}
