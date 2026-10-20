package net.ptcrys.topodemo.data.ore;

import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.api.ore.BuiltinOreTypes;
import net.ptcrys.topodemo.api.ore.common.VeinPreview;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

public final class BuiltinVeinDisplays {

    public static void init() {
        var plugin = TopoDemoPlugin.INSTANCE;
        var preview = new VeinPreview(BuiltinMaterialForms.ORE,
                plugin.lang().key("ore", "height", "Height: %s to %s", "高度：%s 至 %s"),
                plugin.lang().key("ore", "composition", "%s × %s", "%s × %s"));
        plugin.ore().display(BuiltinOreTypes.FEATURE.id(), preview);
        plugin.ore().display(BuiltinOreTypes.GRID.id(), preview);
    }

    private BuiltinVeinDisplays() {}
}
