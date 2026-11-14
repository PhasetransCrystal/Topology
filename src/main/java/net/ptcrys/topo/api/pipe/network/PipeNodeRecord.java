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
 * handle, the packed player intent, the packed effective side roles, and one optional
 * {@link ExtractPort} per extracting side.
 *
 * <p>
 * A filter is structurally part of its port entry, so it cannot outlive the strategy config it
 * belongs to. All mutations go through the saved data so dirty tracking stays correct.
 */
public final class PipeNodeRecord {

    /** One extraction side's persisted settings. */
    record ExtractPort(PipePortStrategyConfig config, PipePortFilter filter) {

        ExtractPort {
            Objects.requireNonNull(config, "port config");
            Objects.requireNonNull(filter, "port filter");
        }

        ExtractPort withConfig(PipePortStrategyConfig value) {
            return value.equals(config) ? this : new ExtractPort(value, filter);
        }

        ExtractPort withFilter(PipePortFilter value) {
            return value.equals(filter) ? this : new ExtractPort(config, value);
        }
    }

    private final PipeDefinition definition;
    private int intentBits;
    private int roleBits;
    private @Nullable EnumMap<Direction, ExtractPort> extractPorts;

    PipeNodeRecord(PipeDefinition definition, int intentBits, int roleBits,
                   @Nullable EnumMap<Direction, ExtractPort> extractPorts) {
        this.definition = Objects.requireNonNull(definition, "pipe definition");
        this.intentBits = intentBits;
        this.roleBits = roleBits;
        this.extractPorts = extractPorts;
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
        ExtractPort port = port(side);
        return port == null ? null : port.config();
    }

    Map<Direction, ExtractPort> extractPortsView() {
        EnumMap<Direction, ExtractPort> ports = extractPorts;
        return ports == null ? Map.of() : ports;
    }

    void setIntentBits(int intentBits) {
        this.intentBits = intentBits;
    }

    void setRoleBits(int roleBits) {
        this.roleBits = roleBits;
    }

    void putExtractConfig(Direction side, PipePortStrategyConfig config) {
        ExtractPort current = port(side);
        ExtractPort updated = current == null ? new ExtractPort(config, PipePortFilter.EMPTY) : current.withConfig(config);
        putPort(side, updated);
    }

    void removeExtractConfig(Direction side) {
        EnumMap<Direction, ExtractPort> ports = extractPorts;
        if (ports != null) {
            ports.remove(side);
            if (ports.isEmpty()) {
                extractPorts = null;
            }
        }
    }

    /** The persisted filter of one extraction side; {@code EMPTY} when the port is absent. */
    public PipePortFilter extractFilter(Direction side) {
        ExtractPort port = port(side);
        return port == null ? PipePortFilter.EMPTY : port.filter();
    }

    void putExtractFilter(Direction side, PipePortFilter filter) {
        ExtractPort current = port(side);
        if (current != null) {
            putPort(side, current.withFilter(filter));
        }
    }

    private @Nullable ExtractPort port(Direction side) {
        EnumMap<Direction, ExtractPort> ports = extractPorts;
        return ports == null ? null : ports.get(side);
    }

    private void putPort(Direction side, ExtractPort port) {
        EnumMap<Direction, ExtractPort> ports = extractPorts;
        if (ports == null) {
            ports = new EnumMap<>(Direction.class);
            extractPorts = ports;
        }
        ports.put(side, port);
    }
}
