package net.ptcrys.topo.api.ore;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OreFeatureKeysTest {

    @Test
    void biomeTagsKeepTheirNamespaceAndFullPath() {
        Identifier vein = Identifier.parse("content:copper");
        assertNotEquals(OreFeatureKeys.biomeModifier(vein, biome("a:b_c")),
                OreFeatureKeys.biomeModifier(vein, biome("a:b/c")));
        assertNotEquals(OreFeatureKeys.biomeModifier(vein, biome("a_b:c")),
                OreFeatureKeys.biomeModifier(vein, biome("a:b_c")));
    }

    @Test
    void nestedVeinPathsCannotConsumeTheBiomeNamespaceBoundary() {
        assertNotEquals(OreFeatureKeys.biomeModifier(Identifier.parse("content:x"), biome("a:b/c")),
                OreFeatureKeys.biomeModifier(Identifier.parse("content:x/a"), biome("b:c")));
    }

    @Test
    void registryAndOwnerArePartOfEveryKey() {
        Identifier vein = Identifier.parse("content:nested/copper");
        var configured = OreFeatureKeys.configured(vein);
        var placed = OreFeatureKeys.placed(vein);
        var modifier = OreFeatureKeys.biomeModifier(vein, biome("minecraft:is_overworld"));
        assertEquals(configured.identifier(), placed.identifier());
        assertNotEquals(configured, placed);
        assertTrue(configured.isFor(Registries.CONFIGURED_FEATURE));
        assertTrue(placed.isFor(Registries.PLACED_FEATURE));
        assertTrue(modifier.isFor(NeoForgeRegistries.Keys.BIOME_MODIFIERS));
        assertEquals("content", modifier.identifier().getNamespace());
    }

    private static TagKey<Biome> biome(String id) {
        return TagKey.create(Registries.BIOME, Identifier.parse(id));
    }
}
