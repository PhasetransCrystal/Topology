package net.ptcrys.topo.datav2.machine;

import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.apiv2.lang.DisplayNames;
import net.ptcrys.topo.apiv2.lang.RegistryDisplayLang;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOICasingBlocks;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;
import net.ptcrys.topo.helper.MaterialHelper;

import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Fixed machine-construction parts and vanilla workbench recipes from
 * {@code docs/design/machine_construction.md}.
 */
public final class BuiltinOIMachineCraftingRecipes {

    public static final BlockEntry<Block> MACHINE_HULL_BASIC = hullBlock("machine_hull_basic", "Basic Machine Hull", "基础机匣", MachineTier.T1);
    public static final BlockEntry<Block> MACHINE_HULL_REINFORCED = hullBlock("machine_hull_reinforced", "Reinforced Machine Hull", "强化机匣", MachineTier.T2);
    public static final BlockEntry<Block> MACHINE_HULL_DENSE = hullBlock("machine_hull_dense", "Dense Machine Hull", "致密机匣", MachineTier.T3);
    /** @deprecated use {@link #MACHINE_HULL_BASIC} */
    @Deprecated
    public static final BlockEntry<Block> MACHINE_HULL = MACHINE_HULL_BASIC;

    public static final ItemEntry<Item> FORGE_ANVIL_CORE = part("forge_anvil_core", "Forge Anvil Core", "锻座");
    public static final ItemEntry<Item> EMBER_CORE = part("ember_core", "Ember Core", "烬芯");
    public static final ItemEntry<Item> EMBER_CORE_BLANK = part("ember_core_blank", "Ember Core Blank", "烬芯坯");
    public static final ItemEntry<Item> GLOW_FILAMENT = part("glow_filament", "Glow Filament", "炽丝");
    public static final ItemEntry<Item> SINTER_CORE = part("sinter_core", "Sinter Core", "烧结芯");
    public static final ItemEntry<Item> CRUSH_WHEEL = part("crush_wheel", "Crush Wheel", "碎轮");
    public static final ItemEntry<Item> GRIND_DISC = part("grind_disc", "Grinding Disc", "磨盘");
    public static final ItemEntry<Item> DRAW_SPOOL = part("draw_spool", "Draw Spool", "拉丝轮");
    public static final ItemEntry<Item> FLOW_VANE = part("flow_vane", "Flow Vane", "引流叶");
    public static final ItemEntry<Item> HEAT_DUCT = part("heat_duct", "Heat Duct", "导热排");
    public static final ItemEntry<Item> HEAT_DUCT_BLANK = part("heat_duct_blank", "Heat Duct Blank", "导热排坯");
    public static final ItemEntry<Item> CORROSION_LINER = part("corrosion_liner", "Corrosion Liner", "蚀纹衬");
    public static final ItemEntry<Item> CORROSION_LINER_BLANK = part("corrosion_liner_blank", "Corrosion Liner Blank", "蚀纹衬坯");
    public static final ItemEntry<Item> SIGNAL_CORE = part("signal_core", "Signal Core", "讯芯");
    public static final ItemEntry<Item> FOCUS_RING = part("focus_ring", "Focus Ring", "聚能环");
    public static final ItemEntry<Item> FOCUS_RING_BLANK = part("focus_ring_blank", "Focus Ring Blank", "聚能环坯");
    public static final ItemEntry<Item> NEXUS_CORE = part("nexus_core", "Nexus Core", "枢芯");
    public static final ItemEntry<Item> NEXUS_CORE_BLANK = part("nexus_core_blank", "Nexus Core Blank", "枢芯坯");
    public static final ItemEntry<Item> RESONANCE_CHAMBER = part("resonance_chamber", "Resonance Chamber", "共鸣腔");
    public static final ItemEntry<Item> RESONANCE_CHAMBER_BLANK = part("resonance_chamber_blank", "Resonance Chamber Blank", "共鸣腔坯");
    public static final ItemEntry<Item> LUMEN_CORE = part("lumen_core", "Lumen Core", "辉芯");
    public static final ItemEntry<Item> LUMEN_CORE_BLANK = part("lumen_core_blank", "Lumen Core Blank", "辉芯坯");

