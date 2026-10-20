package net.ptcrys.topodemo.data;

import net.ptcrys.registrylib.util.entry.RegistryEntry;
import net.ptcrys.topo.helper.MaterialHelper;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.equipment.BuiltinTools;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

public final class BuiltinCreativeTabs {

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MATERIALS = creativeTab("materials",
            "Materials",
            "材料",
            builder -> builder
                    .icon(() -> new ItemStack(MaterialHelper.requireItem(BuiltinMaterials.BRONZE, BuiltinMaterialForms.INGOT))));

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> FLUIDS = creativeTab("fluids",
            "Fluids",
            "流体",
            builder -> builder
                    .icon(() -> new ItemStack(MaterialHelper.requireFluid(BuiltinMaterials.BRONZE, BuiltinMaterialForms.MOLTEN).getBucket())));

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MACHINES = creativeTab("machines",
            "Machines & Pipes",
            "机器与管道",
            builder -> builder.icon(() -> new ItemStack(Items.BLAST_FURNACE)));

    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> EQUIPMENT = creativeTab("equipment",
            "Equipment & Tools",
            "装备与工具",
            builder -> builder.icon(() -> BuiltinTools.PIPE_SURVEYOR.asStack()));

    private BuiltinCreativeTabs() {}

    private static RegistryEntry<CreativeModeTab, CreativeModeTab> creativeTab(
                                                                               String path, String en, String cn, Consumer<CreativeModeTab.Builder> config) {
        var entry = TopoDemo.REGISTRY.creativeTab(path, en, config);
        TopoDemoPlugin.INSTANCE.lang().resource(entry.identifier(), "itemGroup", en, cn);
        return entry;
    }

    public static void init() {}
}
