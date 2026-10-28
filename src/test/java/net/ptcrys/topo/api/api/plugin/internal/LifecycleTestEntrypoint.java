package net.ptcrys.topo.api.api.plugin.internal;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.util.entry.ItemEntry;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.EquipmentDomainRegistration;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RuntimeDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangRegistry;
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.api.registration.ExternalTargets;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.equipment.EquipmentStrategy;
import net.ptcrys.topo.api.lang.DisplayNames;
import net.ptcrys.topo.api.lang.RegistryDisplayLang;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.machine.ui.tooltip.TopoTooltipUiProvider;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
import net.ptcrys.topo.api.material.MaterialFormOptions;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataRegistry;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topo.api.material.form.MaterialFormStrategy;
import net.ptcrys.topo.api.material.process.MaterialPostProcessor;
import net.ptcrys.topo.api.ore.OreVeins;
import net.ptcrys.topo.api.recipe.productionline.ProductionLine;
import net.ptcrys.topo.api.recipe.productionline.ProductionLines;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only entrypoint: exercises the real host event wiring before JUnit tests start. */
@Mod(Topology.MODID)
public class LifecycleTestEntrypoint {

    static final List<String> bindings = new ArrayList<>();
    static final TopoTooltipUiProvider panelA = stack -> null;
    static final TopoTooltipUiProvider panelB = stack -> null;
    static final MachineItemBehavior behavior = (machine, player, stack, hand, hit) -> InteractionResult.PASS;
    static final RecordingCore coreA = new RecordingCore("topo_test_a");
    static final RecordingCore coreB = new RecordingCore("topo_test_b");
    static TestPlugin a;
    static TestPlugin b;
    static boolean legacyBound;
    static Equipment recipeEquipment;
    static ProductionLine recipeLine;
    static boolean recipesSawFrozenContent;
    static int recipeCalls;
    static IllegalStateException duplicateIdentityFailure;
    static TestPlugin duplicateProbe;
    static ItemEntry<Item> generatedEquipment;
    static boolean runtimeSawEquipmentBehavior;
    static IllegalStateException entryThenKeyFailure;
    static IllegalStateException keyThenEntryFailure;
    static ProbeDataType materialMarker;
    static MaterialForm baseForm;
    static MaterialForm extensionForm;
    static final Map<Material, List<MaterialForm>> generatedForms = new LinkedHashMap<>();
    static final Map<Material, RegistryCore> generatedFormOwners = new LinkedHashMap<>();
    static boolean formValidationSawResolvedForms = true;
    static MaterialForm nonMatchingForm;
    static boolean injectionSawAllMaterials;
    static boolean dataWriteReturnedTarget;
    static boolean builderDataSharedAfterBuild;
    static IllegalStateException duplicateDataFailure;
    static IllegalStateException selectionTimeDataFailure;
    static IllegalStateException builderDuplicateDataFailure;
    static MaterialRegistry.Builder lateMaterialBuilder;
    static MaterialRegistry.Builder builtMaterialBuilder;
    static MaterialFormOptions retainedFormOptions;
    static ProbeDataType scopedMarker;
    static int scopedValidations;
    static int ruleCalls;
    static boolean rulesSawEmptyForms = true;
    static boolean rulesReusedMaterialSnapshot = true;
    static MaterialPostProcessor alphaProcessor;
    static MaterialPostProcessor zetaProcessor;
    static final List<String> processorEvents = new ArrayList<>();
    static IllegalArgumentException foreignFormFailure;
    static IllegalStateException duplicateBuildFailure;

