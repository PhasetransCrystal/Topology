package net.ptcrys.topo.api.machine.resource;

import net.ptcrys.topo.api.machine.component.ServiceKey;

/**
 * Optional machine service: when present and active, the machine-level search-pool config chrome
 * ({@link MachineSearchPoolConfig}) is hidden — e.g. ME pattern hatch with per-slot separation owns
 * pools (each pattern slot = one pool spanning that slot's items + fluids).
 */
public interface SearchPoolUiControl {

    ServiceKey<SearchPoolUiControl, Void> KEY = ServiceKey.oi("search_pool_ui", SearchPoolUiControl.class, Void.class);

    /**
     * When {@code true}, {@link MachineSearchPoolConfig} must not show its search-pool config panel.
     */
    boolean suppressPortSearchPoolConfig();
}
