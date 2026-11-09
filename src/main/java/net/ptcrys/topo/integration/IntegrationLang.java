package net.ptcrys.topo.integration;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.pipe.PipeSideRole;

import net.minecraft.core.Direction;

/** 框架集成界面的语言，不声明具体资源、端口或工具。 */
public final class IntegrationLang {

    private static final LangDomainRegistration LANG = OfficialTopoAPIPlugin.INSTANCE.lang();
    public static final LangKey UI_RECIPE_TIME = LANG.key("ui", "recipe.time", "Time", "时间");

    public static final LangKey UI_JEI_TRANSFER_NO_ENCODABLE_IO = LANG.key("ui", "jei.transfer.no_encodable_io", "No item or fluid I/O in this recipe to encode.", "此配方没有可编码的物品或流体输入 / 输出。");

    public static final LangKey UI_PATTERN_BUILDER_FIXED_BLOCKS = LANG.key("ui", "pattern_builder.fixed_blocks", "Fixed Blocks", "固定方块");

    public static final LangKey UI_PATTERN_BUILDER_TOGGLE_FIXED = LANG.key("ui", "pattern_builder.toggle_fixed", "Include in pattern", "纳入样板");

    public static final LangKey UI_PATTERN_BUILDER_REMOVE = LANG.key("ui", "pattern_builder.remove", "Remove one", "移除一个");

    public static final LangKey UI_PATTERN_BUILDER_ADD = LANG.key("ui", "pattern_builder.add", "Add one", "增加一个");

    public static final LangKey UI_PATTERN_BUILDER_INPUTS_SHORT = LANG.key("ui", "pattern_builder.inputs_short", "Pattern Inputs", "样板输入");

    public static final LangKey UI_PATTERN_BUILDER_WRITE = LANG.key("ui", "pattern_builder.write", "Write", "写入");

    public static final LangKey UI_PATTERN_BUILDER_CANCEL = LANG.key("ui", "pattern_builder.cancel", "Cancel", "取消");

    public static final LangKey PIPE_JADE_LCD_NODE = LANG.key("pipe", "jade.lcd.node", "node", "节点");

    public static final LangKey PIPE_JADE_LCD_NETWORK = LANG.key("pipe", "jade.lcd.network", "network", "网络");

    public static final LangKey PIPE_JADE_LCD_EXTRACTORS = LANG.key("pipe", "jade.lcd.extractors", "extractors", "抽取器");

    public static final LangKey PIPE_JADE_LCD_TARGETS = LANG.key("pipe", "jade.lcd.targets", "targets", "目标");

    public static final LangKey PIPE_JADE_LCD_MOVED = LANG.key("pipe", "jade.lcd.moved", "moved", "移动");

    public static final LangKey PIPE_JADE_LCD_PEAK = LANG.key("pipe", "jade.lcd.peak", "peak load", "峰值负载");

    public static final LangKey PIPE_JADE_LCD_TICK = LANG.key("pipe", "jade.lcd.tick", "engine tick", "引擎 tick");

    public static final LangKey PIPE_ROLE_LINK = LANG.key("pipe", "role.link", "Pipe", "管道");

    public static final LangKey PIPE_ROLE_DESTINATION = LANG.key("pipe", "role.destination", "Insert", "插入");

    public static final LangKey PIPE_ROLE_EXTRACT = LANG.key("pipe", "role.extract", "Extract", "抽取");

    public static final LangKey CATEGORY = LANG.key("jei", "ore_vein.title", "Ore Veins", "矿脉");

    private IntegrationLang() {}

    public static void init() {}

    public static LangKey side(Direction direction) {
        return TopoApiLang.side(direction);
    }

    public static LangKey role(PipeSideRole role) {
        return switch (role) {
            case NONE, LINK -> PIPE_ROLE_LINK;
            case DESTINATION -> PIPE_ROLE_DESTINATION;
            case EXTRACT -> PIPE_ROLE_EXTRACT;
        };
    }
}
