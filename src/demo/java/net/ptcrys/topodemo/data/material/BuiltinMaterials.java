package net.ptcrys.topodemo.data.material;

import net.ptcrys.topo.api.api.registration.ExternalTargets;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.BULK;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.FUEL;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.GEM;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.INGOT;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.ORE_DROP;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.ORE_HOST;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.ORE_PRODUCT;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.PULVERIZABLE;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.SHARD;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.TOOL;
import static net.ptcrys.topodemo.api.material.common.GemDataType.GemQuality.FLAWLESS;
import static net.ptcrys.topodemo.api.material.common.OreHostDataType.Host.*;
import static net.ptcrys.topodemo.api.material.common.OreProductDataType.Product.*;

public final class BuiltinMaterials {

    public static final Material HYDROGEN = TopoDemoPlugin.INSTANCE.material().material("hydrogen")
            .lang("Hydrogen", "氢气")
            .data(BuiltinMaterialDataTypes.GAS.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(2))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xDDEEFF))
            .build();

    public static final Material AIR = TopoDemoPlugin.INSTANCE.material().material("air")
            .lang("Air", "空气")
            .data(BuiltinMaterialDataTypes.GAS.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(29))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD4ECFF))
            .build();

    public static final Material SULFUR = TopoDemoPlugin.INSTANCE.material().material("sulfur")
            .lang("Sulfur", "硫磺")
            .data(PULVERIZABLE.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(32))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xE6D64C))
            .build();

    public static final Material SULFUR_DIOXIDE = TopoDemoPlugin.INSTANCE.material().material("sulfur_dioxide")
            .lang("Sulfur Dioxide", "二氧化硫")
            .data(BuiltinMaterialDataTypes.GAS.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(64))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xE5D77A))
            .build();

    public static final Material SULFUR_TRIOXIDE = TopoDemoPlugin.INSTANCE.material().material("sulfur_trioxide")
            .lang("Sulfur Trioxide", "三氧化硫")
            .data(BuiltinMaterialDataTypes.GAS.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(80))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xEFE38B))
            .build();

    public static final Material SULFURIC_ACID = TopoDemoPlugin.INSTANCE.material().material("sulfuric_acid")
            .lang("Sulfuric Acid", "硫酸")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(98))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD8E6A8))
            .build();

    public static final Material SALT = TopoDemoPlugin.INSTANCE.material().material("salt")
            .lang("Salt", "食盐")
            .data(PULVERIZABLE.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(58))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xF2F2EA))
            .build();

    public static final Material BRINE = TopoDemoPlugin.INSTANCE.material().material("brine")
            .lang("Brine", "饱和盐水")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(58))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xB8D6E7))
            .build();

    public static final Material SODIUM_HYDROXIDE = TopoDemoPlugin.INSTANCE.material().material("sodium_hydroxide")
            .lang("Sodium Hydroxide", "氢氧化钠")
            .data(PULVERIZABLE.enabled())
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(40))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xE8F0F0))
            .build();

    public static final Material CHLORINE = TopoDemoPlugin.INSTANCE.material().material("chlorine")
            .lang("Chlorine", "氯气")
            .data(BuiltinMaterialDataTypes.GAS.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(71))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xC7E36B))
            .build();

    public static final Material ELECTROLYTE = TopoDemoPlugin.INSTANCE.material().material("electrolyte")
            .lang("Electrolyte", "电解液")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(1))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x8BD5FF))
            .build();

    public static final Material NICKEL = TopoDemoPlugin.INSTANCE.material().material("nickel")
            .lang("Nickel", "镍")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4))
            .data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(59))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD7C996))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material LEAD = TopoDemoPlugin.INSTANCE.material().material("lead")
            .lang("Lead", "铅")
            .data(INGOT.enabled()).data(PULVERIZABLE.enabled()).data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD, NETHER))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4)).data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(207)).data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x5E617B))
            .data(BuiltinProcessors.GRINDING.activation()).data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material ZINC = TopoDemoPlugin.INSTANCE.material().material("zinc")
            .lang("Zinc", "锌")
            .data(INGOT.enabled()).data(PULVERIZABLE.enabled()).data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4)).data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(65)).data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xC7D6D2))
            .data(BuiltinProcessors.GRINDING.activation()).data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material URANIUM = TopoDemoPlugin.INSTANCE.material().material("uranium")
            .lang("Uranium", "铀")
            .data(INGOT.enabled()).data(PULVERIZABLE.enabled()).data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD, NETHER))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4)).data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(238)).data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x61C853))
            .data(BuiltinProcessors.GRINDING.activation()).data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material COPPER_SULFATE = TopoDemoPlugin.INSTANCE.material().material("copper_sulfate")
            .lang("Copper Sulfate", "硫酸铜")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(160))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x3996D2)).build();
    public static final Material NICKEL_SULFATE = TopoDemoPlugin.INSTANCE.material().material("nickel_sulfate")
            .lang("Nickel Sulfate", "硫酸镍")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(155))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x62C276)).build();
    public static final Material COPPER_NICKEL_SULFATE = TopoDemoPlugin.INSTANCE.material().material("copper_nickel_sulfate")
            .lang("Copper-Nickel Sulfate", "铜镍硫酸盐")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(315))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x4FAE9D)).build();
    public static final Material COPPER_NICKEL_HYDROXIDE = TopoDemoPlugin.INSTANCE.material().material("copper_nickel_hydroxide")
            .lang("Copper-Nickel Hydroxide", "铜镍氢氧化物")
            .data(PULVERIZABLE.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(157))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x6BCF91)).build();
    public static final Material COPPER_NICKEL_OXIDE = TopoDemoPlugin.INSTANCE.material().material("copper_nickel_oxide")
            .lang("Copper-Nickel Oxide", "铜镍氧化物")
            .data(PULVERIZABLE.enabled()).data(BuiltinMaterialDataTypes.SOLUTION.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(139))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x38464A)).build();
    public static final Material LUMINITE = TopoDemoPlugin.INSTANCE.material().material("luminite")
            .lang("Luminite", "辉光铜镍晶")
            .data(GEM.gem(FLAWLESS)).data(BuiltinMaterialDataTypes.SOLUTION.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(256))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x57F2C7)).build();
    public static final Material SODIUM_SULFATE = TopoDemoPlugin.INSTANCE.material().material("sodium_sulfate")
            .lang("Sodium Sulfate", "硫酸钠")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(142))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xDCE8F5)).build();
    public static final Material SPENT_CRYSTAL_LIQUOR = TopoDemoPlugin.INSTANCE.material().material("spent_crystal_liquor")
            .lang("Spent Crystal Liquor", "废晶余液")
            .data(BuiltinMaterialDataTypes.SOLUTION.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(120))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x5B6F83)).build();
    public static final Material WASTE_GAS = TopoDemoPlugin.INSTANCE.material().material("waste_gas")
            .lang("Waste Gas", "废气")
            .data(BuiltinMaterialDataTypes.GAS.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(30))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x77776E)).build();
    public static final Material STEAM = TopoDemoPlugin.INSTANCE.material().material("steam")
            .lang("Steam", "水蒸气")
            .data(BuiltinMaterialDataTypes.GAS.enabled()).data(BuiltinMaterialDataTypes.MASS.mass(18))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xE8F4FF)).build();

    public static final Material COPPER = TopoDemoPlugin.INSTANCE.material().material("copper")
            .lang("Copper", "铜")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4))
            .data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(64))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xB87333))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.item(Items.COPPER_INGOT)))
            .form(BuiltinMaterialForms.NUGGET, options -> options.overrideItem(ExternalTargets.item(Items.COPPER_NUGGET)))
            .form(BuiltinMaterialForms.RAW, options -> options.overrideItem(ExternalTargets.item(Items.RAW_COPPER)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.COPPER_BLOCK)))
            .build();

    public static final Material TIN = TopoDemoPlugin.INSTANCE.material().material("tin")
            .lang("Tin", "锡")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(ORE_HOST.host(OVERWORLD, NETHER))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4))
            .data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(119))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD5DFDF))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material BRONZE = TopoDemoPlugin.INSTANCE.material().material("bronze")
            .lang("Bronze", "青铜")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(BuiltinMaterialDataTypes.MASS.mass(78))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xC58A42))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material IRON = TopoDemoPlugin.INSTANCE.material().material("iron")
            .lang("Iron", "铁")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4))
            .data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(56))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD8D8D8))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.item(Items.IRON_INGOT)))
            .form(BuiltinMaterialForms.NUGGET, options -> options.overrideItem(ExternalTargets.item(Items.IRON_NUGGET)))
            .form(BuiltinMaterialForms.RAW, options -> options.overrideItem(ExternalTargets.item(Items.RAW_IRON)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.IRON_BLOCK)))
            .build();

    public static final Material GOLD = TopoDemoPlugin.INSTANCE.material().material("gold")
            .lang("Gold", "金")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.RAW, 1, 4))
            .data(ORE_PRODUCT.product(RAW))
            .data(BuiltinMaterialDataTypes.MASS.mass(197))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xFFD54A))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.item(Items.GOLD_INGOT)))
            .form(BuiltinMaterialForms.NUGGET, options -> options.overrideItem(ExternalTargets.item(Items.GOLD_NUGGET)))
            .form(BuiltinMaterialForms.RAW, options -> options.overrideItem(ExternalTargets.item(Items.RAW_GOLD)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.GOLD_BLOCK)))
            .build();

    public static final Material STEEL = TopoDemoPlugin.INSTANCE.material().material("steel")
            .lang("Steel", "钢")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(BuiltinMaterialDataTypes.MASS.mass(56))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x89949D))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .build();

    public static final Material NETHERITE = TopoDemoPlugin.INSTANCE.material().material("netherite")
            .lang("Netherite", "下界合金")
            .data(INGOT.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(TOOL.tool(2, 200))
            .data(ORE_HOST.host(NETHER))
            .data(ORE_DROP.drop(BuiltinMaterialForms.SCRAP, 1, 1))
            .data(ORE_PRODUCT.product(SCRAP))
            .data(BuiltinMaterialDataTypes.MASS.mass(200))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x52494B))
            .data(BuiltinProcessors.GRINDING.activation())
            .data(BuiltinProcessors.FINE_GRINDING.activation())
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.item(Items.NETHERITE_INGOT)))
            .form(BuiltinMaterialForms.SCRAP, options -> options.overrideItem(ExternalTargets.item(Items.NETHERITE_SCRAP)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.NETHERITE_BLOCK)))
            .build();

    public static final Material COAL = TopoDemoPlugin.INSTANCE.material().material("coal")
            .lang("Coal", "煤炭")
            .data(FUEL.fuel(1600, 1000))
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.FUEL, 1, 1))
            .data(BuiltinMaterialDataTypes.MASS.mass(12))
            .data(BuiltinProcessors.FUEL_GRINDING.activation())
            .form(BuiltinMaterialForms.FUEL, options -> options.overrideItem(ExternalTargets.item(Items.COAL)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.COAL_BLOCK)))
            .build();

    public static final Material CHARCOAL = TopoDemoPlugin.INSTANCE.material().material("charcoal")
            .lang("Charcoal", "木炭")
            .data(FUEL.fuel(1600, 1000))
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(12))
            .data(BuiltinProcessors.FUEL_GRINDING.activation())
            .form(BuiltinMaterialForms.FUEL, options -> options.overrideItem(ExternalTargets.item(Items.CHARCOAL)))
            .build();

    public static final Material REDSTONE = TopoDemoPlugin.INSTANCE.material().material("redstone")
            .lang("Redstone", "红石")
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.DUST, 4, 5))
            .data(BuiltinMaterialDataTypes.MASS.mass(32))
            .form(BuiltinMaterialForms.DUST, options -> options.overrideItem(ExternalTargets.item(Items.REDSTONE)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.REDSTONE_BLOCK)))
            .build();

    public static final Material DIAMOND = TopoDemoPlugin.INSTANCE.material().material("diamond")
            .lang("Diamond", "钻石")
            .data(GEM.allQualities())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.GEM, 1, 1))
            .data(BuiltinMaterialDataTypes.MASS.mass(12))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.item(Items.DIAMOND)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.DIAMOND_BLOCK)))
            .build();

    public static final Material LAPIS = TopoDemoPlugin.INSTANCE.material().material("lapis")
            .lang("Lapis Lazuli", "青金石")
            .data(GEM.allQualities())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.GEM, 4, 9))
            .data(BuiltinMaterialDataTypes.MASS.mass(56))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.item(Items.LAPIS_LAZULI)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.LAPIS_BLOCK)))
            .build();

    public static final Material EMERALD = TopoDemoPlugin.INSTANCE.material().material("emerald")
            .lang("Emerald", "绿宝石")
            .data(GEM.allQualities())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(OVERWORLD))
            .data(ORE_DROP.drop(BuiltinMaterialForms.GEM, 1, 1))
            .data(BuiltinMaterialDataTypes.MASS.mass(64))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.item(Items.EMERALD)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.EMERALD_BLOCK)))
            .build();

    public static final Material QUARTZ = TopoDemoPlugin.INSTANCE.material().material("quartz")
            .lang("Quartz", "石英")
            .data(GEM.allQualities())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(ORE_HOST.host(NETHER))
            .data(ORE_DROP.drop(BuiltinMaterialForms.GEM, 1, 1))
            .data(BuiltinMaterialDataTypes.MASS.mass(60))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.item(Items.QUARTZ)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.QUARTZ_BLOCK)))
            .build();

    public static final Material AMETHYST = TopoDemoPlugin.INSTANCE.material().material("amethyst")
            .lang("Amethyst", "紫水晶")
            .data(SHARD.enabled())
            .data(PULVERIZABLE.enabled())
            .data(BULK.enabled())
            .data(BuiltinMaterialDataTypes.MASS.mass(60))
            .data(BuiltinProcessors.SHARD_GRINDING.activation())
            .form(BuiltinMaterialForms.SHARD, options -> options.overrideItem(ExternalTargets.item(Items.AMETHYST_SHARD)))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.block(Blocks.AMETHYST_BLOCK)))
            .build();

    private BuiltinMaterials() {}

    public static void init() {}
}
