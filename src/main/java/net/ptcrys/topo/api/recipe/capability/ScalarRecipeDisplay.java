package net.ptcrys.topo.api.recipe.capability;

import net.ptcrys.topo.api.machine.resource.MachineResourceType;

import net.minecraft.network.chat.Component;

/** 标量配方能力可选的展示扩展；不要求继承内容模组的能量或热量类型。 */
public interface ScalarRecipeDisplay {

    MachineResourceType<?> resourceType();

    Component displayName();

    int color();
}
