package net.ptcrys.topodemo.api.machine;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.machine.FormedMachineBlock;
import net.ptcrys.topo.api.machine.OrientedActiveMachineBlock;
import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.multiblock.ability.PartRole;
import net.ptcrys.topo.api.machine.resource.RecipeRole;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.machine.common.CubeRender;
import net.ptcrys.topodemo.api.machine.common.ItemPort;
import net.ptcrys.topodemo.api.recipe.BuiltinResources;

public final class BuiltinMachineTypes {

    private static final MachineDomainRegistration MACHINE = TopoDemoApiPlugin.INSTANCE.machine();
    public static final CubeRender CUBE = MACHINE.renderType("cube", new CubeRender(MACHINE.id("cube")), new CubeRender.Strategy());
    public static final PartRole INPUT = MACHINE.partRole("item_input", "Item Input Bus", "物品输入总线", BuiltinResources.ITEMS, RecipeRole.INPUT);
    public static final PartRole OUTPUT = MACHINE.partRole("item_output", "Item Output Bus", "物品输出总线", BuiltinResources.ITEMS, RecipeRole.OUTPUT);
    public static final AttachmentType<ItemPort.Metadata> ITEM_PORT = MACHINE.attachmentType("item_port", ItemPort.Metadata.class);

    private BuiltinMachineTypes() {}

    public static void init() {
        var lang = TopoDemoApiPlugin.INSTANCE.lang();
        MACHINE.propertyBoolean("formed",
                lang.key("machine", "state.formed", "Structure", "结构"),
                lang.key("machine", "state.formed.yes", "Formed", "已成型"),
                lang.key("machine", "state.formed.no", "Incomplete", "未成型"),
                FormedMachineBlock.FORMED);
        MACHINE.propertyBoolean("active",
                lang.key("machine", "state.active", "Activity", "工作状态"),
                lang.key("machine", "state.active.yes", "Working", "运行中"),
                lang.key("machine", "state.active.no", "Idle", "空闲"),
                OrientedActiveMachineBlock.ACTIVE);
    }
}
