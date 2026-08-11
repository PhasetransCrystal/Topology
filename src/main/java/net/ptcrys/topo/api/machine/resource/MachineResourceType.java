package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.machine.MachineBlockEntity;
import net.ptcrys.topo.api.machine.data.DataFieldBuilder;
import net.ptcrys.topo.api.machine.data.DataManualDirtyField;
import net.ptcrys.topo.api.machine.data.DataScope;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Registered machine resource kind.
 *
 * <p>
 * The handle is also the strategy (H == S): it owns the stable identity, the resource Java type,
 * the optional NeoForge block capability exposed to automation, and the default aggregation policy.
 */
public final class MachineResourceType<R extends Resource> {

    private final Identifier id;
    private final Class<R> resourceClass;
    private final @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability;
    private final ResourceDataFieldFactory<R> dataFieldFactory;
    private @Nullable LangKey nameLang;

    MachineResourceType(
                        Identifier id,
                        Class<R> resourceClass,
                        @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability,
                        ResourceDataFieldFactory<R> dataFieldFactory) {
        this.id = Objects.requireNonNull(id, "machine resource type id");
        this.resourceClass = Objects.requireNonNull(resourceClass, "resource class");
        this.blockCapability = blockCapability;
        this.dataFieldFactory = Objects.requireNonNull(dataFieldFactory, "data field factory");
    }

    /**
     * Binds the display-name handle minted at resource-family registration. Prefer
     * {@link MachineDomainRegistration#bindResourceName}; product tables
     * must not invent {@link LangKey}s and bind later.
     */
    public void bindNameLang(LangKey nameLang) {
        if (this.nameLang != null) {
            throw new IllegalStateException("name lang already bound for " + id);
        }
        this.nameLang = Objects.requireNonNull(nameLang, "nameLang");
    }

    public boolean hasNameLang() {
        return nameLang != null;
    }

    public Identifier id() {
        return id;
    }

    public LangKey nameLang() {
        return Objects.requireNonNull(nameLang, "name lang not bound for " + id);
    }

    public Component displayName() {
        return nameLang().getComponent();
    }

    public Class<R> resourceClass() {
        return resourceClass;
    }

    public @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability() {
        return blockCapability;
    }

    public DataFieldBuilder<? extends DataManualDirtyField> dataField(
                                                                      DataScope scope,
                                                                      String key,
                                                                      ResourceHandler<R> handler,
                                                                      Runnable afterRead) {
        return dataFieldFactory.create(scope, key, handler, afterRead);
    }

    public @Nullable ResourceHandler<R> combine(List<? extends ResourceHandler<R>> handlers) {
        Objects.requireNonNull(handlers, "resource handlers");
        return switch (handlers.size()) {
            case 0 -> null;
            case 1 -> handlers.get(0);
            default -> new CombinedLongResourceHandler<>(handlers);
        };
    }

    public void registerBlockEntityCapability(
                                              RegisterCapabilitiesEvent event,
                                              BlockEntityType<MachineBlockEntity> blockEntityType) {
        Objects.requireNonNull(event, "capabilities event");
        Objects.requireNonNull(blockEntityType, "machine block entity type");
        BlockCapability<ResourceHandler<R>, @Nullable Direction> capability = blockCapability;
        if (capability != null) {
            event.registerBlockEntity(
                    capability,
                    blockEntityType,
                    (machine, side) -> machine.machineComponents().resources().transferSide().handler(this, side));
        }
    }

    @SuppressWarnings("unchecked")
    public ResourceHandler<R> castHandler(ResourceHandler<?> handler) {
        return (ResourceHandler<R>) Objects.requireNonNull(handler, "resource handler");
    }
}
