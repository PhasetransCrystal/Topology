package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.apiv2.machine.ui.recipe.XeiRecipeLookup;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;
import net.ptcrys.topo.integration.jei.OIJeiCategoryRegistry.RecipeCategorySpec;

import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link XeiRecipeLookup} 的 JEI 插头:把机器报的 {@link OIRecipeType} 按身份映射到注册期铸造的
 * JEI {@link IRecipeType}(经 {@link OIJeiCategoryRegistry} 的 spec 缓存,二者同源),打开 JEI
 * 配方浏览器并定位到这些分类。一个都映射不到时拒绝(false)且不触碰 runtime——插座把拒绝吸收
 * 为静默无操作。由 {@code OIJeiPlugin} 在 JEI runtime 可用/不可用时安装/卸下。
 */
final class JeiRecipeLookupPlug implements XeiRecipeLookup.Opener {

    private final IJeiRuntime runtime;
    private final Supplier<List<RecipeCategorySpec>> specs;

    JeiRecipeLookupPlug(IJeiRuntime runtime, Supplier<List<RecipeCategorySpec>> specs) {
        this.runtime = runtime;
        this.specs = specs;
    }

    @Override
    public boolean open(List<OIRecipeType<?>> recipeTypes) {
        List<IRecipeType<?>> jeiTypes = resolve(recipeTypes, specs.get());
        if (jeiTypes.isEmpty()) {
            return false;
        }
        runtime.getRecipesGui().showTypes(jeiTypes);
        return true;
    }

    /** 身份匹配(注册表单例)、保持请求顺序、静默跳过没有 JEI 分类的类型。 */
    static List<IRecipeType<?>> resolve(List<OIRecipeType<?>> requested, List<RecipeCategorySpec> specs) {
        List<IRecipeType<?>> result = new ArrayList<>(requested.size());
        for (OIRecipeType<?> recipeType : requested) {
            for (RecipeCategorySpec spec : specs) {
                if (spec.recipeType() == recipeType) {
                    result.add(spec.jeiRecipeType());
                    break;
                }
            }
        }
        return result;
    }
}
