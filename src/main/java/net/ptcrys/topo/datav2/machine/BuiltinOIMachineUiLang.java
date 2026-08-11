package net.ptcrys.topo.datav2.machine;

import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.api.lang.OIApiLang;
import net.ptcrys.topo.apiv2.machine.resource.AutomationIo;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;

/**
 * Registers machine UI translation keys for trait UI code ({@code RecipeUi}, {@code MultiblockUi}).
 * {@link #init()} forces field initializers before datagen. Recipe-state and multiblock fold keys
 * used by API runtime are owned by {@link OIApiLang} and re-exported here.
 *
 * <p>
 * Each entry is a {@link LangKey}; en/cn payloads feed the datagen bridge for en_us / zh_cn /
 * zh_tw.
 */
public final class BuiltinOIMachineUiLang {

    private static final LangDomainRegistration LANG = OfficialOIPlugin.INSTANCE.lang();

    // Recipe machine UI (shared by single-block and multiblock recipe machines).
    public static final LangKey UI_RECIPE_PAGE = LANG.key("ui", "recipe.page", "Recipe", "配方");
    public static final LangKey UI_RECIPE_STATUS = LANG.key("ui", "recipe.status", "Status", "状态");
    public static final LangKey UI_RECIPE_STATE = LANG.key("ui", "recipe.state", "State", "状态");
    // Recipe state machine display (API-owned; re-exported for data UI). Halted is a workMode
    // overlay shown instead of the held state while a regulator pauses work.
    public static final LangKey UI_RECIPE_STATE_IDLE = OIApiLang.UI_RECIPE_STATE_IDLE;
    public static final LangKey UI_RECIPE_STATE_WORKING = OIApiLang.UI_RECIPE_STATE_WORKING;
    public static final LangKey UI_RECIPE_STATE_WAITING_OUTPUT = OIApiLang.UI_RECIPE_STATE_WAITING_OUTPUT;
    public static final LangKey UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_PROCESS = OIApiLang.UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_PROCESS;
    public static final LangKey UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_START = OIApiLang.UI_RECIPE_STATE_WAITING_TICK_INPUT_TO_START;
    public static final LangKey UI_RECIPE_STATE_WAITING_TICK_OUTPUT_TO_START = OIApiLang.UI_RECIPE_STATE_WAITING_TICK_OUTPUT_TO_START;
    public static final LangKey UI_RECIPE_STATE_MISSING_ACTIVE_RECIPE = OIApiLang.UI_RECIPE_STATE_MISSING_ACTIVE_RECIPE;
    public static final LangKey UI_RECIPE_STATE_HALTED = LANG.key("ui", "recipe.state.halted", "Halted", "已停止");
    public static final LangKey UI_RECIPE_PROGRESS = LANG.key("ui", "recipe.progress", "Progress", "进度");
    public static final LangKey UI_RECIPE_DURATION = LANG.key("ui", "recipe.duration", "Duration", "耗时");
    public static final LangKey UI_RECIPE_TIME = LANG.key("ui", "recipe.time", "Time", "时间");
    // 进度条悬浮提示,仅查看器(JEI)就绪时显示。
    public static final LangKey UI_RECIPE_SHOW_RECIPES = LANG.key("ui", "recipe.show_recipes", "Show recipes", "查看配方");
    // Recipe modifier list (RecipeUi right-column card; omitted when the machine has none).
    // Each modifier supplies title + description + its own details UI (typically an LcdData);
    // list stacks title+body; machine item tooltips show title|description.
    public static final LangKey UI_RECIPE_MODIFIERS = LANG.key("ui", "recipe.modifiers", "Recipe Modifiers", "配方修正");
    // PerformanceRecipeModifiers nested types (arg = tier level).
    public static final LangKey UI_RECIPE_MODIFIER_CONSUMER_ENERGY_NAME = LANG.key("ui", "recipe.modifier.consumer.energy.name", "Tier %s Energy Use", "Tier %s 耗电");
    public static final LangKey UI_RECIPE_MODIFIER_CONSUMER_HEAT_NAME = LANG.key("ui", "recipe.modifier.consumer.heat.name", "Tier %s Heat Use", "Tier %s 耗热");
    public static final LangKey UI_RECIPE_MODIFIER_CONSUMER_ADV_ENERGY_NAME = LANG.key("ui", "recipe.modifier.consumer.adv_energy.name", "Tier %s Adv. Energy Use", "Tier %s 耗高能");
    public static final LangKey UI_RECIPE_MODIFIER_PRODUCER_ENERGY_NAME = LANG.key("ui", "recipe.modifier.producer.energy.name", "Tier %s Generator Effects", "Tier %s 发电效果");
    public static final LangKey UI_RECIPE_MODIFIER_PRODUCER_HEAT_NAME = LANG.key("ui", "recipe.modifier.producer.heat.name", "Tier %s Boiler Effects", "Tier %s 锅炉效果");
    public static final LangKey UI_RECIPE_MODIFIER_CONVERTER_NAME = LANG.key("ui", "recipe.modifier.converter.name", "Tier %s Converter Effects", "Tier %s 转换效果");
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_NAME = OIApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_NAME;
    // Multiblock fold has no numeric factors — short one-liner for tooltip/UI metadata.
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_DESC = OIApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_DESC;
    // Machine item tooltip panel (title | description rows for mounted modifiers).
    public static final LangKey TOOLTIP_MACHINE_RECIPE_MODIFIERS = LANG.key("tooltip", "machine.recipe_modifiers", "Recipe Modifiers", "配方修正");
    public static final LangKey TOOLTIP_MACHINE_MODIFIER_COUNT = LANG.key("tooltip", "machine.modifier_count", "%s", "%s");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_DURATION = LANG.key("ui", "recipe.modifier.attr.duration", "Duration", "耗时");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_POWER = LANG.key("ui", "recipe.modifier.attr.power", "Power", "功率");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_GENERATION = LANG.key("ui", "recipe.modifier.attr.generation", "Generation / t", "发电 / t");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_HEAT_OUT = LANG.key("ui", "recipe.modifier.attr.heat_out", "Heat / t", "产热 / t");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_YIELD = LANG.key("ui", "recipe.modifier.attr.yield", "Yield", "产率");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_AFFECTS = LANG.key("ui", "recipe.modifier.attr.affects", "Affects", "作用");
    public static final LangKey UI_RECIPE_MODIFIER_SCALAR_ENERGY = LANG.key("ui", "recipe.modifier.scalar.energy", "Energy", "能量");
    public static final LangKey UI_RECIPE_MODIFIER_SCALAR_HEAT = LANG.key("ui", "recipe.modifier.scalar.heat", "Heat", "热量");
    public static final LangKey UI_RECIPE_MODIFIER_SCALAR_ADVANCED_ENERGY = LANG.key("ui", "recipe.modifier.scalar.advanced_energy", "Adv. Energy", "高能");
    // Derived (dependent) — orange LCD rows after free parameters.
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_SPEED = LANG.key("ui", "recipe.modifier.attr.speed", "Speed", "速度");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_ENERGY = LANG.key("ui", "recipe.modifier.attr.total_energy", "Energy / craft", "单次耗电");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_HEAT_COST = LANG.key("ui", "recipe.modifier.attr.total_heat_cost", "Heat / craft", "单次耗热");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_ADV_ENERGY = LANG.key("ui", "recipe.modifier.attr.total_adv_energy", "Adv. energy / craft", "单次高能耗");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_SCALAR = LANG.key("ui", "recipe.modifier.attr.total_scalar", "Scalar / craft", "单次标量");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_GENERATION = LANG.key("ui", "recipe.modifier.attr.total_generation", "Gen. / craft", "单次发电");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_HEAT = LANG.key("ui", "recipe.modifier.attr.total_heat", "Heat out / craft", "单次产热");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_INPUT = LANG.key("ui", "recipe.modifier.attr.total_input", "Input / craft", "单次输入");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_TOTAL_OUTPUT = LANG.key("ui", "recipe.modifier.attr.total_output", "Output / craft", "单次输出");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_OUTPUT_RATE = LANG.key("ui", "recipe.modifier.attr.output_rate", "Output / time", "输出速率");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_SCOPE = OIApiLang.UI_RECIPE_MODIFIER_ATTR_SCOPE;
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_EFFECT = OIApiLang.UI_RECIPE_MODIFIER_ATTR_EFFECT;
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_PARALLEL = LANG.key("ui", "recipe.modifier.attr.parallel", "Parallelism", "并行度");
    public static final LangKey UI_RECIPE_MODIFIER_PARALLEL_TITLE = LANG.key("ui", "recipe.modifier.parallel.title", "Parallel Processing", "并行处理");
    public static final LangKey UI_RECIPE_MODIFIER_PARALLEL_EFFECT = LANG.key("ui", "recipe.modifier.parallel.effect", "Actual 1..N from inputs; I/O × actual; duration unchanged", "按输入实算 1..N；I/O×实际并行；耗时不变");
    public static final LangKey UI_RECIPE_MODIFIER_ATTR_THROUGHPUT = LANG.key("ui", "recipe.modifier.attr.throughput", "Throughput", "吞吐");
    public static final LangKey UI_RECIPE_MODIFIER_PARALLEL_THROUGHPUT = LANG.key("ui", "recipe.modifier.parallel.throughput", "Materials/s scale with actual parallel (up to ×%s)", "材料/秒随实际并行变化（最高 ×%s）");
    public static final LangKey UI_RECIPE_MODIFIER_FACTOR = LANG.key("ui", "recipe.modifier.factor", "×%s", "×%s");
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_SCOPE = OIApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_SCOPE;
    public static final LangKey UI_RECIPE_MODIFIER_MULTIBLOCK_EFFECT = OIApiLang.UI_RECIPE_MODIFIER_MULTIBLOCK_EFFECT;
    // Temporary macerator multi-modifier preview entries (remove with PreviewRecipeModifierComponent).
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_PARALLEL_TITLE = LANG.key("ui", "recipe.modifier.preview.parallel.title", "Parallel Hatch", "并行仓效果");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_OVERCLOCK_TITLE = LANG.key("ui", "recipe.modifier.preview.overclock.title", "Overclocking", "超频效果");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_CATALYST_TITLE = LANG.key("ui", "recipe.modifier.preview.catalyst.title", "Catalyst Bonus", "催化剂加成");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_ATTR_PARALLEL = LANG.key("ui", "recipe.modifier.preview.attr.parallel", "Parallelism", "并行度");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_ATTR_MODE = LANG.key("ui", "recipe.modifier.preview.attr.mode", "Mode", "模式");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_ATTR_OC = LANG.key("ui", "recipe.modifier.preview.attr.oc", "OC", "超频");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_ATTR_BONUS = LANG.key("ui", "recipe.modifier.preview.attr.bonus", "Bonus", "加成");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_PARALLEL_MODE = LANG.key("ui", "recipe.modifier.preview.parallel.mode", "Batch", "批量");
    public static final LangKey UI_RECIPE_MODIFIER_PREVIEW_OVERCLOCK_MODE = LANG.key("ui", "recipe.modifier.preview.overclock.mode", "Perfect", "完美");

