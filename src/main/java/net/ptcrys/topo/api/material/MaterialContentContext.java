package net.ptcrys.topo.api.material;

import net.ptcrys.registrylib.RegistryCore;

/** Context passed to a form strategy's register(...): the RegistryLib core + the material being registered. */
public record MaterialContentContext(RegistryCore core, Material material) {}
