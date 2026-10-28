package net.ptcrys.topodemo.data;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.ore.common.VanillaOreFeatureBridge;
import net.ptcrys.topodemo.data.equipment.BuiltinEquipment;
import net.ptcrys.topodemo.data.equipment.BuiltinToolPanels;
import net.ptcrys.topodemo.data.equipment.BuiltinTools;
import net.ptcrys.topodemo.data.machine.BuiltinMachines;
import net.ptcrys.topodemo.data.material.BuiltinMaterialFollowUps;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.material.BuiltinProcessors;
import net.ptcrys.topodemo.data.ore.BuiltinEnvironments;
import net.ptcrys.topodemo.data.ore.BuiltinVeinDisplays;
import net.ptcrys.topodemo.data.ore.BuiltinVeins;
import net.ptcrys.topodemo.data.pipe.BuiltinPipes;
import net.ptcrys.topodemo.data.recipe.*;

/** Declares this plugin's contributions; the host owns every lifecycle boundary. */
public final class TopoDemoPlugin extends TopoPlugin {

    public static final TopoDemoPlugin INSTANCE = new TopoDemoPlugin();

    private TopoDemoPlugin() {
        super(TopoDemo.REGISTRY);
    }

    @Override
    public void registerRecipeTypes(RecipeDomainRegistration recipe) {
        BuiltinRecipeTypes.init();
    }

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        BuiltinProcessors.init();
    }

    @Override
    public void registerMaterials(MaterialDomainRegistration material) {
        BuiltinMaterials.init();
    }

    @Override
    public void registerMaterialCreativeTabs(MaterialDomainRegistration material) {
        BuiltinCreativeTabs.init();
    }

    @Override
    public void registerMaterialFollowUps(MaterialDomainRegistration material) {
        BuiltinMaterialFollowUps.init();
    }

    @Override
    public void registerEquipmentKinds(EquipmentDomainRegistration equipment) {
        BuiltinEquipment.init();
    }

    @Override
    public void registerMachines(MachineDomainRegistration machine) {
        BuiltinMachines.init();
    }

    @Override
    public void registerMachineFollowUps(MachineDomainRegistration machine) {
        BuiltinPipes.init();
        BuiltinTools.init();
    }

    @Override
    public void registerOreFoundation(OreDomainRegistration ore) {
        BuiltinEnvironments.init();
    }

    @Override
    public void registerOreDisplays(OreDomainRegistration ore) {
        BuiltinVeinDisplays.init();
    }

    @Override
    public void registerOreVeins(OreDomainRegistration ore) {
        BuiltinVeins.init();
    }

    @Override
    public void registerOreDatagen(OreDomainRegistration ore) {
        VanillaOreFeatureBridge.registerDatagen(ore.registry());
    }

    @Override
    public void registerRecipes(RecipeDomainRegistration recipe) {
        BuiltinRecipes.init();
    }

    @Override
    public void registerLang(LangDomainRegistration lang) {
        ContentLang.init();
    }

    @Override
    public void registerRuntime(RuntimeDomainRegistration runtime) {
        BuiltinToolPanels.register(runtime);
    }
}
