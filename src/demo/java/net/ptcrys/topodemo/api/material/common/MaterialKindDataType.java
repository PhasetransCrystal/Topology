package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Predicate;

/** Classification shared by material declarations and independently registered form rules. */
public final class MaterialKindDataType extends MaterialDataType<MaterialKindDataType.Kind> {

    public enum Kind {
        METAL,
        GEM,
        FUEL,
        DUST,
        SHARD
    }

    public MaterialKindDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<Kind> kind(Kind kind) {
        return use(Objects.requireNonNull(kind, "kind"));
    }

    public Predicate<Material> is(Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return material -> material.strategy().data(this).filter(kind::equals).isPresent();
    }
}
