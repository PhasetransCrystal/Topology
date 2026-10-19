package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.EquipmentDomainRegistration;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RecipeDomainRegistration;
import net.ptcrys.topo.api.api.builtin.RuntimeDomainRegistration;
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

    public LifecycleTestEntrypoint(IEventBus bus) {
        a = new TestPlugin(coreA) {

            @Override
            protected void configure(PluginRegistration registration) {
                registration.on(EquipmentDomainRegistration.KINDS, equipment -> {
                    equipment.kind("lifecycle_tool", new EquipmentStrategy() {

                        @Override
                        public boolean appliesTo(Material material) {
                            return false;
                        }

                        @Override
                        public void register(MaterialContentContext context, Equipment self) {
                            throw new AssertionError("test equipment must not generate items");
                        }
                    });
                });
                registration.on(RecipeDomainRegistration.RECIPES, recipe -> {
                    recipeEquipment = EquipmentRegistry.require(equipment().id("lifecycle_tool"));
                    recipeLine = ProductionLines.require(b.recipe().id("machine_line"));
                    recipesSawFrozenContent = EquipmentRegistry.isFrozen() && OreVeins.isFrozen();
                    recipe.productionLine("final_line");
                    recipeCalls++;
                });
                registration.on(LangDomainRegistration.KEYS, lang -> {
                    lang.key("ui", "owned_a", "A", "甲");
                });
                registration.on(RuntimeDomainRegistration.BINDINGS, runtime -> {
                    runtime.tooltip(Items.STICK, panelA);
                    runtime.itemBehavior(Items.STICK, behavior);
                    bindings.add("a");
                });
            }
        };
        b = new TestPlugin(coreB) {

            @Override
            protected void configure(PluginRegistration registration) {
                registration.on(MachineDomainRegistration.FOLLOW_UPS, machine -> recipe().productionLine("machine_line"));
                registration.on(LangDomainRegistration.KEYS, lang -> {
                    lang.resource(net.minecraft.resources.Identifier.fromNamespaceAndPath(modId(), "owned_b"), "pipe.strategy", "B", "乙");
                    lang.absolute("jade.config." + modId() + ".owned", "Config", "配置");
                });
                registration.on(RuntimeDomainRegistration.BINDINGS, runtime -> {
                    runtime.tooltip(Items.BONE, panelB);
                    runtime.itemBehavior(Items.BONE, behavior);
                    bindings.add("b");
                });
            }
        };
        TopoPlugins.register(b);
        TopoPlugins.register(a);
        TopoPlugins.register(OfficialTopoAPIPlugin.INSTANCE);

        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ItemTooltipUis.register(Items.COAL, panelA);
            MachineItemBehaviors.register(Items.COAL, behavior);
            legacyBound = true;
        }));
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
