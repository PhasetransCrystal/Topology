package net.ptcrys.topodemo.data.material;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topodemo.api.material.BuiltinMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinMaterialForms;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

public final class BuiltinMaterials {

    public static final Material COPPER = TopoDemoPlugin.INSTANCE.material().material("copper")
            .lang("Copper", "铜")
            .data(BuiltinMaterialDataTypes.MASS.mass(64))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.INGOT, BuiltinMaterialForms.DUST, BuiltinMaterialForms.ORE, BuiltinMaterialForms.DEEPSLATE_ORE)
            .build();

    public static final Material TIN = TopoDemoPlugin.INSTANCE.material().material("tin")
            .lang("Tin", "锡")
            .data(BuiltinMaterialDataTypes.MASS.mass(119))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.INGOT, BuiltinMaterialForms.DUST, BuiltinMaterialForms.ORE, BuiltinMaterialForms.DEEPSLATE_ORE)
            .build();

    public static final Material BRONZE = TopoDemoPlugin.INSTANCE.material().material("bronze")
            .lang("Bronze", "青铜")
            .data(BuiltinMaterialDataTypes.MASS.mass(78))
            .data(BuiltinProcessors.GRINDING.activation())
            .forms(BuiltinMaterialForms.INGOT, BuiltinMaterialForms.DUST)
            .build();

    private BuiltinMaterials() {}

    public static void init() {}
}
