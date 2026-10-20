package net.ptcrys.topodemo.api.material;

import net.ptcrys.topo.api.api.builtin.MaterialDomainRegistration;
import net.ptcrys.topodemo.api.TopoDemoApiPlugin;
import net.ptcrys.topodemo.api.material.common.form.FormAmountDataType;

/** 形态自身的类型化载荷,与材料数据分开登记。 */
public final class BuiltinFormDataTypes {

    private static final MaterialDomainRegistration MATERIAL = TopoDemoApiPlugin.INSTANCE.material();

    public static final FormAmountDataType AMOUNT = MATERIAL.formDataType("amount", new FormAmountDataType(MATERIAL.id("amount")));

    private BuiltinFormDataTypes() {}

    public static void init() {}
}
