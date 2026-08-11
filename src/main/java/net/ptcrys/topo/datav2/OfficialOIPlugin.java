package net.ptcrys.topo.datav2;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.apiv2.plugin.EquipmentDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.LangDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.MaterialDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.OIPlugin;
import net.ptcrys.topo.apiv2.plugin.OreDomainRegistration;
import net.ptcrys.topo.apiv2.plugin.RecipeDomainRegistration;
import net.ptcrys.topo.data.pipe.BuiltinOIPipeDistributionStrategies;
import net.ptcrys.topo.data.pipe.BuiltinOIPipeLang;
import net.ptcrys.topo.data.pipe.BuiltinOIPipes;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.equipment.BuiltinOIEquipment;
import net.ptcrys.topo.datav2.equipment.BuiltinOIEquipmentLang;
import net.ptcrys.topo.datav2.machine.BuiltinOIControllerMachines;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineCraftingRecipes;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineFeedbackLang;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineRenderTypes;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachineUiLang;
import net.ptcrys.topo.datav2.machine.BuiltinOIMachines;
import net.ptcrys.topo.datav2.machine.BuiltinOIMeMachines;
import net.ptcrys.topo.datav2.machine.BuiltinOIPartMachines;
import net.ptcrys.topo.datav2.machine.common.component.resource.FluidResourcePortMetadata;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePortMetadata;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOICasingBlocks;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOIPartRoles;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOIPropertyDisplays;
import net.ptcrys.topo.datav2.material.BuiltinOIFormDataTypes;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialDataTypes;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialFormLang;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialForms;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterialPostProcessors;
import net.ptcrys.topo.datav2.material.BuiltinOIMaterials;
import net.ptcrys.topo.datav2.material.BuiltinOIProcessDies;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreEnvironments;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreLang;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreModes;
import net.ptcrys.topo.datav2.ore.BuiltinOIOrePolicies;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreShapes;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreVeinDisplays;
import net.ptcrys.topo.datav2.ore.BuiltinOIOreVeins;
import net.ptcrys.topo.datav2.ore.bindings.VanillaOreFeatureBridge;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLineRecipes;
import net.ptcrys.topo.datav2.recipe.productionline.BuiltinOIProductionLines;
import net.ptcrys.topo.gametest.OICreativeMachineGameTestFixtures;
import net.ptcrys.topo.gametest.OIMachineDestroyedGameTestFixtures;
import net.ptcrys.topo.gametest.OIOreGameTestFixtures;
import net.ptcrys.topo.gametest.OIPipeGameTestFixtures;
import net.ptcrys.topo.gametest.OIScalarGameTestFixtures;
import net.ptcrys.topo.gametest.OISideIoGameTestFixtures;
import net.ptcrys.topo.gametest.OITickHotPathGameTestFixtures;

/**
 * Official content plugin. Owns domain registration APIs with fixed namespace
 * {@link Topology#MODID} and shared {@link Topology#REGISTRY}. Product tables
 * register only through {@link #material()} / {@link #equipment()} / {@link #recipe()} /
 * {@link #machine()} / {@link #ore()} / {@link #lang()}.
 */
public final class OfficialOIPlugin implements OIPlugin {

    public static final OfficialOIPlugin INSTANCE = new OfficialOIPlugin();

    private final MaterialDomainRegistration materials = MaterialDomainRegistration.of(this);
    private final EquipmentDomainRegistration equipments = EquipmentDomainRegistration.of(this);
    private final RecipeDomainRegistration recipes = RecipeDomainRegistration.of(this);
    private final MachineDomainRegistration machines = MachineDomainRegistration.of(this);
    private final OreDomainRegistration ores = OreDomainRegistration.of(this);
    private final LangDomainRegistration langs = LangDomainRegistration.of(this);

    private OfficialOIPlugin() {}

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

    // ── recipe ───────────────────────────────────────────────────────────────

    @Override
    public void registerRecipeFoundation(RecipeDomainRegistration recipe) {
        // Pairs machine resource types with recipe capabilities (ITEM/FLUID/ENERGY/…).
        BuiltinOIResourceIntegrations.init();
    }

    @Override
    public void registerRecipeTypes(RecipeDomainRegistration recipe) {
        BuiltinOIProductionLines.init();
        BuiltinOIRecipeTypes.init();
        if (OIScalarGameTestFixtures.enabled()) {
            OIScalarGameTestFixtures.initRecipeTypes();
        }
    }

