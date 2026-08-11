package net.ptcrys.topo.data.pipe;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.TopoApiLang;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.PipeSideRole;
import net.ptcrys.topo.data.OfficialTopoPlugin;

import net.minecraft.core.Direction;

/**
 * Raw lang entries for the pipe domain (side modes, port UI, Jade lines and tooltip labels).
 * Strategy display names live with their strategy registrations. API-runtime keys are owned by
 * {@link TopoApiLang} and re-exported here for data-layer convenience.
 */
public final class BuiltinTopoPipeLang {

    private static final LangDomainRegistration LANG = OfficialTopoPlugin.INSTANCE.lang();

    public static final LangKey PIPE_SIDE_MODE = TopoApiLang.PIPE_SIDE_MODE;
    public static final LangKey PIPE_SIDE_DOWN = TopoApiLang.PIPE_SIDE_DOWN;
    public static final LangKey PIPE_SIDE_UP = TopoApiLang.PIPE_SIDE_UP;
    public static final LangKey PIPE_SIDE_NORTH = TopoApiLang.PIPE_SIDE_NORTH;
    public static final LangKey PIPE_SIDE_SOUTH = TopoApiLang.PIPE_SIDE_SOUTH;
    public static final LangKey PIPE_SIDE_WEST = TopoApiLang.PIPE_SIDE_WEST;
    public static final LangKey PIPE_SIDE_EAST = TopoApiLang.PIPE_SIDE_EAST;
    public static final LangKey PIPE_MODE_AUTO = TopoApiLang.PIPE_MODE_AUTO;
    public static final LangKey PIPE_MODE_DISABLED = TopoApiLang.PIPE_MODE_DISABLED;
    public static final LangKey PIPE_MODE_EXTRACT = TopoApiLang.PIPE_MODE_EXTRACT;
    public static final LangKey PIPE_ROLE_LINK = LANG.key("pipe", "role.link", "Pipe", "管道");
    public static final LangKey PIPE_ROLE_DESTINATION = LANG.key("pipe", "role.destination", "Insert", "插入");
    public static final LangKey PIPE_ROLE_EXTRACT = LANG.key("pipe", "role.extract", "Extract", "抽取");
    public static final LangKey PIPE_STRATEGY_SELECTED = TopoApiLang.PIPE_STRATEGY_SELECTED;
    public static final LangKey PIPE_DISTANCE_ORDER_NEAREST = LANG.key("pipe", "distance_order.nearest", "Nearest", "最近");
    public static final LangKey PIPE_DISTANCE_ORDER_FARTHEST = LANG.key("pipe", "distance_order.farthest", "Farthest", "最远");
    public static final LangKey PIPE_JADE_NODE = LANG.key("pipe", "jade.node", "Node %s / %s per tick", "节点 %s / 每 tick %s");
    public static final LangKey PIPE_JADE_FLOW = LANG.key("pipe", "jade.flow", "%s: %s · %s/t", "%s: %s · %s/t");
    public static final LangKey PIPE_JADE_EXTRACT_DETAIL = LANG.key(
            "pipe",
            "jade.extract_detail",
            "%s · rate %s / cap %s",
            "%s · 速率 %s / 容量 %s");
    public static final LangKey PIPE_JADE_NETWORK = LANG.key(
            "pipe",
            "jade.network",
            "Network: %s nodes · %s extractors · %s targets",
            "网络: %s 节点 · %s 抽取器 · %s 目标");
    public static final LangKey PIPE_JADE_MOVED = LANG.key("pipe", "jade.moved", "Moved last tick: %s", "上 tick 移动: %s");
    public static final LangKey PIPE_JADE_BOTTLENECK = LANG.key("pipe", "jade.bottleneck", "Peak node load: %s%%", "节点峰值负载: %s%%");
    public static final LangKey PIPE_JADE_TICK_TIME = LANG.key("pipe", "jade.tick_time", "Network tick: %s µs", "网络 tick: %s µs");
    public static final LangKey PIPE_JADE_LCD_NODE = LANG.key("pipe", "jade.lcd.node", "node", "节点");
    public static final LangKey PIPE_JADE_LCD_NETWORK = LANG.key("pipe", "jade.lcd.network", "network", "网络");
    public static final LangKey PIPE_JADE_LCD_EXTRACTORS = LANG.key("pipe", "jade.lcd.extractors", "extractors", "抽取器");
    public static final LangKey PIPE_JADE_LCD_TARGETS = LANG.key("pipe", "jade.lcd.targets", "targets", "目标");
    public static final LangKey PIPE_JADE_LCD_MOVED = LANG.key("pipe", "jade.lcd.moved", "moved", "移动");
    public static final LangKey PIPE_JADE_LCD_PEAK = LANG.key("pipe", "jade.lcd.peak", "peak load", "峰值负载");
    public static final LangKey PIPE_JADE_LCD_TICK = LANG.key("pipe", "jade.lcd.tick", "engine tick", "引擎 tick");
    // 管道物品悬浮规格面板(PipeSpecTooltips):注册期事实,无运行期数据。
    public static final LangKey TOOLTIP_PIPE_MAX_RATE = LANG.key("tooltip", "pipe.max_rate", "Max rate", "最大速率");
    public static final LangKey TOOLTIP_PIPE_NODE_THROUGHPUT = LANG.key("tooltip", "pipe.node_throughput", "Node throughput", "节点吞吐");
    public static final LangKey TOOLTIP_PIPE_STRATEGIES = LANG.key("tooltip", "pipe.strategies", "Strategies", "策略");
    public static final LangKey TOOLTIP_PIPE_STRATEGY_COUNT = LANG.key("tooltip", "pipe.strategy_count", "%s kinds", "%s 种");
    public static final LangKey TOOLTIP_PIPE_FILTER = LANG.key("tooltip", "pipe.filter", "Filter", "过滤");
    public static final LangKey TOOLTIP_PIPE_PER_TICK = LANG.key("tooltip", "pipe.per_tick", "%s / t", "%s / t");
    public static final LangKey TOOLTIP_PIPE_FILTER_ENTRIES = LANG.key("tooltip", "pipe.filter_entries", "%s entries", "%s 条目");
    public static final LangKey TOOLTIP_PIPE_FILTER_ENTRIES_TAGS = LANG.key(
            "tooltip",
            "pipe.filter_entries_tags",
            "%s entries · #tag",
            "%s 条目 · #tag");
    public static final LangKey TOOLTIP_PIPE_FILTER_NONE = LANG.key("tooltip", "pipe.filter_none", "—", "—");
    // 设置卡:行式标签 + 可点数值。值/范围细节住数值编辑弹窗。
    public static final LangKey UI_PIPE_PORT_RATE_VALUE = LANG.key("ui", "pipe_port.rate_value", "%s / %s", "%s / %s");
    public static final LangKey UI_PIPE_PORT_RATE = LANG.key("ui", "pipe_port.rate", "Rate", "速率");
    public static final LangKey UI_PIPE_PORT_SECTION_HINT = LANG.key("ui", "pipe_port.section_hint", "rate", "速率");
    public static final LangKey UI_PIPE_PORT_INTERVAL = LANG.key("ui", "pipe_port.interval", "Interval", "间隔");
    public static final LangKey UI_PIPE_PORT_INTERVAL_FIXED = LANG.key("ui", "pipe_port.interval_fixed", "Interval (fixed)", "间隔 (固定)");
    public static final LangKey UI_PIPE_PORT_INTERVAL_VALUE = LANG.key("ui", "pipe_port.interval_value", "%s t · %s s", "%s t · %s s");
    public static final LangKey UI_PIPE_PORT_DISTANCE_ORDER = LANG.key("ui", "pipe_port.distance_order", "Order", "顺序");
    public static final LangKey UI_PIPE_PORT_CLICK_TO_EDIT = LANG.key(
            "ui",
            "pipe_port.click_to_edit",
            "Click to type an exact value",
            "点击以输入精确值");
    public static final LangKey UI_PIPE_PORT_AMOUNT_POPUP_TITLE = LANG.key("ui", "pipe_port.amount_popup_title", "Batch amount", "批量数量");
    public static final LangKey UI_PIPE_PORT_INTERVAL_POPUP_TITLE = LANG.key("ui", "pipe_port.interval_popup_title", "Interval (ticks)", "间隔 (tick)");
    public static final LangKey UI_PIPE_PORT_WHITELIST = LANG.key("ui", "pipe_port.whitelist", "Whitelist", "白名单");
    public static final LangKey UI_PIPE_PORT_BLACKLIST = LANG.key("ui", "pipe_port.blacklist", "Blacklist", "黑名单");
    public static final LangKey UI_PIPE_PORT_WHITELIST_SHORT = LANG.key("ui", "pipe_port.whitelist_short", "W %s", "白 %s");
    public static final LangKey UI_PIPE_PORT_BLACKLIST_SHORT = LANG.key("ui", "pipe_port.blacklist_short", "B %s", "黑 %s");
    public static final LangKey UI_PIPE_PORT_FILTER_RULES = LANG.key("ui", "pipe_port.filter_rules", "Filter Rules", "过滤规则");
    public static final LangKey UI_PIPE_PORT_FILTER_COUNT = LANG.key("ui", "pipe_port.filter_count", "%s / %s entries", "%s / %s 条目");
    public static final LangKey UI_PIPE_PORT_FILTER_PLACEHOLDER = LANG.key("ui", "pipe_port.filter_placeholder", "id or #tag...", "id 或 #tag...");
    // 条目行右侧类型提示:具体物品=item、流体=fluid、标签=按预览物品数显 "preview N"(否则 tag)。
    public static final LangKey UI_PIPE_PORT_FILTER_ENTRY_ITEM = LANG.key("ui", "pipe_port.filter_entry_item", "item", "物品");
    public static final LangKey UI_PIPE_PORT_FILTER_ENTRY_FLUID = LANG.key("ui", "pipe_port.filter_entry_fluid", "fluid", "流体");
    public static final LangKey UI_PIPE_PORT_FILTER_ENTRY_TAG = LANG.key("ui", "pipe_port.filter_entry_tag", "tag", "标签");
    public static final LangKey UI_PIPE_PORT_FILTER_ENTRY_PREVIEW = LANG.key("ui", "pipe_port.filter_entry_preview", "preview %s", "预览 %s");
    public static final LangKey UI_PIPE_PORT_ADD_TO_WHITELIST = LANG.key("ui", "pipe_port.add_to_whitelist", "Add to whitelist", "加入白名单");
    public static final LangKey UI_PIPE_PORT_ADD_TO_BLACKLIST = LANG.key("ui", "pipe_port.add_to_blacklist", "Add to blacklist", "加入黑名单");
    public static final LangKey UI_PIPE_PORT_FILTER_PICK_TOOLTIP = LANG.key(
            "ui",
            "pipe_port.filter_pick_tooltip",
            "Pick items from your inventory or drag from JEI",
            "从背包选取物品或从 JEI 拖入");
    public static final LangKey UI_PIPE_PORT_FILTER_ADD_TOOLTIP = LANG.key(
            "ui",
            "pipe_port.filter_add_tooltip",
            "Add the typed id or #tag",
            "添加输入的 id 或 #tag");
    public static final LangKey UI_PIPE_PORT_FILTER_INPUT_TOOLTIP = LANG.key(
            "ui",
            "pipe_port.filter_input_tooltip",
            "Type a registry id (minecraft:coal) or a tag (#c:ingots), then press Enter or +",
            "输入注册 id (minecraft:coal) 或标签 (#c:ingots),然后按 Enter 或 +");
    // Jade asserts (dev) that every plugin provider UID has a config entry translation.
    public static final LangKey CONFIG_JADE_PLUGIN_ODYSSEYINDUSTRIAL_PIPE = LANG.absolute("config.jade.plugin_topo.pipe", "Pipe Network", "管道网络");

