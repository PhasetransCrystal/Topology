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
}
