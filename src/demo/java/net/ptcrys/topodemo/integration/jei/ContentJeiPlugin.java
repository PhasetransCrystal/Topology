package net.ptcrys.topodemo.integration.jei;

import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.ui.recipe.XeiRecipeLookup;
import net.ptcrys.topo.api.ore.OreVein;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topodemo.data.ContentLang;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.machine.BuiltinMachines;
import net.ptcrys.topodemo.data.ore.BuiltinVeins;
import net.ptcrys.topodemo.data.recipe.BuiltinRecipeTypes;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.ArrayList;
import java.util.List;

/** Optional client integration; gameplay registration remains entirely in the Topo plugins. */
@JeiPlugin
public final class ContentJeiPlugin implements IModPlugin {

    private static final IRecipeType<OreVein> ORE = IRecipeType.create(
            PluginIds.id(TopoDemoPlugin.INSTANCE, "ore_deposits"), OreVein.class);
    private List<RecipeCategory> recipes = List.of();

    private record RecipeCategory(TopoRecipeType<TopoRecipe> topo, IRecipeType<TopoRecipe> jei, MachineDefinition station) {

        private RecipeCategory(TopoRecipeType<TopoRecipe> type, MachineDefinition station) {
            this(type, IRecipeType.create(type.id(), TopoRecipe.class), station);
        }
    }

    @Override
    public Identifier getPluginUid() {
        return PluginIds.id(TopoDemoPlugin.INSTANCE, "content");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        recipes = List.of(new RecipeCategory(BuiltinRecipeTypes.GRINDING, BuiltinMachines.GRINDER),
                new RecipeCategory(BuiltinRecipeTypes.ALLOYING, BuiltinMachines.ALLOYER));
        var gui = registration.getJeiHelpers().getGuiHelper();
        for (var category : recipes) {
            registration.addRecipeCategories(new ContentCategory<>(category.jei(), category.topo().displayName(),
                    new ItemStack(category.station().registeredBlock().get()), ContentRecipePanels.RECIPE_HEIGHT,
                    recipe -> ContentRecipePanels.recipe(category.topo(), recipe), gui));
        }
        registration.addRecipeCategories(new ContentCategory<>(ORE, ContentLang.VEIN_CATEGORY.getComponent(),
                new ItemStack(Items.COPPER_ORE), ContentRecipePanels.VEIN_HEIGHT, ContentRecipePanels::vein, gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        var minecraft = Minecraft.getInstance();
        for (var category : recipes) {
            var server = minecraft.getSingleplayerServer();
            // This content declares its recipes directly on both sides. Without an integrated
            // server, use those declarations rather than the server-only displayRecipes query.
            var visible = server == null ? category.topo().registeredRecipes() :
                    category.topo().displayRecipes(server, minecraft.level);
            registration.addRecipes(category.jei(), new ArrayList<>(visible));
        }
        registration.addRecipes(ORE, List.of(BuiltinVeins.COPPER, BuiltinVeins.COPPER_TIN));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (var category : recipes) registration.addCraftingStation(category.jei(), category.station().registeredBlock().get());
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        XeiRecipeLookup.install(requested -> {
            List<IRecipeType<?>> resolved = new ArrayList<>();
            for (var type : requested) {
                for (var category : recipes) {
                    if (category.topo() == type) {
                        resolved.add(category.jei());
                        break;
                    }
                }
            }
            if (resolved.isEmpty()) return false;
            runtime.getRecipesGui().showTypes(resolved);
            return true;
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        XeiRecipeLookup.uninstall();
    }
}
