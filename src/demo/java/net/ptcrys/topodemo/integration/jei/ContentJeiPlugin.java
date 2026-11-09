package net.ptcrys.topodemo.integration.jei;

import net.ptcrys.topo.api.api.plugin.PluginIds;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

import net.minecraft.resources.Identifier;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;

/** Demo 提供自己的配方内容转换；通用分类及运行时桥由 Topo 统一注册。 */
@JeiPlugin
public final class ContentJeiPlugin implements IModPlugin {

    @Override
    public Identifier getPluginUid() {
        return PluginIds.id(TopoDemoPlugin.INSTANCE, "content");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        DemoAeRecipeConverters.register();
    }
}
