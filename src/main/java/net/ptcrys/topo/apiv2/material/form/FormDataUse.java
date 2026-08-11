package net.ptcrys.topo.apiv2.material.form;

/** A minted (type, value) payload entry for one form declaration; see {@link FormDataType}. */
public final class FormDataUse<D> {

    private final FormDataType<D> type;
    private final D data;

    FormDataUse(FormDataType<D> type, D data) {
        this.type = type;
        this.data = data;
    }

    public FormDataType<D> type() {
        return type;
    }

    public D data() {
        return data;
    }
}
