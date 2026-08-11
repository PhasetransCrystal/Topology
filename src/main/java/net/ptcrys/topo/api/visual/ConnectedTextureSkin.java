package net.ptcrys.topo.api.visual;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * One connected-texture surface, atomically paired: the {@link ConnectedTextureFamily identity
 * token} plus the base and CTM textures every block wearing that surface renders with. Declared
 * once next to the family's casing block; render contributions consume the skin as a single strong
 * handle instead of hand-pairing (family, base, ctm) at every call site.
 *
 * <p>
 * The family stays an id-only token because model JSON round-trips it by id alone; the textures
 * ride here, on the declaration-side value.
 */
public record ConnectedTextureSkin(ConnectedTextureFamily family, Identifier base, Identifier ctm) {

    public ConnectedTextureSkin {
        Objects.requireNonNull(family, "connected texture family");
        Objects.requireNonNull(base, "base texture");
        Objects.requireNonNull(ctm, "ctm texture");
    }
}