    private static final int GATE_DURATION = 100;
    private static final long GATE_ENERGY_PER_TICK = 30;
    private static final long GATE_ADV_ENERGY_PER_TICK = 20;
    private static final int GATE_FLUID_MB = 250;

    private BuiltinOIMachineCraftingRecipes() {}

    /**
     * Force class load so hull/part RegistryLib entries are queued. Do not emit recipes here —
     * machine-referencing recipes must wait until after {@code registerMachines}.
     */
    public static void initItems() {
        // Touch a field so static Item/Block entries run without registering recipes.
        Objects.requireNonNull(MACHINE_HULL_BASIC);
    }

    /**
     * Workbench + machine process recipes. Call only after machine catalogs (and casings) exist.
     */
    public static void initRecipes() {
        registerWorkbenchRecipes();
        registerCookingRecipes();
        registerMachineProcessRecipes();
    }

    /** Material item resolved only at datagen emit (not class-init). */
    private static Supplier<Item> mat(net.ptcrys.topo.apiv2.material.Material material, net.ptcrys.topo.apiv2.material.form.MaterialForm form) {
        return () -> MaterialHelper.requireItem(material, form);
    }

    private static Supplier<? extends ItemLike> blockOf(MachineDefinition machine) {
        return () -> machine.registeredBlock().get();
    }

    /**
     * GTCEu-style compact shaped: pattern strings then {@code 'K', ingredient} pairs. Ingredient may
     * be {@link ItemLike}, {@link Supplier}, tag, or ingredient. Result is always a Supplier.
     */
    private static void shaped(
                               String path, Supplier<? extends ItemLike> result, Supplier<? extends ItemLike> unlock, Object... recipe) {
        BuiltinOIRecipeTypes.CRAFTING_SHAPED.recipe(path, result, recipe).unlockedBy(unlock).save();
    }

    private static void shaped(
                               String path,
                               Supplier<? extends ItemLike> result,
                               int count,
                               Supplier<? extends ItemLike> unlock,
                               Object... recipe) {
        BuiltinOIRecipeTypes.CRAFTING_SHAPED.recipe(path, result, count, recipe).unlockedBy(unlock).save();
    }

    private static void machine(
                                MachineDefinition machine, Supplier<? extends ItemLike> unlock, Object... recipe) {
        shaped("shape/machine/" + machine.id().getPath(), blockOf(machine), unlock, recipe);
    }

    private static ItemEntry<Item> part(String id, String enName, String zhName) {
        var builder = Topology.REGISTRY.item(id);
        RegistryDisplayLang.applyItem(builder, Topology.REGISTRY, id, DisplayNames.fixed(enName, zhName));
        return builder.addTab(BuiltinOICreativeTabs.COMPONENTS.getKey()).register();
    }

    /**
     * Graded hull casing: top/side/bottom from {@link MachineTier#hullTextureFolder()}
     * ({@code textures/block/machine_hulls/t1|t2|t3/}).
     */
    private static BlockEntry<Block> hullBlock(String id, String enName, String zhName, MachineTier tier) {
        String folder = tier.hullTextureFolder();
        var builder = Topology.REGISTRY.block(id, Block::new)
                .initialProperties(Blocks.IRON_BLOCK)
                .blockstate(() -> (block, provider) -> provider.generateWithTemplate(
                        block,
                        ModelTemplates.CUBE_BOTTOM_TOP,
                        new TextureMapping()
                                .put(
                                        TextureSlot.SIDE,
                                        new Material(OfficialOIPlugin.INSTANCE.machine().id(folder + "/side")))
                                .put(
                                        TextureSlot.TOP,
                                        new Material(OfficialOIPlugin.INSTANCE.machine().id(folder + "/top")))
                                .put(
                                        TextureSlot.BOTTOM,
                                        new Material(
                                                OfficialOIPlugin.INSTANCE.machine().id(folder + "/bottom")))))
                .item(item -> item.addTab(BuiltinOICreativeTabs.COMPONENTS.getKey()));
        RegistryDisplayLang.applyBlock(builder, Topology.REGISTRY, id, DisplayNames.fixed(enName, zhName));
        return builder.register();
    }