    // 创造能源发电机:可点击的每 tick 发电量编辑器(复用数量编辑弹窗)。
    public static final LangKey UI_CREATIVE_GENERATOR_RATE = LANG.key("ui", "creative_generator.rate", "Generation / t", "发电量 / t");
    public static final LangKey UI_CREATIVE_GENERATOR_RATE_POPUP_TITLE = LANG.key("ui", "creative_generator.rate_popup_title", "Set generation per tick", "设置每 tick 发电量");
    // 通用数量编辑器(机器 UI 可点击数值的悬浮提示)。
    public static final LangKey UI_AMOUNT_EDITOR_CLICK_TO_EDIT = LANG.key("ui", "amount_editor.click_to_edit", "Click to edit", "点击以编辑");

    // Multiblock overview / diagnostics UI.
    public static final LangKey UI_MULTIBLOCK_STRUCTURE = LANG.key("ui", "multiblock.structure", "Structure", "结构");
    public static final LangKey UI_MULTIBLOCK_FORMED = LANG.key("ui", "multiblock.formed", "Formed", "已成型");
    public static final LangKey UI_MULTIBLOCK_MATCHED = LANG.key("ui", "multiblock.matched", "Matched", "已匹配");
    public static final LangKey UI_MULTIBLOCK_MISSING = LANG.key("ui", "multiblock.missing", "Missing", "缺失");
    public static final LangKey UI_MULTIBLOCK_NEED = LANG.key("ui", "multiblock.need", "Need", "需要");
    public static final LangKey UI_MULTIBLOCK_YES = LANG.key("ui", "multiblock.yes", "Yes", "是");
    public static final LangKey UI_MULTIBLOCK_NO = LANG.key("ui", "multiblock.no", "No", "否");
    public static final LangKey UI_MULTIBLOCK_NONE = LANG.key("ui", "multiblock.none", "—", "—");
    public static final LangKey UI_MULTIBLOCK_UNFORMED_NOTICE = LANG.key("ui", "multiblock.unformed_notice", "Structure incomplete — assemble the multiblock to use this machine.", "结构不完整 — 组装好多方块才能使用此机器。");

