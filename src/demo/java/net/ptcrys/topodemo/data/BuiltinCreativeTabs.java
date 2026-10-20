package net.ptcrys.topodemo.data;

import net.ptcrys.registrylib.util.entry.RegistryEntry;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public final class BuiltinCreativeTabs {

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> KEY = TopoDemo.REGISTRY.creativeTab("materials_and_machines",
            "Materials & Machines",
            Map.of("zh_cn", "材料与机器"),
            builder -> builder
                    .icon(() -> new ItemStack(MaterialHelper.requireItem(BuiltinMaterials.BRONZE, BuiltinMaterialForms.INGOT))));

    private BuiltinCreativeTabs() {}

    public static void init() {}
}
