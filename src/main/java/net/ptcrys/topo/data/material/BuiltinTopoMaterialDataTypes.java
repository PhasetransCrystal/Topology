package net.ptcrys.topo.data.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.common.ArmorStatsDataType;
import net.ptcrys.topo.data.material.common.ItemAdditiveDataType;
import net.ptcrys.topo.data.material.common.MassDataType;
import net.ptcrys.topo.data.material.common.RgbColorDataType;
import net.ptcrys.topo.data.material.common.SurveyStatsDataType;
import net.ptcrys.topo.data.material.common.ToolStatsDataType;

public final class BuiltinTopoMaterialDataTypes {

    private static final MaterialDomainRegistration MATERIALS = OfficialTopoPlugin.INSTANCE.material();

    public static final RgbColorDataType PRIMARY_COLOR = MATERIALS.dataType(
            "primary_color", new RgbColorDataType(MATERIALS.id("primary_color")));

    public static final RgbColorDataType SECONDARY_COLOR = MATERIALS.dataType(
            "secondary_color", new RgbColorDataType(MATERIALS.id("secondary_color")));

    public static final MassDataType MASS = MATERIALS.dataType(
            "mass", new MassDataType(MATERIALS.id("mass")));

    /** Flux/binder appended to every sintering step of materials that declare it (e.g. alloys). */
    public static final ItemAdditiveDataType SINTERING_ADDITIVE = MATERIALS.dataType(
            "sintering_additive", new ItemAdditiveDataType(MATERIALS.id("sintering_additive")));

    /** Declaring tool stats is what makes a material's tools exist (equipment domain driver). */
    public static final ToolStatsDataType TOOL_STATS = MATERIALS.dataType(
            "tool_stats", new ToolStatsDataType(MATERIALS.id("tool_stats")));

    /** Declaring armor stats is what makes a material's armor exist (equipment domain driver). */
    public static final ArmorStatsDataType ARMOR_STATS = MATERIALS.dataType(
            "armor_stats", new ArmorStatsDataType(MATERIALS.id("armor_stats")));

    /** Declaring survey stats is what makes a material's pipe surveyor exist (equipment driver). */
    public static final SurveyStatsDataType SURVEY_STATS = MATERIALS.dataType(
            "survey_stats", new SurveyStatsDataType(MATERIALS.id("survey_stats")));

    private BuiltinTopoMaterialDataTypes() {}

    public static void init() {}
}
