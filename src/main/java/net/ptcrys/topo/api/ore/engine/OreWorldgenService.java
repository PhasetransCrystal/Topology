package net.ptcrys.topo.api.ore.engine;

import net.ptcrys.topo.api.ore.OreVeins;

import net.minecraft.core.RegistryAccess;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Builds and caches the {@link OreVeinPlanner} per {@link RegistryAccess}. The vein set is the frozen
 * {@link OreVeins} view, so the planner is built once at the lifecycle boundary and reused.
 */
public final class OreWorldgenService {

    private static final Map<RegistryAccess, OreVeinPlanner> PLANNERS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile RegistryAccess lastRegistryAccess;
    private static volatile OreVeinPlanner lastPlanner;

    private OreWorldgenService() {}

    public static OreVeinPlanner planner(RegistryAccess registryAccess) {
        OreVeinPlanner cached = lastPlanner;
        if (cached != null && lastRegistryAccess == registryAccess) {
            return cached;
        }
        synchronized (PLANNERS) {
            OreVeinPlanner planner = PLANNERS.computeIfAbsent(registryAccess, ignored -> build());
            lastRegistryAccess = registryAccess;
            lastPlanner = planner;
            return planner;
        }
    }

    public static OreVeinPlanner bootstrapPlanner() {
        return build();
    }

    private static OreVeinPlanner build() {
        return new OreVeinPlanner(OreVeins.view(), OreBlockResolver.materialHelper());
    }
}
