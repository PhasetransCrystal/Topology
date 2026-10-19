package net.ptcrys.topodemo.api.material;

import net.ptcrys.registrylib.util.TextureRef;
import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.api.material.form.MaterialForm;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.form.ItemForm;

public final class BuiltinDemoMaterialForms {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final MaterialForm INGOT = MATERIAL.form("ingot")
            .lang("Ingot", "锭")
            .strategy(new ItemForm("%s_ingot", "%s Ingot", "%s锭", TextureRef.mc("item/iron_ingot")))
            .build();

    public static final MaterialForm DUST = MATERIAL.form("dust")
            .lang("Dust", "粉")
            .strategy(new ItemForm("%s_dust", "%s Dust", "%s粉", TextureRef.mc("item/gunpowder")))
            .build();

    private BuiltinDemoMaterialForms() {}

    public static void init() {}
}
