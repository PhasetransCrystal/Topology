package net.ptcrys.topo.datav2.equipment;

import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.api.lang.OIApiLang;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;

/**
 * Equipment domain tooltip labels and regulator feedback. Pipe surveyor feedback keys are owned by
 * {@link OIApiLang} (API plugin) and re-exported here.
 */
public final class BuiltinOIEquipmentLang {

    private static final LangDomainRegistration LANG = OfficialOIPlugin.INSTANCE.lang();

    public static final LangKey TOOLTIP_EQUIPMENT_DURABILITY = LANG.key("tooltip", "equipment.durability", "Durability", "耐久");
    public static final LangKey TOOLTIP_EQUIPMENT_FUNCTION = LANG.key("tooltip", "equipment.function", "Function", "功能");
    public static final LangKey EQUIPMENT_REGULATOR_HALTED = LANG.key("equipment", "regulator.halted", "Machine halted", "机器已停止");
    public static final LangKey EQUIPMENT_REGULATOR_RESUMED = LANG.key("equipment", "regulator.resumed", "Machine resumed", "机器已恢复");
    public static final LangKey TOOLTIP_EQUIPMENT_RANGE = LANG.key("tooltip", "equipment.range", "Range", "范围");
    public static final LangKey TOOLTIP_EQUIPMENT_RANGE_BLOCKS = LANG.key("tooltip", "equipment.range_blocks", "%s blocks", "%s 格");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_ANCHORED = OIApiLang.EQUIPMENT_PIPE_SURVEYOR_ANCHORED;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_POINT_A = OIApiLang.EQUIPMENT_PIPE_SURVEYOR_POINT_A;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_POINT_B = OIApiLang.EQUIPMENT_PIPE_SURVEYOR_POINT_B;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_CLEARED = OIApiLang.EQUIPMENT_PIPE_SURVEYOR_CLEARED;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR = OIApiLang.EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR;

    private BuiltinOIEquipmentLang() {}

    public static void init() {}
}
