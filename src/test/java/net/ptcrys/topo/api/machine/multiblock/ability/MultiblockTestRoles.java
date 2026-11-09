package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.machine.resource.MultiblockTestResources;
import net.ptcrys.topo.api.machine.resource.RecipeRole;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** 包内构造隔离的测试角色，避免在注册表冻结后注册测试内容。 */
public final class MultiblockTestRoles {

    public static final PartRole INPUT = new PartRole(Identifier.parse("topo_test:structure_input"),
            Component.literal("输入"), MultiblockTestResources.TYPE, RecipeRole.INPUT);
    public static final PartRole OUTPUT = new PartRole(Identifier.parse("topo_test:structure_output"),
            Component.literal("输出"), MultiblockTestResources.TYPE, RecipeRole.OUTPUT);

    private MultiblockTestRoles() {}
}
