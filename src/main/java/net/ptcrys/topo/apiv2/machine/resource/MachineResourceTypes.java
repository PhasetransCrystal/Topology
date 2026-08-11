package net.ptcrys.topo.apiv2.machine.resource;

import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.apiv2.machine.MachineBlockEntity;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.util.ValueIOSerializable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.resource.Resource;

import org.jspecify.annotations.Nullable;

import java.util.List;

public final class MachineResourceTypes {

    private static final FreezableStrategyRegistry<Identifier, MachineResourceType<?>, MachineResourceType<?>> REGISTRY = FreezableStrategyRegistry.create("machine resource types");

    private MachineResourceTypes() {}

    /**
     * Single write entry for {@link net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration} resource
     * methods. Product code must not call this.
     */
    public static <R extends Resource> MachineResourceType<R> begin(
                                                                    Identifier id,
                                                                    Class<R> resourceClass,
                                                                    @Nullable BlockCapability<ResourceHandler<R>, @Nullable Direction> blockCapability,
                                                                    ResourceDataFieldFactory<R> dataFieldFactory) {
        MachineResourceType<R> type = new MachineResourceType<>(id, resourceClass, blockCapability, dataFieldFactory);
        REGISTRY.register(id, type, type);
        return type;
    }

    public static <R extends Resource> ResourceDataFieldFactory<R> valueIoDataFieldFactory() {
        return (scope, key, handler, afterRead) -> {
            if (handler instanceof ValueIOSerializable valueIo) {
                return scope.valueIoField(key, valueIo::serialize, valueIo::deserialize, afterRead);
            }
            throw new IllegalArgumentException(
                    "Resource handler for '" + key + "' does not implement " + ValueIOSerializable.class.getSimpleName());
        };
    }

    public static MachineResourceType<?> require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static List<MachineResourceType<?>> registered() {
        return REGISTRY.handlesView();
    }

    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }

    public static void freeze() {
        for (MachineResourceType<?> type : REGISTRY.handlesView()) {
            if (!type.hasNameLang()) {
                throw new IllegalStateException(
                        "machine resource type " + type.id() + " has no display name; bind via machine().bindResourceName in the same factory");
            }
        }
        REGISTRY.freeze();
    }

    public static void registerBlockEntityCapabilities(
                                                       RegisterCapabilitiesEvent event,
                                                       BlockEntityType<MachineBlockEntity> blockEntityType) {
        if (!isFrozen()) {
            throw new IllegalStateException("Machine resource types must be frozen before capability registration");
        }
        for (MachineResourceType<?> type : registered()) {
            type.registerBlockEntityCapability(event, blockEntityType);
        }
    }
}