    @Override
    public void registerRecipes(RecipeDomainRegistration recipe) {
        BuiltinOIProductionLineRecipes.init();
        if (OIScalarGameTestFixtures.enabled()) {
            OIScalarGameTestFixtures.initRecipes();
        }
    }

    // ── material ─────────────────────────────────────────────────────────────

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        BuiltinOIMaterialDataTypes.init();
        BuiltinOIFormDataTypes.init();
        BuiltinOICreativeTabs.init();
        BuiltinOIMaterialForms.init();
        BuiltinOIMaterialFormLang.init();
        BuiltinOIProcessDies.init();
        BuiltinOIMaterialPostProcessors.init();
    }

    @Override
    public void registerMaterials(MaterialDomainRegistration material) {
        BuiltinOIMaterials.init();
    }

    @Override
    public void registerMaterialCreativeTabs(MaterialDomainRegistration material) {
        BuiltinOICreativeTabs.init();
    }

    @Override
    public void registerMaterialFollowUps(MaterialDomainRegistration material) {
        // Production-line recipes moved to registerRecipes (recipe domain).
    }

    // ── equipment / tools ────────────────────────────────────────────────────

    @Override
    public void registerEquipmentKinds(EquipmentDomainRegistration equipment) {
        BuiltinOIEquipment.init();
        BuiltinOIEquipmentLang.init();
    }

    // ── machine ──────────────────────────────────────────────────────────────

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        BuiltinOICreativeTabs.init();
        // Resource integrations already run in registerRecipeFoundation; re-touch is no-op.
        BuiltinOIResourceIntegrations.init();
        BuiltinOIMachineRenderTypes.init();
        BuiltinOIPartRoles.init();
        BuiltinOIPropertyDisplays.init();
        ItemResourcePortMetadata.init();
        FluidResourcePortMetadata.init();
        ScalarResourcePortMetadata.init();
    }

    @Override
    public void registerMachines(MachineDomainRegistration machine) {
        // Catalog order: casings → parts → controllers → ME → single-block → gametest fixtures.
        BuiltinOICasingBlocks.init();
        BuiltinOIPartMachines.init();
        BuiltinOIControllerMachines.init();
        BuiltinOIMeMachines.init();
        BuiltinOIMachines.init();
        if (OIScalarGameTestFixtures.enabled()) {
            OIScalarGameTestFixtures.initMachines();
            OISideIoGameTestFixtures.initMachines();
            OIMachineDestroyedGameTestFixtures.initMachines();
            OIPipeGameTestFixtures.initMachines();
            OITickHotPathGameTestFixtures.initMachines();
            OICreativeMachineGameTestFixtures.initMachines();
        }
    }

    @Override
    public void registerMachineFollowUps(MachineDomainRegistration machine) {
        // Machine workbench + process recipes after catalogs freeze (see code-style §3.11).
        BuiltinOIMachineCraftingRecipes.initRecipes();
        // Pipes remain product tables on the official plugin until a dedicated pipe domain exists.
        BuiltinOIPipeDistributionStrategies.init();
        BuiltinOIPipes.init();
        BuiltinOIPipeLang.init();
    }

    // ── ore ──────────────────────────────────────────────────────────────────

    @Override
    public void registerOreFoundation(OreDomainRegistration ore) {
        BuiltinOIOreLang.init();
        BuiltinOIOreShapes.init();
        BuiltinOIOreModes.init();
        BuiltinOIOrePolicies.init();
    }

    @Override
    public void registerOreDisplays(OreDomainRegistration ore) {
        BuiltinOIOreVeinDisplays.init();
    }

    @Override
    public void registerOreVeins(OreDomainRegistration ore) {
        BuiltinOIOreEnvironments.init();
        BuiltinOIOreVeins.init();
        if (OIOreGameTestFixtures.enabled()) {
            OIOreGameTestFixtures.initVeins();
        }
    }

    @Override
    public void registerOreDatagen(OreDomainRegistration ore) {
        VanillaOreFeatureBridge.registerDatagen(ore.registry());
    }

    // ── lang (product catalogs; API keys live on OfficialOIAPIPlugin) ─────────

    @Override
    public void registerLang(LangDomainRegistration lang) {
        BuiltinOIPipeLang.init();
        BuiltinOIMachineUiLang.init();
        BuiltinOIMachineFeedbackLang.init();
        BuiltinOIEquipmentLang.init();
        BuiltinOIMaterialFormLang.init();
        BuiltinOIOreLang.init();
    }
}
