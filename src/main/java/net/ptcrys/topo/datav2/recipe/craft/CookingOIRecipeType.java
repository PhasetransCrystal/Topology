package net.ptcrys.topo.datav2.recipe.craft;

import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;
import net.ptcrys.topo.apiv2.recipe.ImportedRecipeAdapter;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.apiv2.recipe.content.OIItemInput;
import net.ptcrys.topo.apiv2.recipe.content.OIItemOutput;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;

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
 * Vanilla-facing cooking as a real {@link OIRecipeType}.
 *
 * <p>
 * Owns both directions for one cooking table:
 * <ul>
 * <li><b>Write</b>: {@link #recipe(String, Supplier)} → OI mirror + export foreign JSON</li>
 * <li><b>Read into machines</b>: {@link #poweredImport(OIRecipeType)} projects the foreign
 * {@link #foreignRecipeType()} table (vanilla + other mods + our exports) into a powered
 * machine recipe type — no separate adapter class</li>
 * </ul>
 *
 * <p>
 * Machine energy defaults (total 4000 ENERGY / op; ignores foreign cookingTime):
 * smelting 20/t × 200t; blasting 40/t × 100t; smoking/campfire same as smelting until tuned.
 */
public final class CookingOIRecipeType extends OIRecipeType<OIRecipe> {

    public static final int SMELTING_DURATION_TICKS = 200;
    public static final long SMELTING_ENERGY_PER_TICK = 20L;
    public static final int BLASTING_DURATION_TICKS = SMELTING_DURATION_TICKS / 2;
    public static final long BLASTING_ENERGY_PER_TICK = SMELTING_ENERGY_PER_TICK * 2;

    private final CookingRecipeBuilder.Kind kind;
    private final String kindFolder;

    public CookingOIRecipeType(Identifier id, CookingRecipeBuilder.Kind kind, String kindFolder) {
        super(id, OIRecipe::new);
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
     * Projection used by machine types: foreign cooking recipe → powered {@link OIRecipe} minted
     * on the <em>machine</em> type via {@code createRecipe}.
     *
     * <pre>{@code
     * ELECTRIC_FURNACE.importRecipesFrom(SMELTING::poweredImport, SMELTING.foreignRecipeType());
     * }</pre>
     */
    public ImportedRecipeAdapter<OIRecipe> poweredImport(OIRecipeType<OIRecipe> machineType) {
        Objects.requireNonNull(machineType, "machineType");
        int duration = machineDurationTicks();
        long energyPerTick = machineEnergyPerTick();
        return (sourceId, recipe) -> adaptCooking(machineType, recipe, duration, energyPerTick);
    }

    private static @Nullable OIRecipe adaptCooking(
                                                   OIRecipeType<OIRecipe> machineType,
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
            OIItemInput input = itemInputFrom(cooking.input());
            if (input == null) {
                return null;
            }
            ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
            ScalarRecipeCapability energy = BuiltinOIResourceIntegrations.ENERGY.recipeCapability();
            return machineType.createRecipe(
                    new OIRecipe.InputEntry<?>[] { new OIRecipe.InputEntry<>(items, List.of(input)) },
                    new OIRecipe.OutputEntry<?>[] {
                            new OIRecipe.OutputEntry<>(items, List.of(OIItemOutput.of(result)))
                    },
                    new OIRecipe.InputEntry<?>[] {
                            new OIRecipe.InputEntry<>(energy, List.of(energyPerTick))
                    },
                    OIRecipe.EMPTY_OUTPUTS,
                    durationTicks,
                    List.of());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable OIItemInput itemInputFrom(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        OIItemInput input = OIItemInput.fromIngredient(ingredient, 1);
        if (input instanceof OIItemInput.AnyOf anyOf && anyOf.templates().isEmpty()) {
            return null;
        }
        return input;
    }

    private static RecipeDomainRegistration official() {
        return OfficialOIPlugin.INSTANCE.recipe();
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
    public OIRecipe.Builder<OIRecipe> recipe(String recipeName) {
        throw new UnsupportedOperationException(
                id() + " is vanilla-facing; use recipe(path, result, …) instead of capability builder");
    }
}
