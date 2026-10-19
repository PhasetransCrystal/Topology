package net.ptcrys.topodemo.data.material;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.MaterialRegistry;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialDataTypes;
import net.ptcrys.topodemo.api.material.BuiltinDemoMaterialForms;
import net.ptcrys.topodemo.data.TopoDemoPlugin;

/** Concrete products compose the API plugin's reusable forms and payload types. */
public final class BuiltinDemoMaterials {

    public static final Material COPPER = material("copper", "Demo Copper", "演示铜", 64).build();
    public static final Material TIN = material("tin", "Demo Tin", "演示锡", 119).build();
    public static final Material BRONZE = material("bronze", "Demo Bronze", "演示青铜", 78).build();

    private BuiltinDemoMaterials() {}

    public static void init() {}

    private static MaterialRegistry.Builder material(String path, String en, String cn, int mass) {
        return TopoDemoPlugin.INSTANCE.material().material(path)
                .lang(en, cn)
                .data(BuiltinDemoMaterialDataTypes.MASS.mass(mass))
                .forms(BuiltinDemoMaterialForms.INGOT, BuiltinDemoMaterialForms.DUST);
    }
}
