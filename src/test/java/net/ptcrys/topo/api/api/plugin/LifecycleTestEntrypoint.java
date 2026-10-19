package net.ptcrys.topo.api.api.plugin;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.Topology;
import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.api.builtin.LangDomainRegistration;
import net.ptcrys.topo.api.machine.MachineItemBehavior;
import net.ptcrys.topo.api.machine.MachineItemBehaviors;
import net.ptcrys.topo.api.machine.ui.tooltip.ItemTooltipUis;
import net.ptcrys.topo.api.machine.ui.tooltip.TopoTooltipUiProvider;

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

    public LifecycleTestEntrypoint(IEventBus bus) {
        a = new TestPlugin(coreA) {

            @Override
            public void registerLang(LangDomainRegistration lang) {
                bindings.add("a");
                lang.key("ui", "owned_a", "A", "甲");
            }
        };
        b = new TestPlugin(coreB) {

            @Override
            public void registerLang(LangDomainRegistration lang) {
                bindings.add("b");
                lang.resource(net.minecraft.resources.Identifier.fromNamespaceAndPath(modId(), "owned_b"), "pipe.strategy", "B", "乙");
                lang.absolute("jade.config." + modId() + ".owned", "Config", "配置");
            }
        };
        TopoPlugins.register("test:b", b, "test:a");
        TopoPlugins.register("test:a", a);
        TopoPlugins.register(OfficialTopoAPIPlugin.INSTANCE);

        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            ItemTooltipUis.register(Items.STICK, panelA);
            MachineItemBehaviors.register(Items.STICK, behavior);
            ItemTooltipUis.register(Items.BONE, panelB);
            MachineItemBehaviors.register(Items.BONE, behavior);
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
