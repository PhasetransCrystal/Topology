package net.ptcrys.topo.apiv2.material.data;

import net.ptcrys.topo.apiv2.material.Material;

public interface MaterialDataStrategy<D> {

    void validate(Material material, D data);
}
