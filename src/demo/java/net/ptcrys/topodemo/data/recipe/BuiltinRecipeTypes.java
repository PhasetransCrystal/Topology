package net.ptcrys.topodemo.data.recipe;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.special.CombustionGeneratorRecipeType;

public final class BuiltinRecipeTypes {

    private static final RecipeDomainRegistration RECIPE = TopoDemoPlugin.INSTANCE.recipe();
    public static final TopoRecipeType<TopoRecipe> BOILER = RECIPE.recipeType("boiler")
            .displayName("Boiler", "锅炉");
    public static final TopoRecipeType<TopoRecipe> COMBUSTION_GENERATOR = RECIPE.recipeType("combustion_generator", new CombustionGeneratorRecipeType(RECIPE.id("combustion_generator")))
            .displayName("Combustion Generator", "燃烧发电");
    public static final TopoRecipeType<TopoRecipe> AIR_COLLECTOR = RECIPE.recipeType("air_collector")
            .displayName("Air Collector", "集气");
    public static final TopoRecipeType<TopoRecipe> ENERGY_COMPRESSOR = RECIPE.recipeType("energy_compressor")
            .displayName("Energy Compressor", "能量压缩");
    public static final TopoRecipeType<TopoRecipe> INDUSTRIAL_COMBUSTION_FURNACE = RECIPE.recipeType("industrial_combustion_furnace")
            .displayName("Industrial Combustion Furnace", "工业燃烧炉");
    public static final TopoRecipeType<TopoRecipe> CHEMICAL_REACTOR = RECIPE.recipeType("chemical_reactor")
            .displayName("Chemical Reactor", "化学反应");
    public static final TopoRecipeType<TopoRecipe> MIXING_TANK = RECIPE.recipeType("mixing_tank")
            .displayName("Mixing Tank", "混合");
    public static final TopoRecipeType<TopoRecipe> ELECTROLYZER = RECIPE.recipeType("electrolyzer")
            .displayName("Electrolyzer", "电解");
    public static final TopoRecipeType<TopoRecipe> EVAPORATOR = RECIPE.recipeType("evaporator")
            .displayName("Evaporator", "蒸发");
    public static final TopoRecipeType<TopoRecipe> FINE_GRINDER = RECIPE.recipeType("fine_grinder")
            .displayName("Fine Grinding", "精细研磨");
    public static final TopoRecipeType<TopoRecipe> HIGH_PRESSURE_CRYSTALLIZER = RECIPE.recipeType("high_pressure_crystallizer")
            .displayName("High-Pressure Crystallizer", "高压晶化");
    public static final TopoRecipeType<TopoRecipe> ELECTRIC_FURNACE = RECIPE.recipeType("electric_furnace")
            .displayName("Electric Smelting", "电力熔炼");
    public static final TopoRecipeType<TopoRecipe> GRINDING = RECIPE.recipeType("grinding").displayName("Grinding", "粉碎");
    public static final TopoRecipeType<TopoRecipe> ALLOYING = RECIPE.recipeType("alloying").displayName("Alloying", "合金混合");
    public static final TopoRecipeType<TopoRecipe> RESISTIVE_HEATER = RECIPE.recipeType("resistive_heater")
            .displayName("Resistive Heater", "电阻加热");
    public static final ProductionLine METALLURGY = RECIPE.productionLine("metallurgy");

    private BuiltinRecipeTypes() {}

    public static void init() {}
}
