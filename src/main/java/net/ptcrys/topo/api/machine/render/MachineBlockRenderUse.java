package net.ptcrys.topo.api.machine.render;

public final class MachineBlockRenderUse<D> {

    private final MachineBlockRenderType<D> type;
    private final D data;

    MachineBlockRenderUse(MachineBlockRenderType<D> type, D data) {
        this.type = type;
        this.data = data;
    }

    public MachineBlockRenderType<D> type() {
        return type;
    }

    public D data() {
        return data;
    }
}
