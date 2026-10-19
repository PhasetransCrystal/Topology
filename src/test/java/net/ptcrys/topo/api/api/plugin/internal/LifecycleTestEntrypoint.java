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
import net.ptcrys.topo.api.api.plugin.TopoPlugins;
import net.ptcrys.topo.api.equipment.Equipment;
import net.ptcrys.topo.api.equipment.EquipmentRegistry;
import net.ptcrys.topo.api.equipment.EquipmentStrategy;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.machine.ui.tooltip.TopoTooltipUiProvider;
import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialContentContext;
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

    public LifecycleTestEntrypoint(IEventBus bus) {
        a = new TestPlugin(coreA) {

            @Override
            public void registerMaterials(MaterialDomainRegistration material) {
                material.material("lifecycle_material").lang("Probe", "探针材料").build();
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
                        generatedEquipment = context.core().item("lifecycle_tool").register();
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
