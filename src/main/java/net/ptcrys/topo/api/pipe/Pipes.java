package net.ptcrys.topo.api.pipe;

import net.ptcrys.registrylib.RegistryCore;
import net.ptcrys.topo.api.api.infrastructure.FreezableStrategyRegistry;
import net.ptcrys.topo.helper.IdHelper;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owner facade for the pipe domain table (K = {@link Identifier}, H == S =
 * {@link PipeDefinition}). The builder exception is approved in
 * {@code standards/pipe-domain.md}: strategies are a variable-arity dimension, fields are
 * cross-validated before commit, and block registration is a same-origin by-product consumed by
 * the bootstrap block pass.
 */
public final class Pipes {

    private static final FreezableStrategyRegistry<Identifier, PipeDefinition, PipeDefinition> REGISTRY = FreezableStrategyRegistry.create("pipes");
    private static boolean blocksRegistered;

    private Pipes() {}

    /** Begin declaring a pipe. The path is the identity definition point in the Topo namespace. */
    public static Builder register(String path) {
        return new Builder(IdHelper.oi(Objects.requireNonNull(path, "pipe path")));
    }

    public static PipeDefinition require(Identifier id) {
        return REGISTRY.require(id);
    }

    public static @Nullable PipeDefinition byId(Identifier id) {
        return REGISTRY.get(id);
    }

    /** Frozen, cached view of every registered pipe. Safe to iterate without per-call allocation. */
    public static List<PipeDefinition> registered() {
        return REGISTRY.handlesView();
    }

    public static void freeze() {
        REGISTRY.freeze();
    }

    /**
     * Bootstrap driver: register every pipe's block/item pair through its block template, then
     * let each distinct {@link PipeBlockTemplate#sharedGroup} contribute its shared datagen once.
     */
    public static void registerBlocks(RegistryCore core) {
        Objects.requireNonNull(core, "registry core");
        if (!REGISTRY.isFrozen()) {
            throw new IllegalStateException("Pipe registry must be frozen before pipe blocks are registered");
        }
        if (blocksRegistered) {
            throw new IllegalStateException("Pipe blocks are already registered");
        }
        blocksRegistered = true;
        for (PipeDefinition definition : registered()) {
            definition.registerBlockEntry(core);
        }
        Set<Object> sharedGroups = new LinkedHashSet<>();
        for (PipeDefinition definition : registered()) {
            if (sharedGroups.add(definition.blockTemplate().sharedGroup())) {
                definition.blockTemplate().contributeShared(core);
            }
        }
    }

    /** Flat pipe builder. Self-registers on {@link #build()} and returns the registered handle. */
    public static final class Builder {

        private final Identifier id;
        private final List<PipeStrategyOffer> strategies = new ArrayList<>();
        private final Set<Identifier> strategyIds = new LinkedHashSet<>();
        private @Nullable PipeResourceProfile profile;
        private @Nullable PipeBlockTemplate blockTemplate;
        private @Nullable String displayName;
        private @Nullable ResourceKey<CreativeModeTab> creativeTab;
        private @Nullable PipeFilterSettings filterSettings;
        private int maxExtractRate;
        private int nodeThroughput;

        private Builder(Identifier id) {
            this.id = id;
        }

        public Builder resource(PipeResourceProfile profile) {
            this.profile = Objects.requireNonNull(profile, "pipe resource profile");
            return this;
        }

        /** The template carrying this pipe's block style, tags, loot and asset datagen. */
        public Builder blockTemplate(PipeBlockTemplate blockTemplate) {
            this.blockTemplate = Objects.requireNonNull(blockTemplate, "pipe block template");
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = Objects.requireNonNull(displayName, "display name");
            return this;
        }

        public Builder creativeTab(ResourceKey<CreativeModeTab> creativeTab) {
            this.creativeTab = Objects.requireNonNull(creativeTab, "creative tab");
            return this;
        }

        public Builder maxExtractRate(int maxExtractRate) {
            this.maxExtractRate = maxExtractRate;
            return this;
        }

        public Builder nodeThroughput(int nodeThroughput) {
            this.nodeThroughput = nodeThroughput;
            return this;
        }

        /**
         * Declare the white/black list envelope of this tier — required like {@code strategy},
         * no omission form. Tiers without the feature declare {@link PipeFilterSettings#NONE};
         * a non-{@code NONE} envelope requires the resource profile to carry a filter adapter.
         */
        public Builder filter(PipeFilterSettings filterSettings) {
            this.filterSettings = Objects.requireNonNull(filterSettings, "pipe filter settings");
            return this;
        }

        /**
         * Declare one unlocked strategy with its aggregation window — both required, no omission
         * form. Declaration order is GUI order; the first declaration is the initial strategy.
         */
        public Builder strategy(PipeDistributionStrategy strategy, AggregationWindow aggregation) {
            Objects.requireNonNull(strategy, "pipe distribution strategy");
            Objects.requireNonNull(aggregation, "aggregation window");
            if (!strategyIds.add(strategy.id())) {
                throw new IllegalStateException(
                        "Duplicate strategy '" + strategy.id() + "' on pipe '" + id + "'");
            }
            strategies.add(new PipeStrategyOffer(strategy, aggregation));
            return this;
        }

        public PipeDefinition build() {
            PipeResourceProfile builtProfile = Objects.requireNonNull(profile, "pipe '" + id + "' missing resource profile");
            PipeBlockTemplate builtTemplate = Objects.requireNonNull(blockTemplate, "pipe '" + id + "' missing block template");
            String builtDisplayName = Objects.requireNonNull(displayName, "pipe '" + id + "' missing display name");
            PipeFilterSettings builtFilter = Objects.requireNonNull(filterSettings, "pipe '" + id + "' missing filter settings");
            if (maxExtractRate <= 0) {
                throw new IllegalStateException("Pipe '" + id + "' needs a positive maxExtractRate");
            }
            if (nodeThroughput <= 0) {
                throw new IllegalStateException("Pipe '" + id + "' needs a positive nodeThroughput");
            }
            if (strategies.isEmpty()) {
                throw new IllegalStateException("Pipe '" + id + "' needs at least one distribution strategy");
            }
            if (builtFilter.enabled() && builtProfile.filterAdapter() == null) {
                throw new IllegalStateException("Pipe '" + id + "' declares filter capacity " + builtFilter.entryCapacity() + " but its resource profile has no filter adapter");
            }
            PipeDefinition definition = new PipeDefinition(
                    id,
                    builtProfile,
                    builtTemplate,
                    builtDisplayName,
                    creativeTab,
                    maxExtractRate,
                    nodeThroughput,
                    List.copyOf(strategies),
                    builtFilter);
            return REGISTRY.register(id, definition, definition);
        }
    }
}
