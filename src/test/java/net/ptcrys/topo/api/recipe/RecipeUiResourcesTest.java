package net.ptcrys.topo.api.recipe;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

class RecipeUiResourcesTest {

    @Test
    void frameworkSpritesAreBundledWithTheDimensionsTheirConsumersUse() throws IOException {
        assertSprite("progress_bar/arrow.png", 20, 40);
        assertSprite("slot/mold_overlay.png", 16, 16);
        assertSprite("slot/fluid_overlay.png", 16, 16);
        assertSprite("recipe/scalar_conversion_fan.png", 32, 32);
    }

    private static void assertSprite(String path, int width, int height) throws IOException {
        String resource = "/assets/topo/textures/gui/" + path;
        try (var input = RecipeUiResourcesTest.class.getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            var image = ImageIO.read(input);
            assertNotNull(image, resource);
            assertEquals(width, image.getWidth(), resource);
            assertEquals(height, image.getHeight(), resource);
        }
    }
}
