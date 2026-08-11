package net.ptcrys.topo.data.machine;

import net.ptcrys.topo.api.machine.render.MachineShellMaterial;
import net.ptcrys.topo.data.OfficialTopoPlugin;

/**
 * Machine construction tier (hull shell, display suffix, rated I/O capacity).
 *
 * <p>
 * Recipe performance is <b>not</b> stored here — machines mount capability-specific modifiers from
 * {@code PerformanceRecipeModifiers} (energy/heat/adv consumers, producers, converter).
 *
 * <p>
 * Textures live under {@code textures/block/machine_hulls/<pathSuffix>/} (side/top/bottom).
 */
public enum MachineTier {

    T1(1),
    T2(4),
    T3(16);

    private final int ratedPowerMultiplier;
    private final String hullTextureFolder;
    private final MachineShellMaterial shellMaterial;

    MachineTier(int ratedPowerMultiplier) {
        this.ratedPowerMultiplier = ratedPowerMultiplier;
        String suffix = name().toLowerCase();
        this.hullTextureFolder = "block/machine_hulls/" + suffix;
        this.shellMaterial = new MachineShellMaterial(
                OfficialTopoPlugin.INSTANCE.machine().id(hullTextureFolder + "/bottom"),
                OfficialTopoPlugin.INSTANCE.machine().id(hullTextureFolder + "/side"),
                OfficialTopoPlugin.INSTANCE.machine().id(hullTextureFolder + "/top"));
    }

    /**
     * Multiplier on scalar port capacity / rated input budget (construction side: 1 → 4 → 16).
     */
    public int ratedPowerMultiplier() {
        return ratedPowerMultiplier;
    }

    /** Registry / texture folder key: {@code t1}, {@code t2}, {@code t3}. */
    public String pathSuffix() {
        return name().toLowerCase();
    }

    /**
     * Texture path prefix for this tier's hull (no leading slash), e.g. {@code
     * block/machine_hulls/t1}.
     */
    public String hullTextureFolder() {
        return hullTextureFolder;
    }

    /** Shell material used by single-block machine renders and hull blocks. */
    public MachineShellMaterial shellMaterial() {
        return shellMaterial;
    }

    /** 1 for T1, 2 for T2, 3 for T3. */
    public int level() {
        return ordinal() + 1;
    }

    /** English display suffix, always appended: {@code " (Tier 1)"}. */
    public String displaySuffixEn() {
        return " (Tier " + level() + ")";
    }

    /** Chinese display suffix, always appended: {@code "（Tier 1）"}. */
    public String displaySuffixCn() {
        return "（Tier " + level() + "）";
    }
}