    public LifecycleTestEntrypoint(IEventBus bus) {
        a = new TestPlugin(coreA) {

            @Override
            public void registerMaterialFoundation(MaterialDomainRegistration material) {
                materialMarker = material.dataType("form_probe", new ProbeDataType(material.id("form_probe")));
                scopedMarker = MaterialDataRegistry.begin(material.id("scoped_probe"), new ProbeDataType(material.id("scoped_probe")),
                        (target, enabled) -> {
                            if (!enabled) throw new IllegalStateException("unmatched form data must not be validated");
                            scopedValidations++;
                        });
                baseForm = material.form("base_form").lang("Base", "基础形态")
                        .appliesTo(value -> {
                            ruleCalls++;
                            var snapshot = MaterialRegistry.registered();
                            rulesReusedMaterialSnapshot &= snapshot == MaterialRegistry.registered();
                            rulesSawEmptyForms &= snapshot.stream().allMatch(target -> target.strategy().forms().isEmpty());
                            try {
                                value.data(materialMarker.enabled());
                            } catch (IllegalStateException error) {
                                selectionTimeDataFailure = error;
                            }
                            return value.strategy().data(materialMarker).orElse(false);
                        })
                        .strategy(probeFormStrategy()).build();
                nonMatchingForm = material.form("non_matching_form").lang("Non-matching", "不匹配形态")
                        .strategy(probeFormStrategy()).build();
                // Register and activate in reverse order: execution must still use full IDs.
                zetaProcessor = material.postProcessor("zeta", probeProcessor(material, "zeta"));
                alphaProcessor = material.postProcessor("alpha", probeProcessor(material, "alpha"));
                material.postProcessor("inactive", probeProcessor(material, "inactive"));
            }

            @Override
            public void registerMaterials(MaterialDomainRegistration material) {
                lateMaterialBuilder = material.material("late_material").lang("Late", "晚到材料");
                lateMaterialBuilder.data(materialMarker.enabled());
                try {
                    lateMaterialBuilder.data(materialMarker.enabled());
                } catch (IllegalStateException error) {
                    builderDuplicateDataFailure = error;
                }
                builtMaterialBuilder = material.material("lifecycle_material").lang("Probe", "探针材料");
                builtMaterialBuilder
                        .form(nonMatchingForm, options -> options.overrideItem(ExternalTargets.mcItem("stick")).data(scopedMarker.disabled()))
                        .build();
                builtMaterialBuilder.form(baseForm, options -> {
                    retainedFormOptions = options;
                    options.data(scopedMarker.enabled());
                });
                try {
                    builtMaterialBuilder.form(new MaterialForm(baseForm.id(), probeFormStrategy(), null), options -> {});
                } catch (IllegalArgumentException error) {
                    foreignFormFailure = error;
                }
                try {
                    builtMaterialBuilder.build();
                } catch (IllegalStateException error) {
                    duplicateBuildFailure = error;
                }
            }

            @Override
            public void registerEquipmentKinds(EquipmentDomainRegistration equipment) {
                equipment.kind("lifecycle_tool", new EquipmentStrategy() {

                    @Override
                    public boolean appliesTo(Material material) {
                        return material.id().equals(material().id("lifecycle_material"));
                    }

                    @Override
                    public void register(MaterialContentContext context, Equipment self) {
                        var builder = context.core().item("lifecycle_tool");
                        RegistryDisplayLang.applyItem(builder, context.core(), "lifecycle_tool", DisplayNames.fixed("Tool", "工具"));
                        generatedEquipment = builder.register();
                        try {
                            lang().absolute("item." + modId() + ".lifecycle_tool", "Tool", "工具");
                        } catch (IllegalStateException error) {
                            entryThenKeyFailure = error;
                        }
                        EquipmentRegistry.recordItem(self, context.material(), generatedEquipment);
                    }

                    @Override
                    public MachineItemBehavior machineBehavior() {
                        return behavior;
                    }
                });
            }

            @Override
            public void registerRecipes(RecipeDomainRegistration recipe) {
                recipeEquipment = EquipmentRegistry.require(equipment().id("lifecycle_tool"));
                recipeLine = ProductionLines.require(b.recipe().id("machine_line"));
                recipesSawFrozenContent = EquipmentRegistry.isFrozen() && OreVeins.isFrozen();
                recipe.productionLine("final_line");
                recipeCalls++;
            }

            @Override
            public void registerLang(LangDomainRegistration lang) {
                lang.key("ui", "owned_a", "A", "甲");
                try {
                    LangRegistry.claimEntryName("ui." + modId() + ".owned_a");
                } catch (IllegalStateException error) {
                    keyThenEntryFailure = error;
                }
            }

            @Override
            public void registerRuntime(RuntimeDomainRegistration runtime) {
                runtimeSawEquipmentBehavior = MachineItemBehaviors.find(generatedEquipment.get()) == behavior;
                runtime.tooltip(Items.STICK, panelA);
                runtime.itemBehavior(Items.STICK, behavior);
                bindings.add("a");
            }
        };
        b = new TestPlugin(coreB) {

            @Override
            public void registerMaterialFoundation(MaterialDomainRegistration material) {
                extensionForm = material.form("extension_form").lang("Extension", "扩展形态")
                        .appliesToAll()
                        .strategy(probeFormStrategy()).build();
            }

            @Override
            public void registerMaterials(MaterialDomainRegistration material) {
                var builder = material.material("extension_material").lang("Extension", "扩展材料");
                var built = builder.build();
                injectionSawAllMaterials = MaterialRegistry.registered().size() == 2 && !MaterialRegistry.isFrozen();
                builder.data(materialMarker.enabled());
                builderDataSharedAfterBuild = built.strategy().data(materialMarker).orElse(false);
                var target = MaterialRegistry.require(a.material().id("lifecycle_material"));
                dataWriteReturnedTarget = target.data(materialMarker.enabled()) == target;
                target.data(zetaProcessor.activation()).data(alphaProcessor.activation());
                try {
                    target.data(materialMarker.enabled());
                } catch (IllegalStateException error) {
                    duplicateDataFailure = error;
                }
            }

            @Override
            public void registerMachineFollowUps(MachineDomainRegistration machine) {
                recipe().productionLine("machine_line");
            }

            @Override
            public void registerLang(LangDomainRegistration lang) {
                lang.resource(net.minecraft.resources.Identifier.fromNamespaceAndPath(modId(), "owned_b"), "pipe.strategy", "B", "乙");
                lang.absolute("jade.config." + modId() + ".owned", "Config", "配置");
            }

            @Override
            public void registerRuntime(RuntimeDomainRegistration runtime) {
                runtime.tooltip(Items.BONE, panelB);
                runtime.itemBehavior(Items.BONE, behavior);
                bindings.add("b");
            }
        };
        duplicateProbe = duplicateCandidate();
        TopoPlugins.register(duplicateProbe);
        TopoPlugins.register(duplicateProbe);
        try {
            TopoPlugins.register(duplicateCandidate());
        } catch (IllegalStateException error) {
            duplicateIdentityFailure = error;
        }
        TopoPlugins.register(b);
        TopoPlugins.register(a);
        TopoPlugins.register(OfficialTopoAPIPlugin.INSTANCE);

        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ItemTooltipUis.register(Items.COAL, panelA);
            MachineItemBehaviors.register(Items.COAL, behavior);
            legacyBound = true;
        }));
    }

    private static TestPlugin duplicateCandidate() {
        return new TestPlugin(coreA) {};
    }

    private static MaterialPostProcessor probeProcessor(MaterialDomainRegistration material, String path) {
        return new MaterialPostProcessor(material.id(path), baseForm) {

            @Override
            public void validate(Material target) {
                super.validate(target);
                processorEvents.add("validate:" + path);
            }

            @Override
            public void process(Material target) {
                if (generatedForms.size() != 2) throw new IllegalStateException("processor ran before all material content");
                // Deliberately no activation guard: dispatch belongs to the framework.
                processorEvents.add("process:" + path);
            }
        };
    }

    private static MaterialFormStrategy probeFormStrategy() {
        return new MaterialFormStrategy(List.of()) {

            @Override
            public String registryPath() {
                return "%s_probe";
            }

            @Override
            public void validateMaterial(Material material, MaterialForm form) {
                for (Material declared : MaterialRegistry.registered()) {
                    formValidationSawResolvedForms &= declared.strategy().forms().containsAll(List.of(baseForm, extensionForm));
                }
            }

            @Override
            public void register(MaterialContentContext context, MaterialForm form) {
                if (!MaterialRegistry.isFrozen()) throw new IllegalStateException("content before material freeze");
                generatedForms.computeIfAbsent(context.material(), ignored -> new ArrayList<>()).add(form);
                generatedFormOwners.put(context.material(), context.core());
                context.core().item(context.material().id().getPath() + "_" + form.id().getPath()).register();
            }
        };
    }

    static final class ProbeDataType extends MaterialDataType<Boolean> {

        ProbeDataType(net.minecraft.resources.Identifier id) {
            super(id);
        }

        MaterialDataUse<Boolean> enabled() {
            return use(true);
        }

        MaterialDataUse<Boolean> disabled() {
            return use(false);
        }
    }

    /** Capture the actual engine-to-RegistryLib datagen bridge without writing resource files. */
    static final class RecordingCore extends RegistryCore {

        final Map<String, Map<String, String>> languages = new LinkedHashMap<>();

        RecordingCore(String modId) {
            super(modId);
        }

        @Override
        public boolean doDatagen() {
            return true;
        }

        @Override
        public MutableComponent lang(String key, Map<String, String> locales) {
            languages.put(key, Map.copyOf(locales));
            return Component.translatable(key);
        }
    }
}
