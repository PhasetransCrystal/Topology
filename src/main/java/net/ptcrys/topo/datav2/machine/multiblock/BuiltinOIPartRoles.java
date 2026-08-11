package net.ptcrys.topo.datav2.machine.multiblock;

import net.ptcrys.topo.apiv2.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.recipe.BuiltinOIResourceIntegrations;

import java.util.Objects;

/**
 * Builtin {@link PartRole} declarations via {@link OfficialOIPlugin#machine()}. Each call owns path,
 * en/cn display, resource family, and recipe role together (code-style §3.12).
 */
public final class BuiltinOIPartRoles {

    private static final MachineDomainRegistration MACHINE = OfficialOIPlugin.INSTANCE.machine();

    public static final PartRole ITEM_INPUT = MACHINE.partRole(
            "item_input", "Item Input", "物品输入",
            BuiltinOIResourceIntegrations.ITEM.resourceType(), RecipeRole.INPUT);
    public static final PartRole ITEM_OUTPUT = MACHINE.partRole(
            "item_output", "Item Output", "物品输出",
            BuiltinOIResourceIntegrations.ITEM.resourceType(), RecipeRole.OUTPUT);
    public static final PartRole FLUID_INPUT = MACHINE.partRole(
            "fluid_input", "Fluid Input", "流体输入",
            BuiltinOIResourceIntegrations.FLUID.resourceType(), RecipeRole.INPUT);
    public static final PartRole FLUID_OUTPUT = MACHINE.partRole(
            "fluid_output", "Fluid Output", "流体输出",
            BuiltinOIResourceIntegrations.FLUID.resourceType(), RecipeRole.OUTPUT);
    public static final PartRole ENERGY_INPUT = MACHINE.partRole(
            "energy_input", "Energy Input", "能量输入",
            BuiltinOIResourceIntegrations.ENERGY.resourceType(), RecipeRole.INPUT);
    public static final PartRole ENERGY_OUTPUT = MACHINE.partRole(
            "energy_output", "Energy Output", "能量输出",
            BuiltinOIResourceIntegrations.ENERGY.resourceType(), RecipeRole.OUTPUT);

    private BuiltinOIPartRoles() {}

    public static void init() {
        Objects.requireNonNull(ENERGY_OUTPUT);
    }
}
