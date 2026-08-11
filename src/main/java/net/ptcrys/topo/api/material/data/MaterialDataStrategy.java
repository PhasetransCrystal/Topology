package net.ptcrys.topo.api.material.data;

import net.ptcrys.topo.api.material.Material;

public interface MaterialDataStrategy<D> {

    void validate(Material material, D data);
}
