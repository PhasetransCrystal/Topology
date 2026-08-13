package net.ptcrys.topo.api.api.lang;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;

/**
 * API-owned translation keys used by runtime API code (recipe state, pipe overlays, surveyor
 * feedback, multiblock modifier fold). Registered only through
 * {@link OfficialTopoAPIPlugin#lang()}.
 */
public final class TopoApiLang {

    private static final LangDomainRegistration LANG = OfficialTopoAPIPlugin.INSTANCE.lang();

    // --- RecipeLogic state display -----------------------------------------------------------

    public static final LangKey UI_RECIPE_STATE_IDLE = LANG.key("ui", "recipe.state.idle", "Idle", "空闲");
    public static final LangKey UI_RECIPE_STATE_WORKING = LANG.key("ui", "recipe.state.working", "Working", "运行中");
    public static final LangKey UI_RECIPE_STATE_WAITING_OUTPUT = LANG.key("ui", "recipe.state.waiting_output", "Output full", "输出已满");
    public static final LangKey UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_PROCESS = LANG.key("ui", "recipe.state.waiting_tick_input_to_process", "Input starved", "输入不足");
    public static final LangKey UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_START = LANG.key(
            "ui",
            "recipe.state.waiting_tick_input_to_start",
            "Awaiting input",
            "等待输入");
    public static final LangKey UI_RECIPE_STATE_WAITING_TICK_OUTPUT_TO_START = LANG.key(
            "ui",
            "recipe.state.waiting_tick_output_to_start",
            "Awaiting output space",
            "等待输出空间");
    public static final LangKey UI_RECIPE_STATE_MISSING_ACTIVE_RECIPE = LANG.key("ui", "recipe.state.missing_active_recipe", "Recipe missing", "配方缺失");

    // --- Machine UI generic labels (runtime UI framework owns the layout, not content) --------

    public static final LangKey UI_RECIPE_PAGE = LANG.key("ui", "recipe.page", "Recipe", "配方");
    public static final LangKey UI_RECIPE_STATUS = LANG.key("ui", "recipe.status", "Status", "状态");
    public static final LangKey UI_RECIPE_STATE = LANG.key("ui", "recipe.state", "State", "状态");
    public static final LangKey UI_RECIPE_STATE_HALTED = LANG.key("ui", "recipe.state.halted", "Halted", "已停止");
    public static final LangKey UI_RECIPE_PROGRESS = LANG.key("ui", "recipe.progress", "Progress", "进度");
    public static final LangKey UI_RECIPE_DURATION = LANG.key("ui", "recipe.duration", "Duration", "耗时");
    public static final LangKey UI_RECIPE_MODIFIERS = LANG.key("ui", "recipe.modifiers", "Recipe Modifiers", "配方修正");
    public static final LangKey UI_RECIPE_SHOW_RECIPES = LANG.key("ui", "recipe.show_recipes", "Show recipes", "查看配方");
    public static final LangKey UI_AMOUNT_POPUP_CONFIRM = LANG.key("ui", "amount_popup.confirm", "Confirm", "确认");
    public static final LangKey UI_AMOUNT_POPUP_CANCEL = LANG.key("ui", "amount_popup.cancel", "Cancel", "取消");
    public static final LangKey UI_SIDE_IO_TOOLTIP = LANG.key("ui", "side_io.tooltip", "%s: %s", "%s: %s");
    public static final LangKey UI_SIDE_IO_RESET = LANG.key("ui", "side_io.reset", "Reset sides to default", "重置各面为默认");
    public static final LangKey UI_SIDE_IO_DISABLE = LANG.key("ui", "side_io.disable", "Close all sides", "关闭所有面");
    public static final LangKey UI_SIDE_IO_TITLE_INSERT = LANG.key("ui", "side_io.title.insert", "Input", "输入");
    public static final LangKey UI_SIDE_IO_TITLE_EXTRACT = LANG.key("ui", "side_io.title.extract", "Output", "输出");
    public static final LangKey UI_SIDE_IO_TITLE_BOTH = LANG.key("ui", "side_io.title.both", "Storage", "存储");
    public static final LangKey UI_SIDE_IO_FACE_FRONT = LANG.key("ui", "side_io.face.front", "Front", "前");
    public static final LangKey UI_SIDE_IO_FACE_BACK = LANG.key("ui", "side_io.face.back", "Back", "后");
    public static final LangKey UI_SIDE_IO_FACE_LEFT = LANG.key("ui", "side_io.face.left", "Left", "左");
    public static final LangKey UI_SIDE_IO_FACE_RIGHT = LANG.key("ui", "side_io.face.right", "Right", "右");
    public static final LangKey UI_SIDE_IO_FACE_UP = LANG.key("ui", "side_io.face.up", "Top", "上");
    public static final LangKey UI_SIDE_IO_FACE_DOWN = LANG.key("ui", "side_io.face.down", "Bottom", "下");
    public static final LangKey UI_SIDE_IO_MODE_NONE = LANG.key("ui", "side_io.mode.none", "Closed", "关闭");
    public static final LangKey UI_SIDE_IO_MODE_INSERT = LANG.key("ui", "side_io.mode.insert", "Input", "输入");
    public static final LangKey UI_SIDE_IO_MODE_EXTRACT = LANG.key("ui", "side_io.mode.extract", "Output", "输出");
    public static final LangKey UI_SIDE_IO_MODE_BOTH = LANG.key("ui", "side_io.mode.both", "Input + Output", "输入 + 输出");

