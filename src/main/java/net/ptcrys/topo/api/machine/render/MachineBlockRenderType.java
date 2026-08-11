package net.ptcrys.topo.api.machine.render;

import net.minecraft.resources.Identifier;

public abstract class MachineBlockRenderType<D> {

    private final Identifier id;

    protected MachineBlockRenderType(Identifier id) {
        this.id = id;
    }

    public final Identifier id() {
        return id;
    }

    protected final MachineBlockRenderUse<D> use(D data) {
        return MachineRenderRegistry.useBlock(this, data);
    }
}
