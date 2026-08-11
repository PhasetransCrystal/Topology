package net.ptcrys.topo.data.equipment.common;

import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.data.equipment.BuiltinTopoEquipment;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes;
import net.ptcrys.topo.data.material.common.SurveyStatsData;

import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * 勘测仪观测范围查表:物品 → 材质 SURVEY_STATS.range。装备注册台账(equipment×material
 * →item)在冻结后惰性反查一次构表;物品绑定先于任何 useOn,首次查询即完整。
 */
public final class SurveyorRanges {

    /** 未声明档位时的保底范围(理论上不可达:appliesTo 要求 SURVEY_STATS)。 */
    public static final int FALLBACK_RANGE = 16;

    private static volatile Map<Item, Integer> ranges;

    private SurveyorRanges() {}

    public static int rangeOf(Item item) {
        Map<Item, Integer> table = ranges;
        if (table == null) {
            table = buildTable();
            ranges = table;
        }
        return table.getOrDefault(item, FALLBACK_RANGE);
    }

    private static Map<Item, Integer> buildTable() {
        Map<Item, Integer> table = new HashMap<>();
        for (EquipmentRegistry.EquipmentItemRecord record : EquipmentRegistry.itemRecords()) {
            if (record.equipment() != BuiltinTopoEquipment.PIPE_SURVEYOR) {
                continue;
            }
            int range = record.material().strategy()
                    .data(BuiltinTopoMaterialDataTypes.SURVEY_STATS)
                    .map(SurveyStatsData::range)
                    .orElse(FALLBACK_RANGE);
            table.put(record.entry().get(), range);
        }
        return Map.copyOf(table);
    }
}
