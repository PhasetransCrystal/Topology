package net.ptcrys.topo.datav2.recipe;

import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.VanillaFacingPaths;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.recipe.craft.CookingOIRecipeType;
import net.ptcrys.topo.datav2.recipe.craft.CookingRecipeBuilder;
import net.ptcrys.topo.datav2.recipe.craft.ShapedCraftingRecipeType;
import net.ptcrys.topo.datav2.recipe.craft.ShapelessCraftingRecipeType;
import net.ptcrys.topo.datav2.recipe.craft.VanillaExportedRecipeAdapters;
import net.ptcrys.topo.datav2.recipe.special.BoilerRecipeType;
import net.ptcrys.topo.datav2.recipe.special.CombustionGeneratorRecipeType;

import net.minecraft.world.item.crafting.RecipeType;

import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;

import java.util.Objects;

/**
 * Builtin recipe types — one table for machines and vanilla-facing craft/cook.
 *
 * <p>
 * Cooking SoT: declare on {@link #SMELTING}/{@link #BLASTING}; machines pull the foreign table
 * via {@link CookingOIRecipeType#poweredImport} (no separate adapter class).
 *
 * <pre>{@code
 * BuiltinOIRecipeTypes.SMELTING.recipe("smelting/…", ingot, dust).save();
 * // ELECTRIC_FURNACE imports RecipeType.SMELTING (ours + vanilla + other mods) as powered recipes
 * }</pre>
 */
public final class BuiltinOIRecipeTypes {

    private static final RecipeDomainRegistration RECIPE = OfficialOIPlugin.INSTANCE.recipe();

    // ── Vanilla-facing cooking first (machines below may import from these) ──

    public static final CookingOIRecipeType SMELTING = registerCooking(
            "oi_smelting",
            CookingRecipeBuilder.Kind.SMELTING,
            VanillaFacingPaths.SMELTING,
            "Smelting",
            "熔炉熔炼");
    public static final CookingOIRecipeType BLASTING = registerCooking(
            "oi_blasting",
            CookingRecipeBuilder.Kind.BLASTING,
            VanillaFacingPaths.BLASTING,
            "Blasting Table",
            "高炉熔炼表");
    public static final CookingOIRecipeType SMOKING = registerCooking(
            "oi_smoking",
            CookingRecipeBuilder.Kind.SMOKING,
            VanillaFacingPaths.SMOKING,
            "Smoking",
            "烟熏");
    public static final CookingOIRecipeType CAMPFIRE = registerCooking(
            "oi_campfire",
            CookingRecipeBuilder.Kind.CAMPFIRE,
            VanillaFacingPaths.CAMPFIRE_COOKING,
            "Campfire Cooking",
            "营火烹饪");

    public static final ShapedCraftingRecipeType CRAFTING_SHAPED = registerShaped(
            "crafting_shaped", "Shaped Crafting", "有序合成");
    public static final ShapelessCraftingRecipeType CRAFTING_SHAPELESS = registerShapeless(
            "crafting_shapeless", "Shapeless Crafting", "无序合成");

    // ── Machine types ────────────────────────────────────────────────────────

