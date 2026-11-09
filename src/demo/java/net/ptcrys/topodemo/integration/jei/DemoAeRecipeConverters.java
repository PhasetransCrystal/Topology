package net.ptcrys.topodemo.integration.jei;

import net.ptcrys.topo.api.recipe.content.TopoFluidIngredient;
import net.ptcrys.topo.api.recipe.content.TopoItemInput;
import net.ptcrys.topo.api.recipe.content.TopoItemOutput;
import net.ptcrys.topo.integration.jei.ae2.TopoRecipeToAe2StackConverter;
import net.ptcrys.topo.integration.jei.ae2.TopoRecipeToAe2StackConverter.ContentConverter;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Demo 资源家族的 AE 样板表示，按具体配方能力注册，不影响其他模组的资源。 */
public final class DemoAeRecipeConverters {

    private DemoAeRecipeConverters() {}

    public static void register() {
        TopoRecipeToAe2StackConverter.register(BuiltinTopoResourceIntegrations.ITEM.recipeCapability(),
                new ContentConverter<TopoItemInput.Resource, TopoItemOutput>() {

                    @Override
                    public List<GenericStack> inputs(TopoItemInput.Resource content) {
                        return content.displayStacks().stream().filter(stack -> !stack.isEmpty()).limit(64)
                                .map(stack -> new GenericStack(AEItemKey.of(stack), content.count())).toList();
                    }

                    @Override
                    public GenericStack output(TopoItemOutput content) {
                        return new GenericStack(AEItemKey.of(content.resource()), content.count());
                    }
                });
        TopoRecipeToAe2StackConverter.register(BuiltinTopoResourceIntegrations.FLUID.recipeCapability(),
                new ContentConverter<TopoFluidIngredient, TopoFluidIngredient>() {

                    @Override
                    public List<GenericStack> inputs(TopoFluidIngredient content) {
                        return List.of(output(content));
                    }

                    @Override
                    public GenericStack output(TopoFluidIngredient content) {
                        return new GenericStack(AEFluidKey.of(content.resource()), content.amount());
                    }
                });
        ContentConverter<Long, Long> scalar = new ContentConverter<>() {

            @Override
            public List<GenericStack> inputs(Long content) {
                return List.of();
            }

            @Override
            public @Nullable GenericStack output(Long content) {
                return null;
            }
        };
        // AE 加工样板无法表示 Demo 的标量消耗；由实现方明确选择不编码。
        TopoRecipeToAe2StackConverter.register(BuiltinTopoResourceIntegrations.ENERGY.recipeCapability(), scalar);
        TopoRecipeToAe2StackConverter.register(BuiltinTopoResourceIntegrations.ADVANCED_ENERGY.recipeCapability(), scalar);
        TopoRecipeToAe2StackConverter.register(BuiltinTopoResourceIntegrations.HEAT.recipeCapability(), scalar);
    }
}
