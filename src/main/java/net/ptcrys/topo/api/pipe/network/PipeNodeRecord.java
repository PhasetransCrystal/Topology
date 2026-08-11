package net.ptcrys.topo.api.pipe.network;

import net.ptcrys.topo.api.pipe.PipeDefinition;
import net.ptcrys.topo.api.pipe.PipePortFilter;
import net.ptcrys.topo.api.pipe.PipePortStrategyConfig;
import net.ptcrys.topo.api.pipe.PipeSideIntent;
import net.ptcrys.topo.api.pipe.PipeSideRole;

import net.minecraft.core.Direction;

import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Authoritative per-node state inside {@link PipeNetworksSavedData}: the resolved definition
 * handle, the packed player intent, the packed effective side roles, and the per-extract-port
 * strategy configs plus white/black filters. Mutations go through the saved data so dirty
 * tracking stays correct.
 *
 * <p>
 * Invariant: a port filter only persists for sides that also persist a strategy config (the
 * saved-data entry nests the filter inside the port entry). The runtime's filter write channel
 * heals the config first, so the invariant holds by construction.
 */
public final class PipeNodeRecord {

    private final PipeDefinition definition;
    private int intentBits;
    private int roleBits;
    private @Nullable EnumMap<Direction, PipePortStrategyConfig> extractConfigs;
    private @Nullable EnumMap<Direction, PipePortFilter> extractFilters;

    PipeNodeRecord(PipeDefinition definition, int intentBits, int roleBits,
                   @Nullable EnumMap<Direction, PipePortStrategyConfig> extractConfigs,
                   @Nullable EnumMap<Direction, PipePortFilter> extractFilters) {
        this.definition = Objects.requireNonNull(definition, "pipe definition");
        this.intentBits = intentBits;
        this.roleBits = roleBits;
        this.extractConfigs = extractConfigs;
        this.extractFilters = extractFilters;
    }

    public PipeDefinition definition() {
        return definition;
    }

    public int intentBits() {
        return intentBits;
    }

    public int roleBits() {
        return roleBits;
    }

    public PipeSideIntent intent(Direction side) {
        return PipeSideIntent.unpack(intentBits, side);
    }

    public PipeSideRole role(Direction side) {
        return PipeSideRole.unpack(roleBits, side);
    }

    public @Nullable PipePortStrategyConfig extractConfig(Direction side) {
        EnumMap<Direction, PipePortStrategyConfig> configs = extractConfigs;
        return configs == null ? null : configs.get(side);
    }

    Map<Direction, PipePortStrategyConfig> extractConfigsView() {
        EnumMap<Direction, PipePortStrategyConfig> configs = extractConfigs;
        return configs == null ? Map.of() : configs;
    }

    void setIntentBits(int intentBits) {
        this.intentBits = intentBits;
    }

    void setRoleBits(int roleBits) {
        this.roleBits = roleBits;
    }

    void putExtractConfig(Direction side, PipePortStrategyConfig config) {
        EnumMap<Direction, PipePortStrategyConfig> configs = extractConfigs;
        if (configs == null) {
            configs = new EnumMap<>(Direction.class);
            extractConfigs = configs;
        }
        configs.put(side, config);
    }

    void removeExtractConfig(Direction side) {
        EnumMap<Direction, PipePortStrategyConfig> configs = extractConfigs;
        if (configs != null) {
            configs.remove(side);
        }
        EnumMap<Direction, PipePortFilter> filters = extractFilters;
        if (filters != null) {
            // The filter belongs to the port identity; it dies with the config entry.
            filters.remove(side);
        }
    }

    /** The persisted filter of one extraction side; {@code EMPTY} when none is stored. */
    public PipePortFilter extractFilter(Direction side) {
        EnumMap<Direction, PipePortFilter> filters = extractFilters;
        PipePortFilter filter = filters == null ? null : filters.get(side);
        return filter == null ? PipePortFilter.EMPTY : filter;
    }

    void putExtractFilter(Direction side, PipePortFilter filter) {
        if (filter.isEmpty()) {
            EnumMap<Direction, PipePortFilter> filters = extractFilters;
            if (filters != null) {
                filters.remove(side);
            }
            return;
        }
        EnumMap<Direction, PipePortFilter> filters = extractFilters;
        if (filters == null) {
            filters = new EnumMap<>(Direction.class);
            extractFilters = filters;
        }
        filters.put(side, filter);
    }
}
