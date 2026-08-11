package net.ptcrys.topo.apiv2.material.form;

import net.minecraft.resources.Identifier;

/**
 * Typed payload key on a material form declaration. A form declares heterogeneous payloads
 * (amount, future per-form data) as {@code FormDataUse} entries minted by the owning type;
 * consumers read them back type-safely via {@link MaterialFormStrategy#data(FormDataType)}.
 *
 * <p>
 * KHSD citizen, symmetric with {@code MaterialDataType}: the use constructor is
 * package-private, only the registered handle can mint its own values, and minting verifies
 * handle ownership against {@link FormDataRegistry}.
 */
public abstract class FormDataType<D> {

    private final Identifier id;

    protected FormDataType(Identifier id) {
        this.id = id;
    }

    public final Identifier id() {
        return id;
    }

    protected final FormDataUse<D> use(D data) {
        return FormDataRegistry.use(this, data);
    }
}
