package net.ptcrys.topo.api.material.form;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * Open identity of one kind of material output. The framework only compares content-type ids; it
 * does not own a closed enum. Built-in item/block/fluid types live in
 * {@link MaterialContentTypes}, while plugins may declare their own namespaced types.
 */
public record MaterialContentType(Identifier id) {

    public MaterialContentType {
        Objects.requireNonNull(id, "content type id");
    }
}
