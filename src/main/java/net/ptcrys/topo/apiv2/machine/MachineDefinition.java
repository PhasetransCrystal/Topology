package net.ptcrys.topo.apiv2.machine;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.topo.api.lang.ChineseConvert;
import net.ptcrys.topo.apiv2.machine.component.Attachment;
import net.ptcrys.topo.apiv2.machine.component.AttachmentType;
import net.ptcrys.topo.apiv2.machine.component.ComponentMount;
import net.ptcrys.topo.apiv2.machine.component.RecipeLogicMetadata;
import net.ptcrys.topo.apiv2.machine.render.MachineBlockRenderUse;
import net.ptcrys.topo.apiv2.machine.render.MachineRenderRegistry;
import net.ptcrys.topo.apiv2.machine.resource.ResourcePortMetadata;
import net.ptcrys.topo.apiv2.recipe.OIRecipeType;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * A registered machine: its stable identity plus the full recipe for registering and running it.
 * This one type plays both the registry handle (H) and strategy (S) roles.
 *
 * <p>
 * A machine is a block + block entity composed of behaviour traits, plus optional generated
 * visual data selected through a typed machine render use.
 */
public final class MachineDefinition {

    private final Identifier id;
    private final RegistryCore registry;
    private final BiFunction<BlockBehaviour.Properties, Identifier, ? extends MachineBlock> blockFactory;
    private final MachineBlockEntityFactory<? extends MachineBlockEntity> blockEntityFactory;
    private final @Nullable String displayName;
    private final @Nullable String displayNameCn;
    private final @Nullable ResourceKey<CreativeModeTab> creativeTab;
    private final @Nullable MachineBlockRenderUse<?> render;
    /** Declared trait mounts in creation order. Order-preserving and immutable. */
    private final List<ComponentMount<?>> componentMounts;

    /** Set once by {@link #registerBlockEntry} on the mod-init thread; read later from world threads. */
    private volatile @Nullable BlockEntry<? extends MachineBlock> block;

    MachineDefinition(Identifier id,
                      RegistryCore registry,
                      BiFunction<BlockBehaviour.Properties, Identifier, ? extends MachineBlock> blockFactory,
                      MachineBlockEntityFactory<? extends MachineBlockEntity> blockEntityFactory,
                      @Nullable String displayName,
                      @Nullable String displayNameCn,
                      @Nullable ResourceKey<CreativeModeTab> creativeTab,
                      @Nullable MachineBlockRenderUse<?> render,
                      List<ComponentMount<?>> componentMounts) {
        this.id = Objects.requireNonNull(id, "id");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.blockFactory = blockFactory;
        this.blockEntityFactory = blockEntityFactory;
        this.displayName = displayName;
        this.displayNameCn = displayNameCn;
        this.creativeTab = creativeTab;
        this.render = render;
        this.componentMounts = List.copyOf(componentMounts);
    }

    /** RegistryLib handle that owns this machine's block/item mint. */
    public RegistryCore registry() {
        return registry;
    }

    public Identifier id() {
        return id;
    }

    /** Declared trait mounts in creation order. */
    List<ComponentMount<?>> componentMounts() {
        return componentMounts;
    }

    public List<ResourcePortMetadata> resourcePorts() {
        List<ResourcePortMetadata> result = new ArrayList<>();
        for (ComponentMount<?> mount : componentMounts) {
            for (Attachment metadata : mount.metadata()) {
                if (metadata instanceof ResourcePortMetadata port) {
                    result.add(port);
                }
            }
        }
        return List.copyOf(result);
    }

    public List<RecipeLogicMetadata> recipeLogicMounts() {
        return metadata(RecipeLogicMetadata.TYPE);
    }

    public List<OIRecipeType<?>> recipeTypes() {
        List<OIRecipeType<?>> recipeTypes = new ArrayList<>();
        for (RecipeLogicMetadata metadata : recipeLogicMounts()) {
            recipeTypes.addAll(metadata.recipeTypes());
        }
        return List.copyOf(recipeTypes);
    }

    public boolean supportsRecipeType(OIRecipeType<?> recipeType) {
        Objects.requireNonNull(recipeType, "recipe type");
        for (RecipeLogicMetadata metadata : recipeLogicMounts()) {
            if (metadata.recipeTypes().contains(recipeType)) {
                return true;
            }
        }
        return false;
    }

