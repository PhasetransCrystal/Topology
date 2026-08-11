package net.ptcrys.topo.api.machine.resource;

import java.util.Objects;

/** Machine-internal recipe role for resource ports and recipe UI slots. */
public enum RecipeRole {

    NONE(false, false),
    INPUT(true, false),
    OUTPUT(false, true),
    BOTH(true, true);

    private final boolean acceptsInput;
    private final boolean acceptsOutput;

    RecipeRole(boolean acceptsInput, boolean acceptsOutput) {
        this.acceptsInput = acceptsInput;
        this.acceptsOutput = acceptsOutput;
    }

    public boolean acceptsInput() {
        return acceptsInput;
    }

    public boolean acceptsOutput() {
        return acceptsOutput;
    }

    public boolean allows(RecipeRole requested) {
        Objects.requireNonNull(requested, "requested recipe IO");
        if (requested.acceptsInput && !acceptsInput) {
            return false;
        }
        return !requested.acceptsOutput || acceptsOutput;
    }
}
