package net.ptcrys.topodemo.api.material;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.form.ItemForm;
import net.ptcrys.topodemo.api.material.common.form.OreForm;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;

import net.minecraft.world.level.block.Blocks;

public final class BuiltinMaterialForms {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MaterialForm INGOT = MATERIAL.form("ingot")
            .lang("Ingot", "锭")
            .strategy(ItemForm.builder().registryPath("%s_ingot").displayName("%s Ingot", "%s锭")
                    .texture(TextureRef.mc("item/iron_ingot")).creativeTab(() -> BuiltinCreativeTabs.KEY.getKey()).build())
            .build();

    public static final MaterialForm DUST = MATERIAL.form("dust")
            .lang("Dust", "粉")
            .strategy(ItemForm.builder().registryPath("%s_dust").displayName("%s Dust", "%s粉")
                    .texture(TextureRef.mc("item/gunpowder")).creativeTab(() -> BuiltinCreativeTabs.KEY.getKey()).build())
            .build();

    public static final MaterialForm ORE = MATERIAL.form("ore").lang("Ore", "矿石").strategy(OreForm.builder().registryPath("%s_ore").displayName("%s Ore", "%s矿石")
            .baseBlock(() -> Blocks.COPPER_ORE).texture(TextureRef.mc("block/copper_ore"))
            .creativeTab(() -> BuiltinCreativeTabs.KEY.getKey()).build()).build();
    public static final MaterialForm DEEPSLATE_ORE = MATERIAL.form("deepslate_ore").lang("Deepslate Ore", "深层矿石").strategy(OreForm.builder().registryPath("deepslate_%s_ore").displayName("Deepslate %s Ore", "深层%s矿石")
            .baseBlock(() -> Blocks.DEEPSLATE_COPPER_ORE).texture(TextureRef.mc("block/deepslate_copper_ore"))
            .creativeTab(() -> BuiltinCreativeTabs.KEY.getKey()).build()).build();

    private BuiltinMaterialForms() {}

    public static void init() {}
}
