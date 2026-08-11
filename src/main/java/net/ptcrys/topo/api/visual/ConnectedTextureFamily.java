package net.ptcrys.topo.api.visual;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Identity token of one connected-texture group: blocks whose hosts report the same family merge
 * their casing texture across touching faces. Ported from the old GTOdyssey visual API.
 */
public record ConnectedTextureFamily(Identifier id) {

    public ConnectedTextureFamily {
        Objects.requireNonNull(id, "id");
    }
}
