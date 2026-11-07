package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Geological occurrence; absence means that no natural ore processing is declared. */
public final class OreHostDataType extends MaterialDataType<OreHostDataType.Host> {

    public enum Host {
        OVERWORLD,
        NETHER
    }

    public OreHostDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<Host> host(Host host) {
        return use(Objects.requireNonNull(host, "host"));
    }
}
