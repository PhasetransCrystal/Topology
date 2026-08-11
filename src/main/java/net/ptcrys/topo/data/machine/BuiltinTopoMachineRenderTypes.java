package net.ptcrys.topo.data.machine;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.machine.common.render.CtmHatchRenderType;
import net.ptcrys.topo.data.machine.common.render.CtmMachineRenderType;
import net.ptcrys.topo.data.machine.common.render.FormedActiveMachineRenderType;
import net.ptcrys.topo.data.machine.common.render.ShellMachineRenderType;
import net.ptcrys.topo.data.machine.common.render.ShellOverlayMachineRenderType;

/** Builtin machine block render types via {@link OfficialTopoPlugin#machine()}. */
public final class BuiltinTopoMachineRenderTypes {

    private static final MachineDomainRegistration MACHINE = OfficialTopoPlugin.INSTANCE.machine();

    public static final ShellMachineRenderType SHELL = MACHINE.renderType(
            "shell",
            new ShellMachineRenderType(MACHINE.id("shell")),
            new ShellMachineRenderType.Strategy());

    public static final ShellOverlayMachineRenderType SHELL_OVERLAY = MACHINE.renderType(
            "shell_overlay",
            new ShellOverlayMachineRenderType(MACHINE.id("shell_overlay")),
            new ShellOverlayMachineRenderType.Strategy());

    public static final FormedActiveMachineRenderType FORMED_ACTIVE = MACHINE.renderType(
            "formed_active",
            new FormedActiveMachineRenderType(MACHINE.id("formed_active")),
            new FormedActiveMachineRenderType.Strategy());

    public static final CtmMachineRenderType CTM_MACHINE = MACHINE.renderType(
            "ctm_machine",
            new CtmMachineRenderType(MACHINE.id("ctm_machine")),
            new CtmMachineRenderType.Strategy());

    public static final CtmHatchRenderType CTM_HATCH = MACHINE.renderType(
            "ctm_hatch",
            new CtmHatchRenderType(MACHINE.id("ctm_hatch")),
            new CtmHatchRenderType.Strategy());

    private BuiltinTopoMachineRenderTypes() {}

    public static void init() {}
}