    // Generic storage page (hatches, drawing caches, pattern buffers).
    public static final LangKey UI_STORAGE_PAGE = LANG.key("ui", "storage.page", "Storage", "存储");

    // Shared resource slot/bar widget labels. Resource family names live with their integrations.
    // amount/rate 是配方标量悬浮 LCD 面板(ResourceBar.recipeContentTooltipPanel)的键列裸标签,
    // 数值在值列单独渲染;amount_capacity 仍是实况条悬浮的整句文本。
    public static final LangKey UI_RESOURCE_AMOUNT = LANG.key("ui", "resource.amount", "Amount", "数量");
    public static final LangKey UI_RESOURCE_AMOUNT_CAPACITY = LANG.key("ui", "resource.amount_capacity", "Amount: %s / %s", "数量：%s / %s");
    public static final LangKey UI_RESOURCE_CONSUMED = LANG.key("ui", "resource.consumed", "Consumed", "消耗");
    public static final LangKey UI_RESOURCE_PRODUCED = LANG.key("ui", "resource.produced", "Produced", "产出");
    public static final LangKey UI_RESOURCE_RATE = LANG.key("ui", "resource.rate", "Rate", "速率");

    // Runtime side-IO config card (per configurable resource port, LEFT column). Titles are
    // role-only and short; the resource is identified by its family icon inside the grid.
    public static final LangKey UI_SIDE_IO_TITLE_INSERT = LANG.key("ui", "side_io.title.insert", "Input", "输入");
    public static final LangKey UI_SIDE_IO_TITLE_EXTRACT = LANG.key("ui", "side_io.title.extract", "Output", "输出");
    public static final LangKey UI_SIDE_IO_TITLE_BOTH = LANG.key("ui", "side_io.title.both", "Storage", "存储");
    public static final LangKey UI_SIDE_IO_TOOLTIP = LANG.key("ui", "side_io.tooltip", "%s: %s", "%s: %s");
    public static final LangKey UI_SIDE_IO_RESET = LANG.key("ui", "side_io.reset", "Reset sides to default", "重置各面为默认");
    public static final LangKey UI_SIDE_IO_DISABLE = LANG.key("ui", "side_io.disable", "Close all sides", "关闭所有面");
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

