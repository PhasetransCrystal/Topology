package net.ptcrys.topo.api.api.tick;

/**
 * One scheduled unit of tick work.
 *
 * <p>
 * For sync tick kinds, {@code gameTime} is {@code level.getGameTime()}. For async tick kinds it
 * is the owning {@link TickHub}'s async heartbeat counter and must not be treated as Minecraft game
 * time.
 */
@FunctionalInterface
public interface TickHook {

    void tick(long gameTime, TickHandle handle);
}