    public static final OIRecipeType<OIRecipe> COMBUSTION_GENERATOR = register("combustion_generator", new CombustionGeneratorRecipeType(RECIPE.id("combustion_generator")), "Combustion Generator", "燃烧发电", "fuel_generator", FillDirection.DOWN_TO_UP, 18, 36);
    public static final OIRecipeType<OIRecipe> BOILER = register("boiler", new BoilerRecipeType(RECIPE.id("boiler")), "Boiler", "锅炉", "burn", FillDirection.DOWN_TO_UP, 10, 54);
    public static final OIRecipeType<OIRecipe> RESISTIVE_HEATER = register("resistive_heater", "Resistive Heater", "电阻加热", "burn", FillDirection.DOWN_TO_UP, 10, 54);
    public static final OIRecipeType<OIRecipe> COMPONENT_PROCESSOR = register("component_processor", "Component Processor", "部件加工", "bending");
    public static final OIRecipeType<OIRecipe> WIRE_MILL = register("wire_mill", "Wire Mill", "线材轧制", "lathe");
    public static final OIRecipeType<OIRecipe> ENERGY_COMPRESSOR = register("energy_compressor", "Energy Compressor", "能量压缩", "compress");
    public static final OIRecipeType<OIRecipe> AIR_COLLECTOR = register("air_collector", "Air Collector", "集气", "arrow");
    public static final OIRecipeType<OIRecipe> MACERATOR = register("macerator", "Macerator", "粉碎", "macerate");
    public static final OIRecipeType<OIRecipe> FINE_GRINDER = register("fine_grinder", "Fine Grinding", "精细研磨", "macerate");
    public static final OIRecipeType<OIRecipe> ORE_WASHER = register("ore_washer", "Ore Washer", "洗矿", "wash");
    /**
     * 电炉：从 {@link #SMELTING} 对应的 foreign 表（原版/他模/我们的 export）导入并加电。
     * 投影规则在 {@link CookingOIRecipeType#poweredImport}，不另建 Adapter 类。
     */
    public static final OIRecipeType<OIRecipe> ELECTRIC_FURNACE = register(
            "electric_furnace", "Electric Smelting", "电力熔炼", "smelt")
            .importRecipesFrom(SMELTING::poweredImport, SMELTING.foreignRecipeType());
    public static final OIRecipeType<OIRecipe> BLAST_FURNACE = register(
            "blast_furnace", "Blasting", "高炉熔炼", "smelt")
            .importRecipesFrom(BLASTING::poweredImport, BLASTING.foreignRecipeType());
    public static final OIRecipeType<OIRecipe> INDUSTRIAL_COMBUSTION_FURNACE = register("industrial_combustion_furnace", "Industrial Combustion Furnace", "工业燃烧炉", "burn", FillDirection.DOWN_TO_UP, 10, 54);
    public static final OIRecipeType<OIRecipe> CHEMICAL_REACTOR = register("chemical_reactor", "Chemical Reactor", "化学反应", "chemical");
    public static final OIRecipeType<OIRecipe> MIXING_TANK = register("mixing_tank", "Mixing Tank", "混合", "mix");
    public static final OIRecipeType<OIRecipe> ELECTROLYZER = register("electrolyzer", "Electrolyzer", "电解", "electrolyze");
    public static final OIRecipeType<OIRecipe> EVAPORATOR = register("evaporator", "Evaporator", "蒸发", "evaporate");
    public static final OIRecipeType<OIRecipe> HIGH_PRESSURE_CRYSTALLIZER = register("high_pressure_crystallizer", "High-Pressure Crystallizer", "高压晶化", "crystallize");

    private BuiltinOIRecipeTypes() {}

    public static void init() {
        Objects.requireNonNull(HIGH_PRESSURE_CRYSTALLIZER);
        Objects.requireNonNull(CRAFTING_SHAPED);
        Objects.requireNonNull(SMELTING);
        Objects.requireNonNull(ELECTRIC_FURNACE);
    }

    private static CookingOIRecipeType registerCooking(
                                                       String path,
                                                       CookingRecipeBuilder.Kind kind,
                                                       String kindFolder,
                                                       String en,
                                                       String cn) {
        CookingOIRecipeType type = register(
                path,
                new CookingOIRecipeType(RECIPE.id(path), kind, kindFolder),
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

    private static OIRecipeType<OIRecipe> register(String path, String en, String cn, String progressBar) {
        return register(path, en, cn, progressBar, FillDirection.LEFT_TO_RIGHT, 20, 40);
    }

    private static OIRecipeType<OIRecipe> register(
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

    private static <T extends OIRecipeType<OIRecipe>> T register(
                                                                 String path,
                                                                 T recipeType,
                                                                 String en,
                                                                 String cn,
                                                                 String progressBar) {
        return register(path, recipeType, en, cn, progressBar, FillDirection.LEFT_TO_RIGHT, 20, 40);
    }

    private static <T extends OIRecipeType<OIRecipe>> T register(
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
