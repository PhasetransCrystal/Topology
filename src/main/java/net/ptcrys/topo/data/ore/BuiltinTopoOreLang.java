package net.ptcrys.topo.data.ore;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.data.OfficialTopoPlugin;

/**
 * JEI ore-vein panel lang. Vein display names live on the vein builder chain.
 */
public final class BuiltinTopoOreLang {

    private static final LangDomainRegistration LANG = OfficialTopoPlugin.INSTANCE.lang();

    public static final LangKey CATEGORY = LANG.key("jei", "ore_vein.title", "Ore Veins", "矿脉");

    public static final LangKey LABEL_DIM = LANG.key("jei", "ore_vein.dim", "Dim", "维度");
    public static final LangKey LABEL_HEIGHT = LANG.key("jei", "ore_vein.height", "Height", "高度");
    public static final LangKey LABEL_HOST = LANG.key("jei", "ore_vein.host", "Host", "宿主");
    public static final LangKey LABEL_SIZE = LANG.key("jei", "ore_vein.size", "Size", "大小");
    public static final LangKey LABEL_SPACING = LANG.key("jei", "ore_vein.spacing", "Spacing", "间距");
    public static final LangKey LABEL_EXPOSED = LANG.key("jei", "ore_vein.exposed", "Exposed", "暴露");

    public static final LangKey DIM_OVERWORLD = LANG.key("jei", "ore_vein.dim.overworld", "Overworld", "主世界");
    public static final LangKey DIM_NETHER = LANG.key("jei", "ore_vein.dim.nether", "Nether", "下界");
    public static final LangKey DIM_END = LANG.key("jei", "ore_vein.dim.end", "End", "末地");

    public static final LangKey HOST_STONE = LANG.key("jei", "ore_vein.host.stone", "Stone", "石头");
    public static final LangKey HOST_DEEPSLATE = LANG.key("jei", "ore_vein.host.deepslate", "Deepslate", "深板岩");
    public static final LangKey HOST_NETHERRACK = LANG.key("jei", "ore_vein.host.netherrack", "Netherrack", "下界岩");

    public static final LangKey EXPOSED_VISIBLE = LANG.key("jei", "ore_vein.exposed.visible", "Visible", "可见");
    public static final LangKey EXPOSED_HIDDEN = LANG.key("jei", "ore_vein.exposed.hidden", "Hidden", "隐藏");
    public static final LangKey EXPOSED_PARTIAL = LANG.key("jei", "ore_vein.exposed.partial", "Partial %s", "部分 %s");

    public static final LangKey SPACING_BLOCKS = LANG.key("jei", "ore_vein.spacing.blocks", "%s blocks", "%s格");
    public static final LangKey SPACING_ATTEMPTS = LANG.key("jei", "ore_vein.spacing.attempts", "%s/chunk", "%s/区块");
    public static final LangKey SIZE_RADIUS = LANG.key("jei", "ore_vein.size.radius", "r=%s", "r=%s");
    public static final LangKey SIZE_CLUSTER = LANG.key("jei", "ore_vein.size.cluster", "~%s", "~%s");

    private BuiltinTopoOreLang() {}

    public static void init() {}
}
