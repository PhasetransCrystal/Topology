package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.machine.data.DataFieldBuilder;
import net.ptcrys.topo.api.machine.data.DataManualDirtyField;
import net.ptcrys.topo.api.machine.data.DataScope;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

@FunctionalInterface
public interface ResourceDataFieldFactory<R extends Resource> {

    DataFieldBuilder<? extends DataManualDirtyField> create(
                                                            DataScope scope,
                                                            String key,
                                                            ResourceHandler<R> handler,
                                                            Runnable afterRead);
}