    // ME hatch pages and the ghost-slot amount editor popup.
    public static final LangKey UI_AE_CONFIG_PAGE = LANG.key("ui", "ae_config.page", "ME Config", "ME 配置");
    public static final LangKey UI_AE_PATTERNS_PAGE = LANG.key("ui", "ae_patterns.page", "ME Patterns", "ME 样板");
    public static final LangKey UI_AE_BUFFER_PAGE = LANG.key("ui", "ae_buffer.page", "ME Push Buffer", "ME 推送缓冲");
    public static final LangKey UI_AE_AMOUNT_POPUP_TITLE = LANG.key("ui", "ae_amount_popup.title", "Target amount", "目标数量");
    // 通用数量编辑弹窗(AmountEditorPopup):标题由调用方传入,确认/取消是组件公共键。
    public static final LangKey UI_AMOUNT_POPUP_CONFIRM = LANG.key("ui", "amount_popup.confirm", "Confirm", "确认");
    public static final LangKey UI_AMOUNT_POPUP_CANCEL = LANG.key("ui", "amount_popup.cancel", "Cancel", "取消");
    // 通用物品选择弹窗(ItemPickerPopup):标题由调用方传入,提示/确认/取消是组件公共键。
    public static final LangKey UI_ITEM_PICKER_HINT = LANG.key("ui", "item_picker.hint", "Drag from JEI or click your inventory", "从 JEI 拖入或点击你的物品栏");
    public static final LangKey UI_ITEM_PICKER_STAGED_TOOLTIP = LANG.key("ui", "item_picker.staged_tooltip", "Click to remove from the staging tray", "点击以从暂存盘移除");
    public static final LangKey UI_ITEM_PICKER_CONFIRM = LANG.key("ui", "item_picker.confirm", "Add", "添加");
    public static final LangKey UI_ITEM_PICKER_CANCEL = LANG.key("ui", "item_picker.cancel", "Cancel", "取消");
    public static final LangKey UI_AE_PATTERN_PROVIDER_NAME = LANG.key("ui", "ae_pattern_provider.name", "Name", "名称");
    public static final LangKey UI_AE_PATTERN_PROVIDER_NAME_PLACEHOLDER = LANG.key("ui", "ae_pattern_provider.name_placeholder", "Defaults to block name", "默认使用方块名");
    public static final LangKey UI_AE_PATTERN_PROVIDER_MODES = LANG.key("ui", "ae_pattern_provider.modes", "Modes", "模式");
    public static final LangKey UI_AE_PATTERN_PROVIDER_BLOCKING = LANG.key("ui", "ae_pattern_provider.blocking", "Blocking", "阻挡模式");
    public static final LangKey UI_AE_PATTERN_PROVIDER_BLOCKING_TOOLTIP = LANG.key("ui", "ae_pattern_provider.blocking.tooltip", "When on, do not accept a new pattern push while the target buffer still has content.", "开启后，目标缓冲非空时不再接受新的样板推送。");
    public static final LangKey UI_AE_PATTERN_PROVIDER_SEPARATED = LANG.key("ui", "ae_pattern_provider.separated", "Separate pools", "分离搜索池");
    public static final LangKey UI_AE_PATTERN_PROVIDER_SEPARATED_TOOLTIP = LANG.key("ui", "ae_pattern_provider.separated.tooltip", "When on, each pattern uses its own input buffer and recipe search pool. Blocking is per slot.", "开启后，每张样板使用独立输入缓冲与配方搜索池；阻挡按槽计算。");
    public static final LangKey UI_AE_PATTERN_PROVIDER_SLOT_POOL = LANG.key("ui", "ae_pattern_provider.slot_pool", "Search pool: %s", "搜索池：%s");
    public static final LangKey UI_SEARCH_POOL_PANEL = LANG.key("ui", "search_pool.panel", "Search Pool", "搜索池");
    public static final LangKey UI_SEARCH_POOL_ID_PLACEHOLDER = LANG.key("ui", "search_pool.id_placeholder", "DEFAULT", "DEFAULT");
    public static final LangKey UI_SEARCH_POOL_ID_TOOLTIP = LANG.key("ui", "search_pool.id.tooltip", "Pool id for isolatable ports. DEFAULT = one private line; UNIVERSAL = shared by every pool (public inputs / outputs all lines can use); or six [0-9a-z]. Recipes emit into the same pool they searched. Green = valid, red = invalid.", "可隔离端口的搜索池编号。DEFAULT=单独一条线；UNIVERSAL=所有编号池的公共仓（输入供各池共用，输出可接各池产物）；或 6 位 0-9a-z。配方从哪池搜到就向哪池输出。绿边=有效，红边=无效。");
    public static final LangKey UI_SEARCH_POOL_RESET_TOOLTIP = LANG.key("ui", "search_pool.reset.tooltip", "Reset to this hatch's default (DEFAULT for input, UNIVERSAL for output-only)", "重置为本仓默认（输入仓 DEFAULT，纯输出仓 UNIVERSAL）");
    public static final LangKey UI_SEARCH_POOL_UNIVERSAL_TOOLTIP = LANG.key("ui", "search_pool.universal.tooltip", "Set UNIVERSAL: shared across every pool (public input for all lines / accept output from all lines)", "设为 UNIVERSAL：作为所有编号池的公共仓（公共输入各池都能用；公共输出可接各池产物）");
    public static final LangKey UI_SEARCH_POOL_COPY_HINT = LANG.key("ui", "search_pool.copy_hint", "Middle-click to copy", "中键点击复制");

