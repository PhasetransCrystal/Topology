package net.ptcrys.topo.api.recipe;

import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.IdentifierException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RecipeNameTest {

    private final TopoRecipeType<TopoRecipe> type = new TopoRecipeType<>(
            Identifier.fromNamespaceAndPath("topo_test", "grinding"), TopoRecipe::new);

    @Test
    void pathsMayIncludeDirectoriesWithoutBindingRegistryEntries() {
        assertNotNull(type.recipe("copper_ingot_to_dust"));
        assertNotNull(type.recipe("other_mod/copper_ingot_to_dust"));
    }

    @Test
    void localPathBecomesAKeyInTheRecipeRegistry() {
        var key = type.recipeKey("materials/copper");
        assertEquals(ResourceKey.create(Registries.RECIPE, Identifier.parse("topo_test:grinding/materials/copper")), key);
        assertNotEquals(ResourceKey.create(Registries.ITEM, key.identifier()), key);
    }

    @Test
    void replacementIdentityIncludesNamespace() {
        type.markReplacesImported("copper");
        assertTrue(type.replacesImported(type.recipeKey("copper")));
        assertFalse(type.replacesImported(ResourceKey.create(Registries.RECIPE, Identifier.parse("other_mod:grinding/copper"))));
    }

    @Test
    void importedIdsPreserveNamespaceAndPathBoundaries() {
        for (String[] pair : new String[][] {
                { "a:b_c", "a_b:c" },
                { "minecraft:x", "vanilla:x" },
                { "a:b/c", "a:b_c" }
        }) {
            assertNotEquals(type.importedHolderId(source(pair[0])), type.importedHolderId(source(pair[1])));
        }
        assertEquals(source("topo_test:imports/8/grinding/a/b/c"), type.importedHolderId(source("a:b/c")));
        var nestedType = new TopoRecipeType<>(Identifier.parse("topo_test:grinding/imports/a/b"), TopoRecipe::new);
        assertNotEquals(type.importedHolderId(source("a:b/imports/c/d")), nestedType.importedHolderId(source("c:d")));
    }

    @Test
    void defaultProgressTextureBelongsToTheFramework() {
        assertEquals(IdHelper.id("textures/gui/progress_bar/arrow.png"), type.progressBarTexture());
        assertEquals(20, type.progressBarTextureWidth());
        assertEquals(20, type.progressBarTextureFrameHeight());
    }

    private static ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> source(String id) {
        return ResourceKey.create(Registries.RECIPE, Identifier.parse(id));
    }

    @Test
    void invalidNamesFailBeforeQueuingRecipesOrInvokingFactories() {
        for (String name : new String[] { "topodemo:copper_ingot_to_dust", "", " ", "Uppercase", "has space", null }) {
            assertInvalidPath(() -> type.recipe(name));
            assertInvalidPath(() -> new TopoRecipe.Builder<>(type, name));
            assertInvalidPath(() -> type.addRecipe(name, (TopoRecipe) null));
            assertInvalidPath(() -> type.addRecipe(name, (Function<HolderLookup.Provider, TopoRecipe>) registries -> {
                fail("invalid recipe factory must not run");
                return null;
            }));
        }
        assertTrue(type.registeredRecipes().isEmpty());
    }

    private static void assertInvalidPath(Executable action) {
        var error = assertThrows(RuntimeException.class, action);
        assertTrue(error instanceof IllegalArgumentException || error instanceof IdentifierException, error.toString());
    }
}
