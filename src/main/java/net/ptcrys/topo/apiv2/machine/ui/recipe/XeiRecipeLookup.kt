package net.ptcrys.topo.apiv2.machine.ui.recipe

import net.ptcrys.topo.apiv2.recipe.OIRecipeType

/**
 * 机器 UI 侧「查配方」插座：进度条等实况元素点击时，请求配方查看器(JEI/REI/EMI)打开给定配方
 * 类型的分类页。本类零查看器 import——具体查看器作为插头在其 runtime 可用时 [install]、
 * 不可用时 [uninstall]。
 */
object XeiRecipeLookup {
    /** 插头：把配方类型列表展示给玩家，无法展示时返回 false。 */
    fun interface Opener {
        fun open(recipeTypes: List<@JvmSuppressWildcards OIRecipeType<*>>): Boolean
    }

    @Volatile
    private var opener: Opener? = null

    @JvmStatic
    fun install(plug: Opener) {
        opener = plug
    }

    @JvmStatic
    fun uninstall() {
        opener = null
    }

    @JvmStatic
    fun isAvailable(): Boolean = opener != null

    /** 请求查看器展示 [recipeTypes]；空请求或插头缺席静默 false。 */
    @JvmStatic
    fun showRecipes(recipeTypes: List<OIRecipeType<*>>): Boolean {
        if (recipeTypes.isEmpty()) {
            return false
        }
        val plug = opener
        return plug != null && plug.open(recipeTypes)
    }
}
