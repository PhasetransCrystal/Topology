package net.ptcrys.topo.api.machine.component;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/** KHS handle/strategy type for read-only declaration metadata attached to trait mounts. */
public final class AttachmentType<M extends Attachment> {

    private final Identifier id;
    private final Class<M> metadataClass;

    AttachmentType(Identifier id, Class<M> metadataClass) {
        this.id = Objects.requireNonNull(id, "metadata type id");
        this.metadataClass = Objects.requireNonNull(metadataClass, "metadata class");
    }

    public Identifier id() {
        return id;
    }

    M cast(Attachment metadata) {
        if (!metadataClass.isInstance(metadata)) {
            throw new IllegalStateException("Trait mount metadata '" + id + "' is " + metadata.getClass().getName() + ", expected " + metadataClass.getName());
        }
        return metadataClass.cast(metadata);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof AttachmentType<?> other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id + "<" + metadataClass.getSimpleName() + ">";
    }
}