    // --- Multiblock part-modifier fold -------------------------------------------------------

    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_NAME = LANG.key("ui", "recipe.modifier.multiblock.name", "Part Modifiers", "部件配方修正");
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_DESC = LANG.key(
            "ui",
            "recipe.modifier.multiblock.desc",
            "Folds modifiers from formed parts",
            "折叠已成型部件的修正");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_SCOPE = LANG.key("ui", "recipe.modifier.attr.scope", "Scope", "范围");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_EFFECT = LANG.key("ui", "recipe.modifier.attr.effect", "Effect", "效果");
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_SCOPE = LANG.key("ui", "recipe.modifier.multiblock.scope", "Formed parts", "已成型部件");
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_EFFECT = LANG.key(
            "ui",
            "recipe.modifier.multiblock.effect",
            "Fold part modifiers",
            "折叠部件修正");

    // --- Pipe wrench / strategy overlay ------------------------------------------------------

    public static final LangKey PIPE_SIDE_MODE = LANG.key("pipe", "side_mode", "%s: %s", "%s: %s");
    public static final LangKey PIPE_SIDE_DOWN = LANG.key("pipe", "side.down", "Down", "下");
    public static final LangKey PIPE_SIDE_UP = LANG.key("pipe", "side.up", "Up", "上");
    public static final LangKey PIPE_SIDE_NORTH = LANG.key("pipe", "side.north", "North", "北");
    public static final LangKey PIPE_SIDE_SOUTH = LANG.key("pipe", "side.south", "South", "南");
    public static final LangKey PIPE_SIDE_WEST = LANG.key("pipe", "side.west", "West", "西");
    public static final LangKey PIPE_SIDE_EAST = LANG.key("pipe", "side.east", "East", "东");
    public static final LangKey PIPE_MODE_AUTO = LANG.key("pipe", "mode.auto", "Connected", "已连接");
    public static final LangKey PIPE_MODE_DISABLED = LANG.key("pipe", "mode.disabled", "Disconnected", "已断开");
    public static final LangKey PIPE_MODE_EXTRACT = LANG.key("pipe", "mode.extract", "Extracting", "抽取中");
    public static final LangKey PIPE_STRATEGY_SELECTED = LANG.key("pipe", "strategy_selected", "Strategy: %s", "策略: %s");

    // --- Pipe port UI (runtime UI framework) --------------------------------------------------

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

    // --- Pipe surveyor feedback --------------------------------------------------------------

    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_ANCHORED = LANG.key(
            "equipment",
            "pipe_surveyor.anchored",
            "Survey anchored (range %s)",
            "勘测已锚定（范围 %s）");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_POINT_A = LANG.key("equipment", "pipe_surveyor.point_a", "Endpoint A set", "端点 A 已设置");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_POINT_B = LANG.key("equipment", "pipe_surveyor.point_b", "Endpoint B set", "端点 B 已设置");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_CLEARED = LANG.key("equipment", "pipe_surveyor.cleared", "Survey ended", "勘测已结束");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR = LANG.key(
            "equipment",
            "pipe_surveyor.need_anchor",
            "Anchor a pipe network first",
            "请先锚定一个管道网络");

    private TopoApiLang() {}

    public static void init() {}

    /** Display handle for a world direction (pipe port UI / overlays). */
    public static LangKey side(net.minecraft.core.Direction direction) {
        return switch (direction) {
            case DOWN -> PIPE_SIDE_DOWN;
            case UP -> PIPE_SIDE_UP;
            case NORTH -> PIPE_SIDE_NORTH;
            case SOUTH -> PIPE_SIDE_SOUTH;
            case WEST -> PIPE_SIDE_WEST;
            case EAST -> PIPE_SIDE_EAST;
        };
    }

    /** Side-IO card title for a port's recipe role (Input / Output / Storage). */
    public static LangKey sideIoTitle(net.ptcrys.topo.api.machine.resource.RecipeRole role) {
        return switch (role) {
            case INPUT -> UI_SIDE_IO_TITLE_INSERT;
            case OUTPUT -> UI_SIDE_IO_TITLE_EXTRACT;
            case BOTH, NONE -> UI_SIDE_IO_TITLE_BOTH;
        };
    }

    /** Side-IO face cell tooltip mode label. */
    public static LangKey sideIoMode(net.ptcrys.topo.api.machine.resource.AutomationIo mode) {
        return switch (mode) {
            case NONE -> UI_SIDE_IO_MODE_NONE;
            case INSERT -> UI_SIDE_IO_MODE_INSERT;
            case EXTRACT -> UI_SIDE_IO_MODE_EXTRACT;
            case BOTH -> UI_SIDE_IO_MODE_BOTH;
        };
    }

    /**
     * Side-IO face cell name. {@code faceKey} is the local layout key used by the grid
     * ({@code front}/{@code back}/{@code left}/{@code right}/{@code up}/{@code down}).
     */
    public static LangKey sideIoFace(String faceKey) {
        return switch (faceKey) {
            case "front" -> UI_SIDE_IO_FACE_FRONT;
            case "back" -> UI_SIDE_IO_FACE_BACK;
            case "left" -> UI_SIDE_IO_FACE_LEFT;
            case "right" -> UI_SIDE_IO_FACE_RIGHT;
            case "up" -> UI_SIDE_IO_FACE_UP;
            case "down" -> UI_SIDE_IO_FACE_DOWN;
            default -> throw new IllegalArgumentException("unknown side-io face key: " + faceKey);
        };
    }
}
