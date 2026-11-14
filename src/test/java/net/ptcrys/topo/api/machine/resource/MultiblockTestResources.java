package net.ptcrys.topo.api.machine.resource;

import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.resource.Resource;

/** 仅供纯结构测试使用，不写入全局注册表，也不创建资源存储。 */
public final class MultiblockTestResources {

    public static final MachineResourceType<Resource> TYPE = new MachineResourceType<>(
            Identifier.parse("topo_test:structure_resource"), Resource.class, null,
            (scope, key, handler, afterRead) -> { throw new UnsupportedOperationException("Structure-only fixture"); });

    private MultiblockTestResources() {}
}
