package net.ptcrys.topo.api.material.form;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * One registry entry a material form will create or reuse. Forms report these keys before content
 * generation so the material pipeline can reject duplicate ids during freeze. The content type is
 * open; built-in forms use {@link MaterialContentTypes}.
 */
public record MaterialContentKey(MaterialContentType type, Identifier id) {

    public MaterialContentKey {
        Objects.requireNonNull(type, "content type");
        Objects.requireNonNull(id, "content id");
    }
}
