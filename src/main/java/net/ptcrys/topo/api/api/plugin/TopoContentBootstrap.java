package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.api.lang.ChineseConvert;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.machine.Machines;
import net.ptcrys.topo.api.machine.component.AttachmentTypes;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRoleRegistry;
import net.ptcrys.topo.api.machine.multiblock.ui.PropertyDisplayRegistry;
import net.ptcrys.topo.api.machine.render.MachineRenderRegistry;
import net.ptcrys.topo.api.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.api.machine.ui.MachineUiIcons;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.form.FormDataRegistry;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormRegistry;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.material.process.MaterialPostProcessorRegistry;
import net.ptcrys.topo.api.ore.OreVeins;
import net.ptcrys.topo.api.ore.display.OreVeinDisplays;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.api.ore.mode.OreVeinModes;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicies;
import net.ptcrys.topo.api.ore.policy.OreConflictPolicies;
import net.ptcrys.topo.api.ore.shape.OreVeinShapes;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategies;
import net.ptcrys.topo.api.pipe.Pipes;
import net.ptcrys.topo.api.recipe.TopoRecipeTypes;
import net.ptcrys.topo.api.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.api.recipe.common.RecipePreviewPlans;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import java.util.Map;
import java.util.function.Consumer;

/** Domain declarations and their freeze fences; independent of NeoForge event dispatch. */
final class TopoContentBootstrap {

    private TopoContentBootstrap() {}

    /**
     * Recipe domain foundation: capabilities + recipe types freeze. Must run before material
     * post-processors that emit machine recipes. Hand-written production-line recipes run after
     * materials in {@link #bootstrapMaterial()}.
     */
    static void bootstrapRecipe() {
        if (TopoPlugins.view().isEmpty()) {
            throw new IllegalStateException("TopoPluginEngine: no plugins registered; register plugins in @Mod constructors");
        }
        TopoPlugins.lock();

        invokePlugins("registerRecipeFoundation", bound -> bound.plugin().registerRecipeFoundation(bound.recipe()));
        RecipeCapabilities.freeze();

        invokePlugins("registerRecipeTypes", bound -> bound.plugin().registerRecipeTypes(bound.recipe()));
        TopoRecipeTypes.freeze();
    }

    static void bootstrapMaterial() {
        invokePlugins("registerMaterialFoundation", bound -> bound.plugin().registerMaterialFoundation(bound.material()));
        MaterialDataRegistry.freeze();
        FormDataRegistry.freeze();
        MaterialFormRegistry.freeze();
        MaterialPostProcessorRegistry.freeze();

        invokePlugins("registerMaterials", bound -> bound.plugin().registerMaterials(bound.material()));
        MaterialRegistry.freeze();

        invokePlugins("registerMaterialCreativeTabs", bound -> bound.plugin().registerMaterialCreativeTabs(bound.material()));

        registerMaterialFormContent();
        runMaterialPostProcessors();

        invokePlugins("registerMaterialFollowUps", bound -> bound.plugin().registerMaterialFollowUps(bound.material()));
        // Production-line recipes and residual recipe tables after materials exist.
        invokePlugins("registerRecipes", bound -> bound.plugin().registerRecipes(bound.recipe()));
        ProductionLines.freeze();
    }

    /**
     * Loads equipment kinds (including unified tool table), freezes the kind registry, then
     * stamps one item per (kind, material) where {@code appliesTo}. Requires materials frozen and
     * form items registered.
     */
    static void bootstrapEquipment() {
        invokePlugins("registerEquipmentKinds", bound -> bound.plugin().registerEquipmentKinds(bound.equipment()));
        EquipmentRegistry.freeze();

        for (Material material : MaterialRegistry.registered()) {
            RegistryCore core = registryForMaterial(material);
            MaterialContentContext context = new MaterialContentContext(core, material);
            for (Equipment equipment : EquipmentRegistry.registered()) {
                if (equipment.strategy().appliesTo(material)) {
                    equipment.strategy().register(context, equipment);
                }
            }
        }
    }

