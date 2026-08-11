package net.ptcrys.topo.datav2.machine.common.component.resource;

@FunctionalInterface
interface ContentsChangedListener<S> {

    void onChanged(int index, S previousContents);
}