    // JEI multiblock structure preview category.
    public static final LangKey JEI_MULTIBLOCK_INFO = LANG.key("jei", "multiblock_info", "Multiblock Structure", "多方块结构");

    // JEI → AE2 pattern transfer ("+" button on recipe pages in the pattern encoding terminal).
    public static final LangKey UI_JEI_TRANSFER_NO_ENCODABLE_IO = LANG.key("ui", "jei.transfer.no_encodable_io", "No item or fluid I/O in this recipe to encode.", "此配方没有可编码的物品或流体输入 / 输出。");

    // Multiblock pattern builder popup (JEI structure page → AE2 pattern encoding terminal).
    public static final LangKey UI_PATTERN_BUILDER_FIXED_BLOCKS = LANG.key("ui", "pattern_builder.fixed_blocks", "Fixed Blocks", "固定方块");
    public static final LangKey UI_PATTERN_BUILDER_TOGGLE_FIXED = LANG.key("ui", "pattern_builder.toggle_fixed", "Include in pattern", "纳入样板");
    public static final LangKey UI_PATTERN_BUILDER_ADD = LANG.key("ui", "pattern_builder.add", "Add one", "增加一个");
    public static final LangKey UI_PATTERN_BUILDER_REMOVE = LANG.key("ui", "pattern_builder.remove", "Remove one", "移除一个");
    public static final LangKey UI_PATTERN_BUILDER_INPUTS_SHORT = LANG.key("ui", "pattern_builder.inputs_short", "Pattern Inputs", "样板输入");
    public static final LangKey UI_PATTERN_BUILDER_WRITE = LANG.key("ui", "pattern_builder.write", "Write", "写入");
    public static final LangKey UI_PATTERN_BUILDER_CANCEL = LANG.key("ui", "pattern_builder.cancel", "Cancel", "取消");