    /**
     * Machine domain: foundation tables → machine catalogs → freeze → block/BE mint → follow-ups
     * (recipe preview plans; pipe catalogs until a pipe domain exists).
     */
    static void bootstrapMachine() {
        invokePlugins("registerMachineFoundation", bound -> bound.plugin().registerMachineFoundation(bound.machine()));
        MachineResourceTypes.freeze();
        MachineUiIcons.freeze();
        MachineRenderRegistry.freeze();
        PartRoleRegistry.freeze();
        PropertyDisplayRegistry.freeze();
        AttachmentTypes.freeze();

        invokePlugins("registerMachines", bound -> bound.plugin().registerMachines(bound.machine()));
        Machines.freeze();
        // Shared BE type lives on the host registry; each machine block uses its plugin RegistryCore.
        Machines.registerBlocksAndBlockEntity(Topology.REGISTRY);

        TopoRecipeTypes.installPreviewPlans(RecipePreviewPlans::canonical);

        invokePlugins("registerMachineFollowUps", bound -> bound.plugin().registerMachineFollowUps(bound.machine()));
        PipeDistributionStrategies.freeze();
        Pipes.freeze();
        // Plugin declarations retain their owners; legacy Pipes.register paths use the host.
        Pipes.registerBlocks(Topology.REGISTRY);
    }

    static void bootstrapOre() {
        invokePlugins("registerOreFoundation", bound -> bound.plugin().registerOreFoundation(bound.ore()));
        OreVeinShapes.freeze();
        OreVeinModes.freeze();
        OreAirExposurePolicies.freeze();
        OreConflictPolicies.freeze();

        invokePlugins("registerOreDisplays", bound -> bound.plugin().registerOreDisplays(bound.ore()));
        OreVeinDisplays.freeze();
        for (OreVeinMode mode : OreVeinModes.view()) {
            OreVeinDisplays.require(mode.id());
        }

        invokePlugins("registerOreVeins", bound -> bound.plugin().registerOreVeins(bound.ore()));
        OreVeins.freeze();

        invokePlugins("registerOreDatagen", bound -> bound.plugin().registerOreDatagen(bound.ore()));
    }

    /**
     * Collects lang keys from every plugin, freezes the table, and (datagen only) bridges into
     * RegistryLib en_us / zh_cn / zh_tw.
     */
    static void bootstrapLang() {
        invokePlugins("registerLang", bound -> bound.plugin().registerLang(bound.lang()));
        LangRegistry.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            RegistryCore core = bound.lang().registry();
            if (core.doDatagen()) {
                for (LangKey key : bound.lang().registeredKeys()) {
                    core.lang(key.key(), Map.of(
                            "en_us", key.en(), "zh_cn", key.cn(),
                            "zh_tw", ChineseConvert.s2t(key.cn())));
                }
            }
        }
    }

    private static void invokePlugins(String hook, Consumer<TopoPlugins.BoundPlugin> action) {
        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            try {
                action.accept(bound);
            } catch (RuntimeException | LinkageError error) {
                throw new IllegalStateException("TopoPluginEngine: plugin '" + bound.id() + "' failed in " + hook, error);
            }
        }
    }

    private static void registerMaterialFormContent() {
        for (Material material : MaterialRegistry.registered()) {
            RegistryCore core = registryForMaterial(material);
            MaterialContentContext context = new MaterialContentContext(core, material);
            for (MaterialForm form : material.strategy().forms()) {
                form.strategy().register(context, form);
            }
        }
    }

    private static void runMaterialPostProcessors() {
        for (Material material : MaterialRegistry.registered()) {
            for (MaterialPostProcessor processor : MaterialPostProcessorRegistry.registered()) {
                processor.validate(material);
            }
        }
        for (Material material : MaterialRegistry.registered()) {
            for (MaterialPostProcessor processor : MaterialPostProcessorRegistry.registered()) {
                processor.process(material);
            }
        }
    }

    private static RegistryCore registryForMaterial(Material material) {
        String namespace = material.id().getNamespace();
        TopoPlugins.BoundPlugin owner = TopoPlugins.findByModId(namespace);
        if (owner != null) {
            return owner.material().registry();
        }
        throw new IllegalStateException(
                "TopoPluginEngine: no plugin registered to mint content for material " + material.id());
    }
}
