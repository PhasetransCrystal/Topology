package net.ptcrys.topodemo.api.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.MassDataType;
import net.ptcrys.topodemo.api.material.common.RgbColorDataType;

public final class BuiltinMaterialDataTypes {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MassDataType MASS = MATERIAL.dataType("mass", new MassDataType(MATERIAL.id("mass")));

    public static final RgbColorDataType PRIMARY_COLOR = MATERIAL.dataType("primary_color", new RgbColorDataType(MATERIAL.id("primary_color")));

    private BuiltinMaterialDataTypes() {}

    /** First invocation triggers static declarations; subsequent invocations are harmless. */
    public static void init() {}
}
