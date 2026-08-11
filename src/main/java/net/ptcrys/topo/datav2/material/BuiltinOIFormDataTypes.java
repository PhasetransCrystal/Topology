package net.ptcrys.topo.datav2.material;

import net.ptcrys.topo.apiv2.plugin.MaterialDomainRegistration;
import net.ptcrys.topo.datav2.OfficialOIPlugin;
import net.ptcrys.topo.datav2.material.common.form.FormAmountDataType;

/** Builtin form payload types: the full decision list of what data a form declaration can carry. */
public final class BuiltinOIFormDataTypes {

    private static final MaterialDomainRegistration MATERIALS = OfficialOIPlugin.INSTANCE.material();

    /** Material quantity per item, in 72-units-per-ingot; declared only on direct unit-conversion forms. */
    public static final FormAmountDataType AMOUNT = MATERIALS.formDataType("amount", new FormAmountDataType(MATERIALS.id("amount")));

    private BuiltinOIFormDataTypes() {}

    public static void init() {}
}
