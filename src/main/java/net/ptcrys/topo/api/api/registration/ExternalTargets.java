package net.ptcrys.topo.api.api.registration;

import net.minecraft.resources.Identifier;

public final class ExternalTargets {

    private ExternalTargets() {}

    public static ExternalItemTarget mcItem(String path) {
        return new ExternalItemTarget(Identifier.withDefaultNamespace(path));
    }

    public static ExternalBlockTarget mcBlock(String path) {
        return new ExternalBlockTarget(Identifier.withDefaultNamespace(path));
    }
}
