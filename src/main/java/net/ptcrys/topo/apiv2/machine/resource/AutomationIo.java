package net.ptcrys.topo.apiv2.machine.resource;

import java.util.Objects;

/** Automation-facing insert/extract policy for exposed block capability handlers. */
public enum AutomationIo {

    NONE(false, false),
    INSERT(true, false),
    EXTRACT(false, true),
    BOTH(true, true);

    private final boolean canInsert;
    private final boolean canExtract;

    AutomationIo(boolean canInsert, boolean canExtract) {
        this.canInsert = canInsert;
        this.canExtract = canExtract;
    }

    public boolean canInsert() {
        return canInsert;
    }

    public boolean canExtract() {
        return canExtract;
    }

    public boolean allows(AutomationIo requested) {
        Objects.requireNonNull(requested, "requested capability IO");
        if (requested.canInsert && !canInsert) {
            return false;
        }
        return !requested.canExtract || canExtract;
    }
}
