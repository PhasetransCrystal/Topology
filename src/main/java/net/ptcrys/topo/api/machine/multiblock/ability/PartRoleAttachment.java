package net.ptcrys.topo.api.machine.multiblock.ability;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.component.Attachment;
import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.component.ComponentMount;
import net.ptcrys.topo.api.machine.resource.ResourcePortMetadata;

import java.util.Objects;

/**
 * Declaration metadata attaching a structural multiblock capability to a mounted part trait.
 *
 * <p>
 * Two layers close the forgery path. Construction is package-private — the only minting site is
 * {@link PartRoleMount#role(PartRole)}. And {@link #validateHost(ComponentMount)} re-proves the
 * structural pairing on <em>every</em> attach: a minted instance harvested from one mount and
 * re-stapled onto another (via {@code ComponentMount#withMetadata} or {@code ComponentKey#mount}) fails
 * unless that host also exposes a matching resource port.
 */
public final class PartRoleAttachment implements Attachment {

    public static final AttachmentType<PartRoleAttachment> TYPE = OfficialTopoAPIPlugin.INSTANCE
            .machine()
            .attachmentType("part_capability", PartRoleAttachment.class);

    private final PartRole capability;

    PartRoleAttachment(PartRole capability) {
        this.capability = Objects.requireNonNull(capability, "part capability");
    }

    public PartRole role() {
        return capability;
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}

    /**
     * The structural invariant: the host mount must expose a {@link ResourcePortMetadata} whose
     * resource type equals this capability's and whose recipe-IO role allows it. Runs on every
     * {@link ComponentMount} construction, so the check is a structural property of the mount, not a
     * courtesy of the {@code cap(...)} call site.
     */
    @Override
    public void validateHost(ComponentMount<?> host) {
        boolean sawResourcePort = false;
        for (Attachment entry : host.metadata()) {
            if (entry instanceof ResourcePortMetadata port) {
                sawResourcePort = true;
                if (port.resourceType() == capability.resourceType() && port.recipeIo().allows(capability.recipeIo())) {
                    return;
                }
            }
        }
        if (!sawResourcePort) {
            throw new IllegalStateException("Part capability " + capability.id() + " must be attached to a resource storage trait mount");
        }
        throw new IllegalStateException("Part capability " + capability.id() + " expects " + capability.resourceType().id() + "/" + capability.recipeIo() + " but trait mount '" + host.key().id() + "' exposes incompatible resource port metadata");
    }

    @Override
    public AttachmentType<PartRoleAttachment> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof PartRoleAttachment other && capability.equals(other.capability);
    }

    @Override
    public int hashCode() {
        return capability.hashCode();
    }

    @Override
    public String toString() {
        return "PartRoleAttachment[capability=" + capability + "]";
    }
}
