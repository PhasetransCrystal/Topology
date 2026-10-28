package net.ptcrys.topodemo.api.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.MassDataType;
import net.ptcrys.topodemo.api.material.common.MaterialKindDataType;
import net.ptcrys.topodemo.api.material.common.OreHostDataType;
import net.ptcrys.topodemo.api.material.common.OreProductDataType;
import net.ptcrys.topodemo.api.material.common.RgbColorDataType;

public final class BuiltinMaterialDataTypes {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MassDataType MASS = MATERIAL.dataType("mass", new MassDataType(MATERIAL.id("mass")));

    public static final RgbColorDataType PRIMARY_COLOR = MATERIAL.dataType("primary_color", new RgbColorDataType(MATERIAL.id("primary_color")));

    public static final MaterialKindDataType KIND = MATERIAL.dataType("kind", new MaterialKindDataType(MATERIAL.id("kind")));
    public static final OreHostDataType ORE_HOST = MATERIAL.dataType("ore_host", new OreHostDataType(MATERIAL.id("ore_host")));
    public static final OreProductDataType ORE_PRODUCT = MATERIAL.dataType("ore_product", new OreProductDataType(MATERIAL.id("ore_product")));

    private BuiltinMaterialDataTypes() {}

    /** First invocation triggers static declarations; subsequent invocations are harmless. */
    public static void init() {}
}
