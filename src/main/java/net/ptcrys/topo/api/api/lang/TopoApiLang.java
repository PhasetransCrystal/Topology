package net.ptcrys.topo.api.api.lang;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.machine.resource.AutomationIo;
import net.ptcrys.topo.api.machine.resource.RecipeRole;

import net.minecraft.core.Direction;

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

    public static final LangKey TOOL_STRUCTURE_ENABLED = LANG.key("equipment", "structure_tool.enabled", "Enabled", "开启");
    public static final LangKey TOOL_STRUCTURE_DISABLED = LANG.key("equipment", "structure_tool.disabled", "Disabled", "关闭");
    // 多方块结构工具
    public static final LangKey TOOL_STRUCTURE_TITLE = LANG.key("equipment", "structure_tool.title", "Multiblock Structure Tool", "多方块结构工具");
    public static final LangKey TOOL_STRUCTURE_MODULE = LANG.key("equipment", "structure_tool.module", "Module index (0 = body)", "模块序号（0 为本体）");
    public static final LangKey TOOL_STRUCTURE_REPEATS = LANG.key("equipment", "structure_tool.repeats", "Repeat counts (comma separated; blank = minimum)", "重复次数（逗号分隔，留空取最小）");
    public static final LangKey TOOL_STRUCTURE_MIRROR = LANG.key("equipment", "structure_tool.mirror", "Placement mirror", "放置镜像");
    public static final LangKey TOOL_STRUCTURE_CONFIG_HINT = LANG.key("equipment", "structure_tool.config_hint", "Modules follow controller declaration order", "模块按控制器声明顺序从 1 编号");
    public static final LangKey TOOL_STRUCTURE_APPLY = LANG.key("equipment", "structure_tool.apply", "Apply", "应用");
    public static final LangKey TOOL_STRUCTURE_INVALID_VALUE = LANG.key("equipment", "structure_tool.invalid_value", "Invalid value for %s", "%s 的配置值无效");
    public static final LangKey TOOL_STRUCTURE_DENIED = LANG.key("equipment", "structure_tool.denied", "Structure operation is not permitted here", "此处不允许进行结构操作");
    public static final LangKey TOOL_STRUCTURE_DENIED_AT = LANG.key("equipment", "structure_tool.denied_at", "Cannot place at %s (permission, border or collision)", "无法在 %s 放置（权限、边界或碰撞）");
    public static final LangKey TOOL_STRUCTURE_OBSTRUCTED = LANG.key("equipment", "structure_tool.obstructed", "Placement blocked by %s structure problems; no blocks placed", "存在 %s 处结构问题，未放置任何方块");
    public static final LangKey TOOL_STRUCTURE_NO_ITEM = LANG.key("equipment", "structure_tool.no_item", "No placeable item for %s", "%s 没有可用的放置物品");
    public static final LangKey TOOL_STRUCTURE_MATERIALS = LANG.key("equipment", "structure_tool.materials", "No usable materials for the remaining structure in player inventory", "背包中没有可用于剩余结构的材料");
    public static final LangKey TOOL_STRUCTURE_CHANGED = LANG.key("equipment", "structure_tool.changed", "Structure changed during placement; operation rolled back", "放置期间结构发生变化，操作已回滚");
    public static final LangKey TOOL_STRUCTURE_BUILT = LANG.key("equipment", "structure_tool.built", "Placed %s blocks; %s still missing", "本次放置 %s 个方块，仍缺 %s 个");
    public static final LangKey TOOL_STRUCTURE_MODULE_RANGE = LANG.key("equipment", "structure_tool.module_range", "Module %s does not exist; this controller has %s modules", "模块 %s 不存在，此控制器有 %s 个模块");
    public static final LangKey TOOL_STRUCTURE_LIMIT = LANG.key("equipment", "structure_tool.limit", "Structure exceeds the tool limit of %s cells", "结构超过工具支持的 %s 个位置上限");
    public static final LangKey TOOL_STRUCTURE_CONFIG_ERROR = LANG.key("equipment", "structure_tool.config_error", "Cannot apply tool configuration: %s", "无法应用工具配置：%s");
    public static final LangKey TOOL_STRUCTURE_EXCLUSIVE = LANG.key("equipment", "structure_tool.exclusive", "Exclusive parts are reserved by another controller", "独占部件已被其他控制器预留");
    public static final LangKey TOOL_STRUCTURE_BODY = LANG.key("equipment", "structure_tool.body", "Controller body is not formed", "控制器本体尚未成型");
    public static final LangKey TOOL_STRUCTURE_DEPENDENCIES = LANG.key("equipment", "structure_tool.dependencies", "Missing module dependencies: %s", "缺少模块依赖：%s");
    public static final LangKey TOOL_STRUCTURE_FORMED = LANG.key("equipment", "structure_tool.formed", "Module %s: formed (%s/%s)", "模块 %s：实际结构满足成型条件（%s/%s）");
    public static final LangKey TOOL_STRUCTURE_FAILED = LANG.key("equipment", "structure_tool.failed", "Module %s: not formed (%s/%s)", "模块 %s：实际结构未成型（%s/%s）");
    public static final LangKey TOOL_STRUCTURE_TARGET = LANG.key("equipment", "structure_tool.target", "Build target: %s/%s; repeats %s; mirror %s", "施工目标：%s/%s；重复次数 %s；镜像%s");
    public static final LangKey TOOL_STRUCTURE_MISSING = LANG.key("equipment", "structure_tool.missing", "At %s: expected %s; actual %s", "位置 %s：需要 %s；实际 %s");
    public static final LangKey TOOL_STRUCTURE_ROLE = LANG.key("equipment", "structure_tool.role", "%s: %s (required %s..%s)", "%s：%s（需要 %s～%s）");

    // --- Multiblock structure diagnosis ------------------------------------------------------

    public static final LangKey UI_MULTIBLOCK_CATEGORY = LANG.key("ui", "multiblock.category", "Multiblock Structures", "多方块结构");

    public static final LangKey UI_MULTIBLOCK_NONE = LANG.key("ui", "multiblock.none", "None", "无");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_NEED = LANG.key(
            "ui",
            "multiblock.diagnose.need",
            "%s at %s",
            "%s（位于 %s）");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_NEED_FOUND = LANG.key(
            "ui",
            "multiblock.diagnose.need_found",
            "%s (found %s) at %s",
            "%s（发现 %s，位于 %s）");
    public static final LangKey UI_MULTIBLOCK_STRUCTURE = LANG.key(
            "ui",
            "multiblock.structure",
            "Structure",
            "多方块结构");
    public static final LangKey UI_MULTIBLOCK_STRUCTURE_FORMED = LANG.key(
            "ui",
            "multiblock.structure.formed",
            "Formed",
            "结构成型");
    public static final LangKey UI_MULTIBLOCK_STRUCTURE_YES = LANG.key(
            "ui",
            "multiblock.structure.yes",
            "Yes",
            "成型");
    public static final LangKey UI_MULTIBLOCK_STRUCTURE_NO = LANG.key(
            "ui",
            "multiblock.structure.no",
            "No",
            "未成形");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_ALL_MET = LANG.key(
            "ui",
            "multiblock.diagnose.all_met",
            "All cells match",
            "全部匹配");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_UNLOADED = LANG.key(
            "ui", "multiblock.diagnose.unloaded", "Waiting for chunks", "等待区块加载");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_UNLOADED_COUNT = LANG.key(
            "ui", "multiblock.diagnose.unloaded_count", "%s positions cannot be read yet", "%s 个位置尚未加载，暂时无法完成检测");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_UNSUPPORTED = LANG.key(
            "ui", "multiblock.diagnose.unsupported", "Orientation unavailable", "此朝向不可用");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_UNSUPPORTED_DETAIL = LANG.key(
            "ui", "multiblock.diagnose.unsupported_detail", "Some required block states cannot face this direction", "部分要求的方块状态无法朝向此方向");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_MORE = LANG.key(
            "ui",
            "multiblock.diagnose.more",
            "%s more…",
            "另有 %s 项…");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_LOCATE = LANG.key(
            "ui",
            "multiblock.diagnose.locate",
            "Click to locate",
            "点击定位");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_CELL_OK = LANG.key(
            "ui",
            "multiblock.diagnose.cell_ok",
            "OK",
            "正常");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_EMPTY_CELL = LANG.key(
            "ui",
            "multiblock.diagnose.empty",
            "Empty cell",
            "空格");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_CONSTRAINT = LANG.key("ui", "multiblock.diagnose.constraint", "Cell constraint failed", "单元约束不满足");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_UNKNOWN = LANG.key("ui", "multiblock.diagnose.unknown", "Detection pending", "检测事实尚未就绪");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_WRONG_BLOCK = LANG.key(
            "ui",
            "multiblock.diagnose.wrong_block",
            "Wrong block",
            "方块错误");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_WRONG_PROPERTY = LANG.key(
            "ui",
            "multiblock.diagnose.wrong_property",
            "Wrong state",
            "状态错误");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_MISSING_PART = LANG.key(
            "ui",
            "multiblock.diagnose.missing_part",
            "Missing part",
            "缺少部件");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_EXCESS_PART = LANG.key(
            "ui",
            "multiblock.diagnose.excess_part",
            "Excess part",
            "多余部件");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_MISSING_COUNT = LANG.key(
            "ui",
            "multiblock.diagnose.missing_count",
            "Missing %s: %s/%s",
            "缺少 %s：%s/%s");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_EXCESS_COUNT = LANG.key(
            "ui",
            "multiblock.diagnose.excess_count",
            "Excess %s: %s/%s",
            "多余 %s：%s/%s");
    public static final LangKey UI_MULTIBLOCK_STATS_BLOCKS = LANG.key(
            "ui",
            "multiblock.stats.blocks",
            "Blocks",
            "方块");
    public static final LangKey UI_MULTIBLOCK_STATS_PARTS = LANG.key(
            "ui",
            "multiblock.stats.parts",
            "Parts",
            "部件");
    public static final LangKey UI_MULTIBLOCK_STATS_CAPTURE = LANG.key(
            "ui",
            "multiblock.stats.capture",
            "Capture",
            "采集");
    public static final LangKey UI_MULTIBLOCK_STATS_SCAN = LANG.key(
            "ui",
            "multiblock.stats.scan",
            "Scan",
            "扫描");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_AS_ROLE = LANG.key(
            "ui",
            "multiblock.preview.as_role",
            "as %s",
            "作为 %s");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_LAYER = LANG.key(
            "ui",
            "multiblock.preview.layer",
            "Layer",
            "层");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ZOOM_IN = LANG.key(
            "ui",
            "multiblock.preview.zoom_in",
            "Zoom in",
            "放大");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ZOOM_OUT = LANG.key(
            "ui",
            "multiblock.preview.zoom_out",
            "Zoom out",
            "缩小");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ROTATE = LANG.key(
            "ui",
            "multiblock.preview.rotate",
            "Toggle rotation",
            "切换旋转");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_RESET = LANG.key(
            "ui",
            "multiblock.preview.reset",
            "Reset view",
            "重置视角");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_EXPAND = LANG.key("ui", "multiblock.preview.expand", "Expand preview", "放大预览");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ROTATABLE = LANG.key("ui", "multiblock.preview.rotatable", "Drag to rotate", "拖动旋转");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ALL_MODULES = LANG.key("ui", "multiblock.preview.all_modules", "Body + all modules", "本体与全部模块");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_BODY = LANG.key("ui", "multiblock.preview.body", "0: Body", "0：本体");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_REPEAT_MORE = LANG.key("ui", "multiblock.preview.repeat_more", "Increase repeat count", "增加重复次数");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_REPEAT_LESS = LANG.key("ui", "multiblock.preview.repeat_less", "Decrease repeat count", "减少重复次数");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_REPEAT = LANG.key("ui", "multiblock.preview.repeat", "%s / section %s: %s", "%s／重复段 %s：%s");
    public static final LangKey UI_MULTIBLOCK_SCENE_SWITCH_TO_REQUIRED = LANG.key(
            "ui",
            "multiblock.scene.switch_to_required",
            "Show required blocks",
            "显示所需方块");
    public static final LangKey UI_MULTIBLOCK_SCENE_SWITCH_TO_DETECTED = LANG.key(
            "ui",
            "multiblock.scene.switch_to_detected",
            "Show built blocks",
            "显示已建方块");
    public static final LangKey UI_SEARCH_POOL_ID_PLACEHOLDER = LANG.key(
            "ui",
            "search_pool.id_placeholder",
            "Pool id",
            "池 ID");
    public static final LangKey UI_SEARCH_POOL_ID_TOOLTIP = LANG.key(
            "ui",
            "search_pool.id.tooltip",
            "Search pool id; blank resets to default",
            "搜索池 ID；留空恢复默认");
    public static final LangKey UI_SEARCH_POOL_COPY_HINT = LANG.key(
            "ui",
            "search_pool.copy_hint",
            "Click to copy",
            "点击复制");
    public static final LangKey UI_SEARCH_POOL_RESET_TOOLTIP = LANG.key(
            "ui",
            "search_pool.reset.tooltip",
            "Reset to default",
            "恢复默认");
    public static final LangKey UI_SEARCH_POOL_UNIVERSAL_TOOLTIP = LANG.key(
            "ui",
            "search_pool.universal.tooltip",
            "Use universal pool",
            "使用通用池");
    public static final LangKey UI_SEARCH_POOL_PANEL = LANG.key(
            "ui",
            "search_pool.panel",
            "Search Pool",
            "搜索池");
    public static final LangKey UI_ITEM_PICKER_CANCEL = LANG.key(
            "ui",
            "item_picker.cancel",
            "Cancel",
            "取消");
    public static final LangKey UI_ITEM_PICKER_CONFIRM = LANG.key(
            "ui",
            "item_picker.confirm",
            "Confirm",
            "确认");
    public static final LangKey UI_ITEM_PICKER_HINT = LANG.key(
            "ui",
            "item_picker.hint",
            "Click an item to stage it",
            "点击物品加入暂存");
    public static final LangKey UI_ITEM_PICKER_STAGED_TOOLTIP = LANG.key(
            "ui",
            "item_picker.staged_tooltip",
            "Click to clear",
            "点击清除");
    public static final LangKey UI_AMOUNT_EDITOR_CLICK_TO_EDIT = LANG.key(
            "ui",
            "amount_editor.click_to_edit",
            "Click to edit",
            "点击编辑");
    public static final LangKey UI_RESOURCE_AMOUNT_CAPACITY = LANG.key(
            "ui",
            "resource.amount_capacity",
            "%s / %s",
            "%s / %s");
    public static final LangKey UI_RESOURCE_CONSUMED = LANG.key(
            "ui",
            "resource.consumed",
            "Consumed",
            "消耗");
    public static final LangKey UI_RESOURCE_PRODUCED = LANG.key(
            "ui",
            "resource.produced",
            "Produced",
            "产出");
    public static final LangKey UI_RESOURCE_RATE = LANG.key(
            "ui",
            "resource.rate",
            "Rate",
            "速率");
    public static final LangKey UI_RESOURCE_AMOUNT = LANG.key(
            "ui",
            "resource.amount",
            "Amount",
            "总量");

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
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_TRUNCATED = LANG.key(
            "equipment",
            "pipe_surveyor.truncated",
            "Incomplete display: over-limit nodes or paths omitted",
            "展示不完整：已省略超限节点或路径");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_CAPACITY = LANG.key(
            "equipment",
            "pipe_surveyor.capacity",
            "Analyzed capacity: %s/t; paths shown: %s",
            "分析容量：%s/t；已显示路径：%s");
    public static final LangKey EQUIPMENT_PIPE_SURVEYOR_NEED_ANCHOR = LANG.key(
            "equipment",
            "pipe_surveyor.need_anchor",
            "Anchor a pipe network first",
            "请先锚定一个管道网络");

    private TopoApiLang() {}

    public static void init() {}

    /** Display handle for a world direction (pipe port UI / overlays). */
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

    /** Side-IO card title for a port's recipe role (Input / Output / Storage). */
    public static LangKey sideIoTitle(RecipeRole role) {
        return switch (role) {
            case INPUT -> UI_SIDE_IO_TITLE_INSERT;
            case OUTPUT -> UI_SIDE_IO_TITLE_EXTRACT;
            case BOTH, NONE -> UI_SIDE_IO_TITLE_BOTH;
        };
    }

    /** Side-IO face cell tooltip mode label. */
    public static LangKey sideIoMode(AutomationIo mode) {
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
