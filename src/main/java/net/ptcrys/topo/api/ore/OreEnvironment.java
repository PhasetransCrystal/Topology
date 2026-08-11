package net.ptcrys.topo.api.ore;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a vein may generate: dimension rules (with biome/display contributions) and host-stone →
 * ore-form rules. Direct-singleton value referenced by strong handle from vein declarations. Height
 * lives only on {@link OrePlacement} — environments do not own a second Y band.
 */
public final class OreEnvironment {

    private final List<OreDimensionRule> dimensions;
    private final List<OreHostRule> hostRules;

    OreEnvironment(List<OreDimensionRule> dimensions, List<OreHostRule> hostRules) {
        this.dimensions = List.copyOf(Objects.requireNonNull(dimensions, "dimensions"));
        this.hostRules = List.copyOf(Objects.requireNonNull(hostRules, "hostRules"));
        if (this.dimensions.isEmpty()) {
            throw new IllegalArgumentException("Ore environment needs at least one dimension");
        }
        if (this.hostRules.isEmpty()) {
            throw new IllegalArgumentException("Ore environment needs at least one host rule");
        }
    }

    public List<OreDimensionRule> dimensions() {
        return dimensions;
    }

    public List<OreHostRule> hostRules() {
        return hostRules;
    }

    public boolean canGenerateIn(ResourceKey<Level> dimension) {
        for (OreDimensionRule rule : dimensions) {
            if (rule.dimension().equals(dimension)) {
                return true;
            }
        }
        return false;
    }

    public Optional<OreDimensionRule> dimensionRule(ResourceKey<Level> dimension) {
        for (OreDimensionRule rule : dimensions) {
            if (rule.dimension().equals(dimension)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }

    public Optional<OreHostRule> hostFor(BlockState state) {
        for (OreHostRule rule : hostRules) {
            if (rule.matches(state)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }
}
