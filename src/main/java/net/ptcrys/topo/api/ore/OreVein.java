package net.ptcrys.topo.api.ore;

import net.ptcrys.topo.api.api.lang.LangKey;
import net.ptcrys.topo.api.ore.mode.OreVeinMode;
import net.ptcrys.topo.api.ore.policy.OreAirExposurePolicy;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * KHS product handle for one registered ore vein. Holds a sealed {@link OrePlacement} (feature or
 * grid), environment, air-exposure policy, material entries, and a required display name. Built by
 * {@link OreVeins#register}.
 */
public final class OreVein {

    private final Identifier id;
    private final OreEnvironment environment;
    private final OrePlacement placement;
    private final OreAirExposurePolicy airExposurePolicy;
    private final double airExposureDiscardChance;
    private final List<OreVeinEntry> entries;
    private final LangKey nameLang;

    OreVein(
            Identifier id,
            OreEnvironment environment,
            OrePlacement placement,
            OreAirExposurePolicy airExposurePolicy,
            double airExposureDiscardChance,
            List<OreVeinEntry> entries,
            LangKey nameLang) {
        this.id = id;
        this.environment = environment;
        this.placement = placement;
        this.airExposurePolicy = airExposurePolicy;
        this.airExposureDiscardChance = airExposureDiscardChance;
        this.entries = List.copyOf(entries);
        this.nameLang = nameLang;
    }

    public Identifier id() {
        return id;
    }

    public OreEnvironment environment() {
        return environment;
    }

    public OrePlacement placement() {
        return placement;
    }

    public OreVeinMode mode() {
        return placement.mode();
    }

    public OreAirExposurePolicy airExposurePolicy() {
        return airExposurePolicy;
    }

    public double airExposureDiscardChance() {
        return airExposureDiscardChance;
    }

    public List<OreVeinEntry> entries() {
        return entries;
    }

    public LangKey nameLang() {
        return nameLang;
    }

    public Component displayName() {
        return nameLang.getComponent();
    }

    public int minY() {
        return placement.minY();
    }

    public int maxY() {
        return placement.maxY();
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
