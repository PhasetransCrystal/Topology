package net.ptcrys.topo.datav2.material.common.process;

import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.apiv2.recipe.OIRecipe;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.datav2.material.BuiltinOIFormDataTypes;
import net.ptcrys.topo.datav2.material.common.ItemAdditiveData;
import net.ptcrys.topo.datav2.material.common.ItemAdditiveDataType;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.common.FluidRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ItemRecipeCapability;
import net.ptcrys.topo.datav2.recipe.common.ScalarRecipeCapability;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Table-driven machine conversions for one recipe type: each step turns counted input forms into
 * counted output forms over a duration, drawing energy and/or heat every tick. Steps must not
 * create material when both sides declare AMOUNT (ingot/nugget/block conversion); process-only forms
 * without AMOUNT skip that unit-balance check.
 *
 * <p>
 * Steps may also consume auxiliary fluids (e.g. cutting coolant), and a processor may declare an
 * {@link ItemAdditiveDataType}: materials that carry that data get the additive appended as an
 * extra input to every step (e.g. a sintering flux for alloys). Auxiliary inputs are consumables,
 * not material mass, so they stay outside the unit balance.
 *
 * <p>
 * A step may require a die ({@link StepBuilder#die}): a catalyst input that must be present in
 * the machine but is never consumed. Dies disambiguate steps that share input kinds — the recipe
 * conflict scan treats them as ordinary acceptance keys.
 */
public final class FormConversionProcessor extends MaterialPostProcessor {

    private final OIRecipeType<OIRecipe> recipeType;
    private final long energyPerTick;
    private final long heatPerTick;
    private final ItemAdditiveDataType additive;
    private final List<Step> steps;

    private FormConversionProcessor(Identifier id, OIRecipeType<OIRecipe> recipeType,
                                    long energyPerTick, long heatPerTick, ItemAdditiveDataType additive, List<Step> steps) {
        super(id, stepForms(steps));
        this.recipeType = recipeType;
        this.energyPerTick = energyPerTick;
        this.heatPerTick = heatPerTick;
        this.additive = additive;
        this.steps = List.copyOf(steps);
    }

    public static Builder builder(Identifier id) {
        return new Builder(id);
    }

    @Override
    public void process(Material material) {
        if (notDeclaredOn(material)) {
            return;
        }
        ItemRecipeCapability items = BuiltinOIResourceIntegrations.ITEM.recipeCapability();
        FluidRecipeCapability fluids = BuiltinOIResourceIntegrations.FLUID.recipeCapability();
        ScalarRecipeCapability energy = energyPerTick > 0 ? BuiltinOIResourceIntegrations.ENERGY.recipeCapability() : null;
        ScalarRecipeCapability heat = heatPerTick > 0 ? BuiltinOIResourceIntegrations.HEAT.recipeCapability() : null;
        Optional<ItemAdditiveData> materialAdditive = additive == null ? Optional.empty() : material.strategy().data(additive);
        String materialPath = material.id().getPath();
        for (Step step : steps) {
            var recipe = recipeType.recipe(recipeId(materialPath, step));
            for (Stack input : step.inputs) {
                recipe.input(items.in(material, input.form, input.count));
            }
            for (FluidUse fluid : step.fluids) {
                recipe.input(fluids.in(fluid.fluid, fluid.amount));
            }
            if (step.die != null) {
                recipe.input(items.unconsumedInput(step.die));
            }
            materialAdditive.ifPresent(use -> recipe.input(items.in(use.item(), use.count())));
            for (Stack output : step.outputs) {
                recipe.output(items.out(material, output.form, output.count));
            }
            if (energy != null) {
                recipe.tickInput(energy.in(energyPerTick));
            }
            if (heat != null) {
                recipe.tickInput(heat.in(heatPerTick));
            }
            recipe.duration(step.duration).save();
        }
    }

    private String recipeId(String materialPath, Step step) {
        StringBuilder id = new StringBuilder(itemName(materialPath, step.outputs.get(0).form));
        id.append("_from");
        for (Stack input : step.inputs) {
            id.append('_').append(itemName(materialPath, input.form));
        }
        return id.toString();
    }

    private static String itemName(String materialPath, MaterialForm form) {
        return String.format(form.strategy().registryPath(), materialPath);
    }

    private static MaterialForm[] stepForms(List<Step> steps) {
        LinkedHashSet<MaterialForm> forms = new LinkedHashSet<>();
        for (Step step : steps) {
            for (Stack input : step.inputs) {
                forms.add(input.form);
            }
            for (Stack output : step.outputs) {
                forms.add(output.form);
            }
        }
        return forms.toArray(MaterialForm[]::new);
    }

    private record Stack(MaterialForm form, int count) {}

    private record FluidUse(Fluid fluid, int amount) {}

    private record Step(
                        List<Stack> inputs,
                        List<FluidUse> fluids,
                        @Nullable Supplier<? extends ItemLike> die,
                        List<Stack> outputs,
                        int duration) {}

    public static final class Builder {

        private final Identifier id;
        private OIRecipeType<OIRecipe> recipeType;
        private long energyPerTick;
        private long heatPerTick;
        private ItemAdditiveDataType additive;
        private final List<Step> steps = new ArrayList<>();

        private Builder(Identifier id) {
            this.id = id;
        }

        public Builder recipeType(OIRecipeType<OIRecipe> recipeType) {
            this.recipeType = recipeType;
            return this;
        }

        /** Energy drawn every tick while a step runs; 0 means the machine draws no energy. */
        public Builder energyPerTick(long energyPerTick) {
            this.energyPerTick = energyPerTick;
            return this;
        }

        /** Heat drawn every tick while a step runs; 0 means the machine draws no heat. */
        public Builder heatPerTick(long heatPerTick) {
            this.heatPerTick = heatPerTick;
            return this;
        }

        /**
         * Per-material additive: a material that declares this data gets the additive item appended
         * as an extra input to every step of this conversion.
         */
        public Builder additive(ItemAdditiveDataType additive) {
            this.additive = additive;
            return this;
        }

        public Builder step(Consumer<StepBuilder> step) {
            StepBuilder stepBuilder = new StepBuilder();
            step.accept(stepBuilder);
            steps.add(stepBuilder.build(id));
            return this;
        }

        public FormConversionProcessor build() {
            Objects.requireNonNull(recipeType, () -> "conversion " + id + " requires a recipeType");
            if (energyPerTick < 0 || heatPerTick < 0) {
                throw new IllegalArgumentException("conversion " + id + " has a negative scalar rate");
            }
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("conversion " + id + " declares no steps");
            }
            return new FormConversionProcessor(id, recipeType, energyPerTick, heatPerTick, additive, steps);
        }
    }

    public static final class StepBuilder {

        private final List<Stack> inputs = new ArrayList<>();
        private final List<FluidUse> fluids = new ArrayList<>();
        private final List<Stack> outputs = new ArrayList<>();
        private @Nullable Supplier<? extends ItemLike> die;
        private int duration;

        private StepBuilder() {}

        /** 本步骤要求的模具（催化剂输入：在场不消耗）；同输入族的步骤靠它互相判别。 */
        public StepBuilder die(Supplier<? extends ItemLike> die) {
            this.die = Objects.requireNonNull(die, "die");
            return this;
        }

        public StepBuilder in(MaterialForm form, int count) {
            inputs.add(stack(form, count));
            return this;
        }

        /** Auxiliary fluid consumed by this step (e.g. cutting coolant), outside the mass balance. */
        public StepBuilder inFluid(Fluid fluid, int amount) {
            Objects.requireNonNull(fluid, "fluid");
            if (amount <= 0) {
                throw new IllegalArgumentException("fluid amount must be positive: " + amount);
            }
            fluids.add(new FluidUse(fluid, amount));
            return this;
        }

        public StepBuilder out(MaterialForm form, int count) {
            outputs.add(stack(form, count));
            return this;
        }

        /** Step duration in ticks. */
        public StepBuilder time(int ticks) {
            this.duration = ticks;
            return this;
        }

        private static Stack stack(MaterialForm form, int count) {
            Objects.requireNonNull(form, "form");
            if (count <= 0) {
                throw new IllegalArgumentException("stack count must be positive: " + count);
            }
            return new Stack(form, count);
        }

        private Step build(Identifier id) {
            if (inputs.isEmpty() || outputs.isEmpty()) {
                throw new IllegalArgumentException("conversion " + id + " step needs inputs and outputs");
            }
            if (duration <= 0) {
                throw new IllegalArgumentException("conversion " + id + " step needs a positive duration");
            }
            int inputUnits = units(inputs);
            int outputUnits = units(outputs);
            if (inputUnits >= 0 && outputUnits >= 0 && outputUnits > inputUnits) {
                throw new IllegalArgumentException(
                        "conversion " + id + " step creates material: inputs " + inputUnits + " units < outputs " + outputUnits + " units");
            }
            return new Step(List.copyOf(inputs), List.copyOf(fluids), die, List.copyOf(outputs), duration);
        }

        private static int units(List<Stack> stacks) {
            int total = 0;
            for (Stack stack : stacks) {
                var amount = stack.form.strategy().data(BuiltinOIFormDataTypes.AMOUNT);
                if (amount.isEmpty()) {
                    return -1;
                }
                total = Math.addExact(total,
                        amount.get() * stack.count);
            }
            return total;
        }
    }
}