    // Jade config toggle for the machine overlay providers (UID topo:machine).
    // Jade asserts this key exists in dev; without it the client crashes on the first screen.
    public static final LangKey CONFIG_JADE_PLUGIN_ODYSSEYINDUSTRIAL_MACHINE = LANG.absolute("config.jade.plugin_topo.machine", "Machine", "机器");

    // Multiblock preview scene toolbar + BOM dock (JEI category and the structure page).
    public static final LangKey UI_MULTIBLOCK_PREVIEW_LAYER = LANG.key("ui", "multiblock.preview.layer", "Layer view (click to slice)", "分层视图（点击以切片）");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ZOOM_IN = LANG.key("ui", "multiblock.preview.zoom_in", "Zoom in", "放大");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ZOOM_OUT = LANG.key("ui", "multiblock.preview.zoom_out", "Zoom out", "缩小");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ROTATE = LANG.key("ui", "multiblock.preview.rotate", "Toggle auto-rotation", "切换自动旋转");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_RESET = LANG.key("ui", "multiblock.preview.reset", "Reset view", "重置视图");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_EXPAND = LANG.key("ui", "multiblock.preview.expand", "Open large preview", "打开大图预览");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_ROTATABLE = LANG.key("ui", "multiblock.preview.rotatable", "Rotatable", "可旋转");
    public static final LangKey UI_MULTIBLOCK_PREVIEW_AS_ROLE = LANG.key("ui", "multiblock.preview.as_role", "as %s", "作为 %s");