    /** All declaration metadata of the given type across every trait mount, in mount order. */
    public <M extends Attachment> List<M> metadata(AttachmentType<M> metadataType) {
        Objects.requireNonNull(metadataType, "metadata type");
        List<M> result = new ArrayList<>();
        for (ComponentMount<?> mount : componentMounts) {
            result.addAll(mount.metadata(metadataType));
        }
        return List.copyOf(result);
    }

    public BlockEntry<? extends MachineBlock> registeredBlock() {
        BlockEntry<? extends MachineBlock> current = block;
        if (current == null) {
            throw new IllegalStateException("Machine block '" + id + "' is not registered yet");
        }
        return current;
    }

    /** Register only this definition's block/item pair; the shared BE type is registered later. */
    BlockEntry<? extends MachineBlock> registerBlockEntry() {
        if (block != null) {
            throw new IllegalStateException("Machine block '" + id + "' is already registered");
        }

        BlockBuilder<? extends MachineBlock, RegistryCore> blockBuilder = newBlockBuilder(registry);
        configureBlockBuilder(registry, blockBuilder);

        BlockEntry<? extends MachineBlock> registeredBlock = blockBuilder.register();
        this.block = registeredBlock;
        return registeredBlock;
    }

    private BlockBuilder<? extends MachineBlock, RegistryCore> newBlockBuilder(RegistryCore core) {
        return core.block(id.getPath(), props -> blockFactory.apply(props, id));
    }

    private void configureBlockBuilder(
                                       RegistryCore core, BlockBuilder<? extends MachineBlock, RegistryCore> blockBuilder) {
        blockBuilder.initialProperties(Blocks.STONE);
        // Machines are wrench-only harvest (NOT in mineable/pickaxe): the wrench is the sole correct
        // tool for drops, so Jade shows just the wrench and recovering a machine requires one. Pipes
        // differ — they keep pickaxe + wrench (see PipeBlockTemplates).
        blockBuilder.addTag(MachineBlock.MINEABLE_WITH_WRENCH);
        // en + cn are mandatory at Machines.Builder#displayName(en, cn).
        blockBuilder.lang(Objects.requireNonNull(displayName, "displayName"));
        if (core.doDatagen()) {
            core.lang(
                    id.toLanguageKey("block"),
                    Map.of(
                            "zh_cn", Objects.requireNonNull(displayNameCn, "displayNameCn"),
                            "zh_tw", ChineseConvert.s2t(displayNameCn)));
        }
        configureBlockItem(blockBuilder);
        if (render != null) {
            MachineRenderRegistry.validate(render);
            MachineRenderRegistry.applyBlockModel(render, blockBuilder, this);
        }
        blockBuilder.defaultLoot();
    }

    private void configureBlockItem(BlockBuilder<? extends MachineBlock, RegistryCore> blockBuilder) {
        blockBuilder.item(item -> {
            if (creativeTab != null) {
                item.addTab(creativeTab);
            }
        });
    }

    MachineBlockEntity instantiateBlockEntity(BlockEntityType<?> sharedType, BlockPos pos, BlockState state) {
        MachineBlockEntity blockEntity = Objects.requireNonNull(
                blockEntityFactory.create(sharedType, pos, state),
                () -> "Machine block-entity factory for '" + id + "' returned null");
        MachineBlockEntity validated = validateBlockEntity(blockEntity, sharedType, pos, state);
        validated.initializeMachineRuntime();
        return validated;
    }

    private MachineBlockEntity validateBlockEntity(
                                                   MachineBlockEntity blockEntity, BlockEntityType<?> sharedType, BlockPos pos, BlockState state) {
        if (blockEntity.getType() != sharedType) {
            throw new IllegalStateException("Machine block-entity factory for '" + id + "' constructed " + blockEntity.getClass().getName() + " with unexpected BlockEntityType " + blockEntity.getType());
        }
        if (!blockEntity.getBlockPos().equals(pos)) {
            throw new IllegalStateException("Machine block-entity factory for '" + id + "' constructed " + blockEntity.getClass().getName() + " with unexpected position " + blockEntity.getBlockPos() + " instead of " + pos);
        }
        if (blockEntity.getBlockState() != state) {
            throw new IllegalStateException("Machine block-entity factory for '" + id + "' constructed " + blockEntity.getClass().getName() + " with unexpected BlockState " + blockEntity.getBlockState() + " instead of " + state);
        }
        if (blockEntity.definition() != this) {
            throw new IllegalStateException("Machine block-entity factory for '" + id + "' constructed " + blockEntity.getClass().getName() + " with unexpected MachineDefinition " + blockEntity.definition().id());
        }
        return blockEntity;
    }
}
