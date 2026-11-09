package net.ptcrys.topodemo.api;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.machine.BuiltinMachineTypes;
import net.ptcrys.topodemo.api.material.BuiltinFormDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.api.ore.BuiltinOreTypes;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePortMetadata;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePortMetadata;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** Declares this plugin's contributions; the host owns every lifecycle boundary. */
public final class TopoDemoApiPlugin extends TopoPlugin {

    public static final TopoDemoApiPlugin INSTANCE = new TopoDemoApiPlugin();

    private TopoDemoApiPlugin() {
        super(TopoDemo.REGISTRY);
    }

    @Override
    public void registerRecipeFoundation(RecipeDomainRegistration recipe) {
        BuiltinTopoResourceIntegrations.init();
    }

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        BuiltinMaterialDataTypes.init();
        BuiltinFormDataTypes.init();
        BuiltinMaterialForms.init();
    }

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        ItemResourcePortMetadata.init();
        FluidResourcePortMetadata.init();
        ScalarResourcePortMetadata.init();
        BuiltinMachineTypes.init();
    }

    @Override
    public void registerOreFoundation(OreDomainRegistration ore) {
        BuiltinOreTypes.init();
    }
}
