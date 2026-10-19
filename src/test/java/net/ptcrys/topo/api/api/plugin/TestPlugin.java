package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.builtin.*;

/** Ordering-only fixtures need no game registry; integration fixtures supply an owning core. */
class TestPlugin implements TopoPlugin {

    private final RegistryCore core;
    private final MaterialDomainRegistration material;
    private final EquipmentDomainRegistration equipment;
    private final RecipeDomainRegistration recipe;
    private final MachineDomainRegistration machine;
    private final OreDomainRegistration ore;
    private final LangDomainRegistration lang;

    TestPlugin() {
        this(null);
    }

    TestPlugin(RegistryCore core) {
        this.core = core;
        material = core == null ? null : MaterialDomainRegistration.of(this);
        equipment = core == null ? null : EquipmentDomainRegistration.of(this);
        recipe = core == null ? null : RecipeDomainRegistration.of(this);
        machine = core == null ? null : MachineDomainRegistration.of(this);
        ore = core == null ? null : OreDomainRegistration.of(this);
        lang = core == null ? null : LangDomainRegistration.of(this);
    }

    @Override
    public String modId() {
        return core == null ? "test" : core.getModid();
    }

    @Override
    public RegistryCore registry() {
        return core;
    }

    @Override
    public MaterialDomainRegistration material() {
        return material;
    }

    @Override
    public EquipmentDomainRegistration equipment() {
        return equipment;
    }

    @Override
    public RecipeDomainRegistration recipe() {
        return recipe;
    }

    @Override
    public MachineDomainRegistration machine() {
        return machine;
    }

    @Override
    public OreDomainRegistration ore() {
        return ore;
    }

    @Override
    public LangDomainRegistration lang() {
        return lang;
    }
}
