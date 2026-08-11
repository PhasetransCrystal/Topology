package net.ptcrys.topo.data.equipment;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.data.OfficialTopoPlugin;

/**
 * Equipment domain tooltip labels and regulator feedback. Pipe surveyor feedback keys are owned by
 * {@link TopoApiLang} (API plugin) and re-exported here.
 */
public final class BuiltinTopoEquipmentLang {

    private static final LangDomainRegistration LANG = OfficialTopoPlugin.INSTANCE.lang();

    public static final LangKey TOOLTIP_EQUIPMENT_DURABILITY = LANG.key("tooltip", "equipment.durability", "Durability", "耐久");
    public static final LangKey TOOLTIP_EQUIPMENT_FUNCTION = LANG.key("tooltip", "equipment.function", "Function", "功能");
    public static final LangKey EQUIPMENT_REGULATOR_HALTED = LANG.key("equipment", "regulator.halted", "Machine halted", "机器已停止");
    public static final LangKey EQUIPMENT_REGULATOR_RESUMED = LANG.key("equipment", "regulator.resumed", "Machine resumed", "机器已恢复");
    public static final LangKey TOOLTIP_EQUIPMENT_RANGE = LANG.key("tooltip", "equipment.range", "Range", "范围");
    public static final LangKey TOOLTIP_EQUIPMENT_RANGE_BLOCKS = LANG.key("tooltip", "equipment.range_blocks", "%s blocks", "%s 格");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_ANCHORED = TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_ANCHORED;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_POINT_A = TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_POINT_A;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_POINT_B = TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_POINT_B;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_CLEARED = TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_CLEARED;
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR = TopoApiLang.EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR;

    private BuiltinTopoEquipmentLang() {}

    public static void init() {}
}
