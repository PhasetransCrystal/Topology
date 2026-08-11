package net.ptcrys.topo.data.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topo.data.OfficialTopoPlugin;
import net.ptcrys.topo.data.material.common.form.FormAmountDataType;

/** Builtin form payload types: the full decision list of what data a form declaration can carry. */
public final class BuiltinTopoFormDataTypes {

    private static final MaterialDomainRegistration MATERIALS = OfficialTopoPlugin.INSTANCE.material();

    /** Material quantity per item, in 72-units-per-ingot; declared only on direct unit-conversion forms. */
    public static final FormAmountDataType AMOUNT = MATERIALS.formDataType("amount", new FormAmountDataType(MATERIALS.id("amount")));

    private BuiltinTopoFormDataTypes() {}

    public static void init() {}
}
