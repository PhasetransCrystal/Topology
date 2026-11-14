package net.ptcrys.topodemo.api.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.material.data.MaterialFlagDataType;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.FuelDataType;
import net.ptcrys.topodemo.api.material.common.GemDataType;
import net.ptcrys.topodemo.api.material.common.MassDataType;
import net.ptcrys.topodemo.api.material.common.OreDropDataType;
import net.ptcrys.topodemo.api.material.common.OreHostDataType;
import net.ptcrys.topodemo.api.material.common.OreProductDataType;
import net.ptcrys.topodemo.api.material.common.RadiationDataType;
import net.ptcrys.topodemo.api.material.common.RgbColorDataType;
import net.ptcrys.topodemo.api.material.common.ToolDataType;

public final class BuiltinMaterialDataTypes {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MassDataType MASS = MATERIAL.dataType("mass", new MassDataType(MATERIAL.id("mass")));

    public static final RgbColorDataType PRIMARY_COLOR = MATERIAL.dataType("primary_color", new RgbColorDataType(MATERIAL.id("primary_color")));

    public static final MaterialFlagDataType INGOT = MATERIAL.dataType("ingot", new MaterialFlagDataType(MATERIAL.id("ingot")));
    public static final GemDataType GEM = MATERIAL.dataType("gem", new GemDataType(MATERIAL.id("gem")));
    public static final MaterialFlagDataType SHARD = MATERIAL.dataType("shard", new MaterialFlagDataType(MATERIAL.id("shard")));
    public static final MaterialFlagDataType GAS = MATERIAL.dataType("gas", new MaterialFlagDataType(MATERIAL.id("gas")));
    public static final MaterialFlagDataType SOLUTION = MATERIAL.dataType("solution", new MaterialFlagDataType(MATERIAL.id("solution")));
    public static final FuelDataType FUEL = MATERIAL.dataType("fuel", new FuelDataType(MATERIAL.id("fuel")));
    public static final ToolDataType TOOL = MATERIAL.dataType("tool", new ToolDataType(MATERIAL.id("tool")));
    public static final RadiationDataType RADIATION = MATERIAL.dataType("radiation", new RadiationDataType(MATERIAL.id("radiation")));
    public static final MaterialFlagDataType PULVERIZABLE = MATERIAL.dataType("pulverizable", new MaterialFlagDataType(MATERIAL.id("pulverizable")));
    public static final MaterialFlagDataType BULK = MATERIAL.dataType("bulk", new MaterialFlagDataType(MATERIAL.id("bulk")));
    public static final OreDropDataType ORE_DROP = MATERIAL.dataType("ore_drop", new OreDropDataType(MATERIAL.id("ore_drop")));
    public static final OreHostDataType ORE_HOST = MATERIAL.dataType("ore_host", new OreHostDataType(MATERIAL.id("ore_host")));
    public static final OreProductDataType ORE_PRODUCT = MATERIAL.dataType("ore_product", new OreProductDataType(MATERIAL.id("ore_product")));

    static {
        GEM.excludes(INGOT);
    }

    private BuiltinMaterialDataTypes() {}

    /** First invocation triggers static declarations; subsequent invocations are harmless. */
    public static void init() {}
}