    // Structure page scene mode toggle + live diagnosis list + cell detail dock. The empty/
    // wrong_block/wrong_property strings double as the cell header's verdict tag — keep them
    // short, high-level (no blockstate vocabulary), and tag-shaped.
    public static final LangKey UI_MULTIBLOCK_SCENE_SWITCH_TO_REQUIRED = LANG.key("ui", "multiblock.scene.switch_to_required", "Switch to required", "切换到要求");
    public static final LangKey UI_MULTIBLOCK_SCENE_SWITCH_TO_DETECTED = LANG.key("ui", "multiblock.scene.switch_to_detected", "Switch to as-built", "切换到实建");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_LOCATE = LANG.key("ui", "multiblock.diagnose.locate", "Show in preview", "在预览中显示");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_ALL_MET = LANG.key("ui", "multiblock.diagnose.all_met", "All requirements met", "已满足所有要求");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_MORE = LANG.key("ui", "multiblock.diagnose.more", "…and %s more", "…还有 %s 项");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_EMPTY = LANG.key("ui", "multiblock.diagnose.empty", "Empty cell", "空格");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_WRONG_BLOCK = LANG.key("ui", "multiblock.diagnose.wrong_block", "Wrong block", "方块错误");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_WRONG_PROPERTY = LANG.key("ui", "multiblock.diagnose.wrong_property", "State mismatch", "状态不符");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_CELL_OK = LANG.key("ui", "multiblock.diagnose.cell_ok", "OK", "正常");

    // All-met stats LCD (replaces the problem list when the structure is complete).
    public static final LangKey UI_MULTIBLOCK_STATS_BLOCKS = LANG.key("ui", "multiblock.stats.blocks", "Blocks", "方块");
    public static final LangKey UI_MULTIBLOCK_STATS_PARTS = LANG.key("ui", "multiblock.stats.parts", "Parts", "部件");
    public static final LangKey UI_MULTIBLOCK_STATS_CAPTURE = LANG.key("ui", "multiblock.stats.capture", "Capture", "捕获");
    public static final LangKey UI_MULTIBLOCK_STATS_SCAN = LANG.key("ui", "multiblock.stats.scan", "Scan", "扫描");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_MISSING_PART = LANG.key("ui", "multiblock.diagnose.missing_part", "Missing part: %s (have %s, need ≥ %s)", "缺少部件：%s（现有 %s，需要 ≥ %s）");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_EXCESS_PART = LANG.key("ui", "multiblock.diagnose.excess_part", "Too many parts: %s (have %s, max %s)", "部件过多：%s（现有 %s，上限 %s）");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_NEED = LANG.key("ui", "multiblock.diagnose.need", "Need %s @ %s", "需要 %s @ %s");
    public static final LangKey UI_MULTIBLOCK_DIAGNOSE_NEED_FOUND = LANG.key("ui", "multiblock.diagnose.need_found", "Need %s — found %s @ %s", "需要 %s — 找到 %s @ %s");

