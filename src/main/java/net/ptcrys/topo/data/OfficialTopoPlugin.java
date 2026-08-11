package net.ptcrys.topo.data;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.builtin.EquipmentDomainRegistration;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.builtin.OreDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topo.data.data.vanilla.BuiltinTopoCreativeTabs;
import net.ptcrys.topo.data.equipment.BuiltinTopoEquipment;
import net.ptcrys.topo.data.equipment.BuiltinTopoEquipmentLang;
import net.ptcrys.topo.data.machine.*;
import net.ptcrys.topo.data.machine.common.component.resource.FluidResourcePortMetadata;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topo.data.machine.common.component.resource.ScalarResourcePortMetadata;
import net.ptcrys.topo.data.machine.multiblock.BuiltinTopoCasingBlocks;
import net.ptcrys.topo.data.machine.multiblock.BuiltinTopoPartRoles;
import net.ptcrys.topo.data.machine.multiblock.BuiltinTopoPropertyDisplays;
import net.ptcrys.topo.data.material.BuiltinTopoFormDataTypes;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialDataTypes;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialFormLang;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialForms;
import net.ptcrys.topo.data.material.BuiltinTopoMaterialPostProcessors;
import net.ptcrys.topo.data.material.BuiltinTopoMaterials;
import net.ptcrys.topo.data.material.BuiltinTopoProcessDies;
import net.ptcrys.topo.data.ore.BuiltinTopoOreEnvironments;
import net.ptcrys.topo.data.ore.BuiltinTopoOreLang;
import net.ptcrys.topo.data.ore.BuiltinTopoOreModes;
import net.ptcrys.topo.data.ore.BuiltinTopoOrePolicies;
import net.ptcrys.topo.data.ore.BuiltinTopoOreShapes;
import net.ptcrys.topo.data.ore.BuiltinTopoOreVeinDisplays;
import net.ptcrys.topo.data.ore.BuiltinTopoOreVeins;
import net.ptcrys.topo.data.ore.bindings.VanillaOreFeatureBridge;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipeDistributionStrategies;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipeLang;
import net.ptcrys.topo.data.pipe.BuiltinTopoPipes;
import net.ptcrys.topo.data.recipe.BuiltinTopoRecipeTypes;
import net.ptcrys.topo.data.recipe.BuiltinTopoResourceIntegrations;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLineRecipes;
import net.ptcrys.topo.data.recipe.productionline.BuiltinTopoProductionLines;
import net.ptcrys.topo.gametest.TopoCreativeMachineGameTestFixtures;
import net.ptcrys.topo.gametest.TopoMachineDestroyedGameTestFixtures;
import net.ptcrys.topo.gametest.TopoOreGameTestFixtures;
import net.ptcrys.topo.gametest.TopoPipeGameTestFixtures;
import net.ptcrys.topo.gametest.TopoScalarGameTestFixtures;
import net.ptcrys.topo.gametest.TopoSideIoGameTestFixtures;
import net.ptcrys.topo.gametest.TopoTickHotPathGameTestFixtures;

/**
 * Official content plugin. Owns domain registration APIs with fixed namespace
 * {@link Topology#MODID} and shared {@link Topology#REGISTRY}. Product tables
 * register only through {@link #material()} / {@link #equipment()} / {@link #recipe()} /
 * {@link #machine()} / {@link #ore()} / {@link #lang()}.
 */
public final class OfficialTopoPlugin implements TopoPlugin {

    public static final OfficialTopoPlugin INSTANCE = new OfficialTopoPlugin();

    private final MaterialDomainRegistration materials = MaterialDomainRegistration.of(this);
    private final EquipmentDomainRegistration equipments = EquipmentDomainRegistration.of(this);
    private final RecipeDomainRegistration recipes = RecipeDomainRegistration.of(this);
    private final MachineDomainRegistration machines = MachineDomainRegistration.of(this);
    private final OreDomainRegistration ores = OreDomainRegistration.of(this);
    private final LangDomainRegistration langs = LangDomainRegistration.of(this);

