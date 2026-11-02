package net.ptcrys.topo.api.material.form;

import net.ptcrys.topo.helper.IdHelper;

/** Built-in content-type ids used by the stock material forms. */
public final class MaterialContentTypes {

    public static final MaterialContentType ITEM = new MaterialContentType(IdHelper.id("material/content/item"));
    public static final MaterialContentType BLOCK = new MaterialContentType(IdHelper.id("material/content/block"));
    public static final MaterialContentType FLUID = new MaterialContentType(IdHelper.id("material/content/fluid"));

    private MaterialContentTypes() {}
}
