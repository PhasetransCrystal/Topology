package net.ptcrys.topodemo.data.equipment;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.equipment.common.PipeSurveyorItem;
import net.ptcrys.topodemo.api.equipment.common.StructureToolItem;
import net.ptcrys.topodemo.data.BuiltinCreativeTabs;

import net.minecraft.world.item.Item;

import java.util.Map;

public final class BuiltinTools {

    public static final ItemEntry<Item> PIPE_SURVEYOR = TopoDemo.REGISTRY.<Item>item("pipe_surveyor", properties -> new PipeSurveyorItem(properties, 32))
            .properties(properties -> properties.stacksTo(1))
            .lang(Map.of("en_us", "Pipe Surveyor", "zh_cn", "管网勘测仪"))
            .modelTexture(TextureRef.mc("item/spyglass"))
            .addTab(BuiltinCreativeTabs.EQUIPMENT.getKey()).register();

    public static final ItemEntry<Item> STRUCTURE_TOOL = TopoDemo.REGISTRY.<Item>item("structure_tool", StructureToolItem::new)
            .properties(properties -> properties.stacksTo(1))
            .lang(Map.of("en_us", "Multiblock Structure Tool", "zh_cn", "多方块结构工具"))
            .modelTexture(TextureRef.mc("item/blaze_rod"))
            .addTab(BuiltinCreativeTabs.EQUIPMENT.getKey()).register();

    private BuiltinTools() {}

    public static void init() {}
}
