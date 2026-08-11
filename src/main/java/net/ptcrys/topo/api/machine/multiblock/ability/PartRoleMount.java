package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.machine.component.ComponentContribution;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.component.MachineComponent;

import java.util.Objects;

/**
 * A freshly created resource-port {@link ComponentMount} staged for an optional structural part
 * capability. This is the single validated construction path for {@link PartRoleAttachment}:
 * its canonical constructor is package-private, so a capability can never be forged onto a port that
 * lacks a matching resource. {@link #role(PartRole)} runs the structural validation and, only
 * when it passes, builds the metadata (same package — allowed) and returns the decorated mount.
 *
 * <p>
 * Lives in the multiblock {@code ability} package so it can carry the {@code multiblock.ability}
 * types while importing {@code trait.*} in one direction only — there is no {@code trait <->
 * multiblock.ability} cycle. The bare (no-capability) mount reaches the machine builder through
 * {@link ComponentContribution#mount()}, so the builder never depends on this package.
 */
public final class PartRoleMount<T extends MachineComponent> implements ComponentContribution {

    private final ComponentMount<T> mount;

    public PartRoleMount(ComponentMount<T> mount) {
        this.mount = Objects.requireNonNull(mount, "trait mount");
    }

    /** The underlying mount with no part capability attached. */
    @Override
    public ComponentMount<T> mount() {
        return mount;
    }

    /**
     * Attach {@code capability} to this mount. The structural pairing — the mount must carry a
     * {@code ResourcePortMetadata} whose {@code resourceType()} equals the capability's and whose
     * {@code recipeIo()} allows its role — is proved by
     * {@link PartRoleAttachment#validateHost} when the decorated mount is constructed; a
     * mismatch throws {@link IllegalStateException} right here at the declaration expression.
     *
     * @return a new mount carrying the validated {@link PartRoleAttachment}; the original mount
     *         is not mutated.
     */
    public ComponentMount<T> role(PartRole capability) {
        Objects.requireNonNull(capability, "part capability");
        return mount.withMetadata(new PartRoleAttachment(capability));
    }
}
