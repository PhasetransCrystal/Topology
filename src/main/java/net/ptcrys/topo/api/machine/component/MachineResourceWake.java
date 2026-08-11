package net.ptcrys.topo.api.machine.component;

/**
 * Event hook fanned out by {@link MachineComponents#noteResourceContentChanged()}: any committed
 * resource storage change on this machine. Parked tickers provide it to leave tick backoff the
 * moment new inputs or freed outputs could change their answer, instead of polling every tick.
 *
 * <p>
 * Context-free capability under the {@link MachineComponents#servicesCached} contract: the
 * provider must be pure (always present, same value object). Implementations must be cheap and
 * re-entrancy safe — the fan-out runs inside storage commit paths, including the provider's own
 * recipe I/O commits.
 */
@FunctionalInterface
public interface MachineResourceWake {

    ServiceKey<MachineResourceWake, Void> KEY = ServiceKey.oi("machine_resource_wake", MachineResourceWake.class, Void.class);

    void onResourceContentChanged();
}
