package net.ptcrys.topo.data.recipe;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.VanillaFacingPaths;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.recipe.craft.*;
import net.ptcrys.topo.data.recipe.special.BoilerRecipeType;
import net.ptcrys.topo.data.recipe.special.CombustionGeneratorRecipeType;

import net.minecraft.world.item.crafting.RecipeType;

import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;

import java.util.Objects;

/**
 * Builtin recipe types — one table for machines and vanilla-facing craft/cook.
 *
 * <p>
 * Cooking SoT: declare on {@link #SMELTING}/{@link #BLASTING}; machines pull the foreign table
 * via {@link CookingTopoRecipeType#poweredImport} (no separate adapter class).
 *
 * <pre>{@code
 * BuiltinTopoRecipeTypes.SMELTING.recipe("smelting/…", ingot, dust).save();
 * // ELECTRIC_FURNACE imports RecipeType.SMELTING (ours + vanilla + other mods) as powered recipes
 * }</pre>
 */
public final class BuiltinTopoRecipeTypes {

    private static final RecipeDomainRegistration RECIPE = OfficialTopoPlugin.INSTANCE.recipe();

    // ── Vanilla-facing cooking first (machines below may import from these) ──

    public static final CookingTopoRecipeType SMELTING = registerCooking(
            "topo_smelting",
            CookingRecipeBuilder.Kind.SMELTING,
            VanillaFacingPaths.SMELTING,
            "Smelting",
            "熔炉熔炼");
    public static final CookingTopoRecipeType BLASTING = registerCooking(
            "topo_blasting",
            CookingRecipeBuilder.Kind.BLASTING,
            VanillaFacingPaths.BLASTING,
            "Blasting Table",
            "高炉熔炼表");
    public static final CookingTopoRecipeType SMOKING = registerCooking(
            "topo_smoking",
            CookingRecipeBuilder.Kind.SMOKING,
            VanillaFacingPaths.SMOKING,
            "Smoking",
            "烟熏");
    public static final CookingTopoRecipeType CAMPFIRE = registerCooking(
            "topo_campfire",
            CookingRecipeBuilder.Kind.CAMPFIRE,
            VanillaFacingPaths.CAMPFIRE_COOKING,
            "Campfire Cooking",
            "营火烹饪");

    public static final ShapedCraftingRecipeType CRAFTING_SHAPED = registerShaped(
            "crafting_shaped", "Shaped Crafting", "有序合成");
    public static final ShapelessCraftingRecipeType CRAFTING_SHAPELESS = registerShapeless(
            "crafting_shapeless", "Shapeless Crafting", "无序合成");

    // ── Machine types ────────────────────────────────────────────────────────

    public static final TopoRecipeType<TopoRecipe> COMBUSTION_GENERATOR = register("combustion_generator", new CombustionGeneratorRecipeType(RECIPE.id("combustion_generator")), "Combustion Generator", "燃烧发电", "fuel_generator", FillDirection.DOWN_TO_UP, 18, 36);
    public static final TopoRecipeType<TopoRecipe> BOILER = register("boiler", new BoilerRecipeType(RECIPE.id("boiler")), "Boiler", "锅炉", "burn", FillDirection.DOWN_TO_UP, 10, 54);
    public static final TopoRecipeType<TopoRecipe> RESISTIVE_HEATER = register("resistive_heater", "Resistive Heater", "电阻加热", "burn", FillDirection.DOWN_TO_UP, 10, 54);
    public static final TopoRecipeType<TopoRecipe> COMPONENT_PROCESSOR = register("component_processor", "Component Processor", "部件加工", "bending");
    public static final TopoRecipeType<TopoRecipe> WIRE_MILL = register("wire_mill", "Wire Mill", "线材轧制", "lathe");
    public static final TopoRecipeType<TopoRecipe> ENERGY_COMPRESSOR = register("energy_compressor", "Energy Compressor", "能量压缩", "compress");
    public static final TopoRecipeType<TopoRecipe> AIR_COLLECTOR = register("air_collector", "Air Collector", "集气", "arrow");
    public static final TopoRecipeType<TopoRecipe> MACERATOR = register("macerator", "Macerator", "粉碎", "macerate");
    public static final TopoRecipeType<TopoRecipe> FINE_GRINDER = register("fine_grinder", "Fine Grinding", "精细研磨", "macerate");
    public static final TopoRecipeType<TopoRecipe> ORE_WASHER = register("ore_washer", "Ore Washer", "洗矿", "wash");
    /**
     * 电炉：从 {@link #SMELTING} 对应的 foreign 表（原版/他模/我们的 export）导入并加电。
     * 投影规则在 {@link CookingTopoRecipeType#poweredImport}，不另建 Adapter 类。
     */
    public static final TopoRecipeType<TopoRecipe> ELECTRIC_FURNACE = register(
            "electric_furnace", "Electric Smelting", "电力熔炼", "smelt")
            .importRecipesFrom(SMELTING::poweredImport, SMELTING.foreignRecipeType());
    public static final TopoRecipeType<TopoRecipe> BLAST_FURNACE = register(
            "blast_furnace", "Blasting", "高炉熔炼", "smelt")
            .importRecipesFrom(BLASTING::poweredImport, BLASTING.foreignRecipeType());
    public static final TopoRecipeType<TopoRecipe> INDUSTRIAL_COMBUSTION_FURNACE = register("industrial_combustion_furnace", "Industrial Combustion Furnace", "工业燃烧炉", "burn", FillDirection.DOWN_TO_UP, 10, 54);
    public static final TopoRecipeType<TopoRecipe> CHEMICAL_REACTOR = register("chemical_reactor", "Chemical Reactor", "化学反应", "chemical");
    public static final TopoRecipeType<TopoRecipe> MIXING_TANK = register("mixing_tank", "Mixing Tank", "混合", "mix");
    public static final TopoRecipeType<TopoRecipe> ELECTROLYZER = register("electrolyzer", "Electrolyzer", "电解", "electrolyze");
    public static final TopoRecipeType<TopoRecipe> EVAPORATOR = register("evaporator", "Evaporator", "蒸发", "evaporate");
    public static final TopoRecipeType<TopoRecipe> HIGH_PRESSURE_CRYSTALLIZER = register("high_pressure_crystallizer", "High-Pressure Crystallizer", "高压晶化", "crystallize");

