package net.ptcrys.topo.data.machine.common.component.resource;

@FunctionalInterface
interface ContentsChangedListener<S> {

    void onChanged(int index, S previousContents);
}
