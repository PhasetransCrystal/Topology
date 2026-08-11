package net.ptcrys.topo.api.pipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.registrylib.builders.BlockBuilder;
import net.ptcrys.registrylib.util.entry.BlockEntry;
import net.ptcrys.topo.api.machine.resource.MachineResourceType;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.material.PushReaction;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Registered pipe handle (H == S in the {@link Pipes} table). One definition is one
 * (resource kind x tier) block: it owns the per-tier performance numbers, the strategy set the
 * tier unlocks, the block template driving its registration/datagen and — after the bootstrap
 * block pass — the strong block entry.
 */
public final class PipeDefinition {

    private final Identifier id;
    private final PipeResourceProfile profile;
    private final PipeBlockTemplate blockTemplate;
    private final String displayName;
    private final @Nullable ResourceKey<CreativeModeTab> creativeTab;
    private final int maxExtractRate;
    private final int nodeThroughput;
    private final List<PipeStrategyOffer> strategies;
    private final PipeFilterSettings filterSettings;
    /** Largest {@code maxInterval} across the offers — sizes the network's future ledger. */
    private final int maxAggregationWindow;

    /** Set once by {@link #registerBlockEntry} on the mod-init thread; read from world threads. */
    private volatile @Nullable BlockEntry<PipeBlock> block;

    PipeDefinition(
                   Identifier id,
                   PipeResourceProfile profile,
                   PipeBlockTemplate blockTemplate,
                   String displayName,
                   @Nullable ResourceKey<CreativeModeTab> creativeTab,
                   int maxExtractRate,
                   int nodeThroughput,
                   List<PipeStrategyOffer> strategies,
                   PipeFilterSettings filterSettings) {
        this.id = id;
        this.profile = profile;
        this.blockTemplate = blockTemplate;
        this.displayName = displayName;
        this.creativeTab = creativeTab;
        this.maxExtractRate = maxExtractRate;
        this.nodeThroughput = nodeThroughput;
        this.strategies = strategies;
        this.filterSettings = filterSettings;
        int window = 1;
        for (PipeStrategyOffer offer : strategies) {
            window = Math.max(window, offer.aggregation().maxInterval());
        }
        this.maxAggregationWindow = window;
    }

    public Identifier id() {
        return id;
    }

    public PipeResourceProfile profile() {
        return profile;
    }

    /** The resource kind this pipe transports; also the network-compatibility key. */
    public MachineResourceType<?> resourceType() {
        return profile.resourceType();
    }

    /** The template driving this pipe's block/item registration and asset datagen. */
    public PipeBlockTemplate blockTemplate() {
        return blockTemplate;
    }

    public String displayName() {
        return displayName;
    }

    /** Hard per-tick extraction cap of this tier; strategy budgets are clamped into it. */
    public int maxExtractRate() {
        return maxExtractRate;
    }

    /**
     * Hard per-window batch cap for the given aggregation interval:
     * {@code maxExtractRate × interval}, saturated to int. The single definition point every
     * amount clamp (runtime, UI, Jade) funnels through.
     */
    public int maxBatchAmount(int interval) {
        return (int) Math.min((long) maxExtractRate * Math.max(1, interval), Integer.MAX_VALUE);
    }

    /** Per-tick throughput allowance of one node of this tier (path-occupancy bucket model). */
    public int nodeThroughput() {
        return nodeThroughput;
    }

    /** Strategy offers this tier unlocks, declaration order; the first is the initial one. */
    public List<PipeStrategyOffer> strategies() {
        return strategies;
    }

    /** The offer a freshly created extraction port starts with (first declaration). */
    public PipeStrategyOffer initialOffer() {
        return strategies.get(0);
    }

    /** The offer mounting {@code strategy} on this pipe, or {@code null} when not offered. */
    public @Nullable PipeStrategyOffer offerFor(PipeDistributionStrategy strategy) {
        for (PipeStrategyOffer offer : strategies) {
            if (offer.strategy() == strategy) {
                return offer;
            }
        }
        return null;
    }

    public boolean offersStrategy(PipeDistributionStrategy strategy) {
        return offerFor(strategy) != null;
    }

    /** Largest offered {@code maxInterval}; sizes the per-network future reservation ledger. */
    public int maxAggregationWindow() {
        return maxAggregationWindow;
    }

    /** White/black list envelope of this tier; {@link PipeFilterSettings#NONE} disables the UI. */
    public PipeFilterSettings filterSettings() {
        return filterSettings;
    }

    public BlockEntry<PipeBlock> registeredBlock() {
        BlockEntry<PipeBlock> current = block;
        if (current == null) {
            throw new IllegalStateException("Pipe block '" + id + "' is not registered yet");
        }
        return current;
    }

    /** Register this definition's block/item pair. Called once by {@link Pipes}. */
    BlockEntry<PipeBlock> registerBlockEntry(RegistryCore core) {
        Objects.requireNonNull(core, "registry core");
        if (block != null) {
            throw new IllegalStateException("Pipe block '" + id + "' is already registered");
        }
        BlockBuilder<PipeBlock, RegistryCore> builder = core.block(id.getPath(), props -> new PipeBlock(props, this));
        blockTemplate.configureBlock(builder, this);
        // Framework invariants after the template's style pass: the multipart visual needs
        // noOcclusion, and piston movement would desync the SavedData authority.
        builder.properties(props -> blockTemplate.styleBlockProperties(props)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK));
        builder.lang(displayName);
        builder.item(item -> {
            if (creativeTab != null) {
                item.addTab(creativeTab);
            }
            blockTemplate.configureItem(item, this);
        });
        BlockEntry<PipeBlock> registered = builder.register();
        this.block = registered;
        return registered;
    }
}
