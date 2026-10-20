package net.ptcrys.topodemo.api;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.api.ore.BuiltinOreTypes;
import net.ptcrys.topodemo.api.recipe.BuiltinResources;

/** Declares this plugin's contributions; the host owns every lifecycle boundary. */
public final class TopoDemoApiPlugin extends TopoPlugin {

    public static final TopoDemoApiPlugin INSTANCE = new TopoDemoApiPlugin();

    private TopoDemoApiPlugin() {
        super(TopoDemo.REGISTRY);
    }

    @Override
    public void registerRecipeFoundation(RecipeDomainRegistration recipe) {
        BuiltinResources.init();
    }

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        BuiltinMaterialDataTypes.init();
        BuiltinMaterialForms.init();
    }

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        BuiltinMachineTypes.init();
    }

    @Override
    public void registerOreFoundation(OreDomainRegistration ore) {
        BuiltinOreTypes.init();
    }
}
