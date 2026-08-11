package net.ptcrys.topo.apiv2.material.data;

import net.minecraft.resources.Identifier;

public abstract class MaterialDataType<D> {

    private final Identifier id;

    protected MaterialDataType(Identifier id) {
        this.id = id;
    }

    public final Identifier id() {
        return id;
    }

    protected final MaterialDataUse<D> use(D data) {
        return MaterialDataRegistry.use(this, data);
    }
}
