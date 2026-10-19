package net.ptcrys.topodemo.data;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.EquipmentDomainRegistration;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;

import lombok.Getter;
import lombok.experimental.Accessors;

/** Composition root only. Domain declarations live in the Demo* catalogs. */
@Getter
@Accessors(fluent = true)
public final class TopoDemoPlugin implements TopoPlugin {

    public static TopoDemoPlugin INSTANCE = new TopoDemoPlugin();

    private TopoDemoPlugin() {}

    private final MaterialDomainRegistration material = MaterialDomainRegistration.of(this);
    private final EquipmentDomainRegistration equipment = EquipmentDomainRegistration.of(this);
    private final RecipeDomainRegistration recipe = RecipeDomainRegistration.of(this);
    private final MachineDomainRegistration machine = MachineDomainRegistration.of(this);
    private final OreDomainRegistration ore = OreDomainRegistration.of(this);
    private final LangDomainRegistration lang = LangDomainRegistration.of(this);

    @Override
    public String modId() {
        return TopoDemo.MOD_ID;
    }

    @Override
    public RegistryCore registry() {
        return TopoDemo.REGISTRY;
    }
}
