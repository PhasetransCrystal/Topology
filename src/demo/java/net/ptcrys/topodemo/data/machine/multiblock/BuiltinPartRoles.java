package net.ptcrys.topodemo.data.machine.multiblock;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topodemo.data.TopoDemoPlugin;
import net.ptcrys.topodemo.data.recipe.BuiltinTopoResourceIntegrations;

/** Demo 的物品、流体和能量部件角色。 */
public final class BuiltinPartRoles {

    private static final MachineDomainRegistration MACHINE = TopoDemoPlugin.INSTANCE.machine();

    public static final PartRole ITEM_INPUT = MACHINE.partRole("item_input", "Item Input Bus", "物品输入总线",
            BuiltinTopoResourceIntegrations.ITEM.resourceType(), RecipeRole.INPUT);
    public static final PartRole ITEM_OUTPUT = MACHINE.partRole("item_output", "Item Output Bus", "物品输出总线",
            BuiltinTopoResourceIntegrations.ITEM.resourceType(), RecipeRole.OUTPUT);
    public static final PartRole FLUID_INPUT = MACHINE.partRole("fluid_input", "Fluid Input Hatch", "流体输入仓",
            BuiltinTopoResourceIntegrations.FLUID.resourceType(), RecipeRole.INPUT);
    public static final PartRole FLUID_OUTPUT = MACHINE.partRole("fluid_output", "Fluid Output Hatch", "流体输出仓",
            BuiltinTopoResourceIntegrations.FLUID.resourceType(), RecipeRole.OUTPUT);

    public static final PartRole ENERGY_INPUT = MACHINE.partRole("energy_input", "Energy Input", "能量输入",
            BuiltinTopoResourceIntegrations.ENERGY.resourceType(), RecipeRole.INPUT);
    public static final PartRole ENERGY_OUTPUT = MACHINE.partRole("energy_output", "Energy Output", "能量输出",
            BuiltinTopoResourceIntegrations.ENERGY.resourceType(), RecipeRole.OUTPUT);

    private BuiltinPartRoles() {}

    public static void init() {}
}