    private BuiltinTopoPipeLang() {}

    /** Only activates class initialization. Must remain empty. */
    public static void init() {}

    /** Display handle for a world direction (Jade / wrench overlay). */
    public static LangKey side(Direction direction) {
        return switch (direction) {
            case DOWN -> PIPE_SIDE_DOWN;
            case UP -> PIPE_SIDE_UP;
            case NORTH -> PIPE_SIDE_NORTH;
            case SOUTH -> PIPE_SIDE_SOUTH;
            case WEST -> PIPE_SIDE_WEST;
            case EAST -> PIPE_SIDE_EAST;
        };
    }

    /** Display handle for an effective pipe side role. */
    public static LangKey role(PipeSideRole role) {
        return switch (role) {
            case NONE, LINK -> PIPE_ROLE_LINK;
            case DESTINATION -> PIPE_ROLE_DESTINATION;
            case EXTRACT -> PIPE_ROLE_EXTRACT;
        };
    }

    /** Display handle for a stored side intent (wrench cycle). */
    public static LangKey mode(PipeSideIntent intent) {
        return switch (intent) {
            case AUTO -> PIPE_MODE_AUTO;
            case DISABLED -> PIPE_MODE_DISABLED;
            case EXTRACT -> PIPE_MODE_EXTRACT;
        };
    }

    /** Display handle for {@link BuiltinTopoPipeDistributionStrategies.DistanceOrder}. */
    public static LangKey distanceOrder(BuiltinTopoPipeDistributionStrategies.DistanceOrder order) {
        return switch (order) {
            case NEAREST -> PIPE_DISTANCE_ORDER_NEAREST;
            case FARTHEST -> PIPE_DISTANCE_ORDER_FARTHEST;
        };
    }
}
