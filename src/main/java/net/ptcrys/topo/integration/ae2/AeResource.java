package net.ptcrys.topo.integration.ae2;

import net.neoforged.neoforge.transfer.resource.Resource;

import appeng.api.stacks.AEKey;
import org.jspecify.annotations.Nullable;

/** 任意 AE 键的无损资源身份，包含附属模组定义的键类型；数量仍由请求 Map 承载。 */
public record AeResource(@Nullable AEKey key) implements Resource {

    public static final AeResource EMPTY = new AeResource(null);

    @Override
    public boolean isEmpty() {
        return key == null;
    }
}
