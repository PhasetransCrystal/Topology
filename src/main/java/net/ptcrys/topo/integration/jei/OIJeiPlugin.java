package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.ui.ItemGhostDrop;
import net.ptcrys.topo.apiv2.machine.ui.recipe.XeiRecipeLookup;
import net.ptcrys.topo.apiv2.ore.OreVeins;
import net.ptcrys.topo.helper.IdHelper;
import net.ptcrys.topo.integration.jei.ae2.Ae2PatternTransferRegistrar;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

@JeiPlugin
public final class OIJeiPlugin implements IModPlugin {

    @Override
    public @NonNull Identifier getPluginUid() {
        return IdHelper.oi("jei_plugin");
    }

    @Override
    public void registerCategories(@NonNull IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        for (OIJeiCategoryRegistry.RecipeCategorySpec category : OIJeiCategoryRegistry.recipeCategories()) {
            registration.addRecipeCategories(category.buildCategory(helper));
        }
        List<MachineDefinition> controllers = OIMultiblockJeiUiFactory.controllerDefinitions();
        if (!controllers.isEmpty()) {
            registration.addRecipeCategories(new OIMultiblockJeiCategory(helper, controllers));
        }
        registration.addRecipeCategories(new OIOreVeinJeiCategory(helper));
    }

    @Override
    public void registerRecipes(@NonNull IRecipeRegistration registration) {
        Minecraft minecraft = Minecraft.getInstance();
        // 导入配方（原版熔炉/高炉表）需要 RecipeManager；仅单人世界有本地 server。
        MinecraftServer server = minecraft.getSingleplayerServer();
        Level level = minecraft.level;
        if (server == null && level != null) {
            server = level.getServer();
        }
        for (OIJeiCategoryRegistry.RecipeCategorySpec category : OIJeiCategoryRegistry.recipeCategories()) {
            OIJeiCategoryRegistry.addRecipesTo(registration, category, server, level);
        }
        List<MachineDefinition> controllers = OIMultiblockJeiUiFactory.controllerDefinitions();
        if (!controllers.isEmpty()) {
            registration.addRecipes(OIMultiblockJeiCategory.TYPE, new ArrayList<>(controllers));
        }
        registration.addRecipes(OIOreVeinJeiCategory.TYPE, new ArrayList<>(OreVeins.view()));
    }

    @Override
    public void registerRecipeCatalysts(@NonNull IRecipeCatalystRegistration registration) {
        for (OIJeiCategoryRegistry.RecipeCategorySpec category : OIJeiCategoryRegistry.recipeCategories()) {
            OIJeiCategoryRegistry.addCatalystsTo(registration, category);
        }
        for (MachineDefinition controller : OIMultiblockJeiUiFactory.controllerDefinitions()) {
            registration.addCraftingStation(OIMultiblockJeiCategory.TYPE, controller.registeredBlock().get());
        }
    }

    @Override
    public void registerRecipeTransferHandlers(@NonNull IRecipeTransferRegistration registration) {
        Ae2PatternTransferRegistrar.register(registration);
    }

    @Override
    public void onRuntimeAvailable(@NonNull IJeiRuntime jeiRuntime) {
        XeiRecipeLookup.install(new JeiRecipeLookupPlug(jeiRuntime, OIJeiCategoryRegistry::recipeCategories));
        ItemGhostDrop.install(new ItemGhostDropJeiBridge());
    }

    @Override
    public void onRuntimeUnavailable() {
        XeiRecipeLookup.uninstall();
        ItemGhostDrop.uninstall();
    }
}
