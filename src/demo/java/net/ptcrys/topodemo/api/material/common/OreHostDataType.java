package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Predicate;

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

    public Predicate<Material> is(Host host) {
        Objects.requireNonNull(host, "host");
        return material -> material.strategy().data(this).filter(host::equals).isPresent();
    }
}
