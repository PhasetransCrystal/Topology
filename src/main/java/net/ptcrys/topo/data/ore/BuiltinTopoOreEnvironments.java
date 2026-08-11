package net.ptcrys.topo.data.ore;

import net.ptcrys.topo.api.ore.OreDimensionRule;
import net.ptcrys.topo.api.ore.OreEnvironment;
import net.ptcrys.topo.api.ore.OreEnvironments;
import net.ptcrys.topo.api.ore.OreHostRule;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Builtin host-rock environments. Each dimension and host rule carries its own display LangKey and
 * biome tag — no path-string switch at JEI or datagen.
 */
public final class BuiltinTopoOreEnvironments {

    private static final TagKey<Block> STONE_ORE_REPLACEABLES = vanillaBlockTag("stone_ore_replaceables");
    private static final TagKey<Block> DEEPSLATE_ORE_REPLACEABLES = vanillaBlockTag("deepslate_ore_replaceables");
    private static final TagKey<Block> BASE_STONE_NETHER = vanillaBlockTag("base_stone_nether");

    private static final OreDimensionRule OVERWORLD = new OreDimensionRule(
            Level.OVERWORLD, BiomeTags.IS_OVERWORLD, BuiltinTopoOreLang.DIM_OVERWORLD);
    private static final OreDimensionRule NETHER = new OreDimensionRule(
            Level.NETHER, BiomeTags.IS_NETHER, BuiltinTopoOreLang.DIM_NETHER);

    private static final OreHostRule STONE_HOST = new OreHostRule(
            STONE_ORE_REPLACEABLES, BuiltinTopoMaterialForms.ORE, BuiltinTopoOreLang.HOST_STONE);
    private static final OreHostRule DEEPSLATE_HOST = new OreHostRule(
            DEEPSLATE_ORE_REPLACEABLES, BuiltinTopoMaterialForms.DEEPSLATE_ORE, BuiltinTopoOreLang.HOST_DEEPSLATE);
    private static final OreHostRule NETHERRACK_HOST = new OreHostRule(
            BASE_STONE_NETHER, BuiltinTopoMaterialForms.NETHERRACK_ORE, BuiltinTopoOreLang.HOST_NETHERRACK);

    public static final OreEnvironment OVERWORLD_STONE = OreEnvironments.of(OVERWORLD, STONE_HOST);

    public static final OreEnvironment OVERWORLD_DEEPSLATE = OreEnvironments.of(OVERWORLD, DEEPSLATE_HOST);

    public static final OreEnvironment OVERWORLD_STONE_AND_DEEPSLATE = OreEnvironments.of(OVERWORLD, STONE_HOST, DEEPSLATE_HOST);

    public static final OreEnvironment NETHER_NETHERRACK = OreEnvironments.of(NETHER, NETHERRACK_HOST);

    private BuiltinTopoOreEnvironments() {}

    public static void init() {}

    private static TagKey<Block> vanillaBlockTag(String path) {
        return TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace(path));
    }
}
