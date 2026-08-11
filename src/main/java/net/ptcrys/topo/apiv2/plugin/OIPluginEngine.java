package net.ptcrys.topo.apiv2.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.lang.ChineseConvert;
import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.api.lang.LangRegistry;
import net.ptcrys.topo.api.pipe.PipeDistributionStrategies;
import net.ptcrys.topo.api.pipe.Pipes;
import net.ptcrys.topo.apiv2.equipment.Equipment;
import net.ptcrys.topo.apiv2.equipment.EquipmentRegistry;
import net.ptcrys.topo.apiv2.machine.Machines;
import net.ptcrys.topo.apiv2.machine.component.AttachmentTypes;
import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRoleRegistry;
import net.ptcrys.topo.apiv2.machine.multiblock.ui.PropertyDisplayRegistry;
import net.ptcrys.topo.apiv2.machine.render.MachineRenderRegistry;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.apiv2.machine.ui.MachineUiIcons;
import net.ptcrys.topo.apiv2.material.Material;
import net.ptcrys.topo.apiv2.material.MaterialContentContext;
import net.ptcrys.topo.apiv2.material.MaterialRegistry;
import net.ptcrys.topo.apiv2.material.data.MaterialDataRegistry;
import net.ptcrys.topo.apiv2.material.form.FormDataRegistry;
import net.ptcrys.topo.apiv2.material.form.MaterialForm;
import net.ptcrys.topo.apiv2.material.form.MaterialFormRegistry;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessor;
import net.ptcrys.topo.apiv2.material.process.MaterialPostProcessorRegistry;
import net.ptcrys.topo.apiv2.ore.OreVeins;
import net.ptcrys.topo.apiv2.ore.display.OreVeinDisplays;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinMode;
import net.ptcrys.topo.apiv2.ore.mode.OreVeinModes;
import net.ptcrys.topo.apiv2.ore.policy.OreAirExposurePolicies;
import net.ptcrys.topo.apiv2.ore.policy.OreConflictPolicies;
import net.ptcrys.topo.apiv2.ore.shape.OreVeinShapes;
import net.ptcrys.topo.apiv2.recipe.OIRecipeTypes;
import net.ptcrys.topo.apiv2.recipe.capability.RecipeCapabilities;
import net.ptcrys.topo.apiv2.recipe.productionline.ProductionLines;
import net.ptcrys.topo.datav2.recipe.common.RecipePreviewPlans;

import java.util.Map;

/**
 * Skeleton (engine) for plugin content load. Phases replace domain {@code *Bootstrap} chains:
 * plugins only declare; this class freezes tables, mints form/equipment content, and runs
 * post-processors.
 *
 * <p>
 * Call {@link #prepare()} once after all mods have had a chance to {@link OIPlugins#register}
 * — typically on the first {@code RegisterEvent} at {@code HIGHEST} so RegistryLib ({@code LOW})
 * still sees queued entries. Official and third-party share that single explicit path.
 */
public final class OIPluginEngine {

    private static boolean prepared;
    private static boolean recipeBootstrapped;
    private static boolean materialBootstrapped;
    private static boolean equipmentBootstrapped;
    private static boolean machineBootstrapped;
    private static boolean oreBootstrapped;
    private static boolean langBootstrapped;

    private OIPluginEngine() {}

    /**
     * Locks in already-{@link OIPlugins#register registered} plugins then runs recipe foundation →
     * material → equipment. Safe to call multiple times; only the first call does work. Content /
     * machine / ore / lang phases are orchestrated by the host after this returns.
     */
    public static synchronized void prepare() {
        if (prepared) {
            return;
        }
        prepared = true;
        if (OIPlugins.view().isEmpty()) {
            throw new IllegalStateException(
                    "OIPluginEngine: no plugins registered — call OIPlugins.register from @Mod constructors");
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
        OIPlugins.lock();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerRecipeFoundation(bound.recipe());
        }
        RecipeCapabilities.freeze();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerRecipeTypes(bound.recipe());
        }
        OIRecipeTypes.freeze();

        recipeBootstrapped = true;
    }

    public static synchronized void bootstrapMaterial() {
        if (materialBootstrapped) {
            return;
        }
        if (!recipeBootstrapped) {
            throw new IllegalStateException("OIPluginEngine: bootstrapRecipe() must run before material");
        }
        OIPlugins.lock();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerMaterialFoundation(bound.material());
        }
        MaterialDataRegistry.freeze();
        FormDataRegistry.freeze();
        MaterialFormRegistry.freeze();
        MaterialPostProcessorRegistry.freeze();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerMaterials(bound.material());
        }
        MaterialRegistry.freeze();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerMaterialCreativeTabs(bound.material());
        }

        registerMaterialFormContent();
        runMaterialPostProcessors();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerMaterialFollowUps(bound.material());
        }
        // Production-line recipes and residual recipe tables after materials exist.
        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
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
            throw new IllegalStateException("OIPluginEngine: bootstrapMaterial() must run before equipment");
        }
        OIPlugins.lock();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
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
            throw new IllegalStateException("OIPluginEngine: bootstrapRecipe() must run before machine");
        }
        OIPlugins.lock();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerMachineFoundation(bound.machine());
        }
        MachineResourceTypes.freeze();
        MachineUiIcons.freeze();
        MachineRenderRegistry.freeze();
        PartRoleRegistry.freeze();
        PropertyDisplayRegistry.freeze();
        AttachmentTypes.freeze();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerMachines(bound.machine());
        }
        Machines.freeze();
        // Shared BE type lives on the host registry; each machine block uses its plugin RegistryCore.
        Machines.registerBlocksAndBlockEntity(Topology.REGISTRY);

        OIRecipeTypes.installPreviewPlans(RecipePreviewPlans::canonical);

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
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
        OIPlugins.lock();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerOreFoundation(bound.ore());
        }
        OreVeinShapes.freeze();
        OreVeinModes.freeze();
        OreAirExposurePolicies.freeze();
        OreConflictPolicies.freeze();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerOreDisplays(bound.ore());
        }
        OreVeinDisplays.freeze();
        for (OreVeinMode mode : OreVeinModes.view()) {
            OreVeinDisplays.require(mode.id());
        }

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            bound.plugin().registerOreVeins(bound.ore());
        }
        OreVeins.freeze();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
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
        OIPlugins.lock();

        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
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
        OIPlugins.BoundPlugin owner = OIPlugins.findByModId(namespace);
        if (owner != null) {
            return owner.material().registry();
        }
        for (OIPlugins.BoundPlugin bound : OIPlugins.boundView()) {
            return bound.material().registry();
        }
        throw new IllegalStateException(
                "OIPluginEngine: no plugin registered to mint content for material " + material.id());
    }
}