    private BuiltinTopoRecipeTypes() {}

    public static void init() {
        Objects.requireNonNull(HIGH_PRESSURE_CRYSTALLIZER);
        Objects.requireNonNull(CRAFTING_SHAPED);
        Objects.requireNonNull(SMELTING);
        Objects.requireNonNull(ELECTRIC_FURNACE);
    }

    private static CookingTopoRecipeType registerCooking(
                                                         String path,
                                                         CookingRecipeBuilder.Kind kind,
                                                         String kindFolder,
                                                         String en,
                                                         String cn) {
        CookingTopoRecipeType type = register(
                path,
                new CookingTopoRecipeType(RECIPE.id(path), kind, kindFolder),
                en,
                cn,
                "smelt");
        type.exportRecipesTo(
                VanillaExportedRecipeAdapters.cooking(RECIPE, kind), type.foreignRecipeType());
        return type;
    }

    private static ShapedCraftingRecipeType registerShaped(String path, String en, String cn) {
        ShapedCraftingRecipeType type = register(
                path, new ShapedCraftingRecipeType(RECIPE.id(path)), en, cn, "arrow");
        type.exportRecipesTo(VanillaExportedRecipeAdapters.shaped(RECIPE), RecipeType.CRAFTING);
        return type;
    }

    private static ShapelessCraftingRecipeType registerShapeless(String path, String en, String cn) {
        ShapelessCraftingRecipeType type = register(
                path, new ShapelessCraftingRecipeType(RECIPE.id(path)), en, cn, "arrow");
        type.exportRecipesTo(VanillaExportedRecipeAdapters.shapeless(RECIPE), RecipeType.CRAFTING);
        return type;
    }

    private static TopoRecipeType<TopoRecipe> register(String path, String en, String cn, String progressBar) {
        return register(path, en, cn, progressBar, FillDirection.LEFT_TO_RIGHT, 20, 40);
    }

    private static TopoRecipeType<TopoRecipe> register(
                                                       String path,
                                                       String en,
                                                       String cn,
                                                       String progressBar,
                                                       FillDirection direction,
                                                       int textureWidth,
                                                       int textureHeight) {
        return RECIPE.recipeType(path)
                .displayName(en, cn)
                .progressBar(
                        RECIPE.id("textures/gui/progress_bar/" + progressBar + ".png"),
                        direction,
                        textureWidth,
                        textureHeight);
    }

    private static <T extends TopoRecipeType<TopoRecipe>> T register(
                                                                     String path,
                                                                     T recipeType,
                                                                     String en,
                                                                     String cn,
                                                                     String progressBar) {
        return register(path, recipeType, en, cn, progressBar, FillDirection.LEFT_TO_RIGHT, 20, 40);
    }

    private static <T extends TopoRecipeType<TopoRecipe>> T register(
                                                                     String path,
                                                                     T recipeType,
                                                                     String en,
                                                                     String cn,
                                                                     String progressBar,
                                                                     FillDirection direction,
                                                                     int textureWidth,
                                                                     int textureHeight) {
        T registered = RECIPE.recipeType(path, recipeType);
        registered
                .displayName(en, cn)
                .progressBar(
                        RECIPE.id("textures/gui/progress_bar/" + progressBar + ".png"),
                        direction,
                        textureWidth,
                        textureHeight);
        return registered;
    }
}
