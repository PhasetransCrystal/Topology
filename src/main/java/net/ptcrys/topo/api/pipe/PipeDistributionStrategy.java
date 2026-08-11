package net.ptcrys.topo.api.pipe;

import net.ptcrys.topo.api.lang.LangKey;
import net.ptcrys.topo.api.pipe.ui.PipePortAccess;
import net.ptcrys.topo.api.pipe.ui.PipePortUiCollector;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.mojang.serialization.MapCodec;

/**
 * Distribution strategy table entry (H == S). One stable ID covers the whole behaviour face of a
 * strategy: tick-time allocation, the port-level config shape it persists, and the config UI
 * widgets it contributes to the port screen — these always live and die together.
 */
public interface PipeDistributionStrategy {

    /** Stable ID; must equal the registration key. Persisted inside port configs. */
    Identifier id();

    /**
     * Display-name handle ({@code pipe.strategy.<namespace>.<path>}). Call sites use
     * {@link #displayName()} / {@link #description()} — do not re-derive keys by string concat.
     */
    LangKey nameLang();

    /** Short description handle ({@code … .desc}), registered beside {@link #nameLang()}. */
    LangKey descriptionLang();

    /** Short code handle ({@code … .short}) for compact UI (port side rail). */
    LangKey shortNameLang();

    default Component displayName() {
        return nameLang().getComponent();
    }

    default Component description() {
        return descriptionLang().getComponent();
    }

    default Component shortName() {
        return shortNameLang().getComponent();
    }

    /**
     * @deprecated Prefer {@link #nameLang()} / {@link #displayName()}. Kept for network payloads that
     *             still ship the raw i18n key string.
     */
    @Deprecated
    default String translationKey() {
        return nameLang().key();
    }

    /** Codec for this strategy's port config; dispatched on {@link #id()}. */
    MapCodec<? extends PipePortStrategyConfig> configCodec();

    /**
     * The config a port starts with at the moment it selects this strategy (port creation,
     * strategy switch, or data-recovery reset after an unreadable save). Every field comes from
     * the registration-time offer of this pipe — nothing is implicit.
     */
    PipePortStrategyConfig initialPortConfig(PipeDefinition definition, AggregationWindow aggregation);

    /**
     * Effective per-tick extraction rate. The engine clamps the result into
     * {@code [0, definition.maxExtractRate()]} and multiplies by the configured aggregation
     * interval to form the batch budget.
     */
    default int budget(PipeDefinition definition, PipePortStrategyConfig config) {
        return definition.maxExtractRate();
    }

    /** Allocate one batch budget across {@code context} destinations. */
    void distribute(PipeDistributionContext context);

    /** Contribute config widgets to the port screen; default contributes nothing. */
    default void contributeConfigUi(PipePortUiCollector collector, PipePortAccess access) {}
}
