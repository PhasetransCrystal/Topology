package net.ptcrys.topodemo.api.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.form.BlockForm;
import net.ptcrys.topodemo.api.material.common.form.FluidForm;
import net.ptcrys.topodemo.api.material.common.form.ItemForm;
import net.ptcrys.topodemo.api.material.common.render.TintedTemplate;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;

import net.minecraft.world.level.block.Blocks;

import static net.ptcrys.topodemo.api.material.BuiltinFormDataTypes.AMOUNT;
import static net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes.PRIMARY_COLOR;
import static net.ptcrys.topodemo.api.material.common.render.TintedTemplate.flat;
import static net.ptcrys.topodemo.api.material.common.render.TintedTemplate.opaque;

public final class BuiltinMaterialForms {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MaterialForm INGOT = MATERIAL.form("ingot")
            .lang("Ingot", "锭")
            .strategy(ItemForm.builder().registryPath("%s_ingot").displayName("%s Ingot", "%s锭").commonTag("ingots")
                    .data(AMOUNT.ingots(1))
                    .render(TintedTemplate.item(flat("item/material/ingot")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();

    public static final MaterialForm DUST = MATERIAL.form("dust")
            .lang("Dust", "粉")
            .strategy(ItemForm.builder().registryPath("%s_dust").displayName("%s Dust", "%s粉").commonTag("dusts")
                    .render(TintedTemplate.item(flat("item/material/dust")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();

    public static final MaterialForm GEM = MATERIAL.form("gem").lang("Gem", "宝石")
            .strategy(ItemForm.builder().registryPath("%s_gem").displayName("%s Gem", "%s宝石").commonTag("gems")
                    .render(TintedTemplate.item(flat("item/material/gem")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm SHARD = MATERIAL.form("shard").lang("Shard", "碎片")
            .strategy(ItemForm.builder().registryPath("%s_shard").displayName("%s Shard", "%s碎片").commonTag("gems")
                    .render(TintedTemplate.item(flat("item/material/shard")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm FUEL = MATERIAL.form("fuel").lang("Fuel", "燃料")
            .strategy(ItemForm.builder().registryPath("%s").displayName("%s", "%s").commonTag("fuels")
                    .render(TintedTemplate.item(flat("item/material/fuel")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm SCRAP = MATERIAL.form("scrap").lang("Scrap", "碎屑")
            .strategy(ItemForm.builder().registryPath("%s_scrap").displayName("%s Scrap", "%s碎屑").commonTag("scraps")
                    .render(TintedTemplate.item(flat("item/material/scrap")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();

    public static final MaterialForm ORE = MATERIAL.form("ore").lang("Ore", "矿石").strategy(BlockForm.builder().registryPath("%s_ore").displayName("%s Ore", "%s矿石").commonTag("ores")
            .initialProperties(Blocks.COPPER_ORE)
            .render(TintedTemplate.block(opaque(flat("block/material/ore_host_stone"))))
            .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build()).build();

    public static final MaterialForm DEEPSLATE_ORE = MATERIAL.form("deepslate_ore").lang("Deepslate Ore", "深层矿石").strategy(BlockForm.builder().registryPath("deepslate_%s_ore").displayName("Deepslate %s Ore", "深层%s矿石").commonTag("ores")
            .initialProperties(Blocks.DEEPSLATE_COPPER_ORE)
            .render(TintedTemplate.block(opaque(flat("block/material/ore_host_deepslate"))))
            .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build()).build();

    public static final MaterialForm NUGGET = MATERIAL.form("nugget").lang("Nugget", "粒")
            .strategy(ItemForm.builder().registryPath("%s_nugget").displayName("%s Nugget", "%s粒").commonTag("nuggets")
                    .data(AMOUNT.ingotFraction(9))
                    .render(TintedTemplate.item(flat("item/material/nugget")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm PLATE = MATERIAL.form("plate").lang("Plate", "板")
            .strategy(ItemForm.builder().registryPath("%s_plate").displayName("%s Plate", "%s板").commonTag("plates")
                    .render(TintedTemplate.item(flat("item/material/plate")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm RAW = MATERIAL.form("raw").lang("Raw Material", "粗矿")
            .strategy(ItemForm.builder().registryPath("raw_%s").displayName("Raw %s", "粗%s").commonTag("raw_materials")
                    .render(TintedTemplate.item(flat("item/material/raw")))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm STORAGE_BLOCK = MATERIAL.form("storage_block").lang("Storage Block", "储存块")
            .strategy(BlockForm.builder().registryPath("%s_block").displayName("Block of %s", "%s块").commonTag("storage_blocks")
                    .data(AMOUNT.ingots(9))
                    .initialProperties(Blocks.IRON_BLOCK)
                    .render(TintedTemplate.block(opaque(flat("block/material/block"))))
                    .creativeTab(BuiltinCreativeTabs.MATERIALS.getKey()).build())
            .build();
    public static final MaterialForm MOLTEN = MATERIAL.form("molten").lang("Molten Material", "熔融态")
            .strategy(FluidForm.builder().registryPath("molten_%s").displayName("Molten %s", "熔融%s").commonTag("molten_metals")
                    .texture("block/material/fluid")
                    .tint(PRIMARY_COLOR)
                    .properties(properties -> properties.temperature(1900).density(7500).viscosity(6000))
                    .fluidProperties(properties -> properties.tickRate(20).slopeFindDistance(3).levelDecreasePerBlock(1))
                    .creativeTab(BuiltinCreativeTabs.FLUIDS.getKey()).build())
            .build();

    private BuiltinMaterialForms() {}

    public static void init() {}
}
