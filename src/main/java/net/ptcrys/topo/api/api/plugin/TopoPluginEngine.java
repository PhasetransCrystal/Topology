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
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;
import net.ptcrys.topo.data.recipe.common.RecipePreviewPlans;

import java.util.Map;

/**
 * Skeleton (engine) for plugin content load. Phases replace domain {@code *Bootstrap} chains:
 * plugins only declare; this class freezes tables, mints form/equipment content, and runs
 * post-processors.
 *
 * <p>
 * Call {@link #prepare()} once after all mods have had a chance to {@link TopoPlugins#register}
 * — typically on the first {@code RegisterEvent} at {@code HIGHEST} so RegistryLib ({@code LOW})
 * still sees queued entries. Official and third-party share that single explicit path.
 */
public final class TopoPluginEngine {

    private static boolean prepared;
    private static boolean recipeBootstrapped;
    private static boolean materialBootstrapped;
    private static boolean equipmentBootstrapped;
    private static boolean machineBootstrapped;
    private static boolean oreBootstrapped;
    private static boolean langBootstrapped;

    private TopoPluginEngine() {}

    /**
     * Locks in already-{@link TopoPlugins#register registered} plugins then runs recipe foundation →
     * material → equipment. Safe to call multiple times; only the first call does work. Content /
     * machine / ore / lang phases are orchestrated by the host after this returns.
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        if (TopoPlugins.view().isEmpty()) {
            throw new IllegalStateException(
                    "TopoPluginEngine: no plugins registered — call TopoPlugins.register from @Mod constructors");
        }
        bootstrapRecipe();
        bootstrapMaterial();
        bootstrapEquipment();
    }

    /**
     * Recipe domain foundation: capabilities + recipe types freeze. Must run before material
     * post-processors that emit machine recipes. Hand-written production-line recipes run after
     * materials in {@link #bootstrapMaterial()}.
     */
    public static synchronized void bootstrapRecipe() {
        if (recipeBootstrapped) {
            return;
        }
        TopoPlugins.lock();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerRecipeFoundation(bound.recipe());
        }
        RecipeCapabilities.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerRecipeTypes(bound.recipe());
        }
        TopoRecipeTypes.freeze();

        recipeBootstrapped = true;
    }

    public static synchronized void bootstrapMaterial() {
        if (materialBootstrapped) {
            return;
        }
        if (!recipeBootstrapped) {
            throw new IllegalStateException("TopoPluginEngine: bootstrapRecipe() must run before material");
        }
        TopoPlugins.lock();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMaterialFoundation(bound.material());
        }
        MaterialDataRegistry.freeze();
        FormDataRegistry.freeze();
        MaterialFormRegistry.freeze();
        MaterialPostProcessorRegistry.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMaterials(bound.material());
        }
        MaterialRegistry.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMaterialCreativeTabs(bound.material());
        }

        registerMaterialFormContent();
        runMaterialPostProcessors();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMaterialFollowUps(bound.material());
        }
        // Production-line recipes and residual recipe tables after materials exist.
        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerRecipes(bound.recipe());
        }
        ProductionLines.freeze();

        materialBootstrapped = true;
    }

    /**
     * Loads equipment kinds (including unified tool table), freezes the kind registry, then
     * stamps one item per (kind, material) where {@code appliesTo}. Requires materials frozen and
     * form items registered.
     */
    public static synchronized void bootstrapEquipment() {
        if (equipmentBootstrapped) {
            return;
        }
        if (!materialBootstrapped) {
            throw new IllegalStateException("TopoPluginEngine: bootstrapMaterial() must run before equipment");
        }
        TopoPlugins.lock();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerEquipmentKinds(bound.equipment());
        }
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

        equipmentBootstrapped = true;
    }

    /**
     * Machine domain: foundation tables → machine catalogs → freeze → block/BE mint → follow-ups
     * (recipe preview plans; pipe catalogs until a pipe domain exists).
     */
    public static synchronized void bootstrapMachine() {
        if (machineBootstrapped) {
            return;
        }
        if (!recipeBootstrapped) {
            throw new IllegalStateException("TopoPluginEngine: bootstrapRecipe() must run before machine");
        }
        TopoPlugins.lock();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMachineFoundation(bound.machine());
        }
        MachineResourceTypes.freeze();
        MachineUiIcons.freeze();
        MachineRenderRegistry.freeze();
        PartRoleRegistry.freeze();
        PropertyDisplayRegistry.freeze();
        AttachmentTypes.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMachines(bound.machine());
        }
        Machines.freeze();
        // Shared BE type lives on the host registry; each machine block uses its plugin RegistryCore.
        Machines.registerBlocksAndBlockEntity(Topology.REGISTRY);

        TopoRecipeTypes.installPreviewPlans(RecipePreviewPlans::canonical);

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerMachineFollowUps(bound.machine());
        }
        PipeDistributionStrategies.freeze();
        Pipes.freeze();
        // Pipe blocks use official RegistryCore until a pipe domain owns multi-mod mint.
        Pipes.registerBlocks(Topology.REGISTRY);

        machineBootstrapped = true;
    }

    public static synchronized void bootstrapOre() {
        if (oreBootstrapped) {
            return;
        }
        TopoPlugins.lock();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerOreFoundation(bound.ore());
        }
        OreVeinShapes.freeze();
        OreVeinModes.freeze();
        OreAirExposurePolicies.freeze();
        OreConflictPolicies.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerOreDisplays(bound.ore());
        }
        OreVeinDisplays.freeze();
        for (OreVeinMode mode : OreVeinModes.view()) {
            OreVeinDisplays.require(mode.id());
        }

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerOreVeins(bound.ore());
        }
        OreVeins.freeze();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerOreDatagen(bound.ore());
        }

        oreBootstrapped = true;
    }

    /**
     * Collects lang keys from every plugin, freezes the table, and (datagen only) bridges into
     * RegistryLib en_us / zh_cn / zh_tw.
     */
    public static synchronized void bootstrapLang() {
        if (langBootstrapped) {
            return;
        }
        TopoPlugins.lock();

        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            bound.plugin().registerLang(bound.lang());
        }
        LangRegistry.freeze();

        if (Topology.REGISTRY.doDatagen()) {
            for (LangKey key : LangRegistry.registered()) {
                Topology.REGISTRY.lang(
                        key.key(),
                        Map.of(
                                "en_us", key.en(),
                                "zh_cn", key.cn(),
                                "zh_tw", ChineseConvert.s2t(key.cn())));
            }
        }

        langBootstrapped = true;
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
        for (TopoPlugins.BoundPlugin bound : TopoPlugins.boundView()) {
            return bound.material().registry();
        }
        throw new IllegalStateException(
                "TopoPluginEngine: no plugin registered to mint content for material " + material.id());
    }
}
