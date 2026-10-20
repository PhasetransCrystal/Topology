package net.ptcrys.topodemo.api.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.MassDataType;

public final class BuiltinMaterialDataTypes {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MassDataType MASS = MATERIAL.dataType("mass", new MassDataType(MATERIAL.id("mass")));

    private BuiltinMaterialDataTypes() {}

    /** First invocation triggers static declarations; subsequent invocations are harmless. */
    public static void init() {}
}
