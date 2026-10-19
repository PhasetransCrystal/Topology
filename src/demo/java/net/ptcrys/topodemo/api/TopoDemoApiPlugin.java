package net.ptcrys.topodemo.api;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialForms;

import lombok.Getter;
import lombok.experimental.Accessors;

/** Registers reusable building blocks; concrete materials belong to the data plugin. */
@Getter
@Accessors(fluent = true)
public final class TopoDemoApiPlugin implements TopoPlugin {

    public static final TopoDemoApiPlugin INSTANCE = new TopoDemoApiPlugin();

    private final MaterialDomainRegistration material = MaterialDomainRegistration.of(this);
    private final EquipmentDomainRegistration equipment = EquipmentDomainRegistration.of(this);
    private final RecipeDomainRegistration recipe = RecipeDomainRegistration.of(this);
    private final MachineDomainRegistration machine = MachineDomainRegistration.of(this);
    private final OreDomainRegistration ore = OreDomainRegistration.of(this);
    private final LangDomainRegistration lang = LangDomainRegistration.of(this);

    private TopoDemoApiPlugin() {}

    @Override
    public String modId() {
        return TopoDemo.MOD_ID;
    }

    @Override
    public RegistryCore registry() {
        return TopoDemo.REGISTRY;
    }

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        BuiltinDemoMaterialDataTypes.init();
        BuiltinDemoMaterialForms.init();
    }
}
