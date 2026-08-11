package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.OIRecipeTypes;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class OIJeiCategoryRegistry {

    private static @Nullable List<RecipeCategorySpec> cached;

    private OIJeiCategoryRegistry() {}

    public static @NonNull List<RecipeCategorySpec> recipeCategories() {
        List<RecipeCategorySpec> result = cached;
        if (result == null) {
            result = build();
            cached = result;
        }
        return result;
    }

    public static void addRecipesTo(
                                    IRecipeRegistration registration,
                                    RecipeCategorySpec category,
                                    @Nullable MinecraftServer server,
                                    @Nullable Level level) {
        registration.addRecipes(category.jeiRecipeType(), new ArrayList<>(category.recipes(server, level)));
    }

    public static void addCatalystsTo(
                                      IRecipeCatalystRegistration registration,
                                      RecipeCategorySpec category) {
        if (category.catalysts().isEmpty()) {
            return;
        }
        registration.addCraftingStation(category.jeiRecipeType(), category.catalysts().toArray(new ItemLike[0]));
    }

    private static @NonNull List<RecipeCategorySpec> build() {
        Map<OIRecipeType<?>, List<ItemLike>> stations = buildStationIndex();
        List<RecipeCategorySpec> categories = new ArrayList<>();
        for (OIRecipeType<?> recipeType : OIRecipeTypes.registered()) {
            // Vanilla-facing types export to foreign JEI categories; skip empty OI stone shells.
            if (recipeType.isVanillaFacing()) {
                continue;
            }
            List<ItemLike> catalysts = stations.getOrDefault(recipeType, List.of());
            // 类别标题 = 配方类型显示名（不是机器名：同一机器可挂多种类型）。
            // 图标 = 第一个催化剂机器；无机器时回退石头。
            ItemStack iconStack = catalysts.isEmpty() ? new ItemStack(Items.STONE) : new ItemStack(catalysts.getFirst());
            Component title = recipeType.displayName();
            categories.add(new RecipeCategorySpec(
                    recipeType,
                    IRecipeType.create(recipeType.id(), OIRecipe.class),
                    iconStack,
                    title,
                    catalysts));
        }
        return List.copyOf(categories);
    }

    private static Map<OIRecipeType<?>, List<ItemLike>> buildStationIndex() {
        Map<OIRecipeType<?>, List<ItemLike>> result = new IdentityHashMap<>();
        for (MachineDefinition definition : Machines.registered()) {
            List<OIRecipeType<?>> recipeTypes = recipeTypesOf(definition);
            if (recipeTypes.isEmpty()) {
                continue;
            }
            ItemLike block = definition.registeredBlock().get();
            for (OIRecipeType<?> recipeType : recipeTypes) {
                result.computeIfAbsent(recipeType, unused -> new ArrayList<>()).add(block);
            }
        }
        return result;
    }

    private static List<OIRecipeType<?>> recipeTypesOf(MachineDefinition definition) {
        return definition.recipeTypes();
    }

    public record RecipeCategorySpec(
                                     OIRecipeType<?> recipeType,
                                     IRecipeType<OIRecipe> jeiRecipeType,
                                     ItemStack iconStack,
                                     Component title,
                                     List<ItemLike> catalysts) {

        public RecipeCategorySpec {
            catalysts = List.copyOf(catalysts);
        }

        public OIRecipeCategory buildCategory(IGuiHelper guiHelper) {
            return new OIRecipeCategory(recipeType, jeiRecipeType, iconStack, title, guiHelper);
        }

        public Collection<OIRecipe> recipes(@Nullable MinecraftServer server, @Nullable Level level) {
            return collectRecipes(server, level, recipeType);
        }

        private static <R extends OIRecipe> Collection<OIRecipe> collectRecipes(
                                                                                @Nullable MinecraftServer server,
                                                                                @Nullable Level level,
                                                                                OIRecipeType<R> recipeType) {
            return new ArrayList<>(recipeType.displayRecipes(server, level));
        }
    }
}
