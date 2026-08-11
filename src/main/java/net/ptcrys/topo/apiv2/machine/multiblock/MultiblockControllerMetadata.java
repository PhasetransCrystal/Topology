package net.ptcrys.topo.apiv2.machine.multiblock;

import net.ptcrys.topo.apiv2.machine.component.Attachment;
import net.ptcrys.topo.apiv2.machine.component.AttachmentType;
import net.ptcrys.topo.apiv2.machine.multiblock.pattern.Blueprint;

import java.util.Objects;

/**
 * Declaration metadata marking a machine definition as a multiblock controller and exposing its
 * {@link Blueprint} for introspection (JEI structure preview, future build/export tooling) — the
 * blueprint itself stays captured in the trait factory; this is the typed, queryable face of that
 * fact: {@code definition.metadata(MultiblockControllerMetadata.TYPE)}.
 *
 * <p>
 * The constructor is package-private: the only minting site is
 * {@link MultiblockController#mount}, so a definition carrying this metadata always actually
 * mounts the controller trait with this exact blueprint.
 */
public final class MultiblockControllerMetadata implements Attachment {

    public static final AttachmentType<MultiblockControllerMetadata> TYPE = net.ptcrys.topo.apiv2.OfficialOIAPIPlugin.INSTANCE
            .machine()
            .attachmentType("multiblock_controller", MultiblockControllerMetadata.class);

    private final Blueprint blueprint;

    MultiblockControllerMetadata(Blueprint blueprint) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}

    public Blueprint blueprint() {
        return blueprint;
    }

    @Override
    public AttachmentType<MultiblockControllerMetadata> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof MultiblockControllerMetadata other && blueprint.equals(other.blueprint);
    }

    @Override
    public int hashCode() {
        return blueprint.hashCode();
    }

    @Override
    public String toString() {
        return "MultiblockControllerMetadata[blueprint=" + blueprint + "]";
    }
}
