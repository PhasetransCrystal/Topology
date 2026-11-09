package net.ptcrys.topodemo.data;

import net.ptcrys.topo.api.api.builtin.*;
import net.ptcrys.topo.api.api.plugin.TopoPlugin;
import net.ptcrys.topodemo.TopoDemo;
import net.ptcrys.topodemo.data.equipment.BuiltinEquipment;
import net.ptcrys.topodemo.data.equipment.BuiltinToolPanels;
import net.ptcrys.topodemo.data.equipment.BuiltinTools;
import net.ptcrys.topodemo.data.machine.BuiltinAirCollectors;
import net.ptcrys.topodemo.data.machine.BuiltinChemicalMachines;
import net.ptcrys.topodemo.data.machine.BuiltinElectricFurnaces;
import net.ptcrys.topodemo.data.machine.BuiltinEnergyCompressors;
import net.ptcrys.topodemo.data.machine.BuiltinEnergyHatches;
import net.ptcrys.topodemo.data.machine.BuiltinFineGrinders;
import net.ptcrys.topodemo.data.machine.BuiltinFuelMachines;
import net.ptcrys.topodemo.data.machine.BuiltinMachines;
import net.ptcrys.topodemo.data.machine.BuiltinResistiveHeaters;
import net.ptcrys.topodemo.data.machine.BuiltinStorageHatches;
import net.ptcrys.topodemo.data.machine.BuiltinTopoMeMachines;
import net.ptcrys.topodemo.data.machine.common.component.resource.FluidResourcePortMetadata;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemPort;
import net.ptcrys.topodemo.data.machine.common.component.resource.ItemResourcePortMetadata;
import net.ptcrys.topodemo.data.machine.common.component.resource.ScalarResourcePortMetadata;
import net.ptcrys.topodemo.data.machine.multiblock.BuiltinPartRoles;
import net.ptcrys.topodemo.data.machine.multiblock.BuiltinTopoPropertyDisplays;
import net.ptcrys.topodemo.data.material.BuiltinMaterialFollowUps;
import net.ptcrys.topodemo.data.material.BuiltinMaterials;
import net.ptcrys.topodemo.data.material.BuiltinProcessors;
import net.ptcrys.topodemo.data.ore.BuiltinEnvironments;
import net.ptcrys.topodemo.data.ore.BuiltinVeinDisplays;
import net.ptcrys.topodemo.data.ore.BuiltinVeins;
import net.ptcrys.topodemo.data.pipe.BuiltinPipes;
import net.ptcrys.topodemo.data.pipe.BuiltinTieredPipes;
import net.ptcrys.topodemo.data.recipe.*;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoAirProductionLineRecipes;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoElectrolyteProductionLineRecipes;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoEnergyProductionLineRecipes;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoHeatProductionLineRecipes;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoLuminiteProductionLineRecipes;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoProductionLines;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoSodiumHydroxideProductionLineRecipes;
import net.ptcrys.topodemo.data.recipe.productionline.BuiltinTopoSulfuricAcidProductionLineRecipes;

/** Declares this plugin's contributions; the host owns every lifecycle boundary. */
public final class TopoDemoPlugin extends TopoPlugin {

    public static final TopoDemoPlugin INSTANCE = new TopoDemoPlugin();

    private TopoDemoPlugin() {
        super(TopoDemo.REGISTRY);
    }

    @Override
    public void registerRecipeFoundation(RecipeDomainRegistration recipe) {
        BuiltinTopoResourceIntegrations.init();
    }

    @Override
    public void registerMachineFoundation(MachineDomainRegistration machine) {
        ItemPort.Metadata.init();
        ItemResourcePortMetadata.init();
        FluidResourcePortMetadata.init();
        ScalarResourcePortMetadata.init();
        BuiltinTopoPropertyDisplays.init();
        BuiltinPartRoles.init();
    }

    @Override
    public void registerRecipeTypes(RecipeDomainRegistration recipe) {
        BuiltinTopoProductionLines.init();
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
        BuiltinEnergyHatches.init();
        BuiltinStorageHatches.init();
        BuiltinFuelMachines.init();
        BuiltinAirCollectors.init();
        BuiltinEnergyCompressors.init();
        BuiltinChemicalMachines.init();
        BuiltinFineGrinders.init();
        BuiltinElectricFurnaces.init();
        BuiltinResistiveHeaters.init();
        BuiltinMachines.init();
        BuiltinTopoMeMachines.init();
    }

    @Override
    public void registerMachineFollowUps(MachineDomainRegistration machine) {
        BuiltinPipes.init();
        BuiltinTieredPipes.init();
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
        ore.featureDatagen();
    }

    @Override
    public void registerRecipes(RecipeDomainRegistration recipe) {
        BuiltinRecipes.init();
        BuiltinTopoHeatProductionLineRecipes.init();
        BuiltinTopoAirProductionLineRecipes.init();
        BuiltinTopoEnergyProductionLineRecipes.init();
        BuiltinTopoSulfuricAcidProductionLineRecipes.init();
        BuiltinTopoSodiumHydroxideProductionLineRecipes.init();
        BuiltinTopoElectrolyteProductionLineRecipes.init();
        BuiltinTopoLuminiteProductionLineRecipes.init();
    }

    @Override
    public void registerRuntime(RuntimeDomainRegistration runtime) {
        BuiltinToolPanels.register(runtime);
    }
}
