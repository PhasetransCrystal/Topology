package net.ptcrys.topodemo.api.material.common;

import net.ptcrys.topo.api.material.Material;
import net.ptcrys.topo.api.material.data.MaterialDataType;
import net.ptcrys.topo.api.material.data.MaterialDataUse;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.function.Predicate;

/** Product recovered from a material's natural ore before refining. */
public final class OreProductDataType extends MaterialDataType<OreProductDataType.Product> {

    public enum Product {
        RAW,
        SCRAP
    }

    public OreProductDataType(Identifier id) {
        super(id);
    }

    public MaterialDataUse<Product> product(Product product) {
        return use(Objects.requireNonNull(product, "product"));
    }

    public Predicate<Material> is(Product product) {
        Objects.requireNonNull(product, "product");
        return material -> material.strategy().data(this).filter(product::equals).isPresent();
    }
}
