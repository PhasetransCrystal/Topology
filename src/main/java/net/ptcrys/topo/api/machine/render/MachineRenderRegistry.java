package net.ptcrys.topo.api.machine.render;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.topo.api.api.builtin.MachineDomainRegistration;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.api.machine.MachineBlock;
import net.ptcrys.topo.api.machine.MachineDefinition;

import net.minecraft.resources.Identifier;

public final class MachineRenderRegistry {

    private static final FreezableStrategyRegistry<Identifier, MachineBlockRenderType<?>, MachineBlockRenderStrategy<?>> BLOCK_REGISTRY = FreezableStrategyRegistry.create("machine block render types");

    private MachineRenderRegistry() {}

    /**
     * Single write entry for {@link MachineDomainRegistration#renderType}.
     * Product code must not call this.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <D, H extends MachineBlockRenderType<D>> H beginBlock(
                                                                        Identifier id, H handle, MachineBlockRenderStrategy<D> strategy) {
        BLOCK_REGISTRY.register(id, handle, (MachineBlockRenderStrategy) strategy);
        return handle;
    }

    public static MachineBlockRenderType<?> requireBlock(Identifier id) {
        return BLOCK_REGISTRY.require(id);
    }

    public static boolean isFrozen() {
        return BLOCK_REGISTRY.isFrozen();
    }

    public static void freeze() {
        BLOCK_REGISTRY.freeze();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void validate(MachineBlockRenderUse<?> render) {
        MachineBlockRenderStrategy strategy = BLOCK_REGISTRY.strategy(render.type().id());
        if (strategy == null) {
            throw new IllegalStateException("no machine block render strategy for " + render.type().id());
        }
        strategy.validate(render.data());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void applyBlockModel(
                                       MachineBlockRenderUse<?> render,
                                       BlockBuilder<? extends MachineBlock, RegistryCore> builder,
                                       MachineDefinition definition) {
        MachineBlockRenderStrategy strategy = BLOCK_REGISTRY.strategy(render.type().id());
        if (strategy == null) {
            throw new IllegalStateException("no machine block render strategy for " + render.type().id());
        }
        strategy.applyBlockModel(builder, render.data(), definition);
    }

    static <D> MachineBlockRenderUse<D> useBlock(MachineBlockRenderType<D> type, D data) {
        BLOCK_REGISTRY.verifyOwnedHandle(type.id(), type);
        return new MachineBlockRenderUse<>(type, data);
    }
}
