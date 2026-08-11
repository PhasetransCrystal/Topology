package net.ptcrys.topo.api.machine.multiblock;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Pure per-level holder of the multiblock runtime services. Holds no event subscriptions — world
 * events are observed by {@link MultiblockChangeWatcher}, which also calls {@link #forget} on level
 * unload.
 *
 * <p>
 * Threading contract: the returned {@link Services} (and the {@link MultiblockClaimIndex} /
 * {@link MultiblockRecheckDirtySet} inside) are server-thread-only and not thread-safe. All binder methods
 * share one lock so the weak map itself stays internally consistent regardless of which path
 * (controller tick, event ingress, unload) touches it.
 */
public final class MultiblockLevelBinder {

    private static final Map<Level, Services> SERVICES = new WeakHashMap<>();

    private MultiblockLevelBinder() {}

    /** Returns the services for {@code level}, creating them on first access. */
    public static synchronized Services services(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return SERVICES.computeIfAbsent(level, ignored -> new Services(level));
    }

    /**
     * Returns the services for {@code level} only if they already exist, never creating them.
     * Event-path ingress must use this so levels without multiblock activity pay no allocation.
     */
    public static synchronized @Nullable Services servicesIfPresent(ServerLevel level) {
        Objects.requireNonNull(level, "level");
        return SERVICES.get(level);
    }

    /** Drops the services held for {@code level}; the next {@link #services} call recreates them. */
    public static synchronized void forget(Level level) {
        SERVICES.remove(Objects.requireNonNull(level, "level"));
    }

    /** Bundle of the per-level multiblock services. */
    public record Services(MultiblockClaimIndex claims, MultiblockRecheckDirtySet rechecks) {

        Services(ServerLevel level) {
            this(new MultiblockClaimIndex(level), new MultiblockRecheckDirtySet());
        }
    }
}
