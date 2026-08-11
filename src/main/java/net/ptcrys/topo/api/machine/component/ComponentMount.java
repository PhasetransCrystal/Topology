package net.ptcrys.topo.api.machine.component;

import net.ptcrys.topo.api.machine.MachineBlockEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A machine-definition contribution that mounts one trait instance and knows how to create its
 * runtime object for a block entity.
 */
public final class ComponentMount<T extends MachineComponent> {

    private final ComponentKey<T> key;
    private final Function<? super ComponentContext<T>, ? extends T> factory;
    private final List<Attachment> metadata;
    private final Map<AttachmentType<?>, List<Attachment>> metadataByType;

    ComponentMount(ComponentKey<T> key, Function<? super ComponentContext<T>, ? extends T> factory) {
        this(key, factory, List.of());
    }

    ComponentMount(
                   ComponentKey<T> key,
                   Function<? super ComponentContext<T>, ? extends T> factory,
                   List<? extends Attachment> metadata) {
        this.key = Objects.requireNonNull(key, "trait key");
        this.factory = Objects.requireNonNull(factory, "trait factory");
        this.metadata = List.copyOf(Objects.requireNonNull(metadata, "trait metadata"));
        this.metadataByType = indexMetadata(this.metadata);
        // Every construction path (initial mount, withMetadata re-attach) re-validates each entry's
        // structural invariant against the assembled host, so forged metadata/mount combinations
        // fail right at the attach expression.
        for (Attachment entry : this.metadata) {
            entry.validateHost(this);
        }
    }

    public ComponentKey<T> key() {
        return key;
    }

    public List<Attachment> metadata() {
        return metadata;
    }

    public <M extends Attachment> List<M> metadata(AttachmentType<M> metadataType) {
        Objects.requireNonNull(metadataType, "metadata type");
        List<Attachment> entries = metadataByType.get(metadataType);
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }
        List<M> matches = new ArrayList<>();
        for (Attachment candidate : entries) {
            matches.add(metadataType.cast(candidate));
        }
        return List.copyOf(matches);
    }

    public ComponentMount<T> withMetadata(Attachment... additionalMetadata) {
        Objects.requireNonNull(additionalMetadata, "additional metadata");
        if (additionalMetadata.length == 0) {
            return this;
        }
        List<Attachment> updated = new ArrayList<>(metadata.size() + additionalMetadata.length);
        updated.addAll(metadata);
        updated.addAll(Arrays.asList(additionalMetadata));
        return new ComponentMount<>(key, factory, updated);
    }

    private static Map<AttachmentType<?>, List<Attachment>> indexMetadata(
                                                                          List<Attachment> metadata) {
        if (metadata.isEmpty()) {
            return Map.of();
        }
        Map<AttachmentType<?>, List<Attachment>> mutable = new LinkedHashMap<>();
        for (Attachment entry : metadata) {
            Objects.requireNonNull(entry, "trait metadata entry");
            AttachmentType<?> type = Objects.requireNonNull(entry.type(), "trait metadata type");
            mutable.computeIfAbsent(type, ignored -> new ArrayList<>()).add(entry);
        }
        Map<AttachmentType<?>, List<Attachment>> indexed = new LinkedHashMap<>();
        for (Map.Entry<AttachmentType<?>, List<Attachment>> entry : mutable.entrySet()) {
            indexed.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(indexed);
    }

    MachineComponent create(MachineBlockEntity machine) {
        ComponentContext<T> context = new ComponentContext<>(key, machine, machine.data().scope(key));
        T trait = Objects.requireNonNull(factory.apply(context), () -> "Trait factory returned null for " + key);
        return key.cast(trait);
    }
}