    // Property-display aspect texts (BuiltinOIPropertyDisplays declares the same keys with
    // inline English fallbacks; these entries bake them into the generated en_us lang).
    public static final LangKey UI_MULTIBLOCK_STATE_FACING = LANG.key("ui", "multiblock.state.facing", "Facing", "朝向");
    public static final LangKey UI_MULTIBLOCK_STATE_FACING_NORTH = LANG.key("ui", "multiblock.state.facing.north", "North", "北");
    public static final LangKey UI_MULTIBLOCK_STATE_FACING_SOUTH = LANG.key("ui", "multiblock.state.facing.south", "South", "南");
    public static final LangKey UI_MULTIBLOCK_STATE_FACING_EAST = LANG.key("ui", "multiblock.state.facing.east", "East", "东");
    public static final LangKey UI_MULTIBLOCK_STATE_FACING_WEST = LANG.key("ui", "multiblock.state.facing.west", "West", "西");
    public static final LangKey UI_MULTIBLOCK_STATE_FACING_UP = LANG.key("ui", "multiblock.state.facing.up", "Up", "上");
    public static final LangKey UI_MULTIBLOCK_STATE_FACING_DOWN = LANG.key("ui", "multiblock.state.facing.down", "Down", "下");
    public static final LangKey UI_MULTIBLOCK_STATE_HALF = LANG.key("ui", "multiblock.state.half", "Half", "半砖");
    public static final LangKey UI_MULTIBLOCK_STATE_HALF_TOP = LANG.key("ui", "multiblock.state.half.top", "Top", "上");
    public static final LangKey UI_MULTIBLOCK_STATE_HALF_BOTTOM = LANG.key("ui", "multiblock.state.half.bottom", "Bottom", "下");
    public static final LangKey UI_MULTIBLOCK_STATE_STAIR_SHAPE = LANG.key("ui", "multiblock.state.stair_shape", "Shape", "形状");
    public static final LangKey UI_MULTIBLOCK_STATE_STAIR_SHAPE_STRAIGHT = LANG.key("ui", "multiblock.state.stair_shape.straight", "Straight", "直");
    public static final LangKey UI_MULTIBLOCK_STATE_STAIR_SHAPE_INNER_LEFT = LANG.key("ui", "multiblock.state.stair_shape.inner_left", "Inner left", "内左");
    public static final LangKey UI_MULTIBLOCK_STATE_STAIR_SHAPE_INNER_RIGHT = LANG.key("ui", "multiblock.state.stair_shape.inner_right", "Inner right", "内右");
    public static final LangKey UI_MULTIBLOCK_STATE_STAIR_SHAPE_OUTER_LEFT = LANG.key("ui", "multiblock.state.stair_shape.outer_left", "Outer left", "外左");
    public static final LangKey UI_MULTIBLOCK_STATE_STAIR_SHAPE_OUTER_RIGHT = LANG.key("ui", "multiblock.state.stair_shape.outer_right", "Outer right", "外右");
    public static final LangKey UI_MULTIBLOCK_STATE_AXIS = LANG.key("ui", "multiblock.state.axis", "Axis", "轴");
    public static final LangKey UI_MULTIBLOCK_STATE_AXIS_X = LANG.key("ui", "multiblock.state.axis.x", "X", "X");
    public static final LangKey UI_MULTIBLOCK_STATE_AXIS_Y = LANG.key("ui", "multiblock.state.axis.y", "Y", "Y");
    public static final LangKey UI_MULTIBLOCK_STATE_AXIS_Z = LANG.key("ui", "multiblock.state.axis.z", "Z", "Z");

    private BuiltinOIMachineUiLang() {}

    /** Triggers class initialization so the keys are registered before datagen collects lang. */
    public static void init() {}

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
