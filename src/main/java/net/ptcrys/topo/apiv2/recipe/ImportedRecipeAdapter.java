package net.ptcrys.topo.apiv2.recipe;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.Recipe;

import org.jspecify.annotations.Nullable;

/**
 * 将其它 {@link net.minecraft.world.item.crafting.RecipeType} 中的配方投影为本类型的 {@link OIRecipe}。
 *
 * <p>
 * 对应 GTCEu 的 proxy 转换（{@code toGTrecipe}），命名按 OI 习惯：被导入、再适配，而不是
 * 在本类型 datagen 里复制一份 JSON。
 *
 * @param <R> 本类型配方
 */
@FunctionalInterface
public interface ImportedRecipeAdapter<R extends OIRecipe> {

    /**
     * @param sourceId 来源配方 id（通常为原版/其它 mod 的 recipe key）
     * @param recipe   来源配方实例
     * @return 适配后的 OI 配方；无法/不需导入时返回 {@code null}
     */
    @Nullable
    R adapt(Identifier sourceId, Recipe<?> recipe);
}
