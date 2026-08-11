package net.ptcrys.topo.apiv2.machine.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.topo.apiv2.machine.MachineBlock;
import net.ptcrys.topo.apiv2.machine.MachineDefinition;

public interface MachineBlockRenderStrategy<D> {

    void validate(D data);

    void applyBlockModel(
                         BlockBuilder<? extends MachineBlock, RegistryCore> builder,
                         D data,
                         MachineDefinition definition);
}
