package net.ptcrys.topo.api.machine.multiblock;

import net.ptcrys.topo.api.OfficialTopoAPIPlugin;
import net.ptcrys.topo.api.machine.component.Attachment;
import net.ptcrys.topo.api.machine.component.AttachmentType;
import net.ptcrys.topo.api.machine.multiblock.pattern.Blueprint;

import java.util.List;
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

    public static final AttachmentType<MultiblockControllerMetadata> TYPE = OfficialTopoAPIPlugin.INSTANCE
            .machine()
            .attachmentType("multiblock_controller", MultiblockControllerMetadata.class);

    private final Blueprint blueprint;
    private final List<MultiblockModule> modules;

    MultiblockControllerMetadata(Blueprint blueprint) {
        this(blueprint, List.of());
    }

    MultiblockControllerMetadata(Blueprint blueprint, List<MultiblockModule> modules) {
        this.blueprint = Objects.requireNonNull(blueprint, "blueprint");
        this.modules = List.copyOf(Objects.requireNonNull(modules, "multiblock modules"));
    }

    /** Only activates class initialization ({@link #TYPE} registers in the field initializer). */
    public static void init() {}

    public Blueprint blueprint() {
        return blueprint;
    }

    /** Overlay blueprints attached to this controller, in declaration order. */
    public List<MultiblockModule> modules() {
        return modules;
    }

    @Override
    public AttachmentType<MultiblockControllerMetadata> type() {
        return TYPE;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof MultiblockControllerMetadata other && blueprint.equals(other.blueprint) && modules.equals(other.modules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blueprint, modules);
    }

    @Override
    public String toString() {
        return "MultiblockControllerMetadata[blueprint=" + blueprint + ", modules=" + modules + "]";
    }
}
