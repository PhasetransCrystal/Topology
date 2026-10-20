package net.ptcrys.topodemo.data.material;

import net.ptcrys.topo.api.api.registration.ExternalTargets;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import java.util.List;

public final class BuiltinMaterials {

    public static final Material COPPER = TopoDemoPlugin.INSTANCE.material().material("copper")
            .lang("Copper", "铜")
            .data(BuiltinMaterialDataTypes.MASS.mass(64))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xB87333))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.mcItem("copper_ingot")))
            .form(BuiltinMaterialForms.NUGGET, options -> options.overrideItem(ExternalTargets.mcItem("copper_nugget")))
            .form(BuiltinMaterialForms.RAW, options -> options.overrideItem(ExternalTargets.mcItem("raw_copper")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("copper_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("copper_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_copper_ore")))
            .build();

    public static final Material TIN = TopoDemoPlugin.INSTANCE.material().material("tin")
            .lang("Tin", "锡")
            .data(BuiltinMaterialDataTypes.MASS.mass(119))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD5DFDF))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .forms(BuiltinMaterialForms.INGOT, BuiltinMaterialForms.NUGGET, BuiltinMaterialForms.STORAGE_BLOCK)
            .forms(BuiltinMaterialForms.RAW, BuiltinMaterialForms.ORE, BuiltinMaterialForms.DEEPSLATE_ORE)
            .build();

    public static final Material BRONZE = TopoDemoPlugin.INSTANCE.material().material("bronze")
            .lang("Bronze", "青铜")
            .data(BuiltinMaterialDataTypes.MASS.mass(78))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xC58A42))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .forms(BuiltinMaterialForms.INGOT, BuiltinMaterialForms.NUGGET, BuiltinMaterialForms.STORAGE_BLOCK)
            .build();

    public static final Material IRON = TopoDemoPlugin.INSTANCE.material().material("iron")
            .lang("Iron", "铁")
            .data(BuiltinMaterialDataTypes.MASS.mass(56))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xD8D8D8))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.mcItem("iron_ingot")))
            .form(BuiltinMaterialForms.NUGGET, options -> options.overrideItem(ExternalTargets.mcItem("iron_nugget")))
            .form(BuiltinMaterialForms.RAW, options -> options.overrideItem(ExternalTargets.mcItem("raw_iron")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("iron_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("iron_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_iron_ore")))
            .build();

    public static final Material GOLD = TopoDemoPlugin.INSTANCE.material().material("gold")
            .lang("Gold", "金")
            .data(BuiltinMaterialDataTypes.MASS.mass(197))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0xFFD54A))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.mcItem("gold_ingot")))
            .form(BuiltinMaterialForms.NUGGET, options -> options.overrideItem(ExternalTargets.mcItem("gold_nugget")))
            .form(BuiltinMaterialForms.RAW, options -> options.overrideItem(ExternalTargets.mcItem("raw_gold")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("gold_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("gold_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_gold_ore")))
            .build();

    public static final Material STEEL = TopoDemoPlugin.INSTANCE.material().material("steel")
            .lang("Steel", "钢")
            .data(BuiltinMaterialDataTypes.MASS.mass(56))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x89949D))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .forms(BuiltinMaterialForms.INGOT, BuiltinMaterialForms.NUGGET, BuiltinMaterialForms.STORAGE_BLOCK)
            .build();

    public static final Material NETHERITE = TopoDemoPlugin.INSTANCE.material().material("netherite")
            .lang("Netherite", "下界合金")
            .data(BuiltinMaterialDataTypes.MASS.mass(200))
            .data(BuiltinMaterialDataTypes.PRIMARY_COLOR.rgb(0x52494B))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.DUST, BuiltinMaterialForms.PLATE, BuiltinMaterialForms.MOLTEN)
            .form(BuiltinMaterialForms.INGOT, options -> options.overrideItem(ExternalTargets.mcItem("netherite_ingot")))
            .form(BuiltinMaterialForms.SCRAP, options -> options.overrideItem(ExternalTargets.mcItem("netherite_scrap")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("netherite_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("ancient_debris")))
            .build();

    public static final Material COAL = TopoDemoPlugin.INSTANCE.material().material("coal")
            .lang("Coal", "煤炭")
            .data(BuiltinMaterialDataTypes.MASS.mass(12))
            .data(BuiltinProcessors.FUEL_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.FUEL, options -> options.overrideItem(ExternalTargets.mcItem("coal")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("coal_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("coal_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_coal_ore")))
            .build();

    public static final Material CHARCOAL = TopoDemoPlugin.INSTANCE.material().material("charcoal")
            .lang("Charcoal", "木炭")
            .data(BuiltinMaterialDataTypes.MASS.mass(12))
            .data(BuiltinProcessors.FUEL_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.FUEL, options -> options.overrideItem(ExternalTargets.mcItem("charcoal")))
            .build();

    public static final Material REDSTONE = TopoDemoPlugin.INSTANCE.material().material("redstone")
            .lang("Redstone", "红石")
            .data(BuiltinMaterialDataTypes.MASS.mass(32))
            .form(BuiltinMaterialForms.DUST, options -> options.overrideItem(ExternalTargets.mcItem("redstone")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("redstone_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("redstone_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_redstone_ore")))
            .build();

    public static final Material DIAMOND = TopoDemoPlugin.INSTANCE.material().material("diamond")
            .lang("Diamond", "钻石")
            .data(BuiltinMaterialDataTypes.MASS.mass(12))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.mcItem("diamond")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("diamond_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("diamond_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_diamond_ore")))
            .build();

    public static final Material LAPIS = TopoDemoPlugin.INSTANCE.material().material("lapis")
            .lang("Lapis Lazuli", "青金石")
            .data(BuiltinMaterialDataTypes.MASS.mass(56))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.mcItem("lapis_lazuli")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("lapis_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("lapis_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_lapis_ore")))
            .build();

    public static final Material EMERALD = TopoDemoPlugin.INSTANCE.material().material("emerald")
            .lang("Emerald", "绿宝石")
            .data(BuiltinMaterialDataTypes.MASS.mass(64))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.mcItem("emerald")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("emerald_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("emerald_ore")))
            .form(BuiltinMaterialForms.DEEPSLATE_ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("deepslate_emerald_ore")))
            .build();

    public static final Material QUARTZ = TopoDemoPlugin.INSTANCE.material().material("quartz")
            .lang("Quartz", "石英")
            .data(BuiltinMaterialDataTypes.MASS.mass(60))
            .data(BuiltinProcessors.GEM_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.GEM, options -> options.overrideItem(ExternalTargets.mcItem("quartz")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("quartz_block")))
            .form(BuiltinMaterialForms.ORE, options -> options.overrideBlock(ExternalTargets.mcBlock("nether_quartz_ore")))
            .build();

    public static final Material AMETHYST = TopoDemoPlugin.INSTANCE.material().material("amethyst")
            .lang("Amethyst", "紫水晶")
            .data(BuiltinMaterialDataTypes.MASS.mass(60))
            .data(BuiltinProcessors.SHARD_GRINDING.activation())
            .form(BuiltinMaterialForms.DUST)
            .form(BuiltinMaterialForms.SHARD, options -> options.overrideItem(ExternalTargets.mcItem("amethyst_shard")))
            .form(BuiltinMaterialForms.STORAGE_BLOCK, options -> options.overrideBlock(ExternalTargets.mcBlock("amethyst_block")))
            .build();

    /** Explicit content sets keep follow-up generation within this plugin's declarations. */
    public static final List<Material> METALS = List.of(COPPER, TIN, BRONZE, IRON, GOLD, STEEL, NETHERITE);
    public static final List<Material> ORE_PROCESSING = List.of(COPPER, TIN, IRON, GOLD,
            COAL, REDSTONE, DIAMOND, LAPIS, EMERALD, QUARTZ);

    private BuiltinMaterials() {}

    public static void init() {}
}