    private static void registerWorkbenchRecipes() {
        registerStructureCasingRecipes();
        registerPartRecipes();
        registerNativeMachineRecipes();
        registerHigherTier();
        registerMultiblockControllerRecipes();
    }

    private static void registerStructureCasingRecipes() {
        var nickelDense = mat(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.PLATE_DENSE);
        var ironFrame = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.FRAME);
        var nickelBolt = mat(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.BOLT);
        shaped(
                "shape/component/dense_structure_casing",
                BuiltinOICasingBlocks.DENSE_STRUCTURE_CASING,
                2,
                nickelDense,
                "PBP", "BFB", "PBP",
                'P', nickelDense, 'B', nickelBolt, 'F', ironFrame);
    }

    private static void registerMultiblockControllerRecipes() {
        var nickelBolt = mat(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.BOLT);
        machine(
                BuiltinOIControllerMachines.LARGE_GRINDER,
                GRIND_DISC,
                "D D", "DFU", "DBB",
                'D', GRIND_DISC, 'F', blockOf(BuiltinOIMachines.FINE_GRINDER_T3),
                'U', LUMEN_CORE, 'B', nickelBolt);
    }

    private static void registerPartRecipes() {
        var ironPlate = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.PLATE);
        var ironRod = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.ROD);
        var ironGear = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.GEAR);
        var ironBolt = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.BOLT);
        var ironFrame = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.FRAME);
        var copperPlate = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.PLATE);
        var copperRod = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.ROD);
        var copperWire = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.WIRE_1X);
        var goldPlate = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.PLATE);
        var goldRod = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.ROD);
        var goldWire = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.WIRE_1X);
        var goldWire2 = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.WIRE_2X);
        var goldWire4 = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.WIRE_4X);
        var nickelWire = mat(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.WIRE_1X);
        var leadPlate = mat(BuiltinOIMaterials.LEAD, BuiltinOIMaterialForms.PLATE);
        var tinPlate = mat(BuiltinOIMaterials.TIN, BuiltinOIMaterialForms.PLATE);
        var zincPlate = mat(BuiltinOIMaterials.ZINC, BuiltinOIMaterialForms.PLATE);
        var redstoneCrude = mat(BuiltinOIMaterials.REDSTONE, BuiltinOIMaterialForms.CRUDE_DUST);
        var luminiteGem = mat(BuiltinOIMaterials.LUMINITE, BuiltinOIMaterialForms.GEM);

        shaped("shape/component/machine_hull_basic", MACHINE_HULL_BASIC, ironFrame,
                "PBP", "BFB", "PBP", 'P', ironPlate, 'B', ironBolt, 'F', ironFrame);

        shaped("shape/component/forge_anvil_core", FORGE_ANVIL_CORE, ironGear,
                "P P", " G ", " R ", 'P', ironPlate, 'G', ironGear, 'R', ironRod);

        shaped("shape/component/ember_core_blank", EMBER_CORE_BLANK, () -> Items.FURNACE,
                "PRP", "PFP", " R ", 'P', ironPlate, 'R', ironRod, 'F', Items.FURNACE);

        shaped("shape/component/glow_filament", GLOW_FILAMENT, nickelWire,
                " W ", " R ", " W ", 'W', nickelWire, 'R', ironRod);
        shaped("shape/component/sinter_core", SINTER_CORE, copperWire,
                "PWP", " R ", "PWP", 'P', ironPlate, 'W', copperWire, 'R', ironRod);
        shaped("shape/component/crush_wheel", CRUSH_WHEEL, ironGear,
                "GPB", " P ", "BPG", 'G', ironGear, 'P', ironPlate, 'B', ironBolt);
        shaped("shape/component/grind_disc", GRIND_DISC, () -> Items.FLINT,
                " F ", "PGP", " F ", 'F', Items.FLINT, 'P', ironPlate, 'G', ironGear);
        shaped("shape/component/draw_spool", DRAW_SPOOL, ironGear,
                "GR", "RG", 'G', ironGear, 'R', ironRod);
        shaped("shape/component/flow_vane", FLOW_VANE, tinPlate,
                "PW", "WP", 'P', tinPlate, 'W', copperWire);

        // heat duct blank: 4 copper plate + 2 copper rod
        shaped("shape/component/heat_duct_blank", HEAT_DUCT_BLANK, copperPlate,
                "P P", "R R", "P P", 'P', copperPlate, 'R', copperRod);
        // corrosion liner blank: 4 lead plate + glass
        shaped("shape/component/corrosion_liner_blank", CORROSION_LINER_BLANK, leadPlate,
                "P P", " G ", "P P", 'P', leadPlate, 'G', Items.GLASS);
        // signal core: 1 zinc plate + 2 copper 1x + 2 redstone crude
        shaped("shape/component/signal_core", SIGNAL_CORE, zincPlate,
                "WRW", " Z ", " R ", 'W', copperWire, 'Z', zincPlate, 'R', redstoneCrude);
        // focus ring blank: 4 gold 1x + 2 copper plate + 1 copper rod
        shaped("shape/component/focus_ring_blank", FOCUS_RING_BLANK, goldWire,
                "WCW", " R ", "WCW", 'W', goldWire, 'C', copperPlate, 'R', copperRod);
        // nexus blank: 2 signal + 1 gold 2x + 2 zinc plate
        shaped("shape/component/nexus_core_blank", NEXUS_CORE_BLANK, SIGNAL_CORE,
                "SZS", " W ", " Z ", 'S', SIGNAL_CORE, 'Z', zincPlate, 'W', goldWire2);
        // resonance blank: 2 diamond + 4 gold plate + 2 gold rod
        shaped("shape/component/resonance_chamber_blank", RESONANCE_CHAMBER_BLANK, () -> Items.DIAMOND,
                "PRP", "D D", "PRP", 'P', goldPlate, 'R', goldRod, 'D', Items.DIAMOND);
        // lumen blank: 2 nexus + 1 luminite gem + 1 gold 4x
        shaped("shape/component/lumen_core_blank", LUMEN_CORE_BLANK, NEXUS_CORE,
                "N N", " L ", " W ", 'N', NEXUS_CORE, 'L', luminiteGem, 'W', goldWire4);
    }

    private static void registerNativeMachineRecipes() {
        var ironGear = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.GEAR);
        var ironBolt = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.BOLT);
        var nickelBolt = mat(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.BOLT);
        var copperWire = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.WIRE_1X);
        var copperWire4 = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.WIRE_4X);
        var goldWire2 = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.WIRE_2X);
        var goldWire8 = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.WIRE_8X);

        var hullB = MACHINE_HULL_BASIC;
        var hullR = MACHINE_HULL_REINFORCED;
        var hullD = MACHINE_HULL_DENSE;

        machine(BuiltinOIMachines.COMPONENT_PROCESSOR_T1, FORGE_ANVIL_CORE,
                "WGW", "AHB", " BG",
                'W', copperWire, 'G', ironGear, 'A', FORGE_ANVIL_CORE, 'H', hullB, 'B', ironBolt);
        machine(BuiltinOIMachines.WIRE_MILL_T1, DRAW_SPOOL,
                "WGW", "DHB", " BG",
                'W', copperWire, 'G', ironGear, 'D', DRAW_SPOOL, 'H', hullB, 'B', ironBolt);
        machine(BuiltinOIMachines.COMBUSTION_GENERATOR_T1, EMBER_CORE,
                "WGW", "EHB", " BG",
                'W', copperWire, 'G', ironGear, 'E', EMBER_CORE, 'H', hullB, 'B', ironBolt);
        machine(BuiltinOIMachines.BOILER_T1, EMBER_CORE,
                " F ", "EHB", " B ",
                'F', FLOW_VANE, 'E', EMBER_CORE, 'H', hullB, 'B', ironBolt);
        machine(BuiltinOIMachines.RESISTIVE_HEATER_T1, GLOW_FILAMENT,
                "WBW", "GHB",
                'W', copperWire, 'B', ironBolt, 'G', GLOW_FILAMENT, 'H', hullB);
        machine(BuiltinOIMachines.MACERATOR_T1, CRUSH_WHEEL,
                "WGW", "CHB", " BG",
                'W', copperWire, 'G', ironGear, 'C', CRUSH_WHEEL, 'H', hullB, 'B', ironBolt);
        machine(BuiltinOIMachines.FINE_GRINDER_T1, GRIND_DISC,
                "WGW", "DHB", " BG",
                'W', copperWire, 'G', ironGear, 'D', GRIND_DISC, 'H', hullB, 'B', ironBolt);
        machine(BuiltinOIMachines.ORE_WASHER_T1, FLOW_VANE,
                "WFW", "BHB", " F ",
                'W', copperWire, 'F', FLOW_VANE, 'B', ironBolt, 'H', hullB);
        machine(BuiltinOIMachines.ELECTRIC_FURNACE_T1, SINTER_CORE,
                "WBW", "SHB",
                'W', copperWire, 'B', ironBolt, 'S', SINTER_CORE, 'H', hullB);
        machine(BuiltinOIMachines.AIR_COLLECTOR_T1, FLOW_VANE,
                "WFW", "BHB", " GF",
                'W', copperWire, 'F', FLOW_VANE, 'G', Items.GLASS, 'H', hullB, 'B', ironBolt);

        machine(BuiltinOIMachines.ENERGY_COMPRESSOR_T2, FOCUS_RING,
                "WBW", "FHS", " B ",
                'W', goldWire2, 'B', nickelBolt, 'F', FOCUS_RING, 'H', hullR, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.INDUSTRIAL_COMBUSTION_FURNACE_T2, CORROSION_LINER,
                "EL ", "BHS", "DB ",
                'E', EMBER_CORE, 'L', CORROSION_LINER, 'B', nickelBolt, 'H', hullR, 'S', SIGNAL_CORE, 'D', HEAT_DUCT);
        machine(BuiltinOIMachines.CHEMICAL_REACTOR_T2, CORROSION_LINER,
                "WLW", "LHD", "BSB",
                'W', copperWire4, 'L', CORROSION_LINER, 'H', hullR, 'D', HEAT_DUCT, 'B', nickelBolt, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.MIXING_TANK_T2, FLOW_VANE,
                "WFW", "BHB", "LFS",
                'W', copperWire4, 'F', FLOW_VANE, 'B', nickelBolt, 'H', hullR, 'L', CORROSION_LINER, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.EVAPORATOR_T2, HEAT_DUCT,
                "D D", "LHS", "BFB",
                'D', HEAT_DUCT, 'L', CORROSION_LINER, 'H', hullR, 'S', SIGNAL_CORE, 'B', nickelBolt, 'F', FLOW_VANE);
        machine(BuiltinOIMachines.ELECTROLYZER_T2, FOCUS_RING,
                "W W", "FHL", "BSB",
                'W', goldWire2, 'F', FOCUS_RING, 'H', hullR, 'L', CORROSION_LINER, 'B', nickelBolt, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.HIGH_PRESSURE_CRYSTALLIZER_T3, RESONANCE_CHAMBER,
                "WFW", "RHL", "BNB",
                'W', goldWire8, 'F', FOCUS_RING, 'R', RESONANCE_CHAMBER, 'H', hullD, 'L', CORROSION_LINER, 'B', nickelBolt, 'N', NEXUS_CORE);
    }

    private static void registerHigherTier() {
        var nickelBolt = mat(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.BOLT);
        var copperWire4 = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.WIRE_4X);
        var copperWire8 = mat(BuiltinOIMaterials.COPPER, BuiltinOIMaterialForms.WIRE_8X);
        var goldWire8 = mat(BuiltinOIMaterials.GOLD, BuiltinOIMaterialForms.WIRE_8X);
        var ironRotor = mat(BuiltinOIMaterials.IRON, BuiltinOIMaterialForms.ROTOR);
        var hullR = MACHINE_HULL_REINFORCED;
        var hullD = MACHINE_HULL_DENSE;

        higherRotatingT2(BuiltinOIMachines.COMPONENT_PROCESSOR_T2, FORGE_ANVIL_CORE, hullR, copperWire4, ironRotor, nickelBolt);
        higherRotatingT2(BuiltinOIMachines.WIRE_MILL_T2, DRAW_SPOOL, hullR, copperWire4, ironRotor, nickelBolt);
        higherRotatingT2(BuiltinOIMachines.COMBUSTION_GENERATOR_T2, EMBER_CORE, hullR, copperWire4, ironRotor, nickelBolt);
        higherRotatingT2(BuiltinOIMachines.MACERATOR_T2, CRUSH_WHEEL, hullR, copperWire4, ironRotor, nickelBolt);
        higherRotatingT2(BuiltinOIMachines.FINE_GRINDER_T2, GRIND_DISC, hullR, copperWire4, ironRotor, nickelBolt);

        machine(BuiltinOIMachines.BOILER_T2, EMBER_CORE,
                " F ", "EHS", "B B",
                'F', FLOW_VANE, 'E', EMBER_CORE, 'H', hullR, 'S', SIGNAL_CORE, 'B', nickelBolt);
        machine(BuiltinOIMachines.RESISTIVE_HEATER_T2, GLOW_FILAMENT,
                "WBW", "GHS", " B ",
                'W', copperWire4, 'B', nickelBolt, 'G', GLOW_FILAMENT, 'H', hullR, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.ORE_WASHER_T2, FLOW_VANE,
                "WFW", "BHB", " SF",
                'W', copperWire4, 'F', FLOW_VANE, 'B', nickelBolt, 'H', hullR, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.ELECTRIC_FURNACE_T2, SINTER_CORE,
                "WBW", "CHS", " B ",
                'W', copperWire4, 'B', nickelBolt, 'C', SINTER_CORE, 'H', hullR, 'S', SIGNAL_CORE);
        machine(BuiltinOIMachines.AIR_COLLECTOR_T2, FLOW_VANE,
                "WFW", "BHB", "GSF",
                'W', copperWire4, 'F', FLOW_VANE, 'G', Items.GLASS, 'H', hullR, 'S', SIGNAL_CORE, 'B', nickelBolt);

        higherRotatingT3(BuiltinOIMachines.COMPONENT_PROCESSOR_T3, FORGE_ANVIL_CORE, hullD, copperWire8, ironRotor, nickelBolt);
        higherRotatingT3(BuiltinOIMachines.WIRE_MILL_T3, DRAW_SPOOL, hullD, copperWire8, ironRotor, nickelBolt);
        higherRotatingT3(BuiltinOIMachines.COMBUSTION_GENERATOR_T3, EMBER_CORE, hullD, copperWire8, ironRotor, nickelBolt);
        higherRotatingT3(BuiltinOIMachines.MACERATOR_T3, CRUSH_WHEEL, hullD, copperWire8, ironRotor, nickelBolt);
        higherRotatingT3(BuiltinOIMachines.FINE_GRINDER_T3, GRIND_DISC, hullD, copperWire8, ironRotor, nickelBolt);

        machine(BuiltinOIMachines.BOILER_T3, EMBER_CORE,
                " F ", "EHL", "B B",
                'F', FLOW_VANE, 'E', EMBER_CORE, 'H', hullD, 'L', LUMEN_CORE, 'B', nickelBolt);
        machine(BuiltinOIMachines.RESISTIVE_HEATER_T3, GLOW_FILAMENT,
                "WBW", "GHL", " B ",
                'W', copperWire8, 'B', nickelBolt, 'G', GLOW_FILAMENT, 'H', hullD, 'L', LUMEN_CORE);
        machine(BuiltinOIMachines.ORE_WASHER_T3, FLOW_VANE,
                "WFW", "BHB", " LF",
                'W', copperWire8, 'F', FLOW_VANE, 'B', nickelBolt, 'H', hullD, 'L', LUMEN_CORE);
        machine(BuiltinOIMachines.ELECTRIC_FURNACE_T3, SINTER_CORE,
                "WBW", "CHL", " B ",
                'W', copperWire8, 'B', nickelBolt, 'C', SINTER_CORE, 'H', hullD, 'L', LUMEN_CORE);
        machine(BuiltinOIMachines.AIR_COLLECTOR_T3, FLOW_VANE,
                "WFW", "BHB", "GLF",
                'W', copperWire8, 'F', FLOW_VANE, 'G', Items.GLASS, 'H', hullD, 'L', LUMEN_CORE, 'B', nickelBolt);

        machine(BuiltinOIMachines.ENERGY_COMPRESSOR_T3, FOCUS_RING,
                "WBW", "FHL", " B ",
                'W', goldWire8, 'B', nickelBolt, 'F', FOCUS_RING, 'H', hullD, 'L', LUMEN_CORE);
        machine(BuiltinOIMachines.INDUSTRIAL_COMBUSTION_FURNACE_T3, CORROSION_LINER,
                "EL ", "BHU", "DB ",
                'E', EMBER_CORE, 'L', CORROSION_LINER, 'B', nickelBolt, 'H', hullD, 'U', LUMEN_CORE, 'D', HEAT_DUCT);
        machine(BuiltinOIMachines.CHEMICAL_REACTOR_T3, CORROSION_LINER,
                "WLW", "LHD", "BUB",
                'W', copperWire8, 'L', CORROSION_LINER, 'H', hullD, 'D', HEAT_DUCT, 'B', nickelBolt, 'U', LUMEN_CORE);
        machine(BuiltinOIMachines.MIXING_TANK_T3, FLOW_VANE,
                "WFW", "BHB", "LFU",
                'W', copperWire8, 'F', FLOW_VANE, 'B', nickelBolt, 'H', hullD, 'L', CORROSION_LINER, 'U', LUMEN_CORE);
        machine(BuiltinOIMachines.EVAPORATOR_T3, HEAT_DUCT,
                "D D", "LHU", "BFB",
                'D', HEAT_DUCT, 'L', CORROSION_LINER, 'H', hullD, 'U', LUMEN_CORE, 'B', nickelBolt, 'F', FLOW_VANE);
        machine(BuiltinOIMachines.ELECTROLYZER_T3, FOCUS_RING,
                "W W", "FHL", "BUB",
                'W', goldWire8, 'F', FOCUS_RING, 'H', hullD, 'L', CORROSION_LINER, 'B', nickelBolt, 'U', LUMEN_CORE);
    }

    private static void higherRotatingT2(
                                         MachineDefinition machine,
                                         Supplier<? extends ItemLike> module,
                                         Supplier<? extends ItemLike> hull,
                                         Supplier<? extends ItemLike> wire,
                                         Supplier<? extends ItemLike> rotor,
                                         Supplier<? extends ItemLike> bolt) {
        machine(machine, module,
                "WBW", "MHS", "RB ",
                'W', wire, 'B', bolt, 'M', module, 'H', hull, 'S', SIGNAL_CORE, 'R', rotor);
    }

    private static void higherRotatingT3(
                                         MachineDefinition machine,
                                         Supplier<? extends ItemLike> module,
                                         Supplier<? extends ItemLike> hull,
                                         Supplier<? extends ItemLike> wire,
                                         Supplier<? extends ItemLike> rotor,
                                         Supplier<? extends ItemLike> bolt) {
        machine(machine, module,
                "WRW", "MHU", "BRB",
                'W', wire, 'R', rotor, 'M', module, 'H', hull, 'U', LUMEN_CORE, 'B', bolt);
    }

    private static void registerCookingRecipes() {
        BuiltinOIRecipeTypes.SMELTING
                .recipe("smelting/component/ember_core_from_blank", EMBER_CORE, EMBER_CORE_BLANK)
                .experience(0.7F)
                .cookingTime(200)
                .unlockedBy(EMBER_CORE_BLANK)
                .save();
    }

    private static void registerMachineProcessRecipes() {
        ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
        ScalarRecipeCapability energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
        ScalarRecipeCapability advEnergy = BuiltinOIResourceIntegrations.ADVANCED_ENERGY.recipeCapability();
        FluidRecipeCapability fluids = BuiltinOIResourceIntegrations.FLUID.recipeCapability();

        BuiltinOIRecipeTypes.COMPONENT_PROCESSOR.recipe("machine_hull_reinforced_press")
                .input(items.in(deferred(MACHINE_HULL_BASIC), 1))
                .input(items.in(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.PLATE, 4))
                .input(items.in(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.BOLT, 4))
                .output(items.out(deferred(MACHINE_HULL_REINFORCED), 1))
                .tickInput(energy.in(GATE_ENERGY_PER_TICK))
                .duration(GATE_DURATION)
                .productionLine(BuiltinOIProductionLines.MACHINE_COMPONENT)
                .save();
        BuiltinOIRecipeTypes.COMPONENT_PROCESSOR.recipe("machine_hull_dense_press")
                .input(items.in(deferred(MACHINE_HULL_REINFORCED), 1))
                .input(items.in(BuiltinOIMaterials.NICKEL, BuiltinOIMaterialForms.PLATE_DENSE, 4))
                .output(items.out(deferred(MACHINE_HULL_DENSE), 1))
                .tickInput(energy.in(GATE_ENERGY_PER_TICK))
                .duration(GATE_DURATION)
                .productionLine(BuiltinOIProductionLines.MACHINE_COMPONENT)
                .save();

        // Blank finishing: ordinary SMELTING SoT (vanilla furnace + electric import), not EF-only.
        BuiltinOIRecipeTypes.SMELTING
                .recipe("smelting/component/heat_duct_from_blank", HEAT_DUCT, HEAT_DUCT_BLANK)
                .cookingTime(GATE_DURATION)
                .unlockedBy(HEAT_DUCT_BLANK)
                .save();
        BuiltinOIRecipeTypes.SMELTING
                .recipe(
                        "smelting/component/corrosion_liner_from_blank",
                        CORROSION_LINER,
                        CORROSION_LINER_BLANK)
                .cookingTime(GATE_DURATION)
                .unlockedBy(CORROSION_LINER_BLANK)
                .save();
        BuiltinOIRecipeTypes.SMELTING
                .recipe("smelting/component/focus_ring_from_blank", FOCUS_RING, FOCUS_RING_BLANK)
                .cookingTime(GATE_DURATION)
                .unlockedBy(FOCUS_RING_BLANK)
                .save();

        BuiltinOIRecipeTypes.CHEMICAL_REACTOR.recipe("nexus_core_etch")
                .input(items.in(deferred(NEXUS_CORE_BLANK), 1))
                .input(fluids.in(
                        MaterialHelper.materialFluidSupplier(
                                BuiltinOIMaterials.SULFURIC_ACID, BuiltinOIMaterialForms.SOLUTION),
                        GATE_FLUID_MB))
                .output(items.out(deferred(NEXUS_CORE), 1))
                .tickInput(energy.in(GATE_ENERGY_PER_TICK))
                .duration(GATE_DURATION)
                .save();

        BuiltinOIRecipeTypes.ELECTROLYZER.recipe("resonance_chamber_plate")
                .input(items.in(deferred(RESONANCE_CHAMBER_BLANK), 1))
                .output(items.out(deferred(RESONANCE_CHAMBER), 1))
                .tickInput(advEnergy.in(GATE_ADV_ENERGY_PER_TICK))
                .duration(GATE_DURATION)
                .save();

        BuiltinOIRecipeTypes.HIGH_PRESSURE_CRYSTALLIZER.recipe("lumen_core_tune")
                .input(items.in(deferred(LUMEN_CORE_BLANK), 1))
                .output(items.out(deferred(LUMEN_CORE), 1))
                .tickInput(advEnergy.in(GATE_ADV_ENERGY_PER_TICK))
                .duration(GATE_DURATION)
                .save();
    }

    /**
     * Forces the {@link Supplier} overload of recipe I/O. {@link ItemEntry} implements both
     * {@link ItemLike} (SAM {@code asItem}) and {@link Supplier}; bare lambdas/method refs can
     * resolve to ItemLike and eagerly bind deferred holders during class init.
     */
    private static Supplier<? extends ItemLike> deferred(Supplier<? extends ItemLike> entry) {
        return entry;
    }

    private static ResourceKey<Recipe<?>> recipeKey(String path) {
        return ResourceKey.create(Registries.RECIPE, OfficialOIPlugin.INSTANCE.recipe().id(path));
    }
}
