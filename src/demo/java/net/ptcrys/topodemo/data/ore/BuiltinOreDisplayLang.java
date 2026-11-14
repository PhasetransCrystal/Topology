package net.ptcrys.topodemo.data.ore;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

/** Demo 矿脉面板的语言键。 */
public final class BuiltinOreDisplayLang {

    private static final LangDomainRegistration LANG = TopoDemoPlugin.INSTANCE.lang();
    public static final LangKey LABEL_DIM = LANG.key("jei", "ore_vein.dim", "Dim", "维度");
    public static final LangKey LABEL_HEIGHT = LANG.key("jei", "ore_vein.height", "Height", "高度");
    public static final LangKey LABEL_HOST = LANG.key("jei", "ore_vein.host", "Host", "宿主");
    public static final LangKey LABEL_SIZE = LANG.key("jei", "ore_vein.size", "Size", "大小");
    public static final LangKey LABEL_SPACING = LANG.key("jei", "ore_vein.spacing", "Spacing", "间距");
    public static final LangKey LABEL_EXPOSED = LANG.key("jei", "ore_vein.exposed", "Exposed", "暴露");
    public static final LangKey SPACING_BLOCKS = LANG.key("jei", "ore_vein.spacing.blocks", "%s blocks", "%s格");
    public static final LangKey SPACING_ATTEMPTS = LANG.key("jei", "ore_vein.spacing.attempts", "%s/chunk", "%s/区块");
    public static final LangKey SIZE_RADIUS = LANG.key("jei", "ore_vein.size.radius", "r=%s", "r=%s");
    public static final LangKey SIZE_CLUSTER = LANG.key("jei", "ore_vein.size.cluster", "~%s", "~%s");

    private BuiltinOreDisplayLang() {}

    public static void init() {}
}
