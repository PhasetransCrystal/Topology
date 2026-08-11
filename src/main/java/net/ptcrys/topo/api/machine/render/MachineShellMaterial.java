package net.ptcrys.topo.api.machine.render;

import net.minecraft.resources.Identifier;

import java.util.Objects;

public final class MachineShellMaterial {

    private final Identifier bottomTexture;
    private final Identifier sideTexture;
    private final Identifier topTexture;

    public MachineShellMaterial(
                                Identifier bottomTexture,
                                Identifier sideTexture,
                                Identifier topTexture) {
        this.bottomTexture = Objects.requireNonNull(bottomTexture, "bottom texture");
        this.sideTexture = Objects.requireNonNull(sideTexture, "side texture");
        this.topTexture = Objects.requireNonNull(topTexture, "top texture");
    }

    public Identifier bottomTexture() {
        return bottomTexture;
    }

    public Identifier sideTexture() {
        return sideTexture;
    }

    public Identifier topTexture() {
        return topTexture;
    }
}
