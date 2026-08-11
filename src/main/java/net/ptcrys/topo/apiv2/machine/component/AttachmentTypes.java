package net.ptcrys.topo.apiv2.machine.component;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;

import net.minecraft.resources.Identifier;

import java.util.List;

/** Registry for trait mount metadata types. H == S: the metadata type object is its strategy. */
public final class AttachmentTypes {

    private static final FreezableStrategyRegistry<Identifier, AttachmentType<?>, AttachmentType<?>> REGISTRY = FreezableStrategyRegistry.create("trait mount metadata types");

    private AttachmentTypes() {}

    /**
     * Single write entry for {@link net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration#attachmentType}.
     * Product code must not call this.
     */
    public static <M extends Attachment> AttachmentType<M> begin(Identifier id, Class<M> metadataClass) {
        AttachmentType<M> type = new AttachmentType<>(id, metadataClass);
        REGISTRY.register(id, type, type);
        return type;
    }

    public static AttachmentType<?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<AttachmentType<?>> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }
}
