package net.ptcrys.topo.datav2.machine;

import net.ptcrys.topo.apiv2.machine.ConnectedTextureFormedMachineBlock;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogic;
import net.ptcrys.topo.apiv2.machine.component.RecipeUi;
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockController;
import net.ptcrys.topo.apiv2.machine.multiblock.MultiblockUi;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Blueprint;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.CellPredicates;
import net.ptcrys.topo.apiv2.machine.resource.AutomationIo;
import net.ptcrys.topo.apiv2.machine.resource.PortAccess;
import net.ptcrys.topo.data.vanilla.BuiltinOICreativeTabs;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.machine.common.component.PerformanceRecipeModifiers;
import net.ptcrys.topo.datav2.machine.common.component.resource.ItemResourcePort;
import net.ptcrys.topo.datav2.machine.common.component.resource.Ports;
import net.ptcrys.topo.datav2.machine.common.component.resource.ScalarResourcePort;
import net.ptcrys.topo.datav2.machine.multiblock.BuiltinOICasingBlocks;
import net.ptcrys.topo.datav2.recipe.BuiltinOIRecipeTypes;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

/**
 * Builtin multiblock <b>controller</b> machine definitions. A controller is an ordinary machine that
 * mounts {@link MultiblockController} with its blueprint; hatch/bus parts live in
 * {@link BuiltinOIPartMachines} / {@link BuiltinOIMeMachines}.
 *
 * <p>
 * Introspection goes through {@code definition.metadata(MultiblockControllerMetadata.TYPE)}, never
 * through a parallel catalog.
 *
 * <p>
 * First content multiblock is the large grinder ({@code docs/design/machine_construction.md}):
 * controller carries all item/energy IO — no hatch requirement in v1.
 */
public final class BuiltinOIControllerMachines {

    private static final int ITEM_SLOTS = 4;
    private static final int BASE_SCALAR_CAPACITY = 100_000;

    private static final PortAccess INPUT = PortAccess.input()
            .onAllSides()
            .withAutomationIo(AutomationIo.BOTH)
            .withDefaultAutomationIo(AutomationIo.INSERT)
            .withAllowedRuntimeModes(AutomationIo.NONE, AutomationIo.INSERT, AutomationIo.BOTH)
            .withPlayerConfigurableSides();
    private static final PortAccess OUTPUT = PortAccess.output()
            .onAllSides()
            .withPlayerConfigurableSides();

    /**
     * 3×3×3 large grinder: controller on a side-centre face, hollow grinding chamber at the cube
     * centre (space = not a structure cell), remaining 25 body cells are dense structure casing.
     * Canonical frame has the controller facing south (front row last in each aisle string list).
     *
     * <p>
     * Performance: same {@code FINE_GRINDER} recipes as the single-block fine grinder, with T3
     * consumer energy scaling plus fixed 4-wide parallel batching (see {@code production_line.md}).
     */
    private static final Blueprint LARGE_GRINDER_BLUEPRINT = Blueprint.of()
            .aisle(
                    "XXX",
                    "XXX",
                    "XXX")
            .aisle(
                    "XXX",
                    "X X",
                    "X@X")
            .aisle(
                    "XXX",
                    "XXX",
                    "XXX")
            .where('@', CellPredicates.controller())
            .where('X', CellPredicates.block(BuiltinOICasingBlocks.DENSE_STRUCTURE_CASING))
            .build();

    public static final MachineDefinition LARGE_GRINDER = OfficialOIPlugin.INSTANCE.machine().machine("large_grinder")
            .block((properties, id) -> new ConnectedTextureFormedMachineBlock(
                    properties, id, BuiltinOICasingBlocks.DENSE_STRUCTURE_CASING_SKIN.family()))
            .blockEntity(MachineBlockEntity::new)
            .displayName("Large Grinder (Tier 3)", "大型研磨机（Tier 3）")
            .creativeTab(BuiltinOICreativeTabs.MACHINES.getKey())
            .render(BuiltinOIMachineRenderTypes.CTM_MACHINE.ctm(
                    BuiltinOICasingBlocks.DENSE_STRUCTURE_CASING_SKIN,
                    OfficialOIPlugin.INSTANCE.machine().id("block/machines/cutting_mill/overlay_front"),
                    OfficialOIPlugin.INSTANCE.machine().id("block/machines/cutting_mill/overlay_front_active")))
            .component(Ports.item(ItemResourcePort.ITEM_INPUT_1, ITEM_SLOTS, INPUT))
            .component(Ports.item(ItemResourcePort.ITEM_OUTPUT_1, ITEM_SLOTS, OUTPUT))
            .component(Ports.scalar(
                    ScalarResourcePort.ENERGY_INPUT_1,
                    BuiltinOIResourceIntegrations.ENERGY,
                    scalarCapacity(MachineTier.T3),
                    INPUT))
            .component(MultiblockController.mount(LARGE_GRINDER_BLUEPRINT))
            .component(RecipeLogic.mount(RecipeLogic.RECIPE_LOGIC_1, BuiltinOIRecipeTypes.FINE_GRINDER))
            .component(MultiblockUi.mount())
            .component(RecipeUi.mount(
                    RecipeUi.RECIPE_UI_1,
                    RecipeLogic.RECIPE_LOGIC_1,
                    RecipeUi.RecipePageGate.of(
                            traits -> traits.require(MultiblockController.CONTROLLER)::formed,
                            BuiltinOIMachineUiLang.UI_MULTIBLOCK_UNFORMED_NOTICE.getComponent())))
            // Parallel first (IO×N), then T3 energy (duration×0.25, energy×9) — single duration fold.
            .component(PerformanceRecipeModifiers.ParallelModifier.of(4))
            .component(PerformanceRecipeModifiers.EnergyTierModifier.of(MachineTier.T3))
            .build();

    private BuiltinOIControllerMachines() {}

    private static int scalarCapacity(MachineTier tier) {
        return Math.multiplyExact(BASE_SCALAR_CAPACITY, tier.ratedPowerMultiplier());
    }

    public static void init() {}
}
