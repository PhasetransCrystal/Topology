package net.ptcrys.topodemo.data.ore;

import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.ore.*;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;

public final class BuiltinEnvironments {

    private static final LangDomainRegistration LANG = TopoDemoPlugin.INSTANCE.lang();
    private static final OreDimensionRule OVERWORLD = new OreDimensionRule(Level.OVERWORLD, BiomeTags.IS_OVERWORLD,
            LANG.key("ore", "dimension.overworld", "Overworld", "主世界"));
    public static final OreEnvironment OVERWORLD_ROCK = OreEnvironments.of(OVERWORLD,
            new OreHostRule(BlockTags.STONE_ORE_REPLACEABLES, BuiltinMaterialForms.ORE,
                    LANG.key("ore", "host.stone", "Stone", "石头")),
            new OreHostRule(BlockTags.DEEPSLATE_ORE_REPLACEABLES, BuiltinMaterialForms.DEEPSLATE_ORE,
                    LANG.key("ore", "host.deepslate", "Deepslate", "深板岩")));

    private BuiltinEnvironments() {}

    public static void init() {}
}
