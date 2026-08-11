package net.ptcrys.topo.apiv2;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.lang.OIApiLang;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogicMetadata;
import net.ptcrys.topo.apiv2.machine.component.RecipeModifierDisplay;
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockControllerMetadata;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleAttachment;
import net.ptcrys.topo.apiv2.plugin.EquipmentDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.MaterialDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.OIPlugin;
import net.ptcrys.topo.apiv2.plugin.OreDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;

/**
 * Official API plugin: runtime API lang and machine attachment-type activation for code under
 * {@code net.ptcrys.topo.api} / {@code apiv2}. Shares {@link Topology#MODID} and
 * {@link Topology#REGISTRY} with {@link net.ptcrys.topo.datav2.OfficialOIPlugin}.
 *
 * <p>
 * Does not contribute product materials / equipment / machine / ore / recipe tables — those live
 * on the content plugin. Register this plugin before the content plugin.
 */
public final class OfficialOIAPIPlugin implements OIPlugin {

    public static final OfficialOIAPIPlugin INSTANCE = new OfficialOIAPIPlugin();

    private final MaterialDomainRegistration materials = MaterialDomainRegistration.of(this);
    private final EquipmentDomainRegistration equipments = EquipmentDomainRegistration.of(this);
    private final RecipeDomainRegistration recipes = RecipeDomainRegistration.of(this);
    private final MachineDomainRegistration machines = MachineDomainRegistration.of(this);
    private final OreDomainRegistration ores = OreDomainRegistration.of(this);
    private final LangDomainRegistration langs = LangDomainRegistration.of(this);

    private OfficialOIAPIPlugin() {}

    @Override
    public String modId() {
        return Topology.MODID;
    }

    @Override
    public RegistryCore registry() {
        return Topology.REGISTRY;
    }

    @Override
    public MaterialDomainRegistration material() {
        return materials;
    }

    @Override
    public EquipmentDomainRegistration equipment() {
        return equipments;
    }

    @Override
    public RecipeDomainRegistration recipe() {
        return recipes;
    }

    @Override
    public MachineDomainRegistration machine() {
        return machines;
    }

    @Override
    public OreDomainRegistration ore() {
        return ores;
    }

    @Override
    public LangDomainRegistration lang() {
        return langs;
    }

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        // Trait-mount metadata types register in static initializers; activate here in explicit
        // order so AttachmentTypes.freeze never sees a late first touch.
        RecipeLogicMetadata.init();
        RecipeModifierDisplay.init();
        MultiblockControllerMetadata.init();
        PartRoleAttachment.init();
    }

    @Override
    public void registerLang(LangDomainRegistration lang) {
        OIApiLang.init();
    }
}
