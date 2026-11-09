package net.ptcrys.topodemo.api.lang;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;

public final class ContentLang {

    public static final LangKey TOGGLED = TopoDemoApiPlugin.INSTANCE.lang().key("equipment", "regulator.toggled", "Machine work mode toggled", "已切换机器工作状态");
    public static final LangKey TOOL_HINT = TopoDemoApiPlugin.INSTANCE.lang().key("tooltip", "regulator.hint", "Right-click a machine to pause or resume; sneak-click pipes to configure IO", "右键机器切换启停；潜行右键管道配置输入输出");

    public static final LangKey SURVEYOR_HINT = TopoDemoApiPlugin.INSTANCE.lang().key("tooltip", "pipe_surveyor.hint",
            "Use on a pipe to survey; sneak-use selects endpoints; use in air to clear",
            "右键管道勘测；潜行右键选取端点；右键空气结束勘测");

    public static final LangKey STRUCTURE_TOOL_HINT = TopoDemoApiPlugin.INSTANCE.lang().key("tooltip", "structure_tool.hint",
            "Use on a controller to diagnose and preview; sneak-use to build; use in air to configure",
            "右键控制器检测并显示虚影；潜行右键补建；右键空气配置");

    public static final LangKey STRUCTURE_REQUIRED = TopoDemoApiPlugin.INSTANCE.lang().key("machine", "structure_required",
            "Complete the structure to process recipes", "完成结构后可处理配方");
    public static final LangKey VEIN_CATEGORY = TopoDemoApiPlugin.INSTANCE.lang().key("ore", "category", "Ore Deposits", "矿床");
    public static final LangKey RECIPE_DURATION = TopoDemoApiPlugin.INSTANCE.lang().key("recipe", "duration", "Duration: %s ticks", "耗时：%s tick");

    private ContentLang() {}

    public static void init() {}
}
