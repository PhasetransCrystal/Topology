package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

import java.util.Set;
import java.util.function.Predicate;

/** Geological occurrence; absence means that no natural ore processing is declared. */
public final class OreHostDataType extends MaterialDataType<Set<OreHostDataType.Host>> {

    public enum Host {
        OVERWORLD,
        NETHER
    }

    public OreHostDataType(Identifier id) {
        super(id);
    }

    /** 声明材料可生成的宿主；至少指定一个，不允许重复。 */
    public MaterialDataUse<Set<Host>> host(Host... hosts) {
        Set<Host> values = Set.of(hosts);
        if (values.isEmpty()) throw new IllegalArgumentException("ore host must not be empty");
        return use(values);
    }

    /** 判断材料是否支持给定宿主。 */
    public Predicate<Material> has(Host host) {
        return matches(hosts -> hosts.contains(host));
    }
}
