package net.ptcrys.topo.api.machine.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.MachineDefinition;

public interface MachineBlockRenderStrategy<D> {

    void validate(D data);

    void applyBlockModel(
                         BlockBuilder<? extends MachineBlock, RegistryCore> builder,
                         D data,
                         MachineDefinition definition);
}
