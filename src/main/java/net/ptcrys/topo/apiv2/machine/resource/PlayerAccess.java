package net.ptcrys.topo.apiv2.machine.resource;

/** UI visibility and click predicates for player-facing machine resource slots. */
public enum PlayerAccess {

    HIDDEN,
    VIEW_ONLY,
    INSERT_ONLY,
    EXTRACT_ONLY,
    FREE;

    public boolean isVisible() {
        return this != HIDDEN;
    }

    public boolean canPlace() {
        return this == INSERT_ONLY || this == FREE;
    }

    public boolean canTake() {
        return this == EXTRACT_ONLY || this == FREE;
    }
}