    private OfficialTopoPlugin() {}

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
        BuiltinTopoResourceIntegrations.init();
    }

    @Override
    public void registerRecipeTypes(RecipeDomainRegistration recipe) {
        BuiltinTopoProductionLines.init();
        BuiltinTopoRecipeTypes.init();
        if (TopoScalarGameTestFixtures.enabled()) {
            TopoScalarGameTestFixtures.initRecipeTypes();
        }
    }

    @Override
    public void registerRecipes(RecipeDomainRegistration recipe) {
        BuiltinTopoProductionLineRecipes.init();
        if (TopoScalarGameTestFixtures.enabled()) {
            TopoScalarGameTestFixtures.initRecipes();
        }
    }

    // ── material ─────────────────────────────────────────────────────────────

    @Override
    public void registerMaterialFoundation(MaterialDomainRegistration material) {
        BuiltinTopoMaterialDataTypes.init();
        BuiltinTopoFormDataTypes.init();
        BuiltinTopoCreativeTabs.init();
        BuiltinTopoMaterialForms.init();
        BuiltinTopoMaterialFormLang.init();
        BuiltinTopoProcessDies.init();
        BuiltinTopoMaterialPostProcessors.init();
    }

    @Override
    public void registerMaterials(MaterialDomainRegistration material) {
        BuiltinTopoMaterials.init();
    }

    @Override
    public void registerMaterialCreativeTabs(MaterialDomainRegistration material) {
        BuiltinTopoCreativeTabs.init();
    }

    @Override
    public void registerMaterialFollowUps(MaterialDomainRegistration material) {
        // Production-line recipes moved to registerRecipes (recipe domain).
    }

    // ── equipment / tools ────────────────────────────────────────────────────

    @Override
    public void registerEquipmentKinds(EquipmentDomainRegistration equipment) {
        BuiltinTopoEquipment.init();
        BuiltinTopoEquipmentLang.init();
    }

    // ── machine ──────────────────────────────────────────────────────────────

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        BuiltinTopoCreativeTabs.init();
        // Resource integrations already run in registerRecipeFoundation; re-touch is no-op.
        BuiltinTopoResourceIntegrations.init();
        BuiltinTopoMachineRenderTypes.init();
        BuiltinTopoPartRoles.init();
        BuiltinTopoPropertyDisplays.init();
        ItemResourcePortMetadata.init();
        FluidResourcePortMetadata.init();
        ScalarResourcePortMetadata.init();
    }

    @Override
    public void registerMachines(MachineDomainRegistration machine) {
        // Catalog order: casings → parts → controllers → ME → single-block → gametest fixtures.
        BuiltinTopoCasingBlocks.init();
        BuiltinTopoPartMachines.init();
        BuiltinTopoControllerMachines.init();
        BuiltinTopoMeMachines.init();
        BuiltinTopoMachines.init();
        if (TopoScalarGameTestFixtures.enabled()) {
            TopoScalarGameTestFixtures.initMachines();
            TopoSideIoGameTestFixtures.initMachines();
            TopoMachineDestroyedGameTestFixtures.initMachines();
            TopoPipeGameTestFixtures.initMachines();
            TopoTickHotPathGameTestFixtures.initMachines();
            TopoCreativeMachineGameTestFixtures.initMachines();
        }
    }

    @Override
    public void registerMachineFollowUps(MachineDomainRegistration machine) {
        // Machine workbench + process recipes after catalogs freeze (see code-style §3.11).
        BuiltinTopoMachineCraftingRecipes.initRecipes();
        // Pipes remain product tables on the official plugin until a dedicated pipe domain exists.
        BuiltinTopoPipeDistributionStrategies.init();
        BuiltinTopoPipes.init();
        BuiltinTopoPipeLang.init();
    }

    // ── ore ──────────────────────────────────────────────────────────────────

    @Override
    public void registerOreFoundation(OreDomainRegistration ore) {
        BuiltinTopoOreLang.init();
        BuiltinTopoOreShapes.init();
        BuiltinTopoOreModes.init();
        BuiltinTopoOrePolicies.init();
    }

    @Override
    public void registerOreDisplays(OreDomainRegistration ore) {
        BuiltinTopoOreVeinDisplays.init();
    }

    @Override
    public void registerOreVeins(OreDomainRegistration ore) {
        BuiltinTopoOreEnvironments.init();
        BuiltinTopoOreVeins.init();
        if (TopoOreGameTestFixtures.enabled()) {
            TopoOreGameTestFixtures.initVeins();
        }
    }

    @Override
    public void registerOreDatagen(OreDomainRegistration ore) {
        VanillaOreFeatureBridge.registerDatagen(ore.registry());
    }

    // ── lang (product catalogs; API keys live on OfficialTopoAPIPlugin) ─────────

    @Override
    public void registerLang(LangDomainRegistration lang) {
        BuiltinTopoPipeLang.init();
        BuiltinTopoMachineUiLang.init();
        BuiltinTopoMachineFeedbackLang.init();
        BuiltinTopoEquipmentLang.init();
        BuiltinTopoMaterialFormLang.init();
        BuiltinTopoOreLang.init();
    }
}
