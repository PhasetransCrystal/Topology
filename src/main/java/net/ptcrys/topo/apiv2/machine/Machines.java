package net.ptcrys.topo.apiv2.machine;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockEntityBuilder;
import net.ptcrys.registrylib.util.entry.BlockEntityTypeEntry;
import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.topo.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.apiv2.machine.component.Attachment;
import net.ptcrys.topo.apiv2.machine.component.ComponentContribution;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderUse;
import net.ptcrys.topo.apiv2.machine.resource.MachineResourceTypes;
import net.ptcrys.topo.apiv2.machine.resource.MachineSearchPoolConfig;
import net.ptcrys.topo.apiv2.machine.resource.RecipeRole;
import net.ptcrys.topo.apiv2.machine.resource.RecipeSearchPoolId;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePortMetadata;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Machine table (query + freeze + block/BE mint). Product writes only via
 * {@link net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration#machine(String)}.
 *
 * <p>
 * A {@link MachineDefinition} plays both the handle (H) and strategy (S) roles. Namespace and
 * block {@link RegistryCore} are fixed when the builder is created by the plugin domain API.
 */
public final class Machines {

    private static final FreezableStrategyRegistry<Identifier, MachineDefinition, MachineDefinition> REGISTRY = FreezableStrategyRegistry.create("machines");
    private static volatile @Nullable BlockEntityTypeEntry<MachineBlockEntity> blockEntityType;

    private Machines() {}

    /**
     * Single write entry for {@link net.ptcrys.topo.apiv2.plugin.MachineDomainRegistration}. {@code id}
     * namespace must match the plugin; {@code registry} is the plugin's RegistryLib handle used to
     * mint the machine block.
     */
    public static Builder begin(Identifier id, RegistryCore registry) {
        return new Builder(
                Objects.requireNonNull(id, "machine id"),
                Objects.requireNonNull(registry, "registry core"));
    }

    public static MachineDefinition require(Identifier id) {
        return REGISTRY.require(id);
    }

    /** Frozen, cached view of every registered machine. Safe to iterate without per-call allocation. */
    public static List<MachineDefinition> registered() {
        return REGISTRY.handlesView();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    /**
     * Engine driver: register each machine block on its owning {@link RegistryCore}, then bind all
     * of them to one shared machine block-entity type on {@code blockEntityHost} (typically the
     * host mod registry).
     */
    public static void registerBlocksAndBlockEntity(RegistryCore blockEntityHost) {
        Objects.requireNonNull(blockEntityHost, "block entity host registry");
        if (!REGISTRY.isFrozen()) {
            throw new IllegalStateException("Machine registry must be frozen before machine blocks are registered");
        }
        if (blockEntityType != null) {
            throw new IllegalStateException("Machine block-entity type is already registered");
        }
        List<BlockEntry<? extends MachineBlock>> blocks = new ArrayList<>();
        for (MachineDefinition definition : registered()) {
            blocks.add(definition.registerBlockEntry());
        }
        if (blocks.isEmpty()) {
            return;
        }

        BlockEntityBuilder<MachineBlockEntity, RegistryCore> beBuilder = blockEntityHost.blockEntity("machine", Machines::createBlockEntity);
        for (BlockEntry<? extends MachineBlock> block : blocks) {
            beBuilder.validBlock(block);
        }
        blockEntityType = beBuilder.register();
    }

    public static void registerResourceCapabilities(IEventBus modEventBus) {
        Objects.requireNonNull(modEventBus, "mod event bus");
        modEventBus.addListener(Machines::onRegisterCapabilities);
    }

    public static BlockEntityType<MachineBlockEntity> sharedBlockEntityType() {
        BlockEntityTypeEntry<MachineBlockEntity> sharedType = blockEntityType;
        if (sharedType == null) {
            throw new IllegalStateException("Machine block-entity type is not registered yet");
        }
        return sharedType.get();
    }

    public static boolean hasSharedBlockEntityType() {
        return blockEntityType != null;
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        BlockEntityTypeEntry<MachineBlockEntity> sharedType = blockEntityType;
        if (sharedType == null) {
            return;
        }

        MachineResourceTypes.registerBlockEntityCapabilities(event, sharedType.get());
    }

    public static MachineBlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        BlockEntityTypeEntry<MachineBlockEntity> type = blockEntityType;
        if (type == null) {
            throw new IllegalStateException("Machine block-entity type is not registered yet");
        }
        return type.create(pos, state);
    }

    private static MachineBlockEntity createBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof MachineBlock block) {
            return block.definition().instantiateBlockEntity(type, pos, state);
        }
        throw new IllegalStateException(
                "MachineBlockEntity placed on a non-machine block: " + state.getBlock());
    }

    /** Flat machine builder. Self-registers on {@link #build()} and returns the registered definition. */
    public static final class Builder {

        private final Identifier id;
        private final RegistryCore registry;
        private final List<ComponentMount<?>> componentMounts = new ArrayList<>();
        private final Set<Identifier> componentKeys = new LinkedHashSet<>();
        private BiFunction<BlockBehaviour.Properties, Identifier, ? extends MachineBlock> blockFactory = MachineBlock::new;
        private MachineBlockEntityFactory<? extends MachineBlockEntity> blockEntityFactory = MachineBlockEntity::new;
        private @Nullable String displayName;
        private @Nullable String displayNameCn;
        private @Nullable ResourceKey<CreativeModeTab> creativeTab;
        private @Nullable MachineBlockRenderUse<?> render;

        private Builder(Identifier id, RegistryCore registry) {
            this.id = id;
            this.registry = registry;
        }

        /**
         * Choose the block class via a strong-typed factory, for example {@code MachineBlock::new} or
         * {@code OrientedMachineBlock::new}. Defaults to {@link MachineBlock}.
         */
        public Builder block(BiFunction<BlockBehaviour.Properties, Identifier, ? extends MachineBlock> blockFactory) {
            this.blockFactory = Objects.requireNonNull(blockFactory, "block factory");
            return this;
        }

        /** Choose the block-entity class. Defaults to {@link MachineBlockEntity}. */
        public Builder blockEntity(MachineBlockEntityFactory<? extends MachineBlockEntity> blockEntityFactory) {
            this.blockEntityFactory = Objects.requireNonNull(blockEntityFactory, "block entity factory");
            return this;
        }

        /**
         * Unique product entry for machine block/item names. Both en and cn are required
         * (code-style §3.13).
         */
        public Builder displayName(String displayName, String displayNameCn) {
            this.displayName = Objects.requireNonNull(displayName, "display name en");
            if (displayName.isBlank()) {
                throw new IllegalArgumentException("display name en must not be blank");
            }
            this.displayNameCn = Objects.requireNonNull(displayNameCn, "display name cn");
            if (displayNameCn.isBlank()) {
                throw new IllegalArgumentException("display name cn must not be blank");
            }
            return this;
        }

        /**
         * The creative tab the machine item is placed in. The API layer receives the key from the
         * contributor and never references builtin tab data directly.
         */
        public Builder creativeTab(ResourceKey<CreativeModeTab> creativeTab) {
            this.creativeTab = Objects.requireNonNull(creativeTab, "creative tab");
            return this;
        }

        /** Select the typed block render use for generated machine models/blockstates. */
        public Builder render(MachineBlockRenderUse<?> render) {
            this.render = Objects.requireNonNull(render, "machine render");
            return this;
        }

        /**
         * Declare one behaviour trait mount. Duplicate stable trait keys fail here at declaration
         * time, not when a player places the block.
         */
        public Builder component(ComponentMount<?> mount) {
            Objects.requireNonNull(mount, "trait mount");
            Identifier componentId = mount.key().id();
            if (!componentKeys.add(componentId)) {
                throw new IllegalStateException(
                        "Duplicate trait key '" + componentId + "' on machine '" + this.id + "'");
            }
            componentMounts.add(mount);
            return this;
        }

        /**
         * Declare a component staged behind a {@link ComponentContribution} — for example the
         * role-aware port wrapper returned by storage factories, used without {@code .role(...)}.
         * Keeps this facade agnostic of the multiblock role layer: it depends only on the component
         * contribution abstraction and delegates to {@link #component(ComponentMount)}.
         */
        public Builder component(ComponentContribution contribution) {
            Objects.requireNonNull(contribution, "component contribution");
            return component(Objects.requireNonNull(contribution.mount(), "contributed component mount"));
        }

        public MachineDefinition build() {
            if (displayName == null || displayNameCn == null) {
                throw new IllegalStateException(
                        "machine " + id + " requires displayName(en, cn); both languages are mandatory");
            }
            maybeMountMachineSearchPoolConfig();
            MachineDefinition definition = new MachineDefinition(
                    id,
                    registry,
                    blockFactory,
                    blockEntityFactory,
                    displayName,
                    displayNameCn,
                    creativeTab,
                    render,
                    componentMounts);
            return REGISTRY.register(id, definition, definition);
        }

        /**
         * One machine-level search pool for all isolatable ports (item/fluid input and output).
         * Output-only machines (export hatches) default to {@link RecipeSearchPoolId#UNIVERSAL};
         * anything with isolatable inputs defaults to {@link RecipeSearchPoolId#DEFAULT}.
         */
        private void maybeMountMachineSearchPoolConfig() {
            if (componentKeys.contains(MachineSearchPoolConfig.RECIPE_SEARCH_POOL.id())) {
                return;
            }
            boolean hasInput = false;
            boolean hasOutput = false;
            for (ComponentMount<?> mount : componentMounts) {
                for (Attachment metadata : mount.metadata()) {
                    if (!(metadata instanceof ResourcePortMetadata port) || !port.recipePoolIsolatable()) {
                        continue;
                    }
                    RecipeRole role = port.policy().recipeIo();
                    if (role.acceptsInput()) {
                        hasInput = true;
                    }
                    if (role.acceptsOutput()) {
                        hasOutput = true;
                    }
                }
            }
            if (!hasInput && !hasOutput) {
                return;
            }
            // Pure output hatches: UNIVERSAL so any recipe pool can emit into them by default.
            RecipeSearchPoolId fieldDefault = hasInput ? RecipeSearchPoolId.DEFAULT : RecipeSearchPoolId.UNIVERSAL;
            component(MachineSearchPoolConfig.mount(fieldDefault));
        }
    }
}
