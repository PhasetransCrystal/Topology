package net.ptcrys.topo.integration.jei;

import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.multiblock.MultiblockControllerMetadata;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.resources.Identifier;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import java.util.List;

/** 框架统一收集所有内容插件声明的多方块机器，内容模组无需重复注册结构展示。 */
@JeiPlugin
public final class TopoMultiblockJeiPlugin implements IModPlugin {

    private List<MachineDefinition> controllers = List.of();

    @Override
    public Identifier getPluginUid() {
        return IdHelper.topo("multiblock");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        controllers = Machines.registered().stream()
                .filter(machine -> !machine.metadata(MultiblockControllerMetadata.TYPE).isEmpty()).toList();
        if (!controllers.isEmpty()) registration.addRecipeCategories(
                new MultiblockJeiCategory(controllers.getFirst(), registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (!controllers.isEmpty()) registration.addRecipes(MultiblockJeiCategory.TYPE, controllers);
    }
}
