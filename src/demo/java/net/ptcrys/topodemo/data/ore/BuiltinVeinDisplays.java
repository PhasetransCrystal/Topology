package net.ptcrys.topodemo.data.ore;

import net.ptcrys.topodemo.api.ore.BuiltinOreTypes;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.ore.display.OreVeinPanels;

public final class BuiltinVeinDisplays {

    public static void init() {
        var plugin = TopoDemoPlugin.INSTANCE;
        BuiltinOreDisplayLang.init();
        plugin.ore().display(BuiltinOreTypes.FEATURE.id(), OreVeinPanels.INSTANCE::build);
        plugin.ore().display(BuiltinOreTypes.GRID.id(), OreVeinPanels.INSTANCE::build);
    }

    private BuiltinVeinDisplays() {}
}
