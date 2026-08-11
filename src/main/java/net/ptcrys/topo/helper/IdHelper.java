package net.ptcrys.topo.helper;

import net.ptcrys.topo.Topology;

import net.minecraft.resources.Identifier;

public final class IdHelper {

    private IdHelper() {}

    public static Identifier oi(String path) {
        return Identifier.fromNamespaceAndPath(Topology.MODID, path);
    }
}
