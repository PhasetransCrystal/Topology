package net.ptcrys.topo.gametest;

import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.MachineDefinition;
import net.ptcrys.topo.api.machine.ui.ComponentCollector;
import net.ptcrys.topo.api.machine.ui.PageCollector;
import net.ptcrys.topo.data.machine.BuiltinTopoMeMachines;
import net.ptcrys.topo.data.machine.BuiltinTopoPartMachines;
import net.ptcrys.topo.data.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

import java.util.List;
import java.util.function.Consumer;

/**
 * Structure-level UI contribution tests: place real machines and assert which UI pages and side
 * components their traits contribute through {@link MachineBlockEntity#collectMachineUi}. No
 * rendering — the page graph and the trait wiring behind it are what these tests pin down.
 */
public final class HatchUiGameTests {

    private static final String SUITE = "hatch_ui";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final Identifier EMPTY_STRUCTURE = Identifier.withDefaultNamespace("empty");
    private static final int TEST_COUNT = 3;

    private HatchUiGameTests() {}

    public static void register(net.neoforged.neoforge.event.RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(IdHelper.oi("hatch_ui"), new TestEnvironmentDefinition.AllOf());
        int index = 1;
        register(
                event,
                environment,
                index++,
                "item_input_hatch_contributes_storage_page",
                "Tests StoragePageUi and the expanded hatch definition: the 27-slot item input hatch " + "contributes exactly one 'storage' UI page.",
                HatchUiGameTests::itemInputHatchContributesStoragePage);
        register(
                event,
                environment,
                index++,
                "me_machines_contribute_expected_ui_pages",
                "Tests AeConfigPageUi, AePatternProviderUi, and AeBufferPageUi: every ME " + "machine contributes exactly one page, mirroring the old GTOdyssey hatches — " + "drawing/direct buses ae_config, pattern provider ae_patterns with a right-hand " + "name component, export hatch ae_buffer.",
                HatchUiGameTests::meMachinesContributeExpectedUiPages);
        register(
                event,
                environment,
                index,
                "energy_hatches_contribute_bar_components_no_pages",
                "Tests the scalar hatch UI contract: both energy hatches contribute no UI pages (the " + "frame's default page covers them) and surface their buffer as a bottom " + "resource-bar component.",
                HatchUiGameTests::energyHatchesContributeBarComponentsNoPages);
    }

    // ---- 075 ----

    private static void itemInputHatchContributesStoragePage(GameTestHelper helper) {
        MachineBlockEntity machine = place(helper, BuiltinTopoPartMachines.ITEM_INPUT_HATCH);
        int slots = machine.machineComponents().require(ItemResourcePort.ITEM_INPUT_1).resourceIndexCount();
        if (slots != 27) {
            helper.fail("Item input hatch should expose 27 slots after the expansion, got " + slots, MACHINE_POS);
        }
        assertPageKeys(helper, machine, List.of("storage"));
        helper.succeed();
    }

    // ---- 076 ----

    private static void meMachinesContributeExpectedUiPages(GameTestHelper helper) {
        // Single-page contract from the old GTOdyssey reference: the AE config page already shows
        // the live cached / network stock under each ghost slot, so no separate storage page.
        assertPageKeys(helper, place(helper, BuiltinTopoMeMachines.ME_DRAWING_ITEM_INPUT_BUS), List.of("ae_config"));
        assertPageKeys(helper, place(helper, BuiltinTopoMeMachines.ME_DRAWING_FLUID_INPUT_HATCH), List.of("ae_config"));
        assertPageKeys(helper, place(helper, BuiltinTopoMeMachines.ME_DIRECT_ITEM_INPUT_BUS), List.of("ae_config"));
        assertPageKeys(helper, place(helper, BuiltinTopoMeMachines.ME_DIRECT_FLUID_INPUT_HATCH), List.of("ae_config"));

        MachineBlockEntity provider = place(helper, BuiltinTopoMeMachines.ME_PATTERN_PROVIDER);
        assertPageKeys(helper, provider, List.of("ae_patterns"));
        ComponentCollector components = collect(provider).components;
        boolean hasNameComponent = components.entries().stream()
                .anyMatch(entry -> entry.side() == ComponentCollector.Side.RIGHT && entry.key().equals("topo_ae_pattern_provider_name"));
        if (!hasNameComponent) {
            helper.fail("Pattern provider should contribute the right-hand name component", MACHINE_POS);
        }

        MachineBlockEntity exportHatch = place(helper, BuiltinTopoMeMachines.ME_EXPORT_HATCH);
        assertPageKeys(helper, exportHatch, List.of("ae_buffer"));

        helper.succeed();
    }

    // ---- 254 ----

    private static void energyHatchesContributeBarComponentsNoPages(GameTestHelper helper) {
        for (MachineDefinition definition : List.of(
                BuiltinTopoPartMachines.ENERGY_INPUT_HATCH, BuiltinTopoPartMachines.ENERGY_OUTPUT_HATCH)) {
            MachineBlockEntity hatch = place(helper, definition);
            assertPageKeys(helper, hatch, List.of());
            boolean hasBar = collect(hatch).components.entries().stream()
                    .anyMatch(entry -> entry.side() == ComponentCollector.Side.BOTTOM && entry.key().startsWith("topo_resource_bar_"));
            if (!hasBar) {
                helper.fail(
                        "Energy hatch " + definition.id() + " should contribute a bottom resource bar",
                        MACHINE_POS);
            }
        }
        helper.succeed();
    }

    // ---- helpers ----

    private record CollectedUi(PageCollector pages, ComponentCollector components) {}

    private static CollectedUi collect(MachineBlockEntity machine) {
        PageCollector pages = new PageCollector();
        ComponentCollector components = new ComponentCollector();
        machine.collectMachineUi(pages, components);
        return new CollectedUi(pages, components);
    }

    private static void assertPageKeys(GameTestHelper helper, MachineBlockEntity machine, List<String> expected) {
        List<String> actual = collect(machine).pages.entries().stream()
                .map(PageCollector.Page::key)
                .toList();
        if (!actual.equals(expected)) {
            helper.fail("Machine " + machine.definition().id() + " should contribute pages " + expected + ", got " + actual, MACHINE_POS);
        }
    }

    private static MachineBlockEntity place(GameTestHelper helper, MachineDefinition definition) {
        helper.setBlock(MACHINE_POS, definition.registeredBlock().getDefaultState());
        return helper.getBlockEntity(MACHINE_POS, MachineBlockEntity.class);
    }

    private static void register(
                                 net.neoforged.neoforge.event.RegisterGameTestsEvent event,
                                 Holder<TestEnvironmentDefinition<?>> environment,
                                 int index,
                                 String name,
                                 String description,
                                 Consumer<GameTestHelper> test) {
        if (index < 1 || index > TEST_COUNT) {
            throw new IllegalArgumentException("Hatch UI GameTest index out of range: " + index);
        }
        java.util.Objects.requireNonNull(description, "description");
        ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, IdHelper.oi(name));
        event.registerTest(
                IdHelper.oi(name),
                new InlineGameTestInstance(
                        functionKey,
                        new TestData<>(environment, EMPTY_STRUCTURE, 120, 0, true, Rotation.NONE),
                        GameTestReport.wrap(SUITE, index, TEST_COUNT, name, description, test)));
    }
}
