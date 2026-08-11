package net.ptcrys.topo.data.recipe.craft;

import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.recipe.ImportedRecipeAdapter;
import net.ptcrys.topo.api.recipe.TopoRecipe;
import net.ptcrys.topo.api.recipe.TopoRecipeType;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.data.recipe.common.ScalarRecipeCapability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ItemLike;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Vanilla-facing cooking as a real {@link TopoRecipeType}.
 *
 * <p>
 * Owns both directions for one cooking table:
 * <ul>
 * <li><b>Write</b>: {@link #recipe(String, Supplier)} → Topo mirror + export foreign JSON</li>
 * <li><b>Read into machines</b>: {@link #poweredImport(TopoRecipeType)} projects the foreign
 * {@link #foreignRecipeType()} table (vanilla + other mods + our exports) into a powered
 * machine recipe type — no separate adapter class</li>
 * </ul>
 *
 * <p>
 * Machine energy defaults (total 4000 ENERGY / op; ignores foreign cookingTime):
 * smelting 20/t × 200t; blasting 40/t × 100t; smoking/campfire same as smelting until tuned.
 */
public final class CookingTopoRecipeType extends TopoRecipeType<TopoRecipe> {

    public static final int SMELTING_DURATION_TICKS = 200;
    public static final long SMELTING_ENERGY_PER_TICK = 20L;
    public static final int BLASTING_DURATION_TICKS = SMELTING_DURATION_TICKS / 2;
    public static final long BLASTING_ENERGY_PER_TICK = SMELTING_ENERGY_PER_TICK * 2;

    private final CookingRecipeBuilder.Kind kind;
    private final String kindFolder;

    public CookingTopoRecipeType(Identifier id, CookingRecipeBuilder.Kind kind, String kindFolder) {
        super(id, TopoRecipe::new);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.kindFolder = Objects.requireNonNull(kindFolder, "kindFolder");
        vanillaFacing();
    }

    public CookingRecipeBuilder.Kind kind() {
        return kind;
    }

    public String kindFolder() {
        return kindFolder;
    }

    /**
     * The Minecraft/foreign {@link RecipeType} this cooking type projects to/from
     * ({@code SMELTING}, {@code BLASTING}, …).
     */
    public RecipeType<?> foreignRecipeType() {
        return switch (kind) {
            case SMELTING -> RecipeType.SMELTING;
            case BLASTING -> RecipeType.BLASTING;
            case SMOKING -> RecipeType.SMOKING;
            case CAMPFIRE -> RecipeType.CAMPFIRE_COOKING;
        };
    }

    public int machineDurationTicks() {
        return switch (kind) {
            case SMELTING, SMOKING, CAMPFIRE -> SMELTING_DURATION_TICKS;
            case BLASTING -> BLASTING_DURATION_TICKS;
        };
    }

    public long machineEnergyPerTick() {
        return switch (kind) {
            case SMELTING, SMOKING, CAMPFIRE -> SMELTING_ENERGY_PER_TICK;
            case BLASTING -> BLASTING_ENERGY_PER_TICK;
        };
    }

    /**
     * Projection used by machine types: foreign cooking recipe → powered {@link TopoRecipe} minted
     * on the <em>machine</em> type via {@code createRecipe}.
     *
     * <pre>{@code
     * ELECTRIC_FURNACE.importRecipesFrom(SMELTING::poweredImport, SMELTING.foreignRecipeType());
     * }</pre>
     */
    public ImportedRecipeAdapter<TopoRecipe> poweredImport(TopoRecipeType<TopoRecipe> machineType) {
        Objects.requireNonNull(machineType, "machineType");
        int duration = machineDurationTicks();
        long energyPerTick = machineEnergyPerTick();
        return (sourceId, recipe) -> adaptCooking(machineType, recipe, duration, energyPerTick);
    }

    private static @Nullable TopoRecipe adaptCooking(
                                                     TopoRecipeType<TopoRecipe> machineType,
                                                     Recipe<?> recipe,
                                                     int durationTicks,
                                                     long energyPerTick) {
        if (!(recipe instanceof AbstractCookingRecipe cooking)) {
            return null;
        }
        try {
            ItemStack result = cooking.assemble(new SingleRecipeInput(new ItemStack(Items.AIR)));
            if (result.isEmpty()) {
                return null;
            }
            TopoItemInput input = itemInputFrom(cooking.input());
            if (input == null) {
                return null;
            }
            ItemRecipeCapability items = BuiltinTopoResourceIntegrations.ITEM.recipeCapability();
            ScalarRecipeCapability energy = BuiltinTopoResourceIntegrations.ENERGY.recipeCapability();
            return machineType.createRecipe(
                    new TopoRecipe.InputEntry<?>[] { new TopoRecipe.InputEntry<>(items, List.of(input)) },
                    new TopoRecipe.OutputEntry<?>[] {
                            new TopoRecipe.OutputEntry<>(items, List.of(TopoItemOutput.of(result)))
                    },
                    new TopoRecipe.InputEntry<?>[] {
                            new TopoRecipe.InputEntry<>(energy, List.of(energyPerTick))
                    },
                    TopoRecipe.EMPTY_OUTPUTS,
                    durationTicks,
                    List.of());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable TopoItemInput itemInputFrom(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        TopoItemInput input = TopoItemInput.fromIngredient(ingredient, 1);
        if (input instanceof TopoItemInput.AnyOf anyOf && anyOf.templates().isEmpty()) {
            return null;
        }
        return input;
    }

    private static RecipeDomainRegistration official() {
        return OfficialTopoPlugin.INSTANCE.recipe();
    }

    public CookingRecipeBuilder recipe(String recipePath, Supplier<? extends ItemLike> result) {
        return recipe(official(), recipePath, result);
    }

    public CookingRecipeBuilder recipe(
                                       String recipePath, Supplier<? extends ItemLike> result, Object input) {
        return VanillaCraftTokens.applyCookingInput(recipe(recipePath, result), input);
    }

    public CookingRecipeBuilder recipe(
                                       RecipeDomainRegistration domain,
                                       String recipePath,
                                       Supplier<? extends ItemLike> result) {
        return new CookingRecipeBuilder(domain, this, kind, kindFolder, recipePath, result, 1);
    }

    public CookingRecipeBuilder recipe(
                                       RecipeDomainRegistration domain,
                                       String recipePath,
                                       Supplier<? extends ItemLike> result,
                                       Object input) {
        return VanillaCraftTokens.applyCookingInput(recipe(domain, recipePath, result), input);
    }

    @Override
    public TopoRecipe.Builder<TopoRecipe> recipe(String recipeName) {
        throw new UnsupportedOperationException(
                id() + " is vanilla-facing; use recipe(path, result, …) instead of capability builder");
    }
}
