package net.ptcrys.topo.data.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

/** 声明勘测档位即让该材质的管网勘测仪存在(装备域驱动,与 TOOL_STATS 同款公民资格)。 */
public final class SurveyStatsDataType extends MaterialDataType<SurveyStatsData> {

    public SurveyStatsDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<SurveyStatsData> range(int range) {
        return use(SurveyStatsData.create(range));
    }
}
